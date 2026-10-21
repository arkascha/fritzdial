package info.christianreiner.fritzdial.ui

import info.christianreiner.fritzdial.tr064.RecentCall
import info.christianreiner.fritzdial.tr064.RecentCall.Direction.INCOMING
import info.christianreiner.fritzdial.tr064.RecentCall.Direction.OUTGOING
import info.christianreiner.fritzdial.tr064.RecentCall.Source.FRITZBOX
import info.christianreiner.fritzdial.tr064.RecentCall.Source.PHONE
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeRecentsTest {

    /** Stand-in for numberKey (which needs Android): digits only. */
    private val digits: (String) -> String = { n -> n.filter(Char::isDigit) }

    @Test
    fun `sorts both sources newest first`() {
        val merged = mergeRecents(
            listOf(RecentCall("111", null, 10, FRITZBOX), RecentCall("333", null, 30, FRITZBOX)) +
                listOf(RecentCall("222", null, 20, PHONE)),
            digits,
        )
        assertEquals(listOf("333", "222", "111"), merged.map { it.number })
    }

    @Test
    fun `keeps each number once, at its latest call`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("030 123", null, 10, FRITZBOX),
                RecentCall("030-123", null, 50, PHONE),
                RecentCall("999", null, 40, FRITZBOX),
            ),
            digits,
        )
        assertEquals(listOf("030-123", "999"), merged.map { it.number })
        assertEquals(PHONE, merged.first().source)
    }

    @Test
    fun `takes a missing name from an older call of the same number`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("123", "Alice", 10, FRITZBOX),
                RecentCall("123", null, 20, PHONE),
            ),
            digits,
        )
        assertEquals(listOf(RecentCall("123", "Alice", 20, PHONE)), merged)
    }

    @Test
    fun `shows a number with the direction of its latest call`() {
        val merged = mergeRecents(
            listOf(
                RecentCall("123", null, 10, FRITZBOX, OUTGOING),
                RecentCall("123", null, 20, PHONE, INCOMING),
            ),
            digits,
        )
        assertEquals(INCOMING, merged.single().direction)
    }
}
