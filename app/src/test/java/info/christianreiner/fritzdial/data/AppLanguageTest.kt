package info.christianreiner.fritzdial.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @Test
    fun `a chosen language without region shows its usual country`() {
        assertEquals("🇩🇪", AppLanguage.flag(Locale.forLanguageTag("de")))
        assertEquals("🇬🇧", AppLanguage.flag(Locale.forLanguageTag("en")))
        assertEquals("🇵🇱", AppLanguage.flag(Locale.forLanguageTag("pl")))
    }

    @Test
    fun `the region of the locale wins`() {
        assertEquals("🇦🇹", AppLanguage.flag(Locale.forLanguageTag("de-AT")))
        assertEquals("🇺🇸", AppLanguage.flag(Locale.forLanguageTag("en-US")))
        assertEquals("🇧🇪", AppLanguage.flag(Locale.forLanguageTag("nl-BE")))
    }

    @Test
    fun `an untranslated system language shows the English flag, as the app is English then`() {
        assertEquals("🇬🇧", AppLanguage.flag(Locale.forLanguageTag("sv-SE")))
        assertEquals("🇬🇧", AppLanguage.flag(Locale.forLanguageTag("ja")))
    }

    @Test
    fun `a numeric region (e g Latin America) falls back to the language's country`() {
        assertEquals("🇪🇸", AppLanguage.flag(Locale.forLanguageTag("es-419")))
    }
}
