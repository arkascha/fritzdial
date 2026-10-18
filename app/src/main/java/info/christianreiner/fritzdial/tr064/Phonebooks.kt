package info.christianreiner.fritzdial.tr064

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/** One number of a contact, with the box' type tag ("home", "mobile", "work", …). */
data class PhonebookNumber(val number: String, val type: String) {
    /** Human readable label for [type]. */
    val label: String
        get() = when (type.lowercase()) {
            "home" -> "Home"
            "mobile" -> "Mobile"
            "work" -> "Work"
            "fax_work", "fax" -> "Fax"
            "intern" -> "Internal"
            "memo" -> "Memo"
            "other", "" -> "Other"
            else -> type.replaceFirstChar { it.uppercase() }
        }
}

data class PhonebookContact(
    val name: String,
    val numbers: List<PhonebookNumber>,
    /** Name of the phone book this contact comes from. */
    val book: String,
)

data class Phonebook(val id: Int, val name: String, val contacts: List<PhonebookContact>)

/**
 * Reads the phone books of the box via the TR-064 X_AVM-DE_OnTel service: the list of
 * phone book ids, then per id a download URL for an XML export of its contacts.
 * Online phone books configured on the box (e.g. CardDAV) are synced into the box and,
 * as far as known, show up in the same list.
 */
class FritzPhonebooks(config: FritzConfig, authToken: String? = null) {

    private val client = Tr064Client(config, authToken)

    @Throws(IOException::class)
    fun loadAll(): List<Phonebook> = listIds().map { load(it) }

    @Throws(IOException::class)
    fun listIds(): List<Int> =
        call("GetPhonebookList")["NewPhonebookList"].orEmpty()
            .split(',').mapNotNull { it.trim().toIntOrNull() }

    @Throws(IOException::class)
    fun load(id: Int): Phonebook {
        val info = call("GetPhonebook", mapOf("NewPhonebookId" to id.toString()))
        val name = info["NewPhonebookName"].orEmpty().ifBlank { "Phone book ${id + 1}" }
        val url = info["NewPhonebookURL"].orEmpty()
        if (url.isBlank()) return Phonebook(id, name, emptyList())
        val xml = client.download(url)
        return Phonebook(id, name, parseContacts(xml, name))
    }

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(SERVICE_TYPE, CONTROL_URL, action, args)

    companion object {
        const val SERVICE_TYPE = "urn:dslforum-org:service:X_AVM-DE_OnTel:1"
        const val CONTROL_URL = "/upnp/control/x_contact"

        /**
         * Parses the box' phone book export:
         * `<phonebooks><phonebook name="…"><contact><person><realName>…</realName></person>
         *  <telephony><number type="home" …>030123</number>…</telephony></contact>…`
         */
        fun parseContacts(xml: String, bookName: String): List<PhonebookContact> {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))

            val contacts = mutableListOf<PhonebookContact>()
            var inContact = false
            var name = ""
            var numbers = mutableListOf<PhonebookNumber>()
            var numberType = ""
            val text = StringBuilder()

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "contact" -> {
                                inContact = true
                                name = ""
                                numbers = mutableListOf()
                            }
                            "number" -> numberType = parser.getAttributeValue(null, "type").orEmpty()
                        }
                        text.setLength(0)
                    }
                    XmlPullParser.TEXT -> text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (inContact) when (parser.name) {
                            "realName" -> name = text.toString().trim()
                            "number" -> {
                                val n = text.toString().trim()
                                if (n.isNotEmpty()) numbers += PhonebookNumber(n, numberType)
                            }
                            "contact" -> {
                                inContact = false
                                if (numbers.isNotEmpty()) {
                                    contacts += PhonebookContact(name.ifBlank { numbers.first().number }, numbers, bookName)
                                }
                            }
                        }
                        text.setLength(0)
                    }
                }
                parser.next()
            }
            return contacts.sortedBy { it.name.lowercase() }
        }
    }
}
