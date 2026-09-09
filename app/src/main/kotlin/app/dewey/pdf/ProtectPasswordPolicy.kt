package app.dewey.pdf

/**
 * The outcome of checking a password against [ProtectPasswordPolicy], named
 * so the UI can show a specific reason instead of a generic "invalid".
 */
sealed interface PasswordCheck {
    /** Fit to encrypt a document with. */
    data object Valid : PasswordCheck

    /** An empty password is not a password — PDFBox would happily accept it. */
    data object Empty : PasswordCheck

    data class TooShort(val minimumLength: Int) : PasswordCheck

    data class TooLong(val maximumLength: Int) : PasswordCheck
}

/**
 * Whether a password is worth encrypting a document with, checked before
 * PDFBox ever sees it.
 *
 * Kept as plain string arithmetic — no PDFBox, no Android — because it is the
 * one part of "protect" that is worth unit testing in isolation: PDFBox's
 * standard security handler runs the password through a single, fast hash
 * (MD5 for revision 2/3, SHA-256 for AES-256) with none of the deliberate
 * per-guess cost a password hash like bcrypt or scrypt has. Tools built for
 * exactly this — hashcat, John the Ripher — try billions of candidates a
 * second against it offline once a file leaks. Length is the only lever an
 * app has over that; there is no server to rate-limit attempts against.
 */
object ProtectPasswordPolicy {

    /**
     * Below this, an offline attacker's dictionary covers the space in
     * practice regardless of what characters are used. Not a guarantee of
     * safety above it either — it is a floor against trivial passwords, not
     * a strength meter.
     */
    const val MINIMUM_LENGTH = 8

    /**
     * Not a security requirement — PDFBox does not care how long the
     * password is. This exists so a pathologically long paste (a whole file's
     * contents dropped into the field, say) is rejected with a clear reason
     * instead of silently being accepted and then hashed.
     */
    const val MAXIMUM_LENGTH = 512

    fun check(password: String): PasswordCheck {
        if (password.isEmpty()) return PasswordCheck.Empty

        // Counted in code points, not chars: a length check in UTF-16 code
        // units would count a single astral character (many emoji, some
        // scripts) as two, silently rejecting a password a person would
        // reasonably call eight characters long.
        val length = password.codePointCount(0, password.length)

        return when {
            length < MINIMUM_LENGTH -> PasswordCheck.TooShort(MINIMUM_LENGTH)
            length > MAXIMUM_LENGTH -> PasswordCheck.TooLong(MAXIMUM_LENGTH)
            else -> PasswordCheck.Valid
        }
    }

    fun isValid(password: String): Boolean = check(password) is PasswordCheck.Valid
}
