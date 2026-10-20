package info.christianreiner.fritzdial.tr064

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** What a FritzBox says about itself in its public TR-064 device description. */
data class BoxIdentity(
    /** Unique device id, e.g. "uuid:739f2409-bccb-40e7-8e6c-…". */
    val udn: String,
    val friendlyName: String?,
    val modelName: String?,
    /** The host the box answered on. */
    val host: String,
)

/**
 * Finds out which FritzBox is reachable in the current network.
 *
 * Every FritzBox serves `http://<box>:49000/tr64desc.xml` without login (when "Allow access for
 * applications" is enabled). Its device id identifies the box unambiguously – even when every box
 * answers to the same name "fritz.box".
 */
object BoxDiscovery {

    private val http = OkHttpClient.Builder()
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    /** Asks all [hosts] in parallel; returns those that answered with a device description. */
    suspend fun identifyAll(hosts: Collection<String>): List<BoxIdentity> = coroutineScope {
        hosts.distinct().map { host -> async(Dispatchers.IO) { identify(host) } }
            .awaitAll()
            .filterNotNull()
    }

    /** Reads the device description of [host], or null if no FritzBox answers there. */
    fun identify(host: String): BoxIdentity? = runCatching {
        val request = Request.Builder()
            .url("http://${hostForUrl(host)}:${FritzConfig.HTTP_PORT}/tr64desc.xml")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parse(response.body?.string().orEmpty(), host)
        }
    }.getOrNull()

    /** IPv4 gateway of the current network – normally the FritzBox itself. */
    suspend fun currentGateway(context: Context): String? = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return@withContext null
        val network = cm.activeNetwork ?: return@withContext null
        val routes = cm.getLinkProperties(network)?.routes.orEmpty()
        routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
    }

    /** Takes the root device's values: the first UDN / friendlyName / modelName in the file. */
    internal fun parse(xml: String, host: String): BoxIdentity? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val values = HashMap<String, String>()
        var current: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> current = parser.name
                XmlPullParser.TEXT -> current?.let { tag ->
                    if (tag in WANTED && tag !in values) {
                        parser.text.trim().takeIf { it.isNotEmpty() }?.let { values[tag] = it }
                    }
                }
                XmlPullParser.END_TAG -> current = null
            }
            parser.next()
        }
        val udn = values["UDN"] ?: return null
        return BoxIdentity(udn, values["friendlyName"], values["modelName"], host)
    }

    private val WANTED = setOf("UDN", "friendlyName", "modelName")

    private fun hostForUrl(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host
}
