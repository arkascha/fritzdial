package info.christianreiner.fritzdial.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.data.BoxProfile
import info.christianreiner.fritzdial.data.DeviceCallLog
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.tr064.FritzConfig

/**
 * Settings overview: the configured FritzBoxes (the active one marked; tap to edit, menu to switch
 * or delete), "Add FritzBox", the phone book and the recent numbers options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profiles: List<BoxProfile>,
    activeProfileId: String?,
    networkNotice: String?,
    onEditProfile: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    deviceContactsEnabled: Boolean,
    onDeviceContactsChange: (Boolean) -> Unit,
    callLogEnabled: Boolean,
    callLogProblem: String?,
    /** Android's answer to the call log permission: try reading it, enable the feature if that works. */
    onEnableCallLog: (granted: Boolean) -> Unit,
    onDisableCallLog: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<BoxProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "FritzBox connections",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Text(
                "The FritzBox of the network the phone is in is selected automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            networkNotice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    onEdit = { onEditProfile(profile.id) },
                    onActivate = { onActivateProfile(profile.id) },
                    onDelete = { confirmDelete = profile },
                )
            }
            ListItem(
                headlineContent = { Text("Add FritzBox") },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onAddProfile),
            )

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeviceContactsSetting(enabled = deviceContactsEnabled, onChange = onDeviceContactsChange)
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CallLogSetting(
                    enabled = callLogEnabled,
                    problem = callLogProblem,
                    onEnable = onEnableCallLog,
                    onDisable = onDisableCallLog,
                )
            }
        }
    }

    confirmDelete?.let { profile ->
        ConfirmDeleteDialog(
            name = profile.name,
            onConfirm = {
                confirmDelete = null
                onDeleteProfile(profile.id)
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

/** One configured box on a single line: check mark when in use, its name, and a menu. */
@Composable
private fun ProfileRow(
    profile: BoxProfile,
    active: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        // Just the name, on one line; the details are on the connection page.
        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            if (active) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "In use",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                // keeps the names aligned with the active entry
                Box(Modifier.size(24.dp))
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${profile.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text("Use now") },
                            onClick = { menu = false; onActivate() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onEdit),
    )
}

@Composable
private fun ConfirmDeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \u201c$name\u201d?") },
        text = { Text("The connection, its password and its trusted certificate are removed from this phone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Router connection: host, port, HTTPS, user, password and the trusted certificate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    initial: FritzConfig,
    canGoBack: Boolean,
    /** SHA-256 fingerprint of the currently trusted certificate, or null. */
    trustedFingerprint: String?,
    busy: Boolean,
    status: String?,
    /** Name of the connection being edited (empty for a new one). */
    initialName: String,
    /** Adding a new FritzBox rather than editing an existing one. */
    isNew: Boolean,
    onSave: (FritzConfig, String) -> Unit,
    onBack: () -> Unit,
    /** Deletes the edited connection; null for a new one. */
    onDelete: (() -> Unit)?,
    onShowCertificate: () -> Unit,
    onForgetCertificate: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var confirmDelete by remember { mutableStateOf(false) }
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
                title = { Text(if (isNew) "Add FritzBox" else "FritzBox connection") },
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
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("default: the box\u2019s own name", maxLines = 1) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
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
                        ),
                        name,
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

            if (onDelete != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  Delete this FritzBox", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog(
            name = name.ifBlank { initial.host },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * Switch for the optional "this phone's contacts" feature. Turning it on asks Android for the
 * contacts permission; the feature is only enabled once that is granted.
 */
@Composable
private fun DeviceContactsSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        denied = !granted
        if (granted) onChange(true)
    }
    // Re-check when coming back from the Android app settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = DeviceContacts.hasPermission(context)
        if (allowed) denied = false
    }

    Text("Phone books", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Include this phone\u2019s contacts", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Shows the contacts stored on this phone next to the FRITZ!Box phone books. " +
                    "Needs access to your contacts; they never leave the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled && allowed,
            onCheckedChange = { on ->
                when {
                    !on -> onChange(false)
                    allowed -> onChange(true)
                    else -> launcher.launch(Manifest.permission.READ_CONTACTS)
                }
            },
        )
    }
    if (denied || (enabled && !allowed)) {
        Text(
            "Android does not allow FritzDial to read your contacts. You can allow it in the app settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text("App settings")
        }
    }
}

/**
 * Switch for the optional "this phone's calls" feature. Turning it on asks Android for the call log
 * permission and then tries to read the call log; only if that works the feature is enabled,
 * otherwise it stays off and [problem] says why.
 */
@Composable
private fun CallLogSetting(
    enabled: Boolean,
    problem: String?,
    onEnable: (granted: Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onEnable)

    Text("Recent numbers", style = MaterialTheme.typography.titleSmall)
    Text(
        "The recently dialled, received and missed numbers are read from the FritzBox\u2019 call list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Include this phone\u2019s calls", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Also offers the numbers dialled, received and missed with the phone\u2019s own telephony app. Needs access " +
                    "to your call log; it never leaves the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Only works if FritzDial was installed from outside Google Play (APK or F-Droid): " +
                    "Google Play allows reading the call log only for the phone\u2019s default phone app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { on ->
                when {
                    !on -> onDisable()
                    DeviceCallLog.hasPermission(context) -> onEnable(true)
                    else -> launcher.launch(Manifest.permission.READ_CALL_LOG)
                }
            },
        )
    }
    if (problem != null) {
        Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text("App settings")
        }
    }
}
