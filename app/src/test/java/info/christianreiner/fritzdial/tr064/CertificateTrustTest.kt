package info.christianreiner.fritzdial.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CertificateTrustTest {

    // Self-signed test certificate, CN=fritz.box, SAN fritz.box / 192.168.178.1
    private val pem = """
            -----BEGIN CERTIFICATE-----
            MIIC5zCCAc+gAwIBAgIIMsS9MDUqBKgwDQYJKoZIhvcNAQEMBQAwFDESMBAGA1UE
            AxMJZnJpdHouYm94MB4XDTI2MTAxODE3MzcyOFoXDTM2MTAxNTE3MzcyOFowFDES
            MBAGA1UEAxMJZnJpdHouYm94MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
            AQEAzJDVAbgW60sqgdBLnLM+wlEKESWATXsXuem2LGaZDlmJhuf3dPmpCnEwkGpt
            4Heuapc3qMFxCJPYjKwBRAoUhO8S3Kc6Wc9rk9TW0EqsZ6dxUUwjzkx10eJOv1iS
            m4uX0wCardLYO5EKWrxlCdwwa6ULf7EGIou0NhLvnaPWL+tpjiabhocn1cmvmD7V
            jvp4ZTl45gJBQ5pMsVvs/p86auLUT1/tmyyLt2jxmnj4sETC1H3oBdmOXBjFDm27
            b/Vz8x0l8SeOiYxW9L1Qrj8V3DK5rHE9xjXw3zq+dGwd6jM5w47+cu1WSzME/c1b
            ovJZzMW4O15Mh5vO51+fcRpPwQIDAQABoz0wOzAdBgNVHQ4EFgQUEzH6qWH6NCbM
            ptJbac4lZsGjm6YwGgYDVR0RBBMwEYIJZnJpdHouYm94hwTAqLIBMA0GCSqGSIb3
            DQEBDAUAA4IBAQDHlwnTrbM3dlj+NuChmMvUBqg9rXW4ArekeeC/w06cAeRCeeOc
            M5ogVcHyaRPM9mjLKPaECkpqXSljKOPI/XH4wuYENOtbMtVpjWgNzTifEkRutTYv
            2eqRNwh9ocqzUBT222n1Imd4uFkp1kwtmXD0hPewX3GlAg5iwuEDwzU7t39zivtQ
            zMMpJhBbXcPbbeofVqnDVBiqcJwvFGQLGhn730IzVC4UpKGIPgWg0AcNGoDWrnkD
            RjSI8t20/xYTqL8+BRr0NLJk/znMbLMQFPuBVTXk1zL85fJgwIZE3oKdDXNXvXUm
            /VBf0q76N+9etOxExBqdBthT55SxHXZqFncv
            -----END CERTIFICATE-----
    """.trimIndent()

    private val cert: X509Certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(pem.byteInputStream()) as X509Certificate

    @Test
    fun `describes the certificate`() {
        val info = CertificateInfo.from(cert)
        assertEquals("FF:2D:BF:20:FB:1B:C7:9C:EB:EB:B2:2E:52:C8:AE:B6:01:F7:C0:02:95:CD:A6:51:9D:05:1A:F8:A0:1A:4C:61", info.sha256)
        assertEquals("CN=fritz.box", info.subject)
        assertTrue(info.selfSigned)
        assertFalse(info.expired)
        assertTrue(info.subjectAltNames.containsAll(listOf("fritz.box", "192.168.178.1")))
        assertEquals("RSA 2048 bit", info.publicKey)
    }

    @Test
    fun `round trips through the stored DER`() {
        val info = CertificateInfo.from(cert)
        assertEquals(info.sha256, CertificateInfo.fromDerBase64(info.derBase64)?.sha256)
    }

    @Test
    fun `accepts only the pinned certificate`() {
        PinningTrustManager(cert.encoded).checkServerTrusted(arrayOf(cert), "RSA")

        val unpinned = PinningTrustManager(null)
        try {
            unpinned.checkServerTrusted(arrayOf(cert), "RSA")
            fail("expected rejection")
        } catch (expected: CertificateException) {
            assertNotNull(unpinned.rejectedChain)
        }

        try {
            PinningTrustManager(byteArrayOf(1, 2, 3)).checkServerTrusted(arrayOf(cert), "RSA")
            fail("expected rejection")
        } catch (expected: CertificateException) {
        }
    }
}
