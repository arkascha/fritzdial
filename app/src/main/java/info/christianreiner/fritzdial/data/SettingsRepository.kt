package info.christianreiner.fritzdial.data

import android.content.Context
import androidx.core.content.edit
import info.christianreiner.fritzdial.tr064.FritzConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One configured FritzBox.
 *
 * [deviceId] is the box' UPnP device id (UDN from its public `tr64desc.xml`). It is learned when
 * the connection is saved and used to recognise the box of the current network automatically.
 */
data class BoxProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: FritzConfig,
    val deviceId: String? = null,
    val model: String? = null,
)

/** Persists the FritzBox connections (passwords encrypted) and the app options. */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("fritzdial_settings", Context.MODE_PRIVATE)

    init {
        migrateSingleConnection()
    }

    // --- FritzBox connections ---------------------------------------------------------

    fun profiles(): List<BoxProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Inserts or replaces (by id) a connection. */
    fun saveProfile(profile: BoxProfile) {
        val list = profiles().toMutableList()
        val index = list.indexOfFirst { it.id == profile.id }
        if (index >= 0) list[index] = profile else list += profile
        writeProfiles(list)
    }

    fun deleteProfile(id: String) {
        writeProfiles(profiles().filterNot { it.id == id })
        if (activeProfileId == id) activeProfileId = profiles().firstOrNull()?.id
    }

    /** Changes only the trusted certificate of a stored connection (null = forget). */
    fun updateTrustedCert(id: String, certDer: String?) {
        val profile = profiles().firstOrNull { it.id == id } ?: return
        saveProfile(profile.copy(config = profile.config.copy(trustedCertDer = certDer)))
    }

    /** Stores the learned device id of a connection. */
    fun updateDeviceId(id: String, deviceId: String, model: String?) {
        val profile = profiles().firstOrNull { it.id == id } ?: return
        saveProfile(profile.copy(deviceId = deviceId, model = model ?: profile.model))
    }

    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> profiles().any { it.id == id } }
            ?: profiles().firstOrNull()?.id
        set(value) = prefs.edit { if (value != null) putString(KEY_ACTIVE, value) else remove(KEY_ACTIVE) }

    fun activeProfile(): BoxProfile? = activeProfileId?.let { id -> profiles().firstOrNull { it.id == id } }

    fun isConfigured(): Boolean = profiles().isNotEmpty()

    // --- app options ------------------------------------------------------------------

    /** Optional feature: also show the phone's own contacts (needs READ_CONTACTS). Off by default. */
    var deviceContactsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_CONTACTS, false)
        set(value) = prefs.edit { putBoolean(KEY_DEVICE_CONTACTS, value) }

    /** The user chose "Don't ask again" (or switched the feature off in the settings). */
    var deviceContactsNeverAsk: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_CONTACTS_NEVER_ASK, false)
        set(value) = prefs.edit { putBoolean(KEY_DEVICE_CONTACTS_NEVER_ASK, value) }

    // --- storage ----------------------------------------------------------------------

    private fun writeProfiles(list: List<BoxProfile>) {
        val array = JSONArray()
        list.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY_PROFILES, array.toString()) }
    }

    private fun toJson(p: BoxProfile) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("host", p.config.host)
        put("port", p.config.port)
        put("user", p.config.username)
        // The password is stored encrypted with a key from the Android Keystore.
        put("password", SecretCipher.encrypt(p.config.password))
        put("https", p.config.useHttps)
        p.config.trustedCertDer?.let { put("cert", it) }
        p.deviceId?.let { put("deviceId", it) }
        p.model?.let { put("model", it) }
    }

    private fun fromJson(o: JSONObject) = BoxProfile(
        id = o.getString("id"),
        name = o.optString("name"),
        config = FritzConfig(
            host = o.optString("host", FritzConfig().host),
            port = o.optInt("port", FritzConfig.HTTP_PORT),
            username = o.optString("user"),
            password = o.optString("password").takeIf { it.isNotEmpty() }
                ?.let(SecretCipher::decrypt).orEmpty(),
            useHttps = o.optBoolean("https", false),
            trustedCertDer = o.optString("cert").takeIf { it.isNotEmpty() },
        ),
        deviceId = o.optString("deviceId").takeIf { it.isNotEmpty() },
        model = o.optString("model").takeIf { it.isNotEmpty() },
    )

    /** Earlier versions stored a single connection in separate keys: turn it into the first entry. */
    private fun migrateSingleConnection() {
        if (prefs.contains(KEY_PROFILES) || !prefs.contains(LEGACY_HOST)) return
        val config = FritzConfig(
            host = prefs.getString(LEGACY_HOST, null) ?: FritzConfig().host,
            port = prefs.getInt(LEGACY_PORT, FritzConfig.HTTP_PORT),
            username = prefs.getString(LEGACY_USER, null).orEmpty(),
            password = prefs.getString(LEGACY_PASSWORD, null)?.let(SecretCipher::decrypt).orEmpty(),
            useHttps = prefs.getBoolean(LEGACY_HTTPS, false),
            trustedCertDer = prefs.getString(LEGACY_CERT, null),
        )
        val profile = BoxProfile(name = config.host, config = config)
        writeProfiles(listOf(profile))
        prefs.edit {
            putString(KEY_ACTIVE, profile.id)
            listOf(LEGACY_HOST, LEGACY_PORT, LEGACY_USER, LEGACY_PASSWORD, LEGACY_HTTPS, LEGACY_CERT)
                .forEach { remove(it) }
        }
    }

    private companion object {
        const val KEY_PROFILES = "boxes"
        const val KEY_ACTIVE = "active_box"
        const val KEY_DEVICE_CONTACTS = "device_contacts_enabled"
        const val KEY_DEVICE_CONTACTS_NEVER_ASK = "device_contacts_never_ask"

        // single-connection keys of earlier versions (migrated)
        const val LEGACY_HOST = "host"
        const val LEGACY_PORT = "port"
        const val LEGACY_USER = "username"
        const val LEGACY_PASSWORD = "password_enc"
        const val LEGACY_HTTPS = "use_https"
        const val LEGACY_CERT = "trusted_cert_der"
    }
}
