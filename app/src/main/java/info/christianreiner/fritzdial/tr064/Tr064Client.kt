package info.christianreiner.fritzdial.tr064

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

/** Connection parameters for the FRITZ!Box TR-064 interface. */
data class FritzConfig(
    val host: String = "fritz.box",
    val port: Int = HTTP_PORT,
    val username: String = "",
    val password: String = "",
    /** Talk TLS to the box (TR-064 "security port", usually 49443). */
    val useHttps: Boolean = false,
    /** Base64 DER of the certificate the user explicitly trusted, or null. */
    val trustedCertDer: String? = null,
) {
    val baseUrl: String get() = "${if (useHttps) "https" else "http"}://$host:$port"

    companion object {
        const val HTTP_PORT = 49000
        const val HTTPS_PORT = 49443
    }
}

/** The box rejected the credentials (HTTP 401 after the digest challenge was answered). */
class AuthenticationException : IOException("Authentication failed – check user name and password")

/** The box answered with an unexpected HTTP status and no SOAP fault (e.g. 503 while it blocks logins). */
class HttpStatusException(val code: Int) : IOException("Unexpected HTTP status $code from the router")

/** A SOAP fault returned by the box, e.g. 401 "Invalid Action" or 713 "SpecifiedArrayIndexInvalid". */
class Tr064Exception(
    val errorCode: Int?,
    val errorDescription: String?,
    message: String,
) : IOException(message) {
    /** 866: the action is protected and must be confirmed on the box itself (two-factor). */
    val secondFactorRequired: Boolean get() = errorCode == SECOND_FACTOR_REQUIRED

    companion object {
        /** The user is logged in but lacks the right for the action. */
        const val NOT_AUTHORIZED = 606
        const val SECOND_FACTOR_REQUIRED = 866
        const val SECOND_FACTOR_BLOCKED = 867
        const val SECOND_FACTOR_BUSY = 868
    }
}

/**
 * Very small generic TR-064 SOAP client.
 *
 * TR-064 is plain SOAP 1.1 over HTTP(S) with digest authentication.
 * With HTTP (port 49000) credentials still never travel in clear text thanks to
 * digest auth. With HTTPS (port 49443) the box' self-signed certificate is pinned:
 * only the exact certificate the user trusted is accepted (see [PinningTrustManager]).
 */
class Tr064Client(
    private val config: FritzConfig,
    /**
     * Token of a confirmed second-factor authentication (see [FritzDialer.startSecondFactor]).
     * When set, it is sent in the SOAP header so the box accepts a protected action (error 866).
     */
    private val authToken: String? = null,
) {

    private val pinnedDer: ByteArray? =
        config.trustedCertDer?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    private val trustManager: PinningTrustManager? =
        if (config.useHttps) PinningTrustManager(pinnedDer) else null

    private val http: OkHttpClient = OkHttpClient.Builder()
        .apply {
            // Without credentials a 401 is final: answering it would only count as a failed login.
            if (config.username.isNotEmpty() || config.password.isNotEmpty()) {
                authenticator(DigestAuthenticator(config.username, config.password))
            }
        }
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .apply {
            trustManager?.let { tm ->
                val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
                sslSocketFactory(ssl.socketFactory, tm)
                // The box' certificate rarely matches the name/IP used to reach it. Since only the
                // exact pinned certificate is accepted, a host name check adds no security here.
                hostnameVerifier { _, _ -> true }
            }
        }
        .build()

    /**
     * Invokes [action] of [serviceType] at [controlUrl] and returns the response
     * arguments as a name -> value map (e.g. "NewX_AVM-DE_PhoneName" -> "DECT: Mobilteil 1").
     */
    @Throws(IOException::class)
    fun call(
        serviceType: String,
        controlUrl: String,
        action: String,
        arguments: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val envelope = buildEnvelope(serviceType, action, arguments, authToken)
        val request = Request.Builder()
            .url(config.baseUrl + controlUrl)
            .header("SOAPACTION", "\"$serviceType#$action\"")
            .post(envelope.toRequestBody(XML))
            .build()

        val call = http.newCall(request)
        val executed = try {
            call.execute()
        } catch (e: SSLException) {
            val chain = trustManager?.rejectedChain
            if (chain != null) {
                throw UntrustedCertificateException(
                    certificate = CertificateInfo.from(chain.first()),
                    previouslyTrusted = pinnedDer != null,
                )
            }
            throw e
        }

        executed.use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code == 401) throw AuthenticationException()
            if (!response.isSuccessful) {
                val fault = parseFault(body)
                if (fault != null) throw fault
                throw HttpStatusException(response.code)
            }
            return parseResponseArguments(body, "${action}Response")
        }
    }

    /**
     * Downloads a file the box handed out via TR-064 (e.g. a phone book URL with a session id).
     * Scheme, host and port are replaced by the configured ones, so the same connection
     * settings – and the pinned certificate for HTTPS – apply as for the SOAP calls.
     */
    @Throws(IOException::class)
    fun download(url: String): String {
        val given = url.toHttpUrlOrNull() ?: throw IOException("Invalid URL from the router: $url")
        val base = config.baseUrl.toHttpUrl()
        val target = given.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        val call = http.newCall(Request.Builder().url(target).get().build())
        val response = try {
            call.execute()
        } catch (e: SSLException) {
            val chain = trustManager?.rejectedChain
            if (chain != null) {
                throw UntrustedCertificateException(CertificateInfo.from(chain.first()), pinnedDer != null)
            }
            throw e
        }
        response.use {
            if (it.code == 401) throw AuthenticationException()
            if (!it.isSuccessful) throw HttpStatusException(it.code)
            return it.body?.string().orEmpty()
        }
    }

    companion object {
        private val XML = "text/xml; charset=utf-8".toMediaType()

        internal fun buildEnvelope(
            serviceType: String,
            action: String,
            arguments: Map<String, String>,
            authToken: String? = null,
        ): String = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            if (authToken != null) {
                // Second-factor token, as described in AVM's X_AVM-DE_Auth documentation.
                append("<s:Header>")
                append("<avm:token xmlns:avm=\"avm.de\" mustUnderstand=\"1\">")
                append(escapeXml(authToken))
                append("</avm:token>")
                append("</s:Header>")
            }
            append("<s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(serviceType).append("\">")
            for ((name, value) in arguments) {
                append('<').append(name).append('>')
                append(escapeXml(value))
                append("</").append(name).append('>')
            }
            append("</u:").append(action).append('>')
            append("</s:Body></s:Envelope>")
        }

        internal fun escapeXml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        /** Collects the direct child elements of the `<u:ActionResponse>` element. */
        internal fun parseResponseArguments(xml: String, responseElement: String): Map<String, String> {
            val parser = newParser(xml)
            val result = LinkedHashMap<String, String>()
            var inResponse = false
            var depthOfResponse = -1
            var currentName: String? = null
            val text = StringBuilder()

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (!inResponse && parser.name == responseElement) {
                            inResponse = true
                            depthOfResponse = parser.depth
                        } else if (inResponse && parser.depth == depthOfResponse + 1) {
                            currentName = parser.name
                            text.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> if (currentName != null) text.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (inResponse && parser.depth == depthOfResponse + 1 && currentName != null) {
                            result[currentName] = text.toString().trim()
                            currentName = null
                        } else if (inResponse && parser.depth == depthOfResponse) {
                            inResponse = false
                        }
                    }
                }
                parser.next()
            }
            return result
        }

        /** Extracts `<UPnPError><errorCode/><errorDescription/></UPnPError>` from a SOAP fault. */
        internal fun parseFault(xml: String): Tr064Exception? {
            if (!xml.contains("UPnPError")) return null
            val parser = newParser(xml)
            var code: String? = null
            var description: String? = null
            var current: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> current = parser.name
                    XmlPullParser.TEXT -> when (current) {
                        "errorCode" -> code = parser.text.trim()
                        "errorDescription" -> description = parser.text.trim()
                    }
                    XmlPullParser.END_TAG -> current = null
                }
                parser.next()
            }
            val numeric = code?.toIntOrNull()
            return Tr064Exception(
                numeric,
                description,
                "FRITZ!Box error ${code ?: "?"}: ${description ?: "unknown"}",
            )
        }

        private fun newParser(xml: String): XmlPullParser {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            return factory.newPullParser().apply { setInput(StringReader(xml)) }
        }
    }
}
