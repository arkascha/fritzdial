package info.christianreiner.fritzdial.data

import info.christianreiner.fritzdial.tr064.FritzPhonebooks
import info.christianreiner.fritzdial.tr064.Phonebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedDialsTest {

    private val phone = listOf(SpeedDial("2", "Office", "0711111"))
    private val box = listOf(SpeedDial("05", "Doctor", "0711555"), SpeedDial("12", "Pizza", "0711222"))

    @Test
    fun `long press on an empty field uses this phone's speed dial`() {
        assertEquals("Office", SpeedDials.forLongPress("", '2', phone, box)?.name)
        assertNull(SpeedDials.forLongPress("", '3', phone, box))
        // 0 has no phone speed dial (it gives "+").
        assertNull(SpeedDials.forLongPress("", '0', phone, box))
    }

    @Test
    fun `long press after one digit uses the box' speed dial of both digits`() {
        assertEquals("Pizza", SpeedDials.forLongPress("1", '2', phone, box)?.name)
        assertEquals("Doctor", SpeedDials.forLongPress("0", '5', phone, box)?.name)
        assertNull(SpeedDials.forLongPress("1", '3', phone, box))
        assertNull(SpeedDials.forLongPress("12", '3', phone, box))
    }

    @Test
    fun `box speed dial entered as on a handset`() {
        assertEquals("Pizza", SpeedDials.forCode("**712", box)?.name)
        assertEquals("Doctor", SpeedDials.forCode("**75", box)?.name)
        assertEquals("Doctor", SpeedDials.forCode("**705#", box)?.name)
        assertNull(SpeedDials.forCode("**713", box))
        assertNull(SpeedDials.forCode("**610", box))
    }

    @Test
    fun `reads the box' speed dials from its phone books, two-digit`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <phonebooks><phonebook name="Telefonbuch">
            <contact><person><realName>Pizza Roma</realName></person>
              <telephony><number type="work" quickdial="12" prio="1">0711222</number></telephony></contact>
            <contact><person><realName>Praxis</realName></person>
              <telephony><number type="work" quickdial="5">0711555</number>
              <number type="fax_work" quickdial="">0711556</number></telephony></contact>
            <contact><person><realName>Ohne</realName></person>
              <telephony><number type="home" quickdial="-1">0711999</number></telephony></contact>
            </phonebook></phonebooks>"""
        val contacts = FritzPhonebooks.parseContacts(xml, "Telefonbuch")
        val speedDials = SpeedDials.fromPhonebooks(listOf(Phonebook(0, "Telefonbuch", contacts)))
        assertEquals(
            listOf(SpeedDial("05", "Praxis", "0711555"), SpeedDial("12", "Pizza Roma", "0711222")),
            speedDials,
        )
    }
}
