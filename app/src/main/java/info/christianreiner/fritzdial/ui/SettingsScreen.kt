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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.AppLanguage
import info.christianreiner.fritzdial.data.BoxProfile
import info.christianreiner.fritzdial.data.DeviceCallLog
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.tile.FritzDialTile
import info.christianreiner.fritzdial.tile.TileAddResult
import info.christianreiner.fritzdial.tile.canRequestAddTile
import info.christianreiner.fritzdial.tile.isTileAdded
import info.christianreiner.fritzdial.tile.requestAddTile
import info.christianreiner.fritzdial.tr064.BoxInfo
import info.christianreiner.fritzdial.tr064.DialingCodes
import info.christianreiner.fritzdial.tr064.BoxUser
import info.christianreiner.fritzdial.tr064.FritzConfig
import kotlinx.coroutines.delay

/**
 * Settings overview: the configured FritzBoxes (the one in use marked, each with whether it is in
 * this network; tap to edit), "Add FritzBox", the phone book and the recent numbers options. The
 * box in use follows the network; only when none of them is found here can one be chosen by hand
 * (tap its circle). The flag in the top bar opens the language screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profiles: List<BoxProfile>,
    activeProfileId: String?,
    /** Shown on the box in use: it was not found in this network, or the last request failed. */
    activeIssue: ActiveBoxIssue?,
    /** The boxes (ids) that answered the last detection; null before the first one. */
    boxesSeen: Set<String>?,
    /** Not even the box in use is reachable: then none counts as in use, all can be chosen. */
    noBoxReachable: Boolean,
    /** A request to the box is running – e.g. trying the one just chosen by hand. */
    busy: Boolean,
    networkNotice: UiText?,
    onEditProfile: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    onOpenLanguage: () -> Unit,
    deviceContactsEnabled: Boolean,
    onDeviceContactsChange: (Boolean) -> Unit,
    callLogEnabled: Boolean,
    callLogProblem: UiText?,
    /** Android's answer to the call log permission: try reading it, enable the feature if that works. */
    onEnableCallLog: (granted: Boolean) -> Unit,
    onDisableCallLog: () -> Unit,
    phoneSpeedDialsEnabled: Boolean,
    onPhoneSpeedDialsChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    // The national flag of the language in use, as an emoji.
                    val flag = AppLanguage.flag(LocalConfiguration.current.locales[0])
                    val languageLabel = stringResource(R.string.settings_language)
                    IconButton(
                        onClick = onOpenLanguage,
                        modifier = Modifier.semantics { contentDescription = languageLabel },
                    ) {
                        Text(flag, fontSize = 22.sp)
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

            // Chosen automatically; by hand only when none of the boxes is in this network.
            val canChoose = boxesSeen?.isEmpty() == true
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeProfileId && !noBoxReachable,
                    // Chosen by hand while none is reachable: being tried right now.
                    trying = busy && noBoxReachable && profile.id == activeProfileId,
                    seen = boxesSeen?.contains(profile.id),
                    canChoose = canChoose,
                    busy = busy,
                    issue = activeIssue,
                    onEdit = { onEditProfile(profile.id) },
                    onActivate = { onActivateProfile(profile.id) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_add_box)) },
                leadingContent = { MarkSlot { Icon(Icons.Default.Add, contentDescription = null) } },
                modifier = Modifier.clickable(onClick = onAddProfile),
            )

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.phone_books), style = MaterialTheme.typography.titleSmall)
                DeviceContactsSetting(enabled = deviceContactsEnabled, onChange = onDeviceContactsChange)
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_recents), style = MaterialTheme.typography.titleSmall)
                CallLogSetting(
                    enabled = callLogEnabled,
                    problem = callLogProblem,
                    onEnable = onEnableCallLog,
                    onDisable = onDisableCallLog,
                )
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.speed_dial), style = MaterialTheme.typography.titleSmall)
                BoxFeatureSetting(
                    title = stringResource(R.string.settings_phone_speed_dials),
                    hint = stringResource(R.string.settings_phone_speed_dials_hint),
                    enabled = phoneSpeedDialsEnabled,
                    onChange = onPhoneSpeedDialsChange,
                )
            }

            HorizontalDivider()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_tiles), style = MaterialTheme.typography.titleSmall)
                TilesSetting()
            }
        }
    }
}

/**
 * The quick settings tiles: each one already there is shown as such; for the others, from Android 13
 * on, a button which lets Android ask whether to add it; before that (and if adding fails) how to add
 * them by hand.
 */
@Composable
private fun TilesSetting() {
    val context = LocalContext.current
    // The outcome of the last request, shown under the buttons.
    var result by remember { mutableStateOf<Pair<FritzDialTile, TileAddResult>?>(null) }
    // The tiles in the quick settings; checked again on returning (one may have been removed meanwhile).
    var added by remember { mutableStateOf(FritzDialTile.entries.filter { isTileAdded(context, it) }.toSet()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        added = FritzDialTile.entries.filter { isTileAdded(context, it) }.toSet()
    }
    FritzDialTile.entries.filter { it in added }.forEach { tile ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "  " + stringResource(R.string.settings_tile_already_added, stringResource(tile.label)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (canRequestAddTile) {
        FritzDialTile.entries.filter { it !in added }.forEach { tile ->
            OutlinedButton(
                onClick = {
                    result = null
                    requestAddTile(context, tile) { outcome ->
                        result = tile to outcome
                        added = FritzDialTile.entries.filter { isTileAdded(context, it) }.toSet()
                    }
                },
            ) {
                Icon(painterResource(tile.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  " + stringResource(R.string.settings_tile_add, stringResource(tile.label)))
            }
        }
    }
    // An "already there" is shown by the list above.
    result?.takeIf { it.first !in added }?.let { (tile, outcome) ->
        val name = stringResource(tile.label)
        Text(
            when (outcome) {
                TileAddResult.ADDED -> stringResource(R.string.settings_tile_added, name)
                TileAddResult.ALREADY_ADDED -> stringResource(R.string.settings_tile_already_added, name)
                TileAddResult.NOT_ADDED -> stringResource(R.string.settings_tile_not_added, name)
                TileAddResult.FAILED -> stringResource(R.string.settings_tile_failed, name)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (outcome == TileAddResult.FAILED) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (added.size < FritzDialTile.entries.size && (!canRequestAddTile || result?.second == TileAddResult.FAILED)) {
        Text(
            stringResource(R.string.settings_tiles_by_hand),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The column in front of the boxes: as wide as a touch target, so the check mark, the circle to tap
 * and the "Add" icon line up.
 */
@Composable
private fun MarkSlot(content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { content() }
}

/**
 * One configured box: check mark when in use, else – only when [canChoose] – a circle to tap to
 * switch to it, and a progress indicator while it is being [trying]; then its name and whether it
 * is reachable ([seen]; null = not detected yet). Tapping the line opens its connection page (also
 * to delete it).
 */
@Composable
private fun ProfileRow(
    profile: BoxProfile,
    active: Boolean,
    trying: Boolean,
    seen: Boolean?,
    canChoose: Boolean,
    busy: Boolean,
    issue: ActiveBoxIssue?,
    onEdit: () -> Unit,
    onActivate: () -> Unit,
) {
    val presence = when {
        trying -> stringResource(R.string.status_connecting, profile.config.host)
        // Recognised only by its device id, learned once logged in to it.
        profile.deviceId == null -> stringResource(R.string.settings_box_never_recognised)
        seen == true -> stringResource(R.string.settings_box_here)
        seen == false -> stringResource(R.string.settings_box_not_here)
        else -> null
    }
    ListItem(
        // The name and where the box is; the details are on the connection page.
        headlineContent = { Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = presence?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MarkSlot {
                    if (trying) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (active && issue != null) {
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
                    } else if (canChoose) {
                        // Only one attempt at a time (a second tap would otherwise be ignored silently).
                        IconButton(onClick = onActivate, enabled = !busy) {
                            Icon(
                                CircleOutlineIcon,
                                contentDescription = stringResource(R.string.settings_use_box_now, profile.name),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Icon(
                    RouterIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    // The glyph's weight (the box) sits low, under thin antenna arcs: lift it to look centred.
                    modifier = Modifier.offset(y = (-2).dp),
                )
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

/**
 * Router connection: host, port, HTTPS, user, password, whether the box' phone books, call list and
 * speed dials are used, what the box said about itself, and the trusted certificate.
 */
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
    /** Whether this box' phone books, call list and speed dials are used (all on for a new box). */
    initialPhonebooksEnabled: Boolean,
    initialCallListEnabled: Boolean,
    initialSpeedDialsEnabled: Boolean,
    /** Model, FRITZ!OS version and serial number as last read from the box; null for a new one. */
    boxInfo: BoxInfo?,
    /** The box' country and area code as last read; null when not known. */
    dialingCodes: DialingCodes?,
    /** The users of the box at the entered address, offered for the user name (empty = none known). */
    boxUsers: List<BoxUser>,
    /** Asks the box at the entered host, port and HTTPS setting for its users. */
    onLookUpUsers: (FritzConfig) -> Unit,
    onSave: (
        config: FritzConfig,
        name: String,
        phonebooksEnabled: Boolean,
        callListEnabled: Boolean,
        speedDialsEnabled: Boolean,
    ) -> Unit,
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
    var phonebooksEnabled by remember { mutableStateOf(initialPhonebooksEnabled) }
    var callListEnabled by remember { mutableStateOf(initialCallListEnabled) }
    var speedDialsEnabled by remember { mutableStateOf(initialSpeedDialsEnabled) }
    val portValue = port.toIntOrNull()
    val keyboard = LocalSoftwareKeyboardController.current
    val valid = host.isNotBlank() && portValue != null && portValue in 1..65535

    // Once the address has stopped changing for a moment, ask the box there for its users.
    LaunchedEffect(host, portValue, useHttps) {
        if (!valid) return@LaunchedEffect
        delay(USER_LOOKUP_DELAY_MILLIS)
        onLookUpUsers(initial.copy(host = host.trim(), port = portValue ?: FritzConfig.HTTP_PORT, useHttps = useHttps))
    }

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
            UserField(value = user, onValueChange = { user = it }, users = boxUsers)
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.connection_password),
                modifier = Modifier.fillMaxWidth(),
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_phonebooks),
                hint = stringResource(R.string.connection_phonebooks_hint),
                enabled = phonebooksEnabled,
                onChange = { phonebooksEnabled = it },
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_call_list),
                hint = stringResource(R.string.connection_call_list_hint),
                enabled = callListEnabled,
                onChange = { callListEnabled = it },
            )
            BoxFeatureSetting(
                title = stringResource(R.string.connection_speed_dials),
                hint = stringResource(R.string.connection_speed_dials_hint),
                enabled = speedDialsEnabled,
                onChange = { speedDialsEnabled = it },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
                        phonebooksEnabled,
                        callListEnabled,
                        speedDialsEnabled,
                    )
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (busy) R.string.connection_connecting else R.string.connection_save)) }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy && status != null) {
                Text(status.asString(), style = MaterialTheme.typography.bodySmall)
            }

            if (boxInfo != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                BoxInfoSection(boxInfo, dialingCodes)
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

private const val USER_LOOKUP_DELAY_MILLIS = 800L

/**
 * The user name: typed, or chosen from the [users] of the box (when it told them), the one who
 * logged in last marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserField(value: String, onValueChange: (String) -> Unit, users: List<BoxUser>) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = users.isNotEmpty()
    ExposedDropdownMenuBox(expanded = expanded && canOpen, onExpandedChange = { if (canOpen) expanded = it }) {
        OutlinedTextField(
            value = value, onValueChange = onValueChange,
            label = { Text(stringResource(R.string.connection_user)) },
            supportingText = if (canOpen) {
                { Text(pluralStringResource(R.plurals.connection_users_found, users.size, users.size)) }
            } else null,
            trailingIcon = if (canOpen) {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, canOpen),
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            users.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(candidate.name)
                            if (candidate.lastUsed) {
                                Text(
                                    stringResource(R.string.connection_user_last),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onValueChange(candidate.name)
                    },
                )
            }
        }
    }
}

/** What the box reported about itself – to tell it apart from others, and its dialling codes; selectable for copying. */
@Composable
private fun BoxInfoSection(info: BoxInfo, codes: DialingCodes?) {
    Text(stringResource(R.string.connection_box_info), style = MaterialTheme.typography.titleSmall)
    val lines = listOfNotNull(
        info.model?.let { stringResource(R.string.connection_box_model, it) },
        info.fritzOsVersion?.let { stringResource(R.string.connection_box_fritzos, it) },
        info.serialNumber?.let { stringResource(R.string.connection_box_serial, it) },
        // As dialled: "+49", "030".
        codes?.let { stringResource(R.string.connection_box_country_code, "+" + it.countryCode) },
        codes?.areaCode?.let { stringResource(R.string.connection_box_area_code, codes.nationalPrefix + it) },
    )
    if (lines.isEmpty()) {
        Text(
            stringResource(R.string.connection_box_info_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Switch for an optional source (phone books, call list, speed dials); [hint] behind a question mark. */
@Composable
private fun BoxFeatureSetting(title: String, hint: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(title, listOf(hint), Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = onChange)
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_device_contacts),
            listOf(stringResource(R.string.settings_device_contacts_hint)),
            Modifier.weight(1f),
        )
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        OptionTitle(
            stringResource(R.string.settings_call_log),
            listOf(stringResource(R.string.settings_call_log_hint), stringResource(R.string.settings_call_log_play)),
            Modifier.weight(1f),
        )
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
