package io.github.jwillhub.vault.core

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
    private val random = SecureRandom()

    /** A random password that contains at least one character from every enabled group. */
    fun generate(length: Int, upper: Boolean, digits: Boolean, symbols: Boolean): String {
        val groups = buildList {
            add(LOWER)
            if (upper) add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        require(length >= groups.size)
        val all = groups.joinToString("")
        // Drawing again until every group shows up keeps each accepted password uniformly random.
        while (true) {
            val chars = CharArray(length) { all[random.nextInt(all.length)] }
            if (groups.all { group -> chars.any { it in group } }) return String(chars)
        }
    }
}
