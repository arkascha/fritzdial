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

### Several FritzBoxes

**Settings › FritzBox connections** holds any number of boxes (e.g. home and office), each with its
own name, host, port, HTTPS, user, password, use of its phone books and call list, and trusted
certificate. **Add FritzBox** adds one; tap an entry to edit (or delete) it. Each entry says whether the
box is *reachable* (it answered the last detection: in this network, or at its configured host),
*not reachable* or *not recognised yet* (its device id is not known yet, see below). The box in use is
chosen automatically and marked with a check mark, and shown under the app name on the dialer. The mark
turns into a red warning triangle when that box was not found by the last detection, or when the last
request to it failed; a network change clears the latter until the next request. Only when none of the
boxes is reachable (e.g. on mobile data without VPN) does an empty circle appear in front of the others, to choose one by hand; the dialer then says that none of
your boxes is here and which one it tries.

The connection page also shows the box' **device details** – model, FRITZ!OS version and serial number
(TR-064 `DeviceInfo` `GetInfo`) – to tell boxes apart. They are read when the connection is saved and
again whenever the phones are loaded, so they stay current after a FRITZ!OS update. Reading them is
optional: if the box' user lacks the rights for it, they are simply missing. What AVM calls the serial
number is in fact the box' MAC address, not the number on its label.

The box of the current network is selected automatically – at start, whenever the phone's network
changes and when the app comes back to the foreground; if none of the boxes is found, it tries once more
a few seconds later (right after a network change the box often does not answer yet). Every FritzBox
publishes a public device description (`http://<box>:49000/tr64desc.xml`) with a unique device id
(UDN); FritzDial records it when a connection is saved, or – for a connection saved while away – on the
first successful login to it, and asks the network's gateway, `fritz.box` and the configured hosts for
it. The box of the network itself (gateway, `fritz.box`) wins; a box answering only at its configured
host (through a VPN, or at its MyFRITZ address) is used only when the network has none of the boxes.
If a FritzBox answers that is not configured, the dialer says so.

The device description needs no login and can be imitated. So after switching, FritzDial also compares
the box' serial number (`DeviceInfo` `GetInfo`, see above) with the stored one, and refuses a box whose
number differs. With HTTPS, the pinned certificate protects in addition.

### Choosing a number: recent calls and phone books

The address-book button at the left of the number field opens **Choose number** with two tabs,
**Recent** and **Phone books**; tapping a number in either puts it into the dialer. The reload button
at the top refreshes both.

### Phone books

The **Phone books** tab shows the phone books stored on the
FRITZ!Box (TR-064 `X_AVM-DE_OnTel`: `GetPhonebookList`, `GetPhonebook`, then the XML export).
Online phone books configured on the box (e.g. CardDAV) are synced into it and should appear as well.
Search by name or number, filter by phone book, and tap a number to put it into the dialer.
The books are loaded once per app start; the reload button at the top fetches them again.
Whether a box' phone books are used is set per FritzBox, on its connection page
(**Settings › FritzBox connections › <box> › Phone books**, on by default).

Optionally, the contacts stored on the phone itself appear as an additional book **This phone**
(all Android accounts, e.g. Google or CardDAV via DAVx⁵). This is off by default. When the **Phone books**
tab is shown, FritzDial asks whether to include them: **Allow** (Android then asks for the
contacts permission, `READ_CONTACTS`, used for nothing else), **Not now** (asks again the next time
the phone books are opened) or **Don't ask again**. It can always be switched in
**Settings › Phone books › Include this phone's contacts**; switching it off there also stops the
question. The permission itself can be revoked in the Android app settings.

### Recent calls

The **Recent** tab lists the numbers recently dialled, received or missed, newest first, each number
once at its latest call: name and number, the time, then an arrow for how the call went (dialled,
received, missed) and an icon for where it was recorded. Rejected calls and calls with a
withheld number are left out. The list is read anew every time the picker is opened (or with the
reload button), from two sources:

- **FritzBox** (router icon with the box' name from the settings) – the dialled, received and missed calls in the box' call list (TR-064 `X_AVM-DE_OnTel` `GetCallList`),
  i.e. FritzDial's dial-help calls as well as the calls of the handsets themselves. The FRITZ!Box user
  needs the permission *Voice messages, fax messages, FRITZ!App Fon and call list* for this.
  Whether it is used is set per FritzBox, on its connection page (**Settings › FritzBox connections ›
  <box> › Call list**, on by default) – e.g. for a box whose user lacks that permission.
- **This phone** (phone icon; optional, off by default) – the dialled, received and missed calls of the phone's own telephony app,
  read from the Android call log (`READ_CALL_LOG`, used for nothing else). FritzDial's calls run on
  the FRITZ!Box and never appear there. Switch it on in **Settings › Recent numbers › Include this
  phone's calls**. **This only works if FritzDial was installed from outside Google Play** (APK or
  F-Droid): Google Play allows the call log permission only for the default phone app.
  On switching it on, FritzDial asks Android for the permission and test-reads the call log; if
  either fails, the switch stays off and the settings show why. If reading fails later (e.g. the
  permission was revoked), the **Recent** tab shows a note with **Turn off** and **App settings**.

### Speed dial

Speed dials come from two sources that never collide, each of which can be switched off:

- **This phone** – speed dials **1 to 9**, stored on the phone and valid with every FritzBox (name
  and number are kept as a copy). Long-press the digit in the dialer to use one; digits with a speed
  dial have a small dot. Set them up in the picker's **Speed dial** tab (tap a free slot, then the
  number in the phone books; long-press a set one to change or remove it), or long-press a number in
  the **Phone books** tab. Switch: **Settings › Speed dial › This phone's speed dials** (on by default).
- **FritzBox** – the two-digit speed dials (**00 to 99**) set up in the phone books of the box in use
  (the `quickdial` attribute of its phone book export), the same ones its handsets dial with `**7`.
  So they depend on where you are. Tap the first digit and long-press the second, or enter `**7` and
  the number – FritzDial dials the number it stands for. Switch: on each box' connection page,
  **Speed dials** (on by default, independent of **Phone books**). The box' phone books are read in
  the background after connecting, so these work right away.

After a speed dial the number field's label says which one it is and whose number. The **Speed
dial** tab lists both kinds, so you can pick one without knowing its number.

### Language

FritzDial is available in English, German, Dutch, French, Italian, Spanish and Polish.
The flag button in the top bar of the **Settings** opens **Language**: **Auto** (the system
language, the default) or one of these, each with its national flag. Choosing one returns to the
settings, already in the new language. The button shows the flag of the language in use – the
region of the system language with *Auto* (e.g. 🇦🇹 for German in Austria), else the language's usual
country; English (🇬🇧) when the system language is not one of the above. From Android 13 on this is Android's own per-app language, so the same choice also appears under the system's
*Settings › Apps › FritzDial › Language*; before Android 13 the app applies it itself.
Texts from the FRITZ!Box (e.g. its error descriptions) and the phone's own number labels stay as
they come. The translations still need a review by native speakers; menu names of the FRITZ!Box
are taken from the German FRITZ!OS and translated descriptively for the other languages.

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
| `tr064/FritzDialer.kt` | `X_VoIP` actions: `X_AVM-DE_GetPhonePort`, `X_AVM-DE_DialGetConfig`, `X_AVM-DE_DialSetConfig`, `X_AVM-DE_DialNumber`, `X_AVM-DE_DialHangup`; device details (`DeviceInfo` `GetInfo`) |
| `tr064/BoxDiscovery.kt` | Recognises the FritzBox of the current network (gateway, `tr64desc.xml` device id) |
| `tr064/Phonebooks.kt` | Phone book list / download / XML parsing (`X_AVM-DE_OnTel`) |
| `data/DeviceContacts.kt` | The phone's own contacts as an extra phone book (`READ_CONTACTS`) |
| `tr064/CallList.kt` | Dialled, received and missed calls from the box' call list (`X_AVM-DE_OnTel` `GetCallList`, XML export) |
| `data/DeviceCallLog.kt` | The phone's own dialled, received and missed calls from the Android call log (`READ_CALL_LOG`) |
| `data/AppLanguage.kt` | UI language: system or chosen (per-app language on Android 13+, own handling before) |
| `data/SettingsRepository.kt` | FritzBox connections (passwords encrypted via Android Keystore) and options |
| `ui/` | ViewModel and Compose screens (dialer, settings, certificate dialogs); `UiText` carries ViewModel texts as resources |
| `res/values*/strings.xml` | All UI texts: English (`values/`) and the translations (`values-de/`, …) |

Unit tests (`./gradlew test`) cover digest computation, envelope building, number sanitising,
the call list's date format, the device details, merging of the recent numbers and certificate pinning.

## Notes / ideas

- Plain HTTP (port 49000) remains the default; even there the password never travels in clear text
  (digest auth). HTTPS additionally encrypts the numbers you dial and protects against spoofed routers.
- Works only while the phone is in the home network (or via VPN to it).
- Possible next steps: a quick-settings tile.
