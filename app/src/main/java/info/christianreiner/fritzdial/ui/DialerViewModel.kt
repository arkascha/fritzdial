package info.christianreiner.fritzdial.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import info.christianreiner.fritzdial.data.BoxProfile
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.data.SettingsRepository
import info.christianreiner.fritzdial.tr064.BoxDiscovery
import info.christianreiner.fritzdial.tr064.CertificateInfo
import info.christianreiner.fritzdial.tr064.FritzConfig
import info.christianreiner.fritzdial.tr064.FritzDialer
import info.christianreiner.fritzdial.tr064.Phonebook
import info.christianreiner.fritzdial.tr064.SecondFactorChallenge
import info.christianreiner.fritzdial.tr064.Tr064Exception
import info.christianreiner.fritzdial.tr064.UntrustedCertificateException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The box presented a certificate that needs the user's decision. */
data class CertificatePrompt(
    val certificate: CertificateInfo,
    /** A different certificate had been trusted before – the certificate changed. */
    val changed: Boolean,
)

data class UiState(
    val number: String = "",
    val phones: List<String> = emptyList(),
    val selectedPhone: String? = null,
    /** Progress / success information (not an error). */
    val status: String? = null,
    /** The last failure, shown prominently until dismissed or the next action starts. */
    val error: BoxError? = null,
    /** The failed action can be retried as-is. */
    val canRetry: Boolean = false,
    val busy: Boolean = false,
    /**
     * A dial-help call was started and may still be ringing / being set up, so it can be
     * cancelled. The router does not report the call's progress over TR-064, so this ends
     * on hang-up or after [DIAL_ACTIVE_MILLIS].
     */
    val dialActive: Boolean = false,
    /** Connection in use – or, while the connection page is open, the one being edited. */
    val config: FritzConfig = FritzConfig(),
    /** All configured FritzBoxes; [activeProfileId] is the one in use. */
    val profiles: List<BoxProfile> = emptyList(),
    val activeProfileId: String? = null,
    /** Connection page: the box being edited, or null when adding a new one. */
    val editingProfileId: String? = null,
    val editingName: String = "",
    /** Note about the current network, e.g. an unknown FritzBox was found there. */
    val networkNotice: String? = null,
    val showSettings: Boolean = false,
    /** The connection sub-page of the settings is open. */
    val showConnectionSettings: Boolean = false,
    /** A working configuration has been saved before, so the settings screen may be left. */
    val settingsSaved: Boolean = false,
    /** Non-null while the "trust this certificate?" dialog is shown. */
    val certificatePrompt: CertificatePrompt? = null,
    /** Non-null while the trusted certificate is shown read-only. */
    val viewedCertificate: CertificateInfo? = null,
    /** Phone book screen is open. */
    val showPhonebook: Boolean = false,
    /** Phone books of the box, loaded on first use of the phone book screen (cached). */
    val phonebooks: List<Phonebook> = emptyList(),
    val phonebooksLoaded: Boolean = false,
    /** Optional feature, switched in the settings: include the phone's own contacts. */
    val deviceContactsEnabled: Boolean = false,
    /**
     * Ask (when opening the phone books) whether to include the phone's contacts: every time,
     * as long as the feature is off and the user did not choose "Don't ask again".
     */
    val askForDeviceContacts: Boolean = false,
    /** Contacts stored on this device, once enabled and allowed (null = not loaded). */
    val deviceContacts: Phonebook? = null,
    /** Non-null while the box waits for a second-factor confirmation (error 866). */
    val secondFactor: SecondFactorChallenge? = null,
)

/** How long to wait for the user to confirm on the box before giving up. */
private const val SECOND_FACTOR_TIMEOUT_MILLIS = 120_000L

/** The user did not confirm on the box in time. */
class SecondFactorNotConfirmedException : IOException("The action was not confirmed on the FRITZ!Box")

/** How long a started dial-help call counts as "cancellable" (the handset rings about this long). */
private const val DIAL_ACTIVE_MILLIS = 30_000L

/** Answers to the "include this phone's contacts?" question. */
enum class DeviceContactsAnswer { ALLOWED, NOT_NOW, NEVER }

private typealias BoxAction = suspend (FritzDialer) -> String

class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)

    private val _state = MutableStateFlow(
        UiState(
            config = settings.activeProfile()?.config ?: FritzConfig(),
            profiles = settings.profiles(),
            activeProfileId = settings.activeProfileId,
            showSettings = !settings.isConfigured(),
            showConnectionSettings = !settings.isConfigured(),
            settingsSaved = settings.isConfigured(),
            deviceContactsEnabled = settings.deviceContactsEnabled,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Action interrupted by an untrusted certificate; re-run once the user trusts it. */
    private var pendingAction: Pair<String, BoxAction>? = null

    /** Ends [UiState.dialActive] after [DIAL_ACTIVE_MILLIS]. */
    private var dialTimeout: Job? = null

    /** The last action that failed, for "Retry". */
    private var failedAction: Pair<String, BoxAction>? = null

    /**
     * True while settings entered on the settings screen are being verified. The edited
     * config is only persisted once the router accepted it.
     */
    private var verifyingSettings = false

    // --- number input ---------------------------------------------------------------

    fun onNumberChange(value: String) = _state.update { it.copy(number = value) }

    fun appendDigit(c: Char) = _state.update { it.copy(number = it.number + c) }

    fun backspace() = _state.update { it.copy(number = it.number.dropLast(1)) }

    /** Called with the number from a tel: link (ACTION_DIAL / ACTION_VIEW). */
    fun prefill(number: String) = _state.update { it.copy(number = number) }

    fun dismissError() {
        failedAction = null
        _state.update { it.copy(error = null, canRetry = false) }
    }

    fun retry() {
        val action = failedAction ?: return
        dismissError()
        runOnBox(action.first, action.second)
    }

    /** From the error dialog: go to the settings of the box in use to fix the connection. */
    fun openSettingsFromError() {
        dismissError()
        val active = settings.activeProfile()
        if (active != null) {
            _state.update { it.copy(showSettings = true) }
            editProfile(active.id)
        } else {
            openSettings()
        }
    }

    // --- settings ---------------------------------------------------------------------

    /** Opens the settings; without any box configured directly on the "add" page. */
    fun openSettings() {
        _state.update { it.copy(showSettings = true) }
        if (!settings.isConfigured()) addProfile()
    }

    /** Connection page for a new FritzBox. */
    fun addProfile() = _state.update {
        it.copy(
            showConnectionSettings = true,
            editingProfileId = null,
            editingName = "",
            config = FritzConfig(),
        )
    }

    /** Connection page for an existing FritzBox. */
    fun editProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                showConnectionSettings = true,
                editingProfileId = id,
                editingName = profile.name,
                config = profile.config,
            )
        }
    }

    /** Leaves the connection page without saving: back to the connection in use. */
    fun closeConnectionSettings() {
        verifyingSettings = false
        _state.update {
            it.copy(
                showConnectionSettings = false,
                editingProfileId = null,
                config = activeConfig(),
                error = null,
            )
        }
    }

    /** Leaves the settings without saving: back to the connection in use. */
    fun closeSettings() {
        verifyingSettings = false
        _state.update {
            it.copy(
                showSettings = false,
                showConnectionSettings = false,
                editingProfileId = null,
                config = activeConfig(),
                error = null,
            )
        }
    }

    /**
     * Tries the edited connection against the router. Only if that works it is saved – and made
     * the active one, since the box evidently is reachable from here; otherwise the error is shown.
     */
    fun saveSettings(edited: FritzConfig, name: String) {
        val config = edited.copy(trustedCertDer = _state.value.config.trustedCertDer)
        val editingId = _state.value.editingProfileId
        verifyingSettings = true
        _state.update { it.copy(config = config, error = null) }
        runOnBox("Connecting to ${config.host}…") { dialer ->
            val message = loadPhones(dialer)
            // Learn the box' device id, so it can be recognised automatically later.
            val identity = BoxDiscovery.identify(config.host)
            val previous = settings.profiles().firstOrNull { it.id == editingId }
            val profile = BoxProfile(
                id = editingId ?: java.util.UUID.randomUUID().toString(),
                name = name.trim().ifEmpty { identity?.friendlyName ?: previous?.name ?: config.host },
                config = _state.value.config,
                deviceId = identity?.udn ?: previous?.deviceId,
                model = identity?.modelName ?: previous?.model,
            )
            settings.saveProfile(profile)
            settings.activeProfileId = profile.id
            verifyingSettings = false
            // First setup: straight to the dialer. Later changes: back to the settings overview.
            _state.update {
                it.copy(
                    profiles = settings.profiles(),
                    activeProfileId = profile.id,
                    editingProfileId = null,
                    networkNotice = null,
                    phonebooks = emptyList(),
                    phonebooksLoaded = false,
                    showSettings = it.settingsSaved,
                    showConnectionSettings = false,
                    settingsSaved = true,
                )
            }
            message
        }
    }

    /** Switches to another configured FritzBox (manually, or after automatic recognition). */
    fun activateProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        settings.activeProfileId = id
        _state.update {
            it.copy(
                activeProfileId = id,
                config = if (it.showConnectionSettings) it.config else profile.config,
                phones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                networkNotice = null,
            )
        }
        refreshPhones()
    }

    fun deleteProfile(id: String) {
        val wasActive = settings.activeProfileId == id
        settings.deleteProfile(id)
        val remaining = settings.profiles()
        _state.update {
            it.copy(
                profiles = remaining,
                activeProfileId = settings.activeProfileId,
                settingsSaved = remaining.isNotEmpty(),
                showConnectionSettings = if (it.editingProfileId == id) false else it.showConnectionSettings,
                editingProfileId = if (it.editingProfileId == id) null else it.editingProfileId,
            )
        }
        when {
            remaining.isEmpty() -> {
                _state.update { it.copy(phones = emptyList(), selectedPhone = null, config = FritzConfig()) }
                addProfile()
            }
            wasActive -> activateProfile(remaining.first().id)
        }
    }

    private fun activeConfig(): FritzConfig = settings.activeProfile()?.config ?: FritzConfig()

    // --- recognising the FritzBox of the current network -------------------------------

    private var detectJob: Job? = null

    /**
     * Asks the gateway of the current network (and the configured hosts) for their device id and
     * switches to the matching connection. Returns true if it switched to another box.
     */
    private suspend fun detectBox(): Boolean {
        val profiles = settings.profiles()
        if (profiles.isEmpty()) return false
        val gateway = BoxDiscovery.currentGateway(getApplication<Application>())
        val hosts = listOfNotNull(gateway, "fritz.box") + profiles.map { it.config.host }
        val found = BoxDiscovery.identifyAll(hosts)
        // Boxes saved before this feature have no device id yet: learn it from their host.
        profiles.filter { it.deviceId == null }.forEach { p ->
            found.firstOrNull { it.host.equals(p.config.host, ignoreCase = true) }
                ?.let { settings.updateDeviceId(p.id, it.udn, it.modelName) }
        }
        val known = settings.profiles()
        val match = found.firstNotNullOfOrNull { identity -> known.firstOrNull { it.deviceId == identity.udn } }
        val unknown = if (match == null) found.firstOrNull { it.host == gateway } else null
        _state.update {
            it.copy(
                profiles = known,
                networkNotice = unknown?.let { box ->
                    "${box.friendlyName ?: box.modelName ?: "A FritzBox"} in this network is not configured"
                },
            )
        }
        if (match != null && match.id != settings.activeProfileId) {
            activateProfile(match.id)
            return true
        }
        return false
    }

    /** Re-detects the box whenever the phone's network changes (e.g. Wi-Fi at home vs. office). */
    private fun watchNetwork() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = scheduleDetect()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                scheduleDetect()
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        networkCallback = callback
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun scheduleDetect() {
        if (!settings.isConfigured()) return
        detectJob?.cancel()
        detectJob = viewModelScope.launch {
            delay(1_500) // let the network settle (DHCP, routes)
            detectBox()
        }
    }

    override fun onCleared() {
        val cm = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        networkCallback?.let { cb -> runCatching { cm?.unregisterNetworkCallback(cb) } }
        super.onCleared()
    }

    // --- certificates -----------------------------------------------------------------

    /** User accepted the presented certificate: pin it and retry what was interrupted. */
    fun trustCertificate() {
        val prompt = _state.value.certificatePrompt ?: return
        val config = _state.value.config.copy(trustedCertDer = prompt.certificate.derBase64)
        // While verifying new settings, everything is persisted together once the login works.
        if (!verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, config.trustedCertDer) }
        _state.update { it.copy(config = config, certificatePrompt = null) }
        val action = pendingAction
        pendingAction = null
        action?.let { (progress, block) -> runOnBox(progress, block) }
    }

    fun rejectCertificate() {
        failedAction = pendingAction
        pendingAction = null
        _state.update {
            it.copy(
                certificatePrompt = null,
                status = null,
                canRetry = failedAction != null,
                error = BoxError(
                    "Certificate not trusted",
                    "The connection was refused because you did not trust the router's certificate.",
                    settingsRelated = true,
                ),
            )
        }
    }

    fun showTrustedCertificate() {
        val info = _state.value.config.trustedCertDer?.let(CertificateInfo::fromDerBase64) ?: return
        _state.update { it.copy(viewedCertificate = info) }
    }

    fun closeCertificateView() = _state.update { it.copy(viewedCertificate = null) }

    /** The stored connection a certificate decision belongs to: the edited one, else the active one. */
    private fun persistedProfileId(): String? =
        if (_state.value.showConnectionSettings) _state.value.editingProfileId else settings.activeProfileId

    fun forgetCertificate() {
        val config = _state.value.config.copy(trustedCertDer = null)
        if (!verifyingSettings) persistedProfileId()?.let { settings.updateTrustedCert(it, null) }
        _state.update { it.copy(config = config, viewedCertificate = null) }
    }

    // --- phone books ------------------------------------------------------------------

    /** Opens the phone book screen; loads the books the first time. */
    fun openPhonebook() {
        _state.update {
            it.copy(
                showPhonebook = true,
                askForDeviceContacts = !it.deviceContactsEnabled &&
                    !settings.deviceContactsNeverAsk,
            )
        }
        if (!_state.value.phonebooksLoaded) reloadRouterPhonebooks()
        if (_state.value.deviceContacts == null) loadDeviceContacts()
    }

    /**
     * (Re)reads the contacts stored on this device. Does nothing without the contacts
     * permission; the phone book screen calls this again once it is granted.
     */
    fun loadDeviceContacts() {
        val app = getApplication<Application>()
        if (!_state.value.deviceContactsEnabled || !DeviceContacts.hasPermission(app)) return
        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { runCatching { DeviceContacts.load(app) }.getOrNull() }
            if (book != null) _state.update { it.copy(deviceContacts = book) }
        }
    }

    fun closePhonebook() = _state.update { it.copy(showPhonebook = false) }

    /**
     * Switches the optional "this phone's contacts" feature. The settings screen only enables it
     * once Android granted the contacts permission. Disabling stops reading the contacts at once
     * (the permission itself can only be revoked in the Android settings).
     */
    fun setDeviceContactsEnabled(enabled: Boolean) {
        settings.deviceContactsEnabled = enabled
        // Switching it off on purpose also means: don't keep asking about it.
        if (!enabled) settings.deviceContactsNeverAsk = true
        _state.update {
            it.copy(
                deviceContactsEnabled = enabled,
                deviceContacts = if (enabled) it.deviceContacts else null,
                askForDeviceContacts = false,
            )
        }
        if (enabled) loadDeviceContacts()
    }

    fun reloadPhonebooks() {
        loadDeviceContacts()
        reloadRouterPhonebooks()
    }

    private fun reloadRouterPhonebooks() = runOnBox("Loading phone books…") { dialer ->
        val books = dialer.loadPhonebooks()
        _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
        "${books.sumOf { it.contacts.size }} contact(s) loaded"
    }

    /** Answer to the "include this phone's contacts?" question on the phone book screen. */
    fun answerDeviceContactsQuestion(answer: DeviceContactsAnswer) {
        _state.update { it.copy(askForDeviceContacts = false) }
        when (answer) {
            DeviceContactsAnswer.ALLOWED -> setDeviceContactsEnabled(true)
            // Only for this visit: asked again the next time the phone books are opened.
            DeviceContactsAnswer.NOT_NOW -> Unit
            DeviceContactsAnswer.NEVER -> settings.deviceContactsNeverAsk = true
        }
    }

    /** A number was tapped in the phone book: put it into the dialer and go back. */
    fun pickNumber(number: String) =
        _state.update { it.copy(number = number, showPhonebook = false) }

    // --- router actions ---------------------------------------------------------------

    fun refreshPhones() {
        // Another request is running (e.g. for the previous box): reload once it is done.
        if (_state.value.busy) {
            refreshWhenIdle = true
            return
        }
        runOnBox("Loading phones…") { dialer -> loadPhones(dialer) }
    }

    private var refreshWhenIdle = false

    private fun loadPhones(dialer: FritzDialer): String {
        val phones = dialer.listPhones()
        val current = dialer.getDialPhone()
        _state.update {
            it.copy(
                phones = phones,
                selectedPhone = current ?: it.selectedPhone ?: phones.firstOrNull(),
            )
        }
        return "Connected – ${phones.size} phone(s) found"
    }

    fun selectPhone(name: String) {
        _state.update { it.copy(selectedPhone = name) }
        runOnBox("Selecting $name…") { dialer ->
            dialer.ensureDialPhone(name)
            "Calls will ring on ${FritzDialer.displayName(name)}"
        }
    }

    fun dial() {
        val number = FritzDialer.sanitizeNumber(_state.value.number)
        if (number.isEmpty()) {
            _state.update {
                it.copy(
                    status = null,
                    error = BoxError("No number", "Please enter a number to dial.", false),
                    canRetry = false,
                )
            }
            return
        }
        val phone = _state.value.selectedPhone
        runOnBox("Dialling $number…") { dialer ->
            // Only changes the box setting if needed: a change may require confirmation on the box.
            if (phone != null) dialer.ensureDialPhone(phone)
            dialer.dial(number)
            markDialActive()
            "Pick up ${phone ?: "your phone"} – the box then calls $number"
        }
    }

    private fun markDialActive() {
        dialTimeout?.cancel()
        _state.update { it.copy(dialActive = true) }
        dialTimeout = viewModelScope.launch {
            delay(DIAL_ACTIVE_MILLIS)
            _state.update { it.copy(dialActive = false) }
        }
    }

    private fun clearDialActive() {
        dialTimeout?.cancel()
        dialTimeout = null
        _state.update { it.copy(dialActive = false) }
    }

    fun hangup() = runOnBox("Hanging up…") { dialer ->
        dialer.hangup()
        clearDialActive()
        "Call cancelled"
    }

    private fun runOnBox(progress: String, block: BoxAction) {
        if (_state.value.busy) return
        failedAction = null
        _state.update { it.copy(busy = true, status = progress, error = null, canRetry = false) }
        viewModelScope.launch {
            val config = _state.value.config
            val result = runCatching {
                withContext(Dispatchers.IO) { block(FritzDialer(config)) }
            }
            val error = result.exceptionOrNull()
            when {
                error == null -> _state.update { it.copy(busy = false, status = result.getOrNull()) }

                error is Tr064Exception && error.secondFactorRequired ->
                    confirmAndRepeat(progress, block, config)

                error is UntrustedCertificateException -> {
                    pendingAction = progress to block
                    _state.update {
                        it.copy(
                            busy = false,
                            status = "Waiting for certificate decision…",
                            certificatePrompt = CertificatePrompt(error.certificate, error.previouslyTrusted),
                        )
                    }
                }

                else -> {
                    failedAction = progress to block
                    _state.update {
                        it.copy(
                            busy = false,
                            status = null,
                            error = describeError(error, config),
                            canRetry = true,
                        )
                    }
                }
            }
            if (refreshWhenIdle && !_state.value.busy) {
                refreshWhenIdle = false
                refreshPhones()
            }
        }
    }

    // --- second factor ----------------------------------------------------------------

    private var secondFactorJob: Job? = null

    /**
     * The box refused [block] with 866: start a second-factor confirmation, show how to
     * confirm (dialog), wait for it, then repeat [block] with the confirmation token.
     */
    private fun confirmAndRepeat(progress: String, block: BoxAction, config: FritzConfig) {
        _state.update { it.copy(busy = true, status = "Waiting for confirmation on the FRITZ!Box…") }
        secondFactorJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val auth = FritzDialer(config)
                    val challenge = auth.startSecondFactor()
                    _state.update { it.copy(secondFactor = challenge) }

                    val deadline = System.currentTimeMillis() + SECOND_FACTOR_TIMEOUT_MILLIS
                    var authState = FritzDialer.STATE_WAITING
                    while (authState == FritzDialer.STATE_WAITING && System.currentTimeMillis() < deadline) {
                        delay(1_000)
                        authState = auth.secondFactorState()
                    }
                    if (authState == FritzDialer.STATE_WAITING) {
                        runCatching { auth.stopSecondFactor() }
                        throw SecondFactorNotConfirmedException()
                    }
                    // Whatever the final state is called, the repeated action tells whether
                    // the confirmation was accepted (it fails with 866 again if not).
                    _state.update { it.copy(secondFactor = null, status = progress) }
                    block(FritzDialer(config, challenge.token))
                }
            }
            val error = result.exceptionOrNull()
            if (error is CancellationException) return@launch
            _state.update { it.copy(secondFactor = null, busy = false) }
            if (error == null) {
                _state.update { it.copy(status = result.getOrNull()) }
            } else {
                failedAction = progress to block
                val shown = if (error is Tr064Exception && error.secondFactorRequired) {
                    SecondFactorNotConfirmedException()
                } else error
                _state.update {
                    it.copy(status = null, error = describeError(shown, config), canRetry = true)
                }
            }
        }
    }

    /** User cancelled the confirmation dialog. */
    fun cancelSecondFactor() {
        secondFactorJob?.cancel()
        secondFactorJob = null
        val config = _state.value.config
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { FritzDialer(config).stopSecondFactor() }
        }
        _state.update { it.copy(secondFactor = null, busy = false, status = null) }
    }

    // Runs last: all fields above are initialised by now (Kotlin initialises in source order).
    init {
        if (settings.isConfigured()) {
            // Pick the box of the current network first; if that did not switch (and load), load now.
            viewModelScope.launch {
                if (!detectBox()) refreshPhones()
            }
        }
        watchNetwork()
    }
}
