package info.christianreiner.fritzdial.ui

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import info.christianreiner.fritzdial.tr064.PhonebookContact
import java.util.Locale

/**
 * Removes phone numbers that appear more than once across the given contacts, e.g. the same
 * person in a FRITZ!Box phone book and in the phone's own contacts. Numbers are compared in
 * normalized form, so "+49 30 123456", "0049 30 123456" and "030 123456" are equal.
 *
 * The first occurrence wins (callers pass the FRITZ!Box books before "This phone");
 * contacts left without any number are dropped.
 */
fun dedupeNumbers(contacts: List<PhonebookContact>, key: (String) -> String): List<PhonebookContact> {
    val seen = HashSet<String>()
    return contacts.mapNotNull { contact ->
        val numbers = contact.numbers.filter { seen.add(key(it.number)) }
        if (numbers.isEmpty()) null else contact.copy(numbers = numbers)
    }
}

/** Country of the phone (SIM / network, else locale), used to read national numbers like "030…". */
fun deviceCountryIso(context: Context): String {
    val tm = context.getSystemService(TelephonyManager::class.java)
    return listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
        .map { it.uppercase(Locale.ROOT) }
        .firstOrNull { it.length == 2 }
        .orEmpty()
}

/** Comparison key for a phone number: E.164 ("+4930123456") where possible, else its digits. */
fun numberKey(number: String, countryIso: String): String {
    val cleaned = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    // Internal numbers and service codes (**610, *21#, short numbers) are compared as they are.
    if (cleaned.length < 5 || cleaned.startsWith('*') || cleaned.startsWith('#')) return cleaned
    val international = if (cleaned.startsWith("00")) "+" + cleaned.drop(2) else cleaned
    if (countryIso.isNotEmpty()) {
        PhoneNumberUtils.formatNumberToE164(international, countryIso)?.let { return it }
    }
    return international
}
