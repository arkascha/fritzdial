package info.christianreiner.fritzdial.tr064

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/** A user of the box; [lastUsed]: the one who logged in last (to the web UI or an app). */
data class BoxUser(val name: String, val lastUsed: Boolean)

/**
 * Lists the users of the box via TR-064 `LANConfigSecurity` `X_AVM-DE_GetUserList`, so the user
 * name can be chosen instead of looked up – useful above all for the auto-generated one
 * (e.g. "fritz1234") of a box with password-only login.
 */
object BoxUsers {

    const val SERVICE_TYPE = "urn:dslforum-org:service:LANConfigSecurity:1"
    const val CONTROL_URL = "/upnp/control/lanconfigsecurity"

    /**
     * Asks the box without credentials: the list is meant for choosing the user before logging in,
     * and no failed login attempt is counted against the box' login protection. With HTTPS whose
     * certificate is not trusted yet, the box is asked over plain HTTP (the list is no secret).
     */
    @Throws(IOException::class)
    fun list(config: FritzConfig): List<BoxUser> {
        val anonymous = config.copy(username = "", password = "").let {
            if (it.useHttps && it.trustedCertDer == null) it.copy(useHttps = false, port = FritzConfig.HTTP_PORT) else it
        }
        val xml = Tr064Client(anonymous)
            .call(SERVICE_TYPE, CONTROL_URL, "X_AVM-DE_GetUserList")["NewX_AVM-DE_UserList"]
            .orEmpty()
        return parse(xml)
    }

    /** `<List><Username last_user="1">fritz1234</Username><Username>anna</Username></List>` */
    internal fun parse(xml: String): List<BoxUser> {
        if (xml.isBlank()) return emptyList()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val users = mutableListOf<BoxUser>()
        var lastUsed = false
        val text = StringBuilder()
        var inName = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "Username") {
                    inName = true
                    lastUsed = parser.getAttributeValue(null, "last_user") == "1"
                    text.setLength(0)
                }
                XmlPullParser.TEXT -> if (inName) text.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "Username") {
                    inName = false
                    text.toString().trim().takeIf { it.isNotEmpty() }?.let { users += BoxUser(it, lastUsed) }
                }
            }
            parser.next()
        }
        return users.distinctBy { it.name }
    }
}

/**
 * The rights of the logged-in user as the box reports them (`X_AVM-DE_GetCurrentUser`): per area
 * ("BoxAdmin", "Phone", "HomeAuto", "NAS", …) its access ("none", "read" or "readwrite").
 */
data class UserRights(val username: String?, val access: Map<String, String>) {

    /**
     * The right "Voice messages, fax messages, FRITZ!App Fon and call list", needed for the call
     * list; null when the box did not say.
     */
    val phone: Boolean? get() = access[PHONE]?.let { it != NONE }

    companion object {
        const val BOX_ADMIN = "BoxAdmin"
        const val PHONE = "Phone"
        const val HOME_AUTO = "HomeAuto"
        const val NAS = "NAS"
        const val NONE = "none"
        const val READ = "read"
        const val READ_WRITE = "readwrite"

        /** `<rights><path>BoxAdmin</path><access>readwrite</access><path>Phone</path>…</rights>` */
        internal fun parse(username: String?, xml: String): UserRights {
            val access = LinkedHashMap<String, String>()
            if (xml.isNotBlank()) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(StringReader(xml))
                var tag: String? = null
                var path: String? = null
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> tag = parser.name
                        XmlPullParser.TEXT -> {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) when (tag) {
                                "path" -> path = text
                                // Each access belongs to the path just before it.
                                "access" -> path?.let { access[it] = text.lowercase() }.also { path = null }
                            }
                        }
                        XmlPullParser.END_TAG -> tag = null
                    }
                    parser.next()
                }
            }
            return UserRights(username?.takeIf { it.isNotBlank() }, access)
        }
    }
}
