package info.christianreiner.fritzdial.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.data.SettingsRepository
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
    val config: FritzConfig = FritzConfig(),
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
            config = settings.load(),
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

    init {
        if (settings.isConfigured()) refreshPhones()
    }

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

    /** From the error dialog: go to the settings to fix the connection. */
    fun openSettingsFromError() {
        dismissError()
        _state.update { it.copy(showSettings = true, showConnectionSettings = true) }
    }

    // --- settings ---------------------------------------------------------------------

    /** Opens the settings; before the first successful connection directly on the connection page. */
    fun openSettings() = _state.update {
        it.copy(showSettings = true, showConnectionSettings = !it.settingsSaved)
    }

    fun openConnectionSettings() = _state.update { it.copy(showConnectionSettings = true) }

    /** Leaves the connection page without saving: back to the last working configuration. */
    fun closeConnectionSettings() {
        verifyingSettings = false
        _state.update { it.copy(showConnectionSettings = false, config = settings.load(), error = null) }
    }

    /** Leaves the settings without saving: fall back to the last working configuration. */
    fun closeSettings() {
        verifyingSettings = false
        _state.update {
            it.copy(showSettings = false, showConnectionSettings = false, config = settings.load(), error = null)
        }
    }

    /**
     * Tries the edited settings against the router. Only if that works they are saved and the
     * settings screen closes; otherwise the error is shown on the settings screen.
     */
    fun saveSettings(edited: FritzConfig) {
        val config = edited.copy(trustedCertDer = _state.value.config.trustedCertDer)
        verifyingSettings = true
        _state.update { it.copy(config = config, error = null) }
        runOnBox("Connecting to ${config.host}…") { dialer ->
            val message = loadPhones(dialer)
            settings.save(_state.value.config)
            verifyingSettings = false
            // First setup: straight to the dialer. Later changes: back to the settings overview.
            _state.update {
                it.copy(
                    showSettings = it.settingsSaved,
                    showConnectionSettings = false,
                    settingsSaved = true,
                )
            }
            message
        }
    }

    // --- certificates -----------------------------------------------------------------

    /** User accepted the presented certificate: pin it and retry what was interrupted. */
    fun trustCertificate() {
        val prompt = _state.value.certificatePrompt ?: return
        val config = _state.value.config.copy(trustedCertDer = prompt.certificate.derBase64)
        // While verifying new settings, everything is persisted together once the login works.
        if (!verifyingSettings) settings.save(config)
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

    fun forgetCertificate() {
        val config = _state.value.config.copy(trustedCertDer = null)
        if (!verifyingSettings) settings.save(config)
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

    fun refreshPhones() = runOnBox("Loading phones…") { dialer -> loadPhones(dialer) }

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
}
