package info.christianreiner.fritzdial

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.christianreiner.fritzdial.data.AppLanguage
import info.christianreiner.fritzdial.data.SpeedDials
import info.christianreiner.fritzdial.tr064.CertificateInfo
import info.christianreiner.fritzdial.ui.ConnectionSettingsScreen
import info.christianreiner.fritzdial.ui.DialerScreen
import info.christianreiner.fritzdial.ui.TrustCertificateDialog
import info.christianreiner.fritzdial.ui.ViewCertificateDialog
import info.christianreiner.fritzdial.ui.activeBoxIssue
import info.christianreiner.fritzdial.ui.activeProfile
import info.christianreiner.fritzdial.ui.noBoxReachable
import info.christianreiner.fritzdial.ui.DialerViewModel
import info.christianreiner.fritzdial.ui.ErrorDialog
import info.christianreiner.fritzdial.ui.LanguageScreen
import info.christianreiner.fritzdial.ui.NumberPickerScreen
import info.christianreiner.fritzdial.ui.PhonebookTab
import info.christianreiner.fritzdial.ui.RecentsTab
import info.christianreiner.fritzdial.ui.SettingsScreen
import info.christianreiner.fritzdial.ui.SecondFactorDialog
import info.christianreiner.fritzdial.ui.SpeedDialTab
import info.christianreiner.fritzdial.ui.theme.FritzDialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

    /** Before Android 13: apply the language chosen in the settings (from then on Android does it). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            FritzDialTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val trustedFingerprint = remember(state.config.trustedCertDer) {
                    state.config.trustedCertDer?.let(CertificateInfo::fromDerBase64)?.sha256
                }
                if (state.showLanguage && state.showSettings) {
                    BackHandler { viewModel.closeLanguage() }
                    LanguageScreen(onBack = viewModel::closeLanguage)
                } else if (state.showPicker && !state.showSettings) {
                    val routerBooksEnabled = state.activeProfile?.phonebooksEnabled ?: true
                    // While setting up a speed dial, "back" only cancels that.
                    BackHandler {
                        if (state.assigningSpeedDial != null) viewModel.cancelAssigningSpeedDial() else viewModel.closePicker()
                    }
                    NumberPickerScreen(
                        tab = state.pickerTab,
                        onTabChange = viewModel::selectPickerTab,
                        busy = state.busy,
                        onReload = viewModel::reloadPicker,
                        onBack = viewModel::closePicker,
                        recents = {
                            RecentsTab(
                                boxCalls = state.boxCalls,
                                boxCallsLoaded = state.boxCallsLoaded,
                                boxName = state.activeProfile?.name,
                                boxCallsEnabled = state.activeProfile?.callListEnabled ?: true,
                                phoneCalls = state.phoneCalls,
                                callLogEnabled = state.callLogEnabled,
                                callLogProblem = state.callLogProblem,
                                busy = state.busy,
                                onPick = viewModel::pickNumber,
                                onReloadCallLog = viewModel::loadCallLog,
                                onDisableCallLog = viewModel::disableCallLog,
                            )
                        },
                        phonebooks = {
                            PhonebookTab(
                                // Possibly read only for the box' speed dials: then not shown here.
                                routerBooks = if (routerBooksEnabled) state.phonebooks else emptyList(),
                                routerBooksEnabled = routerBooksEnabled,
                                deviceContacts = state.deviceContacts,
                                deviceContactsEnabled = state.deviceContactsEnabled,
                                loaded = state.phonebooksLoaded,
                                busy = state.busy,
                                onPick = viewModel::pickContactNumber,
                                phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                onSetSpeedDial = viewModel::setPhoneSpeedDial,
                                assigningSpeedDial = state.assigningSpeedDial,
                                onCancelAssigning = viewModel::cancelAssigningSpeedDial,
                                onDeviceContactsAllowed = viewModel::loadDeviceContacts,
                                onDisableDeviceContacts = { viewModel.setDeviceContactsEnabled(false) },
                                askForDeviceContacts = state.askForDeviceContacts,
                                onDeviceContactsAnswer = viewModel::answerDeviceContactsQuestion,
                            )
                        },
                        speedDials = {
                            val boxSpeedDials = remember(state.phonebooks) { SpeedDials.fromPhonebooks(state.phonebooks) }
                            SpeedDialTab(
                                phoneSpeedDials = state.phoneSpeedDials.takeIf { state.phoneSpeedDialsEnabled },
                                boxSpeedDials = boxSpeedDials.takeIf { state.activeProfile?.speedDialsEnabled ?: true },
                                boxName = state.activeProfile?.name,
                                boxLoaded = state.phonebooksLoaded,
                                busy = state.busy,
                                onPick = viewModel::useSpeedDial,
                                onAssign = viewModel::startAssigningSpeedDial,
                                onRemove = viewModel::removePhoneSpeedDial,
                            )
                        },
                    )
                } else if (state.showSettings && state.showConnectionSettings) {
                    val canGoBack = state.settingsSaved
                    BackHandler(enabled = canGoBack) { viewModel.closeConnectionSettings() }
                    // A fresh form for every box being edited (or added).
                    key(state.editingProfileId ?: "new") {
                        val edited = state.profiles.firstOrNull { it.id == state.editingProfileId }
                        ConnectionSettingsScreen(
                            initial = state.config,
                            canGoBack = canGoBack,
                            trustedFingerprint = trustedFingerprint,
                            busy = state.busy,
                            status = state.status,
                            initialName = state.editingName,
                            isNew = state.editingProfileId == null,
                            initialPhonebooksEnabled = edited?.phonebooksEnabled ?: true,
                            initialCallListEnabled = edited?.callListEnabled ?: true,
                            initialSpeedDialsEnabled = edited?.speedDialsEnabled ?: true,
                            boxInfo = edited?.info,
                            onSave = viewModel::saveSettings,
                            onBack = viewModel::closeConnectionSettings,
                            onDelete = state.editingProfileId?.let { id -> { viewModel.deleteProfile(id) } },
                            onShowCertificate = viewModel::showTrustedCertificate,
                            onForgetCertificate = viewModel::forgetCertificate,
                        )
                    }
                } else if (state.showSettings) {
                    BackHandler { viewModel.closeSettings() }
                    SettingsScreen(
                        profiles = state.profiles,
                        activeProfileId = state.activeProfileId,
                        activeIssue = state.activeBoxIssue,
                        boxesSeen = state.boxesSeen,
                        noBoxReachable = state.noBoxReachable,
                        busy = state.busy,
                        networkNotice = state.networkNotice,
                        onEditProfile = viewModel::editProfile,
                        onActivateProfile = viewModel::activateProfile,
                        onAddProfile = viewModel::addProfile,
                        onBack = viewModel::closeSettings,
                        onOpenLanguage = viewModel::openLanguage,
                        deviceContactsEnabled = state.deviceContactsEnabled,
                        onDeviceContactsChange = viewModel::setDeviceContactsEnabled,
                        callLogEnabled = state.callLogEnabled,
                        callLogProblem = state.callLogProblem,
                        onEnableCallLog = viewModel::enableCallLog,
                        onDisableCallLog = viewModel::disableCallLog,
                        phoneSpeedDialsEnabled = state.phoneSpeedDialsEnabled,
                        onPhoneSpeedDialsChange = viewModel::setPhoneSpeedDialsEnabled,
                    )
                } else {
                    DialerScreen(
                        state = state,
                        onNumberChange = viewModel::onNumberChange,
                        onDigit = viewModel::appendDigit,
                        onLongDigit = viewModel::longPressDigit,
                        onBackspace = viewModel::backspace,
                        onDial = viewModel::dial,
                        onHangup = viewModel::hangup,
                        onSelectPhone = viewModel::selectPhone,
                        onRefresh = viewModel::refreshPhones,
                        onOpenSettings = viewModel::openSettings,
                        onOpenPicker = viewModel::openPicker,
                    )
                }

                // Errors block all use of the app: shown modally above whichever screen is open.
                state.error?.let { error ->
                    ErrorDialog(
                        error = error,
                        canRetry = state.canRetry,
                        offerSettings = !(state.showSettings && state.showConnectionSettings),
                        onRetry = viewModel::retry,
                        onOpenSettings = viewModel::openSettingsFromError,
                        onDismiss = viewModel::dismissError,
                    )
                }
                state.secondFactor?.let { challenge ->
                    SecondFactorDialog(challenge = challenge, onCancel = viewModel::cancelSecondFactor)
                }
                state.certificatePrompt?.let { prompt ->
                    TrustCertificateDialog(
                        prompt = prompt,
                        onTrust = viewModel::trustCertificate,
                        onReject = viewModel::rejectCertificate,
                    )
                }
                state.viewedCertificate?.let { info ->
                    ViewCertificateDialog(
                        info = info,
                        onClose = viewModel::closeCertificateView,
                        onForget = viewModel::forgetCertificate,
                    )
                }
            }
        }
    }

    /** Back in the foreground: the network may have changed meanwhile. */
    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Pre-fills the number when opened from a tel: link (e.g. tapping a number in Contacts). */
    private fun handleIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme == "tel") {
            data.schemeSpecificPart?.let(viewModel::prefill)
        }
    }
}
