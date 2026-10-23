package info.christianreiner.fritzdial.data

import android.content.Context
import androidx.core.content.edit
import info.christianreiner.fritzdial.tr064.BoxInfo
import info.christianreiner.fritzdial.tr064.DialingCodes
import info.christianreiner.fritzdial.tr064.FritzConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One configured FritzBox.
 *
 * [deviceId] is the box' UPnP device id (UDN from its public `tr64desc.xml`). It is learned when
 * the connection is saved and used to recognise the box of the current network automatically.
 * [phonebooksEnabled], [callListEnabled] and [speedDialsEnabled] switch, for this box, whether its
 * phone books, its call list and the speed dials set up in its phone books are used (on by default).
 * [model], [softwareVersion] and [serialNumber] are what the box last said about itself (see [BoxInfo]);
 * [dialingCodes] its country and area code, for comparing numbers.
 */
data class BoxProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: FritzConfig,
    val deviceId: String? = null,
    val model: String? = null,
    val softwareVersion: String? = null,
    val serialNumber: String? = null,
    val dialingCodes: DialingCodes? = null,
    val phonebooksEnabled: Boolean = true,
    val callListEnabled: Boolean = true,
    val speedDialsEnabled: Boolean = true,
) {
    val info: BoxInfo get() = BoxInfo(model, softwareVersion, serialNumber)
}

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

    /** Stores what the box of a connection reported about itself; unknown values are kept. */
    fun updateBoxInfo(id: String, info: BoxInfo) {
        val profile = profiles().firstOrNull { it.id == id } ?: return
        val updated = profile.copy(
            model = info.model ?: profile.model,
            softwareVersion = info.softwareVersion ?: profile.softwareVersion,
            serialNumber = info.serialNumber ?: profile.serialNumber,
        )
        if (updated != profile) saveProfile(updated)
    }

    /** Stores the country and area code of a connection's box. */
    fun updateDialingCodes(id: String, codes: DialingCodes) {
        val profile = profiles().firstOrNull { it.id == id } ?: return
        if (profile.dialingCodes != codes) saveProfile(profile.copy(dialingCodes = codes))
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

    /**
     * Optional feature: also offer the numbers dialled with the phone's own telephony app (needs
     * READ_CALL_LOG). Off by default; only switched on once reading the call log actually worked.
     */
    var callLogEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_LOG, false)
        set(value) = prefs.edit { putBoolean(KEY_CALL_LOG, value) }

    /** Optional feature: this phone's own speed dials 1–9 (valid with every box). On by default. */
    var phoneSpeedDialsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PHONE_SPEED_DIALS_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_PHONE_SPEED_DIALS_ENABLED, value) }

    /** This phone's speed dials: name and number are stored as copies, so they work everywhere. */
    var phoneSpeedDials: List<SpeedDial>
        get() = runCatching {
            val array = JSONArray(prefs.getString(KEY_PHONE_SPEED_DIALS, null) ?: return emptyList())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SpeedDial(o.getString("slot"), o.optString("name"), o.getString("number"))
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(JSONObject().put("slot", it.slot).put("name", it.name).put("number", it.number)) }
            prefs.edit { putString(KEY_PHONE_SPEED_DIALS, array.toString()) }
        }

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
        p.softwareVersion?.let { put("softwareVersion", it) }
        p.serialNumber?.let { put("serialNumber", it) }
        p.dialingCodes?.let { c ->
            put(
                "dialingCodes",
                JSONObject().put("country", c.countryCode).put("international", c.internationalPrefix)
                    .put("national", c.nationalPrefix).apply { c.areaCode?.let { put("area", it) } },
            )
        }
        put("phonebooks", p.phonebooksEnabled)
        put("callList", p.callListEnabled)
        put("speedDials", p.speedDialsEnabled)
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
        softwareVersion = o.optString("softwareVersion").takeIf { it.isNotEmpty() },
        serialNumber = o.optString("serialNumber").takeIf { it.isNotEmpty() },
        dialingCodes = o.optJSONObject("dialingCodes")?.let { c ->
            DialingCodes(
                countryCode = c.getString("country"),
                internationalPrefix = c.optString("international", "00"),
                areaCode = c.optString("area").takeIf { it.isNotEmpty() },
                nationalPrefix = c.optString("national", "0"),
            )
        },
        // Connections saved before these options existed keep using both.
        phonebooksEnabled = o.optBoolean("phonebooks", true),
        callListEnabled = o.optBoolean("callList", true),
        speedDialsEnabled = o.optBoolean("speedDials", true),
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
        const val KEY_CALL_LOG = "call_log_enabled"
        const val KEY_PHONE_SPEED_DIALS_ENABLED = "phone_speed_dials_enabled"
        const val KEY_PHONE_SPEED_DIALS = "phone_speed_dials"

        // single-connection keys of earlier versions (migrated)
        const val LEGACY_HOST = "host"
        const val LEGACY_PORT = "port"
        const val LEGACY_USER = "username"
        const val LEGACY_PASSWORD = "password_enc"
        const val LEGACY_HTTPS = "use_https"
        const val LEGACY_CERT = "trusted_cert_der"
    }
}
