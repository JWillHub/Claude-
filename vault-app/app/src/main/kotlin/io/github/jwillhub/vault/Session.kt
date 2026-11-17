package io.github.jwillhub.vault

import android.content.Context
import android.os.SystemClock
import io.github.jwillhub.vault.core.Entry
import io.github.jwillhub.vault.core.Matcher
import io.github.jwillhub.vault.core.VaultCodec
import io.github.jwillhub.vault.core.VaultCrypto
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The unlocked vault. While unlocked, the key and the decrypted logins live only in this process's
 * memory; nothing readable is ever written to storage. Locking wipes both.
 */
object Session {
    private const val PREFS = "settings"
    private const val LOCK_MINUTES = "lock_minutes"
    val LOCK_CHOICES = intArrayOf(1, 5, 15, 60)

    private var key: ByteArray? = null
    private var salt: ByteArray? = null
    private var iterations = 0
    private var entries: List<Entry> = emptyList()
    private var lastActive = 0L

    private fun file(context: Context) = File(context.filesDir, "vault.bin")

    fun exists(context: Context): Boolean = file(context).isFile

    fun lockMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(LOCK_MINUTES, 5)

    fun setLockMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(LOCK_MINUTES, minutes).apply()
    }

    /** True while unlocked. Locks first if the vault has sat unused for longer than the chosen time. */
    @Synchronized
    fun isUnlocked(context: Context): Boolean {
        if (key == null) return false
        val idle = SystemClock.elapsedRealtime() - lastActive
        if (idle > lockMinutes(context) * 60_000L) {
            lock()
            return false
        }
        return true
    }

    /** Called on every tap inside the app, so the lock timer counts from the last use. */
    @Synchronized
    fun touch() {
        if (key != null) lastActive = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun lock() {
        key?.fill(0)
        key = null
        salt = null
        entries = emptyList()
    }

    /** Creates an empty vault protected by [password]. Slow: call off the main thread. */
    fun create(context: Context, password: CharArray) {
        val newSalt = VaultCrypto.newSalt()
        val newKey = VaultCrypto.deriveKey(password, newSalt, VaultCrypto.DEFAULT_ITERATIONS)
        synchronized(this) {
            lock()
            key = newKey
            salt = newSalt
            iterations = VaultCrypto.DEFAULT_ITERATIONS
            entries = emptyList()
            lastActive = SystemClock.elapsedRealtime()
            persist(context)
        }
    }

    /** Returns false when the password is wrong. Slow: call off the main thread. */
    fun unlock(context: Context, password: CharArray): Boolean {
        val bytes = file(context).readBytes()
        val header = VaultCrypto.readHeader(bytes) ?: return false
        val candidate = VaultCrypto.deriveKey(password, header.salt, header.iterations)
        val plain = VaultCrypto.decrypt(candidate, bytes) ?: return false
        val decoded = VaultCodec.decode(plain)
        plain.fill(0)
        synchronized(this) {
            lock()
            key = candidate
            salt = header.salt
            iterations = header.iterations
            entries = decoded
            lastActive = SystemClock.elapsedRealtime()
        }
        return true
    }

    /** Checks a password against the stored vault without changing the session. Slow. */
    fun verify(context: Context, password: CharArray): Boolean {
        val bytes = file(context).readBytes()
        val header = VaultCrypto.readHeader(bytes) ?: return false
        val candidate = VaultCrypto.deriveKey(password, header.salt, header.iterations)
        val plain = VaultCrypto.decrypt(candidate, bytes)
        candidate.fill(0)
        plain?.fill(0)
        return plain != null
    }

    /** Re-encrypts the vault under a new master password with a new salt. Slow. */
    fun changePassword(context: Context, password: CharArray) {
        val newSalt = VaultCrypto.newSalt()
        val newKey = VaultCrypto.deriveKey(password, newSalt, VaultCrypto.DEFAULT_ITERATIONS)
        synchronized(this) {
            check(key != null) { "Vault is locked" }
            key?.fill(0)
            key = newKey
            salt = newSalt
            iterations = VaultCrypto.DEFAULT_ITERATIONS
            persist(context)
        }
    }

    @Synchronized
    fun all(): List<Entry> = entries.sortedBy { it.title.lowercase() }

    @Synchronized
    fun get(id: String): Entry? = entries.firstOrNull { it.id == id }

    @Synchronized
    fun matches(pkg: String, webDomain: String?): List<Entry> =
        Matcher.find(entries, pkg, webDomain).sortedBy { it.title.lowercase() }

    @Synchronized
    fun put(context: Context, entry: Entry) {
        check(key != null) { "Vault is locked" }
        val stamped = entry.copy(updated = System.currentTimeMillis())
        entries = entries.filter { it.id != entry.id } + stamped
        persist(context)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        check(key != null) { "Vault is locked" }
        entries = entries.filter { it.id != id }
        persist(context)
    }

    /** Stores a login captured by autofill: updates the matching login for that user, or adds one. */
    @Synchronized
    fun saveCredential(context: Context, pkg: String, webDomain: String?, title: String, username: String, password: String) {
        val existing = Matcher.find(entries, pkg, webDomain).firstOrNull { it.username == username }
        if (existing != null) {
            put(context, existing.copy(password = password))
        } else {
            put(
                context,
                Entry(
                    title = title,
                    username = username,
                    password = password,
                    url = if (webDomain != null) "https://$webDomain" else "",
                    apps = if (webDomain == null) listOf(pkg) else emptyList(),
                )
            )
        }
    }

    /** The encrypted vault file as it is on disk, for a backup. */
    fun exportBytes(context: Context): ByteArray = file(context).readBytes()

    /**
     * Merges a backup into the vault. Returns how many logins the backup held, or null when
     * [password] does not open it. Slow: call off the main thread.
     */
    fun importBackup(context: Context, backup: ByteArray, password: CharArray): Int? {
        val header = VaultCrypto.readHeader(backup) ?: return null
        val backupKey = VaultCrypto.deriveKey(password, header.salt, header.iterations)
        val plain = VaultCrypto.decrypt(backupKey, backup)
        backupKey.fill(0)
        if (plain == null) return null
        val incoming = VaultCodec.decode(plain)
        plain.fill(0)
        synchronized(this) {
            check(key != null) { "Vault is locked" }
            entries = VaultCodec.merge(entries, incoming)
            persist(context)
        }
        return incoming.size
    }

    /** Encrypts and writes the vault, replacing the old file only once the new one is safely on disk. */
    private fun persist(context: Context) {
        val plain = VaultCodec.encode(entries)
        val sealed = VaultCrypto.encrypt(key!!, salt!!, iterations, plain)
        plain.fill(0)
        val target = file(context)
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { out ->
            out.write(sealed)
            out.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
