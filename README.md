# FritzDial

Android dialer that places calls through a FRITZ!Box using the TR-064 **dial help**
("Wählhilfe", click-to-dial). It behaves exactly like dialling from the router's web UI:
the box first rings the selected phone (e.g. a DECT handset); once you pick it up,
the box dials the target number from that phone.

## Open in Android Studio

*File › Open…* → select this folder. Studio downloads Gradle 8.14.3 and the
dependencies on first sync. If it offers an AGP / dependency upgrade, accepting is fine.

- Kotlin + Jetpack Compose (Material 3), minSdk 26, target/compileSdk 36
- Only external library: OkHttp (TR-064 digest auth is implemented in-app)

## FRITZ!Box preparation

1. **Home Network › Network › Network Settings** → enable *Allow access for applications* (TR-064).
2. **Telephony › Calls › Dial Help** → enable *Use dial help*.
3. **System › FRITZ!Box Users** → use (or create) a user with telephony permission.
   If you log in with password only, the box still has an auto-generated user name
   (e.g. `fritz1234`) shown there – TR-064 needs it.

## Using the app

1. On first start, enter host (`fritz.box` or `192.168.178.1`), user and password. Optionally switch on
   **Use HTTPS** (port changes from `49000` to `49443` automatically).
2. The app reads the phones the box knows (FON / DECT / IP) and the one currently configured
   for dial help. Choose your DECT handset in **Ring phone**.
3. Enter a number (long-press **0** for **+**) and tap the green button → the handset rings → pick it up → the call is placed.
   The red button cancels a dial-help call that is still being set up.

**Save & connect** tests the settings first: they are only saved once the router accepts them.
Otherwise the settings stay open and an error dialog explains the problem (wrong user name or password,
missing telephony permission, router not found or not reachable, wrong port / HTTPS setting, router
temporarily blocking logins). Errors during normal use appear in the same dialog on top of the dialer,
with **Retry** and, when the cause is likely in the settings, **Open settings**.

### Phone books

The address-book button at the left of the number field opens the phone books stored on the
FRITZ!Box (TR-064 `X_AVM-DE_OnTel`: `GetPhonebookList`, `GetPhonebook`, then the XML export).
Online phone books configured on the box (e.g. CardDAV) are synced into it and should appear as well.
Search by name or number, filter by phone book, and tap a number to put it into the dialer.
The books are loaded once per app start; the reload button at the top fetches them again.

The contacts stored on the phone itself appear as an additional book **This phone** (all Android
accounts, e.g. Google or CardDAV via DAVx⁵). Android asks once for the contacts permission; it is
optional – without it only the FRITZ!Box phone books are shown.

### Confirmation on the FRITZ!Box (two-factor)

Newer FRITZ!OS versions protect settings changes with an *additional confirmation* on the box
(TR-064 error 866). Changing the dial-help phone is such a change; dialling itself is not. FritzDial
therefore only changes the dial-help phone when it differs from the selected one. When a confirmation
is needed, a dialog shows how to give it – press a button on the box, or dial the shown code
(e.g. `*12622`) on a connected phone – and the step continues automatically once confirmed.

### HTTPS and the router certificate

The FRITZ!Box uses a self-signed certificate, so it cannot be verified automatically. FritzDial
uses *trust on first use* with certificate pinning:

- On the first HTTPS connection a dialog shows the certificate (SHA-256/SHA-1 fingerprints, subject,
  issuer, alternative names, validity, key) and, on request, the full PEM text (selectable for copying).
  Compare the fingerprint with what your browser shows for `https://fritz.box`, then tap **Trust**.
- From then on, only that exact certificate is accepted. Host name checks are skipped because the box'
  certificate rarely matches the name or IP you use; the pin is the stronger check.
- If the box ever presents a different certificate (factory reset, renamed box – or an attacker),
  a **Certificate has changed!** warning appears instead of connecting silently.
- **Settings › Trusted certificate** shows the pinned certificate's fingerprint, with **Show** (full details)
  and **Forget**.

FritzDial also registers for `tel:` links, so you can pick it when tapping a number in
Contacts or a web page; the number is pre-filled.

## Code map

| File | Purpose |
|---|---|
| `tr064/DigestAuthenticator.kt` | HTTP Digest (MD5, qop=auth) for OkHttp |
| `tr064/Tr064Client.kt` | Generic SOAP call over HTTP/HTTPS + response / UPnP fault parsing |
| `tr064/CertificateTrust.kt` | Pinning trust manager, certificate details, "untrusted certificate" exception |
| `tr064/FritzDialer.kt` | `X_VoIP` actions: `X_AVM-DE_GetPhonePort`, `X_AVM-DE_DialGetConfig`, `X_AVM-DE_DialSetConfig`, `X_AVM-DE_DialNumber`, `X_AVM-DE_DialHangup` |
| `tr064/Phonebooks.kt` | Phone book list / download / XML parsing (`X_AVM-DE_OnTel`) |
| `data/DeviceContacts.kt` | The phone's own contacts as an extra phone book (`READ_CONTACTS`) |
| `data/SettingsRepository.kt` | Connection settings; password encrypted via Android Keystore |
| `ui/` | ViewModel and Compose screens (dialer, settings, certificate dialogs) |

Unit tests (`./gradlew test`) cover digest computation, envelope building, number sanitising and
certificate pinning.

## Notes / ideas

- Plain HTTP (port 49000) remains the default; even there the password never travels in clear text
  (digest auth). HTTPS additionally encrypts the numbers you dial and protects against spoofed routers.
- Works only while the phone is in the home network (or via VPN to it).
- Possible next steps: call list / phone book via `X_AVM-DE_OnTel`, recent numbers, a quick-settings tile.
