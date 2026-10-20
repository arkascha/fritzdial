package info.christianreiner.fritzdial

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.christianreiner.fritzdial.tr064.CertificateInfo
import info.christianreiner.fritzdial.ui.ConnectionSettingsScreen
import info.christianreiner.fritzdial.ui.DialerScreen
import info.christianreiner.fritzdial.ui.TrustCertificateDialog
import info.christianreiner.fritzdial.ui.ViewCertificateDialog
import info.christianreiner.fritzdial.ui.DialerViewModel
import info.christianreiner.fritzdial.ui.ErrorDialog
import info.christianreiner.fritzdial.ui.PhonebookScreen
import info.christianreiner.fritzdial.ui.SettingsScreen
import info.christianreiner.fritzdial.ui.SecondFactorDialog
import info.christianreiner.fritzdial.ui.theme.FritzDialTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()

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
                if (state.showPhonebook && !state.showSettings) {
                    BackHandler { viewModel.closePhonebook() }
                    PhonebookScreen(
                        routerBooks = state.phonebooks,
                        deviceContacts = state.deviceContacts,
                        deviceContactsEnabled = state.deviceContactsEnabled,
                        loaded = state.phonebooksLoaded,
                        busy = state.busy,
                        onPick = viewModel::pickNumber,
                        onReload = viewModel::reloadPhonebooks,
                        onBack = viewModel::closePhonebook,
                        onDeviceContactsAllowed = viewModel::loadDeviceContacts,
                        onDisableDeviceContacts = { viewModel.setDeviceContactsEnabled(false) },
                        askForDeviceContacts = state.askForDeviceContacts,
                        onDeviceContactsAnswer = viewModel::answerDeviceContactsQuestion,
                    )
                } else if (state.showSettings && state.showConnectionSettings) {
                    val canGoBack = state.settingsSaved
                    BackHandler(enabled = canGoBack) { viewModel.closeConnectionSettings() }
                    ConnectionSettingsScreen(
                        initial = state.config,
                        canGoBack = canGoBack,
                        trustedFingerprint = trustedFingerprint,
                        busy = state.busy,
                        status = state.status,
                        onSave = viewModel::saveSettings,
                        onBack = viewModel::closeConnectionSettings,
                        onShowCertificate = viewModel::showTrustedCertificate,
                        onForgetCertificate = viewModel::forgetCertificate,
                    )
                } else if (state.showSettings) {
                    BackHandler { viewModel.closeSettings() }
                    SettingsScreen(
                        config = state.config,
                        onOpenConnection = viewModel::openConnectionSettings,
                        onBack = viewModel::closeSettings,
                        deviceContactsEnabled = state.deviceContactsEnabled,
                        onDeviceContactsChange = viewModel::setDeviceContactsEnabled,
                    )
                } else {
                    DialerScreen(
                        state = state,
                        onNumberChange = viewModel::onNumberChange,
                        onDigit = viewModel::appendDigit,
                        onBackspace = viewModel::backspace,
                        onDial = viewModel::dial,
                        onHangup = viewModel::hangup,
                        onSelectPhone = viewModel::selectPhone,
                        onRefresh = viewModel::refreshPhones,
                        onOpenSettings = viewModel::openSettings,
                        onOpenPhonebook = viewModel::openPhonebook,
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
