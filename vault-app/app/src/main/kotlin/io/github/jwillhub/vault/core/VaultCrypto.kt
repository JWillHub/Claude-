package io.github.jwillhub.vault.core

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault file encryption.
 *
 * The key is derived from the master password with PBKDF2-HMAC-SHA256 and the data is sealed with
 * AES-256-GCM, so a wrong password or a modified file is detected instead of yielding garbage.
 *
 * File layout: "VLT1" | iterations (4 bytes) | salt (32) | nonce (12) | ciphertext and tag.
 * Everything before the nonce is authenticated as associated data.
 */
object VaultCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val SALT_LENGTH = 32
    private const val NONCE_LENGTH = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val HEADER_LENGTH = 4 + 4 + SALT_LENGTH
    private val MAGIC = byteArrayOf('V'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte())

    // Bounds on what a file may ask for, so a crafted backup cannot make the phone grind for hours.
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 20_000_000

    private val random = SecureRandom()

    class Header(val iterations: Int, val salt: ByteArray)

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }

    /** Slow on purpose: call off the main thread. */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Reads the key-derivation settings of a vault file, or null if it is not one. */
    fun readHeader(file: ByteArray): Header? {
        if (file.size < HEADER_LENGTH + NONCE_LENGTH + TAG_BITS / 8) return null
        for (i in MAGIC.indices) if (file[i] != MAGIC[i]) return null
        val iterations = ByteBuffer.wrap(file, 4, 4).int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return null
        return Header(iterations, file.copyOfRange(8, HEADER_LENGTH))
    }

    fun encrypt(key: ByteArray, salt: ByteArray, iterations: Int, plaintext: ByteArray): ByteArray {
        require(salt.size == SALT_LENGTH)
        val header = ByteBuffer.allocate(HEADER_LENGTH).put(MAGIC).putInt(iterations).put(salt).array()
        // A fresh random nonce for every write: a nonce must never repeat under the same key.
        val nonce = ByteArray(NONCE_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + nonce + cipher.doFinal(plaintext)
    }

    /** Returns the plaintext, or null when the key is wrong or the file was altered. */
    fun decrypt(key: ByteArray, file: ByteArray): ByteArray? {
        if (readHeader(file) == null) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = GCMParameterSpec(TAG_BITS, file, HEADER_LENGTH, NONCE_LENGTH)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), nonce)
            cipher.updateAAD(file, 0, HEADER_LENGTH)
            val start = HEADER_LENGTH + NONCE_LENGTH
            cipher.doFinal(file, start, file.size - start)
        } catch (e: GeneralSecurityException) {
            null
        }
    }
}
