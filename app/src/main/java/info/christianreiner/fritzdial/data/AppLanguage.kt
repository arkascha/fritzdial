package info.christianreiner.fritzdial.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * The app's UI language: the system language, or one chosen in the settings.
 *
 * From Android 13 on this is Android's own per-app language (also shown under the system's
 * app settings › Language). Before, the choice is stored here and applied to each activity in
 * [wrap]; the system app settings do not offer it there.
 */
object AppLanguage {

    /** Languages FritzDial is translated into, by language tag, named in their own language. */
    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "nl" to "Nederlands",
        "fr" to "Français",
        "it" to "Italiano",
        "es" to "Español",
        "pl" to "Polski",
    )

    /** Country whose flag stands for a language when its locale names none (e.g. a chosen "de"). */
    private val flagCountry = mapOf(
        "en" to "GB", "de" to "DE", "nl" to "NL", "fr" to "FR", "it" to "IT", "es" to "ES", "pl" to "PL",
    )

    /**
     * National flag (emoji) for the language the app shows in [locale]: its region if it has one
     * (de-AT → 🇦🇹), else the language's usual country. A system language FritzDial is not
     * translated into is shown in English, so is its flag.
     */
    fun flag(locale: Locale): String {
        val shown = locale.language.takeIf { language -> supported.any { it.first == language } }
        val country = locale.country.takeIf { shown != null && it.length == 2 && it.all(Char::isLetter) }
            ?: flagCountry.getValue(shown ?: "en")
        // A flag emoji is the country code in "regional indicator" letters.
        return country.uppercase(Locale.ROOT).map { c -> String(Character.toChars(0x1F1E6 + (c - 'A'))) }
            .joinToString("")
    }

    /** The chosen language tag, or null for the system language. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs(context).getString(KEY, null)
        }

    /** Switches to [tag] (null = system language) and redraws [activity] in it. */
    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android stores it and recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit { if (tag == null) remove(KEY) else putString(KEY, tag) }
            activity.recreate()
        }
    }

    /** Before Android 13: the activity's context in the chosen language (call from attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY, null) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    // Same file as the other settings; read here directly, as wrap() runs before anything else.
    private fun prefs(context: Context) =
        context.getSharedPreferences("fritzdial_settings", Context.MODE_PRIVATE)

    private const val KEY = "app_language"
}
