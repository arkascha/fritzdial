package info.christianreiner.fritzdial.tr064

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Minimal HTTP Digest (RFC 7616, MD5 / qop=auth) authenticator for OkHttp.
 *
 * The FRITZ!Box TR-064 interface answers unauthenticated SOAP requests with
 * `401` and a `WWW-Authenticate: Digest realm="F!Box SOAP-Auth", nonce=..., qop="auth"`
 * challenge. OkHttp has no built-in digest support, so we answer the challenge here.
 */
class DigestAuthenticator(
    private val username: String,
    private val password: String,
) : Authenticator {

    private val random = SecureRandom()

    override fun authenticate(route: Route?, response: Response): Request? {
        val challengeHeader = response.headers("WWW-Authenticate")
            .firstOrNull { it.trim().startsWith("Digest", ignoreCase = true) }
            ?: return null

        val challenge = parseChallenge(challengeHeader)
        val nonce = challenge["nonce"] ?: return null

        // If we already answered this very nonce and still got 401, the credentials are wrong.
        // A "stale=true" challenge means the nonce expired and we may retry once with the new one.
        val previous = response.request.header("Authorization")
        val stale = challenge["stale"].equals("true", ignoreCase = true)
        if (previous != null && !stale) return null
        if (responseCount(response) >= 3) return null

        val header = buildAuthorization(
            method = response.request.method,
            uri = response.request.url.encodedPath +
                (response.request.url.encodedQuery?.let { "?$it" } ?: ""),
            challenge = challenge,
            nonce = nonce,
        )
        return response.request.newBuilder()
            .header("Authorization", header)
            .build()
    }

    internal fun buildAuthorization(
        method: String,
        uri: String,
        challenge: Map<String, String>,
        nonce: String,
        cnonce: String = newCnonce(),
        nc: String = "00000001",
    ): String {
        val realm = challenge["realm"].orEmpty()
        val opaque = challenge["opaque"]
        val qopOffered = challenge["qop"]
            ?.split(',')
            ?.map { it.trim() }
            .orEmpty()
        val useQop = qopOffered.contains("auth")

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")
        val responseHash = if (useQop) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        return buildString {
            append("Digest ")
            append("username=\"").append(username).append("\", ")
            append("realm=\"").append(realm).append("\", ")
            append("nonce=\"").append(nonce).append("\", ")
            append("uri=\"").append(uri).append("\", ")
            append("algorithm=MD5, ")
            if (useQop) {
                append("qop=auth, ")
                append("nc=").append(nc).append(", ")
                append("cnonce=\"").append(cnonce).append("\", ")
            }
            if (opaque != null) append("opaque=\"").append(opaque).append("\", ")
            append("response=\"").append(responseHash).append("\"")
        }
    }

    private fun newCnonce(): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private val PARAM = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^,\s]*))""")

        internal fun parseChallenge(header: String): Map<String, String> {
            val body = header.trim().removePrefix("Digest").removePrefix("digest")
            return PARAM.findAll(body).associate { m ->
                m.groupValues[1].lowercase() to
                    (m.groups[2]?.value ?: m.groups[3]?.value.orEmpty())
            }
        }

        internal fun md5(input: String): String =
            MessageDigest.getInstance("MD5")
                .digest(input.toByteArray(Charsets.UTF_8))
                .toHex()

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it) }
    }
}
