package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The password rule [protect] leans on before PDFBox ever runs.
 *
 * All pure string arithmetic — no PDFBox, no Android — because the actual
 * encryption path needs a real PDF to exercise and belongs on a device or an
 * instrumented test, but the "is this password even worth encrypting with"
 * decision does not, and is exactly the kind of boundary math (empty, one
 * under, exactly at, one over, unicode) that is cheap to get wrong silently.
 */
class ProtectToolsTest {

    @Test
    fun `an empty password is rejected as empty, not as too short`() {
        assertThat(ProtectPasswordPolicy.check("")).isEqualTo(PasswordCheck.Empty)
    }

    @Test
    fun `one character under the minimum is too short`() {
        val sevenChars = "a".repeat(ProtectPasswordPolicy.MINIMUM_LENGTH - 1)
        assertThat(ProtectPasswordPolicy.check(sevenChars))
            .isEqualTo(PasswordCheck.TooShort(ProtectPasswordPolicy.MINIMUM_LENGTH))
    }

    @Test
    fun `exactly the minimum length is valid`() {
        val eightChars = "a".repeat(ProtectPasswordPolicy.MINIMUM_LENGTH)
        assertThat(ProtectPasswordPolicy.check(eightChars)).isEqualTo(PasswordCheck.Valid)
    }

    @Test
    fun `a comfortably long password is valid`() {
        assertThat(ProtectPasswordPolicy.check("correct horse battery staple")).isEqualTo(PasswordCheck.Valid)
    }

    @Test
    fun `exactly the maximum length is still valid`() {
        val atMaximum = "a".repeat(ProtectPasswordPolicy.MAXIMUM_LENGTH)
        assertThat(ProtectPasswordPolicy.check(atMaximum)).isEqualTo(PasswordCheck.Valid)
    }

    @Test
    fun `one character over the maximum is too long`() {
        val overMaximum = "a".repeat(ProtectPasswordPolicy.MAXIMUM_LENGTH + 1)
        assertThat(ProtectPasswordPolicy.check(overMaximum))
            .isEqualTo(PasswordCheck.TooLong(ProtectPasswordPolicy.MAXIMUM_LENGTH))
    }

    @Test
    fun `a pathologically long paste is rejected rather than silently hashed`() {
        val wholeFileDropped = "x".repeat(50_000)
        assertThat(ProtectPasswordPolicy.check(wholeFileDropped))
            .isEqualTo(PasswordCheck.TooLong(ProtectPasswordPolicy.MAXIMUM_LENGTH))
    }

    @Test
    fun `astral unicode characters count as one each, not two`() {
        // Each of these emoji is a single code point outside the Basic
        // Multilingual Plane, so it costs two UTF-16 chars but must count
        // as one character of password length.
        val eightEmoji = "😀".repeat(ProtectPasswordPolicy.MINIMUM_LENGTH)
        assertThat(eightEmoji.length).isEqualTo(ProtectPasswordPolicy.MINIMUM_LENGTH * 2)
        assertThat(ProtectPasswordPolicy.check(eightEmoji)).isEqualTo(PasswordCheck.Valid)
    }

    @Test
    fun `a too-short unicode password is still reported as too short`() {
        val threeEmoji = "😀".repeat(3)
        assertThat(ProtectPasswordPolicy.check(threeEmoji))
            .isEqualTo(PasswordCheck.TooShort(ProtectPasswordPolicy.MINIMUM_LENGTH))
    }

    @Test
    fun `accented and non-Latin passwords of sufficient length are valid`() {
        // The length is asserted first, deliberately. The original version of
        // this test used a seven-code-point Japanese string and claimed it was
        // valid against an eight-code-point minimum: nobody reviewing it can
        // count glyphs by eye, so the count is stated rather than assumed.
        val spanish = "contraseña"
        val japanese = "パスワードは安全です"

        assertThat(spanish.codePointCount(0, spanish.length))
            .isAtLeast(ProtectPasswordPolicy.MINIMUM_LENGTH)
        assertThat(japanese.codePointCount(0, japanese.length))
            .isAtLeast(ProtectPasswordPolicy.MINIMUM_LENGTH)

        assertThat(ProtectPasswordPolicy.check(spanish)).isEqualTo(PasswordCheck.Valid)
        assertThat(ProtectPasswordPolicy.check(japanese)).isEqualTo(PasswordCheck.Valid)
    }

    @Test
    fun `a non-Latin password one code point short is rejected`() {
        // The case the test above got wrong. Seven code points, and every one
        // of them a single visible character, so a UTF-16 length check would
        // also say seven and this would pass for the wrong reason -- what makes
        // it worth keeping is that it pins the boundary in a script where the
        // boundary is not countable at a glance.
        val sevenCodePoints = "パスワードです"

        assertThat(sevenCodePoints.codePointCount(0, sevenCodePoints.length)).isEqualTo(7)
        assertThat(ProtectPasswordPolicy.check(sevenCodePoints))
            .isEqualTo(PasswordCheck.TooShort(ProtectPasswordPolicy.MINIMUM_LENGTH))
    }

    @Test
    fun `isValid is true only for the Valid outcome`() {
        assertThat(ProtectPasswordPolicy.isValid("a".repeat(ProtectPasswordPolicy.MINIMUM_LENGTH))).isTrue()
        assertThat(ProtectPasswordPolicy.isValid("")).isFalse()
        assertThat(ProtectPasswordPolicy.isValid("short")).isFalse()
    }
}
