package info.christianreiner.fritzdial.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.BoxProfile
import info.christianreiner.fritzdial.data.DeviceCallLog
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.data.SettingsRepository
import info.christianreiner.fritzdial.data.SpeedDial
import info.christianreiner.fritzdial.data.SpeedDials
import info.christianreiner.fritzdial.tr064.BoxDiscovery
import info.christianreiner.fritzdial.tr064.BoxIdentity
import info.christianreiner.fritzdial.tr064.CertificateInfo
import info.christianreiner.fritzdial.tr064.FritzConfig
import info.christianreiner.fritzdial.tr064.FritzDialer
import info.christianreiner.fritzdial.tr064.Phonebook
import info.christianreiner.fritzdial.tr064.RecentCall
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

/** The tabs of the number picker, in their order. */
enum class PickerTab { RECENT, SPEED_DIALS, PHONEBOOKS }

data class UiState(
    val number: String = "",
    /** Who the number is, when it came from a speed dial ("Speed dial 2: Office"); shown as the field's label. */
    val numberLabel: UiText? = null,
    val phones: List<String> = emptyList(),
    val selectedPhone: String? = null,
    /** Progress / success information (not an error). */
    val status: UiText? = null,
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
    /**
     * Whether the box in use answered the last detection in the current network (null = not
     * detected yet), and whether the last request to it worked (null = none since it became active).
     */
    val activeBoxSeen: Boolean? = null,
    val activeBoxWorking: Boolean? = null,
    /** Note about the current network, e.g. an unknown FritzBox was found there. */
    val networkNotice: UiText? = null,
    val showSettings: Boolean = false,
    /** The connection sub-page of the settings is open. */
    val showConnectionSettings: Boolean = false,
    /** A working configuration has been saved before, so the settings screen may be left. */
    val settingsSaved: Boolean = false,
    /** Non-null while the "trust this certificate?" dialog is shown. */
    val certificatePrompt: CertificatePrompt? = null,
    /** Non-null while the trusted certificate is shown read-only. */
    val viewedCertificate: CertificateInfo? = null,
    /** The language screen is open. */
    val showLanguage: Boolean = false,
    /** The number picker (tabs "Recent", "Speed dial" and "Phone books") is open. */
    val showPicker: Boolean = false,
    val pickerTab: PickerTab = PickerTab.RECENT,
    /** Choosing the number for this phone speed dial slot in the phone books tab. */
    val assigningSpeedDial: String? = null,
    /** Phone books of the box, loaded on first use of the number picker (cached). */
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
    /** Dialled, received and missed calls from the box' call list, loaded whenever the picker is opened. */
    val boxCalls: List<RecentCall> = emptyList(),
    val boxCallsLoaded: Boolean = false,
    /** Optional feature, switched in the settings: include the phone's own calls. */
    val callLogEnabled: Boolean = false,
    /** Dialled, received and missed calls from the phone's call log, once enabled (null = not loaded). */
    val phoneCalls: List<RecentCall>? = null,
    /** Why the phone's call log could not be read (on enabling the feature, or later). */
    val callLogProblem: UiText? = null,
    /** Optional feature, switched in the settings: this phone's own speed dials 1–9. */
    val phoneSpeedDialsEnabled: Boolean = true,
    val phoneSpeedDials: List<SpeedDial> = emptyList(),
)

/** The FritzBox in use, if any. */
val UiState.activeProfile: BoxProfile?
    get() = profiles.firstOrNull { it.id == activeProfileId }

/** This phone's speed dials, if switched on. */
val UiState.usablePhoneSpeedDials: List<SpeedDial>
    get() = if (phoneSpeedDialsEnabled) phoneSpeedDials else emptyList()

/** The speed dials of the box in use (from its phone books, once loaded), if switched on for it. */
val UiState.usableBoxSpeedDials: List<SpeedDial>
    get() = if (activeProfile?.speedDialsEnabled != false) SpeedDials.fromPhonebooks(phonebooks) else emptyList()

/** Why the box in use does not meet the expectations, if it does not; a failed request weighs more. */
val UiState.activeBoxIssue: ActiveBoxIssue?
    get() = when {
        activeBoxWorking == false -> ActiveBoxIssue.NOT_WORKING
        activeBoxSeen == false -> ActiveBoxIssue.NOT_FOUND
        else -> null
    }

enum class ActiveBoxIssue { NOT_FOUND, NOT_WORKING }

/** How long to wait for the user to confirm on the box before giving up. */
private const val SECOND_FACTOR_TIMEOUT_MILLIS = 120_000L

/** The user did not confirm on the box in time. */
class SecondFactorNotConfirmedException : IOException("The action was not confirmed on the FRITZ!Box")

/** How long a started dial-help call counts as "cancellable" (the handset rings about this long). */
private const val DIAL_ACTIVE_MILLIS = 30_000L

/** Answers to the "include this phone's contacts?" question. */
enum class DeviceContactsAnswer { ALLOWED, NOT_NOW, NEVER }

private typealias BoxAction = suspend (FritzDialer) -> UiText

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
            callLogEnabled = settings.callLogEnabled,
            phoneSpeedDialsEnabled = settings.phoneSpeedDialsEnabled,
            phoneSpeedDials = settings.phoneSpeedDials,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Action interrupted by an untrusted certificate; re-run once the user trusts it. */
    private var pendingAction: Pair<UiText, BoxAction>? = null

    /** Ends [UiState.dialActive] after [DIAL_ACTIVE_MILLIS]. */
    private var dialTimeout: Job? = null

    /** The last action that failed, for "Retry". */
    private var failedAction: Pair<UiText, BoxAction>? = null

    /**
     * True while settings entered on the settings screen are being verified. The edited
     * config is only persisted once the router accepted it.
     */
    private var verifyingSettings = false

    // --- number input ---------------------------------------------------------------

    fun onNumberChange(value: String) = _state.update { it.copy(number = value, numberLabel = null) }

    fun appendDigit(c: Char) = _state.update { it.copy(number = it.number + c, numberLabel = null) }

    fun backspace() = _state.update { it.copy(number = it.number.dropLast(1), numberLabel = null) }

    /** Called with the number from a tel: link (ACTION_DIAL / ACTION_VIEW). */
    fun prefill(number: String) = _state.update { it.copy(number = number, numberLabel = null) }

    /**
     * Long press on a digit of the dial pad: a speed dial if there is one for it (see
     * [SpeedDials.forLongPress]); otherwise like a tap – except "0", which gives "+".
     */
    fun longPressDigit(digit: Char) {
        val state = _state.value
        val speedDial = SpeedDials.forLongPress(
            state.number, digit, state.usablePhoneSpeedDials, state.usableBoxSpeedDials,
        )
        if (speedDial != null) useSpeedDial(speedDial) else appendDigit(if (digit == '0') '+' else digit)
    }

    /** Puts a speed dial's number into the field, with its slot and name as the field's label. */
    fun useSpeedDial(speedDial: SpeedDial) = _state.update {
        it.copy(
            number = speedDial.number,
            numberLabel = uiText(R.string.dialer_speed_dial_label, speedDial.slot, speedDial.name),
            showPicker = false,
        )
    }

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
                showLanguage = false,
                editingProfileId = null,
                config = activeConfig(),
                error = null,
            )
        }
    }

    /**
     * Tries the edited connection against the router. Only if that works it is saved – and made
     * the active one, since the box evidently is reachable from here; otherwise the error is shown.
     * [phonebooksEnabled], [callListEnabled], [speedDialsEnabled]: whether this box' phone books, call
     * list and speed dials are used.
     */
    fun saveSettings(
        edited: FritzConfig,
        name: String,
        phonebooksEnabled: Boolean,
        callListEnabled: Boolean,
        speedDialsEnabled: Boolean,
    ) {
        val config = edited.copy(trustedCertDer = _state.value.config.trustedCertDer)
        val editingId = _state.value.editingProfileId
        verifyingSettings = true
        _state.update { it.copy(config = config, error = null) }
        runOnBox(uiText(R.string.status_connecting, config.host)) { dialer ->
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
                phonebooksEnabled = phonebooksEnabled,
                callListEnabled = callListEnabled,
                speedDialsEnabled = speedDialsEnabled,
            )
            settings.saveProfile(profile)
            settings.activeProfileId = profile.id
            verifyingSettings = false
            // First setup: straight to the dialer. Later changes: back to the settings overview.
            _state.update {
                it.copy(
                    profiles = settings.profiles(),
                    activeProfileId = profile.id,
                    // It just answered its device description (if not: it is not a FritzBox we can recognise).
                    activeBoxSeen = identity != null,
                    editingProfileId = null,
                    networkNotice = null,
                    phonebooks = emptyList(),
                    phonebooksLoaded = false,
                    boxCalls = emptyList(),
                    boxCallsLoaded = false,
                    showSettings = it.settingsSaved,
                    showConnectionSettings = false,
                    settingsSaved = true,
                )
            }
            message
        }
        preloadBoxSpeedDials()
    }

    /** Switches to another configured FritzBox (manually, or after automatic recognition). */
    fun activateProfile(id: String) {
        val profile = settings.profiles().firstOrNull { it.id == id } ?: return
        settings.activeProfileId = id
        _state.update {
            it.copy(
                activeProfileId = id,
                // Chosen by hand, it may well not be the box of this network.
                activeBoxSeen = seenInNetwork(profile),
                activeBoxWorking = null,
                config = if (it.showConnectionSettings) it.config else profile.config,
                phones = emptyList(),
                selectedPhone = null,
                phonebooks = emptyList(),
                phonebooksLoaded = false,
                boxCalls = emptyList(),
                boxCallsLoaded = false,
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
        lastFound = found
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
                activeBoxSeen = known.firstOrNull { p -> p.id == settings.activeProfileId }?.let(::seenInNetwork),
                // A request that failed in the previous network says nothing about this one.
                activeBoxWorking = null,
                networkNotice = unknown?.let { box ->
                    (box.friendlyName ?: box.modelName)?.let { uiText(R.string.notice_unknown_box, it) }
                        ?: uiText(R.string.notice_unknown_box_generic)
                },
            )
        }
        if (match != null && match.id != settings.activeProfileId) {
            activateProfile(match.id)
            return true
        }
        return false
    }

    /** The boxes that answered the last detection; null before the first one. */
    private var lastFound: List<BoxIdentity>? = null

    /**
     * Whether [profile]'s box answered the last detection – by its device id, or by its host for a
     * connection whose device id is not known yet. Null before the first detection.
     */
    private fun seenInNetwork(profile: BoxProfile): Boolean? = lastFound?.any { box ->
        if (profile.deviceId != null) box.udn == profile.deviceId
        else box.host.equals(profile.config.host, ignoreCase = true)
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
                    uiText(R.string.error_cert_rejected_title),
                    uiText(R.string.error_cert_rejected),
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

    /**
     * Opens the number picker. The recent calls are read anew each time (they change with every
     * call); the phone books only the first time.
     */
    fun openPicker() {
        _state.update {
            it.copy(
                showPicker = true,
                pickerTab = PickerTab.RECENT,
                assigningSpeedDial = null,
                askForDeviceContacts = !it.deviceContactsEnabled &&
                    !settings.deviceContactsNeverAsk,
            )
        }
        reloadRecents()
        if (!_state.value.phonebooksLoaded) reloadRouterPhonebooks()
        if (_state.value.deviceContacts == null) loadDeviceContacts()
    }

    fun closePicker() = _state.update { it.copy(showPicker = false, assigningSpeedDial = null) }

    fun selectPickerTab(tab: PickerTab) = _state.update { it.copy(pickerTab = tab, assigningSpeedDial = null) }

    fun openLanguage() = _state.update { it.copy(showLanguage = true) }

    fun closeLanguage() = _state.update { it.copy(showLanguage = false) }

    /** The picker's reload button: recent calls and phone books. */
    fun reloadPicker() {
        reloadRecents()
        loadDeviceContacts()
        reloadRouterPhonebooks()
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

    private fun reloadRouterPhonebooks() {
        // Also read for the box' speed dials, which come from its phone books.
        val profile = settings.activeProfile()
        if (profile != null && !profile.phonebooksEnabled && !profile.speedDialsEnabled) return
        if (_state.value.busy) return runWhenIdle("phonebooks", ::reloadRouterPhonebooks)
        runOnBox(uiText(R.string.phonebook_loading)) { dialer ->
            val books = dialer.loadPhonebooks()
            _state.update { it.copy(phonebooks = books, phonebooksLoaded = true) }
            UiText.Plural(R.plurals.status_contacts_loaded, books.sumOf { it.contacts.size })
        }
    }

    /** Answer to the "include this phone's contacts?" question on the picker's phone book tab. */
    fun answerDeviceContactsQuestion(answer: DeviceContactsAnswer) {
        _state.update { it.copy(askForDeviceContacts = false) }
        when (answer) {
            DeviceContactsAnswer.ALLOWED -> setDeviceContactsEnabled(true)
            // Only for this visit: asked again the next time the phone books are opened.
            DeviceContactsAnswer.NOT_NOW -> Unit
            DeviceContactsAnswer.NEVER -> settings.deviceContactsNeverAsk = true
        }
    }

    /** A number was tapped in the picker (recent calls or phone books): put it into the dialer and go back. */
    fun pickNumber(number: String) =
        _state.update { it.copy(number = number, numberLabel = null, showPicker = false) }

    /** A contact's number was tapped in the phone books: for the dialer, or for the speed dial being set up. */
    fun pickContactNumber(name: String, number: String) {
        val slot = _state.value.assigningSpeedDial
        if (slot == null) return pickNumber(number)
        setPhoneSpeedDial(slot, name, number)
        _state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }
    }

    // --- speed dials ------------------------------------------------------------------

    /** Opens the phone books to choose the number for this phone's speed dial [slot]. */
    fun startAssigningSpeedDial(slot: String) =
        _state.update { it.copy(assigningSpeedDial = slot, pickerTab = PickerTab.PHONEBOOKS) }

    fun cancelAssigningSpeedDial() =
        _state.update { it.copy(assigningSpeedDial = null, pickerTab = PickerTab.SPEED_DIALS) }

    /** Sets (or replaces) this phone's speed dial [slot]. */
    fun setPhoneSpeedDial(slot: String, name: String, number: String) {
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot } + SpeedDial(slot, name, number)
        _state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
    }

    fun removePhoneSpeedDial(slot: String) {
        settings.phoneSpeedDials = settings.phoneSpeedDials.filterNot { it.slot == slot }
        _state.update { it.copy(phoneSpeedDials = settings.phoneSpeedDials) }
    }

    /** Switches this phone's speed dials on or off; switched off, they are kept, just not used. */
    fun setPhoneSpeedDialsEnabled(enabled: Boolean) {
        settings.phoneSpeedDialsEnabled = enabled
        _state.update { it.copy(phoneSpeedDialsEnabled = enabled) }
    }

    /**
     * Reads the phone books of the box in use in the background, so its speed dials work right
     * away in the dialer – not only once the picker was opened. Checked when it is its turn, as
     * the box in use may change until then.
     */
    private fun preloadBoxSpeedDials() = runWhenIdle("speedDials") {
        if (settings.activeProfile()?.speedDialsEnabled == true && !_state.value.phonebooksLoaded) {
            reloadRouterPhonebooks()
        }
    }

    // --- recent calls -----------------------------------------------------------------

    private fun reloadRecents() {
        loadCallLog()
        if (settings.activeProfile()?.callListEnabled == false) return
        if (_state.value.busy) return runWhenIdle("recents") { if (_state.value.showPicker) reloadRecents() }
        runOnBox(uiText(R.string.recents_loading)) { dialer ->
            val calls = dialer.loadRecentCalls()
            _state.update { it.copy(boxCalls = calls, boxCallsLoaded = true) }
            UiText.Plural(R.plurals.status_calls_loaded, calls.size)
        }
    }

    /** (Re)reads the phone's calls, if the feature is on. A failure is shown, not thrown. */
    fun loadCallLog() {
        if (!_state.value.callLogEnabled) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            _state.update {
                it.copy(
                    phoneCalls = result.getOrNull() ?: it.phoneCalls,
                    callLogProblem = result.exceptionOrNull()?.let(::describeCallLogProblem),
                )
            }
        }
    }

    /**
     * Turns the "this phone's calls" feature on – but only if reading the call log actually works
     * ([granted]: Android's answer to the permission request). Otherwise it stays off and the
     * settings explain why.
     */
    fun enableCallLog(granted: Boolean) {
        if (!granted) {
            _state.update {
                it.copy(callLogProblem = uiText(R.string.call_log_denied))
            }
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { DeviceCallLog.load(app) } }
            val calls = result.getOrNull()
            settings.callLogEnabled = calls != null
            _state.update {
                it.copy(
                    callLogEnabled = calls != null,
                    phoneCalls = calls,
                    callLogProblem = result.exceptionOrNull()?.let(::describeCallLogProblem),
                )
            }
        }
    }

    fun disableCallLog() {
        settings.callLogEnabled = false
        _state.update { it.copy(callLogEnabled = false, phoneCalls = null, callLogProblem = null) }
    }

    /** No access / no call log say enough as they are; other failures add their (technical) message. */
    private fun describeCallLogProblem(error: Throwable): UiText =
        error.message?.takeIf { error !is SecurityException && error !is IllegalStateException }
            ?.let { uiText(R.string.call_log_failed, it) }
            ?: uiText(R.string.call_log_failed_plain)

    // --- router actions ---------------------------------------------------------------

    fun refreshPhones() {
        // Another request is running (e.g. for the previous box): reload once it is done.
        if (_state.value.busy) return runWhenIdle("phones", ::refreshPhones)
        runOnBox(uiText(R.string.status_loading_phones)) { dialer -> loadPhones(dialer) }
        preloadBoxSpeedDials()
    }

    /**
     * Box requests that came in while another one was running (one per [runWhenIdle] key, so
     * repeated requests are not repeated). They start one after the other once the running one
     * succeeded – not after a failure, whose error dialog or certificate question they would replace.
     */
    private val whenIdle = LinkedHashMap<String, () -> Unit>()

    private fun runWhenIdle(key: String, action: () -> Unit) {
        whenIdle[key] = action
    }

    private fun startQueuedRequests() {
        while (whenIdle.isNotEmpty() && !_state.value.busy) {
            val key = whenIdle.keys.first()
            whenIdle.remove(key)?.invoke()
        }
    }

    private fun loadPhones(dialer: FritzDialer): UiText {
        val phones = dialer.listPhones()
        val current = dialer.getDialPhone()
        _state.update {
            it.copy(
                phones = phones,
                selectedPhone = current ?: it.selectedPhone ?: phones.firstOrNull(),
            )
        }
        return UiText.Plural(R.plurals.status_connected, phones.size)
    }

    fun selectPhone(name: String) {
        _state.update { it.copy(selectedPhone = name) }
        runOnBox(uiText(R.string.status_selecting, FritzDialer.displayName(name))) { dialer ->
            dialer.ensureDialPhone(name)
            uiText(R.string.status_ring_on, FritzDialer.displayName(name))
        }
    }

    fun dial() {
        // A box speed dial entered as on a handset (**712): dial – and show – the number it stands for.
        SpeedDials.forCode(FritzDialer.sanitizeNumber(_state.value.number), _state.value.usableBoxSpeedDials)
            ?.let(::useSpeedDial)
        val number = FritzDialer.sanitizeNumber(_state.value.number)
        if (number.isEmpty()) {
            _state.update {
                it.copy(
                    status = null,
                    error = BoxError(uiText(R.string.error_no_number_title), uiText(R.string.error_no_number), false),
                    canRetry = false,
                )
            }
            return
        }
        val phone = _state.value.selectedPhone
        runOnBox(uiText(R.string.status_dialling, number)) { dialer ->
            // Only changes the box setting if needed: a change may require confirmation on the box.
            if (phone != null) dialer.ensureDialPhone(phone)
            dialer.dial(number)
            markDialActive()
            phone?.let { uiText(R.string.status_pick_up, FritzDialer.displayName(it), number) }
                ?: uiText(R.string.status_pick_up_any, number)
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

    fun hangup() = runOnBox(uiText(R.string.status_hanging_up)) { dialer ->
        dialer.hangup()
        clearDialActive()
        uiText(R.string.status_call_cancelled)
    }

    private fun runOnBox(progress: UiText, block: BoxAction) {
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
                error == null -> _state.update {
                    it.copy(busy = false, status = result.getOrNull(), activeBoxWorking = true)
                }

                error is Tr064Exception && error.secondFactorRequired ->
                    confirmAndRepeat(progress, block, config)

                error is UntrustedCertificateException -> {
                    pendingAction = progress to block
                    _state.update {
                        it.copy(
                            busy = false,
                            status = uiText(R.string.status_waiting_cert),
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
                            // A failed check of edited settings says nothing about the box in use.
                            activeBoxWorking = if (verifyingSettings) it.activeBoxWorking else false,
                        )
                    }
                }
            }
            if (error == null) startQueuedRequests()
        }
    }

    // --- second factor ----------------------------------------------------------------

    private var secondFactorJob: Job? = null

    /**
     * The box refused [block] with 866: start a second-factor confirmation, show how to
     * confirm (dialog), wait for it, then repeat [block] with the confirmation token.
     */
    private fun confirmAndRepeat(progress: UiText, block: BoxAction, config: FritzConfig) {
        _state.update { it.copy(busy = true, status = uiText(R.string.status_waiting_confirmation)) }
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
                _state.update { it.copy(status = result.getOrNull(), activeBoxWorking = true) }
                startQueuedRequests()
            } else {
                failedAction = progress to block
                val shown = if (error is Tr064Exception && error.secondFactorRequired) {
                    SecondFactorNotConfirmedException()
                } else error
                _state.update {
                    it.copy(
                        status = null,
                        error = describeError(shown, config),
                        canRetry = true,
                        activeBoxWorking = if (verifyingSettings) it.activeBoxWorking else false,
                    )
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
