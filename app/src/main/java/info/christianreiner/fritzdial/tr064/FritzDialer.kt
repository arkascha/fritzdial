package info.christianreiner.fritzdial.tr064

import java.io.IOException

/**
 * Click-to-dial ("Wählhilfe") via the TR-064 X_VoIP service.
 *
 * This triggers exactly what the web UI's dial help does: the box first rings the
 * configured phone (e.g. a DECT handset); once it is picked up, the box dials the
 * target number from that phone.
 *
 * Prerequisites on the box:
 *  - Home Network > Network > Network Settings: "Allow access for applications" (TR-064) enabled
 *  - Telephony > Calls > Dial Help: "Use dial help" enabled
 *  - A FRITZ!Box user with telephony permission
 */
class FritzDialer(
    private val config: FritzConfig,
    /** Confirmed second-factor token, for repeating an action that failed with error 866. */
    private val authToken: String? = null,
) {

    private val client = Tr064Client(config, authToken)

    /** Lists the phones (FON, DECT, IP) the dial help can use. Names are passed back verbatim to [setDialPhone]. */
    @Throws(IOException::class)
    fun listPhones(): List<String> {
        val phones = mutableListOf<String>()
        for (index in 1..MAX_PHONE_PORTS) {
            val name = try {
                call(
                    "X_AVM-DE_GetPhonePort",
                    mapOf("NewIndex" to index.toString()),
                )["NewX_AVM-DE_PhoneName"]
            } catch (e: Tr064Exception) {
                // An invalid index (713 / 402 / 820, depending on firmware) marks the end of the list.
                // A fault on the very first index is a real problem (rights, TR-064 disabled, ...).
                if (e.errorCode != null && index > 1) break
                throw e
            }
            if (!name.isNullOrBlank()) phones += name
        }
        return phones
    }

    /** The phone the box currently rings for dial help, or null if none is set. */
    @Throws(IOException::class)
    fun getDialPhone(): String? =
        call("X_AVM-DE_DialGetConfig")["NewX_AVM-DE_PhoneName"]?.takeIf { it.isNotBlank() }

    /** Selects the phone (as returned by [listPhones]) the box should ring for dial help. */
    @Throws(IOException::class)
    fun setDialPhone(phoneName: String) {
        call("X_AVM-DE_DialSetConfig", mapOf("NewX_AVM-DE_PhoneName" to phoneName))
    }

    /** Starts the call: the dial-help phone rings, and after pick-up [number] is dialled. */
    @Throws(IOException::class)
    fun dial(number: String) {
        call("X_AVM-DE_DialNumber", mapOf("NewX_AVM-DE_PhoneNumber" to number))
    }

    /** Cancels a dial-help call that is still ringing / being set up. */
    @Throws(IOException::class)
    fun hangup() {
        call("X_AVM-DE_DialHangup")
    }

    /**
     * Makes [phoneName] the dial-help phone, but only if it is not already. Changing it is a
     * settings change that newer FRITZ!OS versions protect with a second factor (error 866),
     * so it is avoided whenever possible.
     */
    @Throws(IOException::class)
    fun ensureDialPhone(phoneName: String) {
        if (getDialPhone() != phoneName) setDialPhone(phoneName)
    }

    /** Model, FRITZ!OS version and serial number of the box (TR-064 `DeviceInfo` `GetInfo`). */
    @Throws(IOException::class)
    fun deviceInfo(): BoxInfo =
        BoxInfo.from(client.call(DEVICE_INFO_SERVICE_TYPE, DEVICE_INFO_CONTROL_URL, "GetInfo"))

    /**
     * The box' country and area code (`GetVoIPCommonCountryCode`, `GetVoIPCommonAreaCode`), for
     * comparing numbers written in different ways; null if the box has no country code set.
     */
    @Throws(IOException::class)
    fun dialingCodes(): DialingCodes? =
        DialingCodes.from(call("GetVoIPCommonCountryCode"), call("GetVoIPCommonAreaCode"))

    /** All phone books of the box with their contacts (see [FritzPhonebooks]). */
    @Throws(IOException::class)
    fun loadPhonebooks(): List<Phonebook> = FritzPhonebooks(config, authToken).loadAll()

    /** The latest dialled, received and missed calls from the box' call list (see [FritzCallList]). */
    @Throws(IOException::class)
    fun loadRecentCalls(): List<RecentCall> = FritzCallList(config, authToken).load()

    // --- second factor (X_AVM-DE_Auth) ------------------------------------------------

    /**
     * Starts a second-factor confirmation. The user then confirms on the box (button press,
     * or dialling the returned code on a connected phone); poll [secondFactorState] meanwhile.
     */
    @Throws(IOException::class)
    fun startSecondFactor(): SecondFactorChallenge {
        val result = authCall("SetConfig", mapOf("NewAction" to "start"))
        return SecondFactorChallenge.parse(
            token = result["NewToken"].orEmpty(),
            methods = result["NewMethods"].orEmpty(),
        )
    }

    /** Current state, e.g. "waitingforauth" while the box waits for the confirmation. */
    @Throws(IOException::class)
    fun secondFactorState(): String = authCall("GetState")["NewState"].orEmpty()

    /** Aborts a running confirmation. */
    @Throws(IOException::class)
    fun stopSecondFactor() {
        authCall("SetConfig", mapOf("NewAction" to "stop"))
    }

    private fun authCall(action: String, args: Map<String, String> = emptyMap()) =
        client.call(AUTH_SERVICE_TYPE, AUTH_CONTROL_URL, action, args)

    private fun call(action: String, args: Map<String, String> = emptyMap()) =
        client.call(SERVICE_TYPE, CONTROL_URL, action, args)

    companion object {
        const val SERVICE_TYPE = "urn:dslforum-org:service:X_VoIP:1"
        const val CONTROL_URL = "/upnp/control/x_voip"
        const val AUTH_SERVICE_TYPE = "urn:dslforum-org:service:X_AVM-DE_Auth:1"
        const val AUTH_CONTROL_URL = "/upnp/control/x_auth"
        const val DEVICE_INFO_SERVICE_TYPE = "urn:dslforum-org:service:DeviceInfo:1"
        const val DEVICE_INFO_CONTROL_URL = "/upnp/control/deviceinfo"
        const val STATE_WAITING = "waitingforauth"
        private const val MAX_PHONE_PORTS = 40

        /**
         * Phone name for display: strips the technical port prefix the box puts in front of
         * each name ("ISDN: " for DECT handsets – they sit on the box' internal ISDN bus –,
         * "FON1: " for analogue ports, "SIP: " / "IP: " for IP phones, …).
         * The full name must still be used when talking to the box ([setDialPhone]).
         */
        fun displayName(phoneName: String): String {
            val stripped = PORT_PREFIX.replaceFirst(phoneName, "").trim()
            return stripped.ifEmpty { phoneName.trim() }
        }

        private val PORT_PREFIX =
            Regex("""^\s*(ISDN|DECT|FON\s*\d*|SIP|IP|LAN|WLAN|S0|TAM)\s*\d*\s*:\s*""", RegexOption.IGNORE_CASE)

        /** Keeps digits and the characters the box understands (+ * #); drops spaces, dashes, brackets. */
        fun sanitizeNumber(raw: String): String =
            raw.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }
}

/**
 * The box' country and area code with their prefixes, as set on the box (Telephony › Own Numbers ›
 * Line Settings): e.g. "49" with the international prefix "00", and "30" with the national
 * prefix "0" for Berlin. An empty [nationalPrefix]: the country has none (numbers are always
 * dialled in full).
 */
data class DialingCodes(
    val countryCode: String,
    val internationalPrefix: String = "00",
    val areaCode: String? = null,
    val nationalPrefix: String = "0",
) {
    companion object {
        /** From the answers of `GetVoIPCommonCountryCode` and `GetVoIPCommonAreaCode`; null without country code. */
        fun from(country: Map<String, String>, area: Map<String, String>): DialingCodes? {
            val countryCode = country["NewX_AVM-DE_LKZ"].digitsOrNull() ?: return null
            return DialingCodes(
                countryCode = countryCode,
                internationalPrefix = country["NewX_AVM-DE_LKZPrefix"].digitsOrNull() ?: "00",
                areaCode = area["NewX_AVM-DE_OKZ"].digitsOrNull(),
                // Present but empty is meaningful (no national prefix); missing means the usual "0".
                nationalPrefix = area["NewX_AVM-DE_OKZPrefix"]?.filter(Char::isDigit) ?: "0",
            )
        }

        private fun String?.digitsOrNull() = this?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
    }
}

/**
 * What the box reports about itself, to tell boxes apart: e.g. "FRITZ!Box 7590", "164.07.57" and
 * "E0286D123456" (AVM's "serial number", in fact the box' MAC address without separators).
 */
data class BoxInfo(
    val model: String?,
    /** Raw version: the box' product number, then the FRITZ!OS version (see [fritzOsVersion]). */
    val softwareVersion: String?,
    val serialNumber: String?,
) {
    /** FRITZ!OS version for display: "164.07.57" → "7.57", "164.08.00-120123" → "8.00-120123". */
    val fritzOsVersion: String?
        get() = softwareVersion?.let { raw ->
            VERSION.matchEntire(raw)?.let { m -> "${m.groupValues[1].toInt()}.${m.groupValues[2]}${m.groupValues[3]}" }
                ?: raw
        }

    companion object {
        private val VERSION = Regex("""\d+\.(\d+)\.(\d+)(.*)""")

        fun from(arguments: Map<String, String>) = BoxInfo(
            model = arguments["NewModelName"]?.takeIf { it.isNotBlank() },
            softwareVersion = arguments["NewSoftwareVersion"]?.takeIf { it.isNotBlank() },
            serialNumber = arguments["NewSerialNumber"]?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * A running second-factor confirmation.
 * The box reports its methods as e.g. "button,dtmf;*12622": a list of methods and,
 * after the semicolon, the code to dial on a connected phone.
 */
data class SecondFactorChallenge(
    val token: String,
    val button: Boolean,
    /** Code to dial on a phone connected to the box, e.g. "*12622", or null. */
    val dtmfCode: String?,
    val otherMethods: List<String>,
) {
    companion object {
        fun parse(token: String, methods: String): SecondFactorChallenge {
            val parts = methods.split(';').map { it.trim() }
            val names = parts.firstOrNull().orEmpty()
                .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val code = parts.drop(1).firstOrNull { it.isNotEmpty() }
            return SecondFactorChallenge(
                token = token,
                button = "button" in names,
                dtmfCode = code.takeIf { "dtmf" in names || code != null },
                otherMethods = names.filter { it != "button" && it != "dtmf" },
            )
        }
    }
}
