package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import info.christianreiner.fritzdial.tr064.FritzConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initial: FritzConfig,
    canGoBack: Boolean,
    /** SHA-256 fingerprint of the currently trusted certificate, or null. */
    trustedFingerprint: String?,
    busy: Boolean,
    status: String?,
    onSave: (FritzConfig) -> Unit,
    onBack: () -> Unit,
    onShowCertificate: () -> Unit,
    onForgetCertificate: () -> Unit,
) {
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.username) }
    var password by remember { mutableStateOf(initial.password) }
    var useHttps by remember { mutableStateOf(initial.useHttps) }
    val portValue = port.toIntOrNull()
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = host.isNotBlank() && portValue != null && portValue in 1..65535

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Router settings") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = host, onValueChange = { host = it },
                label = { Text("Host (e.g. fritz.box or 192.168.178.1)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Use HTTPS", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Encrypted connection (port ${FritzConfig.HTTPS_PORT}). The box' self-signed " +
                            "certificate must be confirmed once.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useHttps,
                    onCheckedChange = { checked ->
                        useHttps = checked
                        // Swap the port along with the protocol unless the user chose a custom one.
                        if (checked && port == FritzConfig.HTTP_PORT.toString()) {
                            port = FritzConfig.HTTPS_PORT.toString()
                        } else if (!checked && port == FritzConfig.HTTPS_PORT.toString()) {
                            port = FritzConfig.HTTP_PORT.toString()
                        }
                    },
                )
            }
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit) },
                label = { Text("TR-064 port") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text("FRITZ!Box user name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "On the box, enable \"Allow access for applications\" (Home Network › Network › " +
                    "Network Settings) and \"Dial help\" (Telephony › Calls › Dial Help). The user " +
                    "needs telephony permission. If you log in with password only, use the " +
                    "auto-generated user name shown under System › FRITZ!Box Users.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    keyboard?.hide()
                    onSave(
                        initial.copy(
                            host = host.trim(),
                            port = portValue ?: FritzConfig.HTTP_PORT,
                            username = user.trim(),
                            password = password,
                            useHttps = useHttps,
                        )
                    )
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Connecting…" else "Save & connect") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy && status != null) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Trusted certificate", style = MaterialTheme.typography.titleSmall)
            if (trustedFingerprint == null) {
                Text(
                    "None. When connecting via HTTPS you will be asked to confirm the router's certificate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "SHA-256 $trustedFingerprint",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShowCertificate) { Text("Show") }
                    OutlinedButton(onClick = onForgetCertificate) {
                        Text("Forget", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
