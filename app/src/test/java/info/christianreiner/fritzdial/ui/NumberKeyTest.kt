package info.christianreiner.fritzdial.ui

import info.christianreiner.fritzdial.tr064.DialingCodes
import info.christianreiner.fritzdial.tr064.PhonebookContact
import info.christianreiner.fritzdial.tr064.PhonebookNumber
import info.christianreiner.fritzdial.tr064.RecentCall
import info.christianreiner.fritzdial.tr064.RecentCall.Source.FRITZBOX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberKeyTest {

    private val berlin = DialingCodes(countryCode = "49", areaCode = "30")
    private val key = { n: String -> numberKey(n, "", berlin) }

    @Test
    fun `writes numbers the same way by the box' codes`() {
        val expected = "+4930123456"
        listOf("+49 30 123456", "0049 30 123456", "030 123456", "030/123456", "123456")
            .forEach { assertEquals(it, expected, key(it)) }
        assertEquals("+49171234567", key("0171 234567"))
        assertEquals("+43123456789", key("0043 1 23456789"))
    }

    @Test
    fun `leaves short numbers and service codes alone`() {
        assertEquals("**610", key("**610"))
        assertEquals("110", key("110"))
        assertEquals("*21#", key("*21#"))
    }

    @Test
    fun `handles a country without national prefix`() {
        val italy = DialingCodes(countryCode = "39", areaCode = "06", nationalPrefix = "")
        assertEquals("+390612345678", numberKey("06 12345678", "", italy))
        assertEquals("+390612345678", numberKey("0039 06 12345678", "", italy))
    }

    @Test
    fun `without area code a local number stays as it is`() {
        assertEquals("123456", numberKey("123456", "", DialingCodes(countryCode = "49")))
    }

    @Test
    fun `reads the box' answers`() {
        val codes = DialingCodes.from(
            mapOf("NewX_AVM-DE_LKZ" to "49", "NewX_AVM-DE_LKZPrefix" to "00"),
            mapOf("NewX_AVM-DE_OKZ" to "30", "NewX_AVM-DE_OKZPrefix" to "0"),
        )
        assertEquals(DialingCodes("49", "00", "30", "0"), codes)
        assertEquals(null, DialingCodes.from(mapOf("NewX_AVM-DE_LKZ" to ""), emptyMap()))
        assertEquals(DialingCodes("49"), DialingCodes.from(mapOf("NewX_AVM-DE_LKZ" to "49"), emptyMap()))
        assertEquals("", DialingCodes.from(mapOf("NewX_AVM-DE_LKZ" to "39"), mapOf("NewX_AVM-DE_OKZPrefix" to ""))?.nationalPrefix)
    }

    @Test
    fun `names calls from the phone books`() {
        val contacts = listOf(PhonebookContact("Anna", listOf(PhonebookNumber("+49 30 123456", "home")), "Telefonbuch"))
        val calls = listOf(
            RecentCall("123456", null, 20, FRITZBOX),
            RecentCall("030 999999", "Bob", 10, FRITZBOX),
            RecentCall("030 777777", null, 5, FRITZBOX),
        )
        assertEquals(listOf("Anna", "Bob", null), nameRecents(calls, contacts, key).map { it.name })
    }

    @Test
    fun `finds numbers written differently`() {
        assertTrue(numberMatches("+49 30 123456", "030 12", key))
        assertTrue(numberMatches("030 123456", "+49 30 123", key))
        assertTrue(numberMatches("030 123456", "3456", key))
        assertFalse(numberMatches("030 123456", "040 12", key))
        assertFalse(numberMatches("030 123456", "Anna", key))
    }
}
