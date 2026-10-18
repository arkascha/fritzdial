package info.christianreiner.fritzdial.data

import android.content.Context
import androidx.core.content.edit
import info.christianreiner.fritzdial.tr064.FritzConfig

/** Persists the router connection settings. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fritzdial_settings", Context.MODE_PRIVATE)

    fun load(): FritzConfig = FritzConfig(
        host = prefs.getString(KEY_HOST, null) ?: FritzConfig().host,
        port = prefs.getInt(KEY_PORT, FritzConfig().port),
        username = prefs.getString(KEY_USER, null).orEmpty(),
        password = prefs.getString(KEY_PASSWORD, null)?.let(SecretCipher::decrypt).orEmpty(),
        useHttps = prefs.getBoolean(KEY_HTTPS, false),
        trustedCertDer = prefs.getString(KEY_TRUSTED_CERT, null),
    )

    fun save(config: FritzConfig) {
        prefs.edit {
            putString(KEY_HOST, config.host.trim())
            putInt(KEY_PORT, config.port)
            putString(KEY_USER, config.username.trim())
            putString(KEY_PASSWORD, SecretCipher.encrypt(config.password))
            putBoolean(KEY_HTTPS, config.useHttps)
            if (config.trustedCertDer != null) putString(KEY_TRUSTED_CERT, config.trustedCertDer)
            else remove(KEY_TRUSTED_CERT)
        }
    }

    fun isConfigured(): Boolean =
        prefs.contains(KEY_HOST) && prefs.contains(KEY_PASSWORD)

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USER = "username"
        const val KEY_PASSWORD = "password_enc"
        const val KEY_HTTPS = "use_https"
        const val KEY_TRUSTED_CERT = "trusted_cert_der"
    }
}
