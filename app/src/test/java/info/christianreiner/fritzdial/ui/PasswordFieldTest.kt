package info.christianreiner.fritzdial.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordFieldTest {

    @Test
    fun `detects a character appended at the end`() {
        assertEquals(3, insertedCharIndex("abc", "abcd"))
    }

    @Test
    fun `detects a character inserted in the middle`() {
        assertEquals(1, insertedCharIndex("abc", "aXbc"))
        assertEquals(0, insertedCharIndex("", "a"))
    }

    @Test
    fun `reveals nothing for deletes, pastes or replacements`() {
        assertEquals(-1, insertedCharIndex("abcd", "abc"))
        assertEquals(-1, insertedCharIndex("abc", "abcde"))
        assertEquals(-1, insertedCharIndex("abc", "abX"))
        assertEquals(-1, insertedCharIndex("abc", "Xbcd"))
    }
}
