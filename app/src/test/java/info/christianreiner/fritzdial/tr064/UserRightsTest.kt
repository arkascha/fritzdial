package info.christianreiner.fritzdial.tr064

import org.junit.Assert.assertEquals
import org.junit.Test

class UserRightsTest {

    @Test
    fun `reads the rights by area`() {
        val xml = "<rights><path>BoxAdmin</path><access>none</access><path>Phone</path><access>readwrite</access>" +
            "<path>NAS</path><access>read</access></rights>"
        val rights = UserRights.parse("fritz1234", xml)
        assertEquals("fritz1234", rights.username)
        assertEquals(mapOf("BoxAdmin" to "none", "Phone" to "readwrite", "NAS" to "read"), rights.access)
        assertEquals(listOf("BoxAdmin", "Phone", "NAS"), rights.access.keys.toList())
        assertEquals(true, rights.phone)
    }

    @Test
    fun `tells whether the call list may be read`() {
        assertEquals(false, UserRights.parse(null, "<rights><path>Phone</path><access>none</access></rights>").phone)
        assertEquals(null, UserRights.parse(null, "<rights><path>NAS</path><access>read</access></rights>").phone)
        assertEquals(null, UserRights.parse("", "").phone)
        assertEquals(null, UserRights.parse(" ", "").username)
    }
}
