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
}
