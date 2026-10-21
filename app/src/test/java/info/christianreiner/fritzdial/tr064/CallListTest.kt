package info.christianreiner.fritzdial.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class CallListTest {

    @Test
    fun `parses the box' date format in local time`() {
        val time = FritzCallList.parseDate("21.10.26 14:30")
        val expected = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 21, 14, 30)
        }.timeInMillis
        assertEquals(expected, time)
    }

    @Test
    fun `rejects dates it cannot read`() {
        assertNull(FritzCallList.parseDate(""))
        assertNull(FritzCallList.parseDate("2026-10-21 14:30"))
        assertNull(FritzCallList.parseDate("32.10.26 14:30"))
    }

    /** Structure as sent by a FRITZ!Box 7530 AX with FRITZ!OS 8.25 (numbers and names made up). */
    private val export = """<?xml version="1.0" encoding="utf-8"?>
        <root><timestamp>1761055800</timestamp>
        <Call><Id>12</Id><Type>3</Type><Called>03012345678</Called><Caller>SIP: 301111111</Caller><CallerNumber>301111111</CallerNumber><Name>Erika Muster</Name><Numbertype>sip</Numbertype><Device>ISDN Gerät</Device><Port>4</Port><Date>21.10.26 14:30</Date><Duration>0:05</Duration><Count></Count><Path /></Call>
        <Call><Id>11</Id><Type>1</Type><Caller>015112345678</Caller><Called>SIP: 301111111</Called><CalledNumber>301111111</CalledNumber><Name></Name><Numbertype>sip</Numbertype><Device>Headset</Device><Port>10</Port><Date>21.10.26 12:00</Date><Duration>0:12</Duration><Count></Count><Path /></Call>
        <Call><Id>10</Id><Type>2</Type><Caller>0401234567</Caller><Called>SIP: 301111111</Called><CalledNumber>301111111</CalledNumber><Name>Max Muster</Name><Numbertype>sip</Numbertype><Device></Device><Port>-1</Port><Date>20.10.26 18:15</Date><Duration>0:00</Duration><Count></Count><Path /></Call>
        <Call><Id>9</Id><Type>10</Type><Caller>0899999999</Caller><Called>SIP: 301111111</Called><CalledNumber>301111111</CalledNumber><Name></Name><Numbertype>sip</Numbertype><Device></Device><Port>-1</Port><Date>20.10.26 09:00</Date><Duration>0:00</Duration><Count></Count><Path /></Call>
        <Call><Id>8</Id><Type>1</Type><Caller></Caller><Called>SIP: 301111111</Called><CalledNumber>301111111</CalledNumber><Name></Name><Numbertype>sip</Numbertype><Device>Headset</Device><Port>10</Port><Date>19.10.26 08:00</Date><Duration>0:01</Duration><Count></Count><Path /></Call>
        </root>"""

    @Test
    fun `reads dialled, received and missed calls with the other party's number`() {
        val calls = FritzCallList.parseCalls(export)
        assertEquals(
            listOf(
                Triple("03012345678", "Erika Muster", RecentCall.Direction.OUTGOING),
                Triple("015112345678", null, RecentCall.Direction.INCOMING),
                Triple("0401234567", "Max Muster", RecentCall.Direction.MISSED),
            ),
            calls.map { Triple(it.number, it.name, it.direction) },
        )
        assertEquals(FritzCallList.parseDate("21.10.26 14:30"), calls.first().time)
        assertEquals(RecentCall.Source.FRITZBOX, calls.first().source)
    }
}
