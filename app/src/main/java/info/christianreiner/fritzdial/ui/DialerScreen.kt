package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.SpeedDial
import info.christianreiner.fritzdial.tr064.FritzDialer

/**
 * Single, non-scrolling screen: number, dial pad, phone selector and call buttons.
 * The dial pad takes whatever height is left and sizes its keys to fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    state: UiState,
    onNumberChange: (String) -> Unit,
    onDigit: (Char) -> Unit,
    /** Long press on a digit: speed dial, else like a tap ("0": "+"). */
    onLongDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDial: () -> Unit,
    onHangup: () -> Unit,
    onSelectPhone: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // App name, and below it the FritzBox in use – or a note about the network.
                    val box = state.profiles.firstOrNull { it.id == state.activeProfileId }?.name
                    Column {
                        Text(stringResource(R.string.app_name))
                        val notice = state.networkNotice?.asString()
                            ?: if (state.noBoxReachable) stringResource(R.string.notice_no_known_box) else null
                        val subtitle = notice ?: box
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (notice != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Activity indicator with a fixed height, so the layout never jumps.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Display only: input comes from the dial pad. The field is disabled so it has no
                // keyboard and no gesture handling of its own (its long-press text selection would
                // otherwise swallow the long press on ⌫); the colours keep the normal look.
                val onSurface = MaterialTheme.colorScheme.onSurface
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                OutlinedTextField(
                    value = state.number,
                    onValueChange = onNumberChange,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // Never on two lines (long translation, large font): rather cut off with "…".
                    // After a speed dial it says which one, and whose number it is.
                    label = {
                        Text(
                            state.numberLabel?.asString() ?: stringResource(R.string.dialer_number_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = onSurfaceVariant,
                        disabledTrailingIconColor = onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                    leadingIcon = {
                        IconButton(onClick = onOpenPicker) {
                            Icon(ContactsIcon, contentDescription = stringResource(R.string.dialer_choose_number))
                        }
                    },
                    trailingIcon = {
                        if (state.number.isNotEmpty()) {
                            BackspaceButton(
                                onBackspace = onBackspace,
                                onClear = { onNumberChange("") },
                            )
                        }
                    },
                )

                Spacer(Modifier.height(12.dp))

                // Takes all remaining height; keys shrink on small screens / in landscape.
                DialPad(
                    onDigit = onDigit,
                    onLongDigit = onLongDigit,
                    speedDials = state.usablePhoneSpeedDials,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                PhoneSelector(
                    phones = state.phones,
                    selected = state.selectedPhone,
                    busy = state.busy,
                    onSelect = onSelectPhone,
                    onRefresh = onRefresh,
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    RoundActionButton(
                        onClick = onDial,
                        // Only when there is something dialable (same check the dial action uses).
                        // No box to dial through: entering a number still works, dialling does not.
                        enabled = !state.busy && !state.noBoxReachable &&
                            FritzDialer.sanitizeNumber(state.number).isNotEmpty(),
                        color = CallGreen,
                    ) {
                        Icon(Icons.Default.Call, contentDescription = stringResource(R.string.dialer_dial), modifier = Modifier.size(30.dp))
                    }
                    RoundActionButton(onClick = onHangup, enabled = state.dialActive && !state.busy, color = HangupRed) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialer_hang_up), modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

/** ⌫ button: tap deletes the last digit, long press clears the whole number. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceButton(onBackspace: () -> Unit, onClear: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.dialer_delete_digit),
                onLongClickLabel = stringResource(R.string.dialer_clear_number),
                onClick = onBackspace,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClear()
                },
            ),
    ) {
        Icon(BackspaceIcon, contentDescription = stringResource(R.string.dialer_backspace_description))
    }
}

@Composable
private fun RoundActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    icon: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(68.dp),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) { icon() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneSelector(
    phones: List<String>,
    selected: String?,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val canOpen = !busy && phones.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded && canOpen,
        onExpandedChange = { if (canOpen) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let(FritzDialer::displayName)
                ?: stringResource(if (phones.isEmpty()) R.string.dialer_no_phones else R.string.dialer_choose_phone),
            onValueChange = {},
            readOnly = true,
            enabled = !busy,
            label = { Text(stringResource(R.string.dialer_ring_phone)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Reloads the list from the router. The menu anchor sees the same tap and would
                    // toggle the list open, so it is explicitly kept closed here.
                    IconButton(
                        onClick = {
                            expanded = false
                            onRefresh()
                        },
                        enabled = !busy,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dialer_reload_phones))
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canOpen)
                    Spacer(Modifier.size(8.dp))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, canOpen),
        )
        ExposedDropdownMenu(expanded = expanded && canOpen, onDismissRequest = { expanded = false }) {
            phones.forEach { phone ->
                DropdownMenuItem(
                    text = { Text(FritzDialer.displayName(phone)) },
                    onClick = {
                        expanded = false
                        onSelect(phone)
                    },
                )
            }
        }
    }
}

private data class Key(val main: Char, val sub: String = "")

private val KEYS = listOf(
    listOf(Key('1'), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*'), Key('0', "+"), Key('#')),
)

/**
 * 4×3 keypad whose keys are sized to fit the available width *and* height. Keys with one of this
 * phone's [speedDials] have a small dot.
 */
@Composable
private fun DialPad(
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
    speedDials: List<SpeedDial>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 10.dp
        val byWidth = (maxWidth - gap * 2) / 3
        val byHeight = (maxHeight - gap * 3) / 4
        val keySize: Dp = min(min(byWidth, byHeight), 80.dp).coerceAtLeast(36.dp)

        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { key ->
                        DialKey(key, keySize, speedDials.firstOrNull { it.slot == key.main.toString() }, onDigit, onLongDigit)
                    }
                }
            }
        }
    }
}

/**
 * Round key. A long press on a digit is for speed dials; without one it acts like a tap, except
 * "0", which inserts "+" like on a regular phone keypad.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: Key,
    size: Dp,
    speedDial: SpeedDial?,
    onDigit: (Char) -> Unit,
    onLongDigit: (Char) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val longPress: (() -> Unit)? = if (key.main.isDigit()) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongDigit(key.main)
        }
    } else null
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .combinedClickable(
                role = Role.Button,
                onClick = { onDigit(key.main) },
                onLongClickLabel = speedDial?.let {
                    stringResource(R.string.dialer_speed_dial_label, it.slot, it.name)
                },
                onLongClick = longPress,
            ),
    ) {
        if (speedDial != null) {
            // Marks a key with a speed dial, at the top of the round key.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = size * 0.1f)
                    .size((size * 0.08f).coerceAtLeast(4.dp))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.main.toString(),
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            // Letters only when the key is large enough to show them legibly.
            if (key.sub.isNotEmpty() && size >= 52.dp) {
                Text(
                    key.sub,
                    fontSize = (size.value * 0.13f).sp,
                    lineHeight = (size.value * 0.15f).sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
