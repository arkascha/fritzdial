package info.christianreiner.fritzdial.tr064

import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Base64
import javax.net.ssl.X509TrustManager

/**
 * Trust-on-first-use pinning for the FRITZ!Box' self-signed certificate.
 *
 * A server certificate is accepted only if it is byte-identical to the one the user
 * explicitly trusted ([pinnedDer]). Anything else is rejected, and the presented chain
 * is remembered in [rejectedChain] so the UI can show it and ask the user.
 */
class PinningTrustManager(private val pinnedDer: ByteArray?) : X509TrustManager {

    @Volatile
    var rejectedChain: List<X509Certificate>? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Empty certificate chain")
        if (pinnedDer != null && leaf.encoded.contentEquals(pinnedDer)) return
        rejectedChain = chain.toList()
        throw CertificateException("Certificate not trusted: ${CertificateInfo.from(leaf).sha256}")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Thrown when the box presents a certificate the user has not (yet) trusted.
 * [previouslyTrusted] is true when a *different* certificate had been trusted before,
 * i.e. the certificate changed – which deserves a stronger warning.
 */
class UntrustedCertificateException(
    val certificate: CertificateInfo,
    val previouslyTrusted: Boolean,
) : IOException("The router presented an untrusted certificate")

/** Human readable view of an X.509 certificate, plus its DER encoding for pinning. */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String,
    val validUntil: String,
    val expired: Boolean,
    val selfSigned: Boolean,
    val subjectAltNames: List<String>,
    val signatureAlgorithm: String,
    val publicKey: String,
    val sha256: String,
    val sha1: String,
    val pem: String,
    val derBase64: String,
) {
    companion object {
        fun from(cert: X509Certificate): CertificateInfo {
            val der = cert.encoded
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val now = System.currentTimeMillis()
            val b64 = Base64.getEncoder().encodeToString(der)
            return CertificateInfo(
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                serialNumber = cert.serialNumber.toString(16).uppercase(),
                validFrom = fmt.format(cert.notBefore),
                validUntil = fmt.format(cert.notAfter),
                expired = now > cert.notAfter.time || now < cert.notBefore.time,
                selfSigned = cert.subjectX500Principal == cert.issuerX500Principal,
                subjectAltNames = runCatching {
                    cert.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() }
                }.getOrNull().orEmpty(),
                signatureAlgorithm = cert.sigAlgName,
                publicKey = describeKey(cert),
                sha256 = fingerprint(der, "SHA-256"),
                sha1 = fingerprint(der, "SHA-1"),
                pem = toPem(b64),
                derBase64 = b64,
            )
        }

        fun fromDerBase64(derBase64: String): CertificateInfo? = runCatching {
            val der = Base64.getDecoder().decode(derBase64)
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            from(cert)
        }.getOrNull()

        fun fingerprint(der: ByteArray, algorithm: String): String =
            MessageDigest.getInstance(algorithm).digest(der)
                .joinToString(":") { "%02X".format(it) }

        private fun toPem(b64: String): String = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            b64.chunked(64).forEach { append(it).append('\n') }
            append("-----END CERTIFICATE-----")
        }

        private fun describeKey(cert: X509Certificate): String {
            val key = cert.publicKey
            val bits = when (key) {
                is java.security.interfaces.RSAPublicKey -> key.modulus.bitLength()
                is java.security.interfaces.ECPublicKey -> key.params.order.bitLength()
                else -> null
            }
            return if (bits != null) "${key.algorithm} $bits bit" else key.algorithm
        }
    }
}
