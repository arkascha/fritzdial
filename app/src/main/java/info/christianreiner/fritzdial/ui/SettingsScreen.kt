package info.christianreiner.fritzdial.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.R
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
    /** Shown on the box in use: it was not found in this network, or the last request failed. */
    activeIssue: ActiveBoxIssue?,
    networkNotice: UiText?,
    onEditProfile: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    deviceContactsEnabled: Boolean,
    onDeviceContactsChange: (Boolean) -> Unit,
    callLogEnabled: Boolean,
    callLogProblem: UiText?,
    /** Android's answer to the call log permission: try reading it, enable the feature if that works. */
    onEnableCallLog: (granted: Boolean) -> Unit,
    onDisableCallLog: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<BoxProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                stringResource(R.string.settings_connections),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.settings_connections_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            networkNotice?.let {
                Text(
                    it.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeProfileId,
                    issue = activeIssue,
                    onEdit = { onEditProfile(profile.id) },
                    onActivate = { onActivateProfile(profile.id) },
                    onDelete = { confirmDelete = profile },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_add_box)) },
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
    issue: ActiveBoxIssue?,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        // Just the name, on one line; the details are on the connection page.
        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active && issue != null) {
                    // In use, but not as expected: a warning instead of the check mark.
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(
                            when (issue) {
                                ActiveBoxIssue.NOT_FOUND -> R.string.settings_in_use_not_found
                                ActiveBoxIssue.NOT_WORKING -> R.string.settings_in_use_not_working
                            }
                        ),
                        tint = MaterialTheme.colorScheme.error,
                    )
                } else if (active) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.settings_in_use),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    // keeps the router icons and names aligned with the active entry
                    Box(Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    RouterIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The glyph's weight (the box) sits low, under thin antenna arcs: lift it to look centred.
                    modifier = Modifier.offset(y = (-2).dp),
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.settings_more_options, profile.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!active) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_use_now)) },
                            onClick = { menu = false; onActivate() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
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
        title = { Text(stringResource(R.string.settings_delete_title, name)) },
        text = { Text(stringResource(R.string.settings_delete_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
    status: UiText?,
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
                title = { Text(stringResource(if (isNew) R.string.settings_add_box else R.string.connection_title)) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                label = { Text(stringResource(R.string.connection_name)) },
                placeholder = { Text(stringResource(R.string.connection_name_placeholder), maxLines = 1) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host, onValueChange = { host = it },
                label = { Text(stringResource(R.string.connection_host)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.connection_https), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.connection_https_hint, FritzConfig.HTTPS_PORT),
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
                label = { Text(stringResource(R.string.connection_port)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = user, onValueChange = { user = it },
                label = { Text(stringResource(R.string.connection_user)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.connection_password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.connection_help),
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
            ) { Text(stringResource(if (busy) R.string.connection_connecting else R.string.connection_save)) }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy && status != null) {
                Text(status.asString(), style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.connection_trusted_cert), style = MaterialTheme.typography.titleSmall)
            if (trustedFingerprint == null) {
                Text(
                    stringResource(R.string.connection_no_cert),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.connection_cert_fingerprint, trustedFingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onShowCertificate) { Text(stringResource(R.string.show)) }
                    OutlinedButton(onClick = onForgetCertificate) {
                        Text(stringResource(R.string.forget), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (onDelete != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  " + stringResource(R.string.connection_delete_box), color = MaterialTheme.colorScheme.error)
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

    Text(stringResource(R.string.phone_books), style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_device_contacts), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_device_contacts_hint),
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
            stringResource(R.string.settings_device_contacts_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
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
    problem: UiText?,
    onEnable: (granted: Boolean) -> Unit,
    onDisable: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onEnable)

    Text(stringResource(R.string.settings_recents), style = MaterialTheme.typography.titleSmall)
    Text(
        stringResource(R.string.settings_recents_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_call_log), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_call_log_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_call_log_play),
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
        Text(problem.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { context.startActivity(DeviceContacts.appSettingsIntent(context)) }) {
            Text(stringResource(R.string.app_settings))
        }
    }
}
