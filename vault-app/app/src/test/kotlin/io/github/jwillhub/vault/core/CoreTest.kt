package io.github.jwillhub.vault.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val iterations = 100_000

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun keyDerivationMatchesPublishedPbkdf2Sha256Vector() {
        // Widely published PBKDF2-HMAC-SHA256 test vector: "password" / "salt" / 4096 rounds / 32 bytes.
        val key = VaultCrypto.deriveKey("password".toCharArray(), "salt".toByteArray(), 4096)
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", hex(key))
    }

    @Test
    fun roundTrip() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("correct horse".toCharArray(), salt, iterations)
        val file = VaultCrypto.encrypt(key, salt, iterations, "secret".toByteArray())
        val header = VaultCrypto.readHeader(file)
        assertNotNull(header)
        assertEquals(iterations, header!!.iterations)
        assertArrayEquals(salt, header.salt)
        val again = VaultCrypto.deriveKey("correct horse".toCharArray(), header.salt, header.iterations)
        assertArrayEquals("secret".toByteArray(), VaultCrypto.decrypt(again, file))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("right".toCharArray(), salt, iterations)
        val file = VaultCrypto.encrypt(key, salt, iterations, "secret".toByteArray())
        val wrong = VaultCrypto.deriveKey("wrong".toCharArray(), salt, iterations)
        assertNull(VaultCrypto.decrypt(wrong, file))
    }

    @Test
    fun anyAlteredByteIsRejected() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("pw".toCharArray(), salt, iterations)
        val file = VaultCrypto.encrypt(key, salt, iterations, "secret".toByteArray())
        for (i in file.indices) {
            val copy = file.clone()
            copy[i] = (copy[i].toInt() xor 1).toByte()
            assertNull("byte $i", VaultCrypto.decrypt(key, copy))
        }
    }

    @Test
    fun secretsDoNotAppearInTheFileAndEachWriteDiffers() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey("pw".toCharArray(), salt, iterations)
        val plain = VaultCodec.encode(listOf(Entry(title = "Bank", username = "alice", password = "hunter2-unique")))
        val a = VaultCrypto.encrypt(key, salt, iterations, plain)
        val b = VaultCrypto.encrypt(key, salt, iterations, plain)
        assertFalse(String(a, Charsets.ISO_8859_1).contains("hunter2-unique"))
        assertFalse(String(a, Charsets.ISO_8859_1).contains("alice"))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun notAVaultFile() {
        assertNull(VaultCrypto.readHeader(ByteArray(10)))
        assertNull(VaultCrypto.readHeader(ByteArray(200)))
        assertNull(VaultCrypto.decrypt(ByteArray(32), ByteArray(200)))
    }

    @Test
    fun entriesSurviveEncoding() {
        val entries = listOf(
            Entry(title = "Mail", username = "a@b.c", password = "p\"w\\ ü 密", url = "https://mail.example", apps = listOf("com.mail"), notes = "line1\nline2"),
            Entry(title = "Empty"),
        )
        assertEquals(entries, VaultCodec.decode(VaultCodec.encode(entries)))
    }

    @Test
    fun mergeKeepsTheNewerCopy() {
        val old = Entry(id = "1", title = "old", updated = 10)
        val new = Entry(id = "1", title = "new", updated = 20)
        val other = Entry(id = "2", title = "other", updated = 5)
        assertEquals(listOf(new, other), VaultCodec.merge(listOf(old), listOf(new, other)))
        assertEquals(listOf(new), VaultCodec.merge(listOf(new), listOf(old)))
    }

    @Test
    fun hosts() {
        assertEquals("example.com", Matcher.hostOf("https://www.Example.com/login?x=1"))
        assertEquals("example.com", Matcher.hostOf("example.com"))
        assertEquals("accounts.example.com", Matcher.hostOf("accounts.example.com/path"))
        assertNull(Matcher.hostOf(""))
        assertNull(Matcher.hostOf("not a url"))
    }

    @Test
    fun matching() {
        val site = Entry(title = "Site", url = "https://www.example.com/login")
        val sub = Entry(title = "Sub", url = "evil.example.com")
        val app = Entry(title = "App", apps = listOf("com.example.app"))
        val all = listOf(site, sub, app)
        assertEquals(listOf(site), Matcher.find(all, "com.android.chrome", "example.com"))
        assertEquals(listOf(app), Matcher.find(all, "com.example.app", null))
        assertTrue(Matcher.find(all, "com.other", null).isEmpty())
        assertTrue(Matcher.find(all, "com.android.chrome", "example.org").isEmpty())
    }

    @Test
    fun generatedPasswords() {
        repeat(200) {
            val p = PasswordGenerator.generate(12, upper = true, digits = true, symbols = true)
            assertEquals(12, p.length)
            assertTrue(p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
        }
        assertTrue(PasswordGenerator.generate(30, upper = false, digits = false, symbols = false).all { it in 'a'..'z' })
    }
}
