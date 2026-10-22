package info.christianreiner.fritzdial.data

import info.christianreiner.fritzdial.tr064.Phonebook

/**
 * A speed dial. [slot] is "1"–"9" for one of this phone's own, "00"–"99" for one set up in the
 * phone books of the FritzBox in use – the different lengths keep the two apart, whichever box
 * is in use.
 */
data class SpeedDial(val slot: String, val name: String, val number: String)

object SpeedDials {

    /** The slots of this phone's own speed dials. */
    val PHONE_SLOTS: List<String> = ('1'..'9').map(Char::toString)

    /** The box' speed dials from its phone books, by slot; if a slot is used twice, the first one counts. */
    fun fromPhonebooks(books: List<Phonebook>): List<SpeedDial> =
        books.asSequence()
            .flatMap { it.contacts }
            .flatMap { contact ->
                contact.numbers.mapNotNull { n -> n.speedDial?.let { SpeedDial(it, contact.name, n.number) } }
            }
            .distinctBy { it.slot }
            .sortedBy { it.slot }
            .toList()

    /**
     * The speed dial a long press on [digit] stands for, with [number] in the field so far: on an
     * empty field one of this phone's ([phone], 1–9); after a single digit the box' speed dial of
     * both digits ([box], e.g. "1" then a long "2" → "12"). Null if there is none.
     */
    fun forLongPress(number: String, digit: Char, phone: List<SpeedDial>, box: List<SpeedDial>): SpeedDial? =
        when {
            number.isEmpty() && digit in '1'..'9' -> phone.firstOrNull { it.slot == digit.toString() }
            number.length == 1 && number[0].isDigit() && digit.isDigit() ->
                box.firstOrNull { it.slot == "${number[0]}$digit" }
            else -> null
        }

    /** A box speed dial entered as on a handset: `**7` and its number (`**712`, `**75`, also with `#`). */
    fun forCode(number: String, box: List<SpeedDial>): SpeedDial? {
        val slot = BOX_CODE.matchEntire(number)?.groupValues?.get(1)?.padStart(2, '0') ?: return null
        return box.firstOrNull { it.slot == slot }
    }

    private val BOX_CODE = Regex("""\*\*7(\d{1,2})#?""")
}
