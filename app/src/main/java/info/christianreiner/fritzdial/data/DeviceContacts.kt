package info.christianreiner.fritzdial.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import info.christianreiner.fritzdial.tr064.Phonebook
import info.christianreiner.fritzdial.tr064.PhonebookContact
import info.christianreiner.fritzdial.tr064.PhonebookNumber

/**
 * The contacts stored on this Android device (all accounts: local, Google, CardDAV via DAVx⁵, …),
 * presented as one more phone book next to the FRITZ!Box ones.
 */
object DeviceContacts {

    /** Id used for the device "phone book", distinct from the box' ids (0, 1, …). */
    const val BOOK_ID = -1
    const val BOOK_NAME = "This phone"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Reads all contacts that have at least one phone number. Call off the main thread. */
    fun load(context: Context): Phonebook {
        if (!hasPermission(context)) return Phonebook(BOOK_ID, BOOK_NAME, emptyList())

        data class Entry(val name: String, val numbers: MutableList<PhonebookNumber>)
        val byContact = LinkedHashMap<Long, Entry>()

        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameCol = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val numberCol = c.getColumnIndexOrThrow(Phone.NUMBER)
            val typeCol = c.getColumnIndexOrThrow(Phone.TYPE)
            val labelCol = c.getColumnIndexOrThrow(Phone.LABEL)
            while (c.moveToNext()) {
                val number = c.getString(numberCol)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)?.trim().orEmpty()
                val label = Phone.getTypeLabel(context.resources, c.getInt(typeCol), c.getString(labelCol))
                    .toString()
                val entry = byContact.getOrPut(id) { Entry(name, mutableListOf()) }
                // The same number is often stored twice (e.g. synced from two accounts).
                val digits = number.filter { it.isDigit() || it == '+' }
                if (entry.numbers.none { it.number.filter { ch -> ch.isDigit() || ch == '+' } == digits }) {
                    entry.numbers += PhonebookNumber(number, label)
                }
            }
        }

        val contacts = byContact.values.map { e ->
            PhonebookContact(e.name.ifBlank { e.numbers.first().number }, e.numbers, BOOK_NAME)
        }.sortedBy { it.name.lowercase() }
        return Phonebook(BOOK_ID, BOOK_NAME, contacts)
    }
}
