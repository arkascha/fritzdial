package info.christianreiner.fritzdial.tr064

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigestAuthenticatorTest {

    @Test
    fun `computes the RFC 2617 reference response`() {
        val challenge = DigestAuthenticator.parseChallenge(
            "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
                "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        )
        val header = DigestAuthenticator("Mufasa", "Circle Of Life").buildAuthorization(
            method = "GET",
            uri = "/dir/index.html",
            challenge = challenge,
            nonce = challenge.getValue("nonce"),
            cnonce = "0a4f113b",
            nc = "00000001",
        )
        assertTrue(header, header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
    }

    @Test
    fun `parses a FRITZ!Box challenge`() {
        val c = DigestAuthenticator.parseChallenge(
            "Digest realm=\"F!Box SOAP-Auth\", nonce=\"ABCDEF0123456789\", algorithm=MD5, qop=\"auth\""
        )
        assertEquals("F!Box SOAP-Auth", c["realm"])
        assertEquals("ABCDEF0123456789", c["nonce"])
        assertEquals("MD5", c["algorithm"])
        assertEquals("auth", c["qop"])
    }

    @Test
    fun `sanitizes phone numbers`() {
        assertEquals("+4930123456", FritzDialer.sanitizeNumber("+49 (30) 123-456"))
        assertEquals("**610", FritzDialer.sanitizeNumber("**610"))
    }

    @Test
    fun `builds a SOAP envelope with escaped arguments`() {
        val xml = Tr064Client.buildEnvelope(
            FritzDialer.SERVICE_TYPE, "X_AVM-DE_DialSetConfig",
            mapOf("NewX_AVM-DE_PhoneName" to "DECT: Küche & Flur"),
        )
        assertTrue(xml.contains("<u:X_AVM-DE_DialSetConfig xmlns:u=\"urn:dslforum-org:service:X_VoIP:1\">"))
        assertTrue(xml.contains("<NewX_AVM-DE_PhoneName>DECT: Küche &amp; Flur</NewX_AVM-DE_PhoneName>"))
    }
}
