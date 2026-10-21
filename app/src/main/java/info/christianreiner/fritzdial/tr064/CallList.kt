package info.christianreiner.fritzdial.tr064

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/** A recent call – dialled, received or missed – from the FRITZ!Box' call list or the phone's own call log. */
data class RecentCall(
    val number: String,
    /** Contact name, if the box or the phone knows it. */
    val name: String?,
    /** When the call took place, in milliseconds since the epoch. */
    val time: Long,
    val source: Source,
    val direction: Direction = Direction.OUTGOING,
) {
    enum class Source { FRITZBOX, PHONE }
    enum class Direction { OUTGOING, INCOMING, MISSED }
}

/**
 * Reads the dialled, received and missed calls from the box' call list via the TR-064 X_AVM-DE_OnTel service:
 * `GetCallList` hands out a download URL for an XML export of the list.
 * It holds all calls through the box – dial-help calls from FritzDial as well as calls from
 * the handsets themselves.
 */
class FritzCallList(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    @Throws(IOException::class)
    fun load(max: Int = MAX_ENTRIES): List<RecentCall> {
        val url = client.call(FritzPhonebooks.SERVICE_TYPE, FritzPhonebooks.CONTROL_URL, "GetCallList")
            .get("NewCallListURL").orEmpty()
        if (url.isBlank()) return emptyList()
        // The list holds rejected calls too, so ask for a few more than will be shown.
        val withMax = url.toHttpUrlOrNull()?.newBuilder()
            ?.setQueryParameter("max", (max * 3 / 2).toString())
            ?.build()?.toString() ?: url
        return parseCalls(client.download(withMax)).take(max)
    }

    companion object {
        /** How many calls to offer at most. */
        const val MAX_ENTRIES = 100

        /**
         * Call types of the export: 1 = incoming, 2 = missed, 3 = outgoing,
         * 9 / 11 = active incoming / outgoing. Rejected calls (10) are left out.
         */
        private val INCOMING_TYPES = setOf("1", "9")
        private val MISSED_TYPES = setOf("2")
        private val OUTGOING_TYPES = setOf("3", "11")

        /**
         * Parses the call list export, newest first as the box sends it:
         * `<root><Call><Type>3</Type><Called>030123</Called><Name>…</Name>
         *  <Date>21.10.26 14:30</Date>…</Call>…`. The other party is `Called` for outgoing calls and
         * `Caller` for incoming and missed ones; calls with a withheld number are skipped (nothing to call back).
         */
        fun parseCalls(xml: String): List<RecentCall> {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))

            val calls = mutableListOf<RecentCall>()
            var fields = mutableMapOf<String, String>()
            var inCall = false
            val text = StringBuilder()

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "Call") {
                            inCall = true
                            fields = mutableMapOf()
                        }
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (inCall) {
                            if (parser.name == "Call") {
                                inCall = false
                                val direction = when (fields["Type"]) {
                                    in OUTGOING_TYPES -> RecentCall.Direction.OUTGOING
                                    in INCOMING_TYPES -> RecentCall.Direction.INCOMING
                                    in MISSED_TYPES -> RecentCall.Direction.MISSED
                                    else -> null
                                }
                                val number = when (direction) {
                                    RecentCall.Direction.OUTGOING -> fields["Called"]
                                    else -> fields["Caller"]
                                }.orEmpty()
                                val time = parseDate(fields["Date"].orEmpty())
                                if (direction != null && number.isNotEmpty() && time != null) {
                                    calls += RecentCall(
                                        number = number,
                                        name = fields["Name"]?.takeIf { it.isNotEmpty() },
                                        time = time,
                                        source = RecentCall.Source.FRITZBOX,
                                        direction = direction,
                                    )
                                }
                            } else {
                                fields[parser.name] = text.toString().trim()
                            }
                        }
                        text.setLength(0)
                    }
                }
                parser.next()
            }
            return calls
        }

        /** The box' date format "dd.MM.yy HH:mm", in its (= presumably the phone's) local time. */
        fun parseDate(value: String): Long? =
            try {
                SimpleDateFormat("dd.MM.yy HH:mm", Locale.ROOT).apply { isLenient = false }
                    .parse(value.trim())?.time
            } catch (_: ParseException) {
                null
            }
    }
}
