package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.SpeedDial
import info.christianreiner.fritzdial.data.SpeedDials

/**
 * The number picker's "Speed dial" tab, in two parts that never collide: this phone's own speed
 * dials 1–9 (valid with every box; free slots can be set up from the phone books, a long press on
 * a set one changes or removes it), and the two-digit ones set up in the phone books of the box in
 * use (read only). Tapping one puts its number into the dialer.
 */
@Composable
fun SpeedDialTab(
    /** This phone's speed dials; null when switched off in the settings. */
    phoneSpeedDials: List<SpeedDial>?,
    /** The speed dials of the box in use; null when switched off for it. */
    boxSpeedDials: List<SpeedDial>?,
    /** Name of the box in use (as in the settings). */
    boxName: String?,
    /** The box' phone books (which hold its speed dials) have been read. */
    boxLoaded: Boolean,
    busy: Boolean,
    onPick: (SpeedDial) -> Unit,
    /** Choose a number from the phone books for this phone's speed dial slot. */
    onAssign: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (phoneSpeedDials == null && boxSpeedDials == null) {
        Message(stringResource(R.string.speed_dial_none_enabled))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (phoneSpeedDials != null) {
            item {
                SectionTitle(
                    stringResource(R.string.this_phone),
                    listOf(stringResource(R.string.speed_dial_phone_help)),
                )
            }
            items(SpeedDials.PHONE_SLOTS) { slot ->
                PhoneSlotRow(
                    slot = slot,
                    speedDial = phoneSpeedDials.firstOrNull { it.slot == slot },
                    onPick = onPick,
                    onAssign = { onAssign(slot) },
                    onRemove = { onRemove(slot) },
                )
                HorizontalDivider()
            }
        }
        if (boxSpeedDials != null) {
            item {
                SectionTitle(
                    boxName ?: stringResource(R.string.fritzbox),
                    listOf(stringResource(R.string.speed_dial_box_help)),
                )
            }
            when {
                boxSpeedDials.isEmpty() && busy -> item { Message(stringResource(R.string.phonebook_loading)) }
                boxSpeedDials.isEmpty() && !boxLoaded -> item { Message(stringResource(R.string.speed_dial_box_failed)) }
                boxSpeedDials.isEmpty() -> item { Message(stringResource(R.string.speed_dial_box_none)) }
                else -> items(boxSpeedDials, key = { "box-${it.slot}" }) { speedDial ->
                    ListItem(
                        leadingContent = { SlotLabel(speedDial.slot) },
                        headlineContent = { Text(speedDial.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(speedDial.number, maxLines = 1) },
                        modifier = Modifier.clickable { onPick(speedDial) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, help: List<String>) {
    OptionTitle(
        title,
        help,
        Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary),
    )
}

/** The slot number in front of a speed dial, in a fixed width so the names line up. */
@Composable
private fun SlotLabel(slot: String) {
    Text(
        slot,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(32.dp),
    )
}

/** One of this phone's slots: tap to use (or, when free, to set up); long press to change or remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneSlotRow(
    slot: String,
    speedDial: SpeedDial?,
    onPick: (SpeedDial) -> Unit,
    onAssign: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        ListItem(
            leadingContent = { SlotLabel(slot) },
            headlineContent = {
                if (speedDial != null) {
                    Text(speedDial.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(stringResource(R.string.speed_dial_free), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            supportingContent = speedDial?.let { { Text(it.number, maxLines = 1) } },
            trailingContent = if (speedDial == null) {
                { Icon(Icons.Default.Add, contentDescription = null) }
            } else null,
            modifier = if (speedDial != null) {
                Modifier.combinedClickable(
                    onClick = { onPick(speedDial) },
                    onLongClickLabel = stringResource(R.string.speed_dial_change_or_remove),
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
            } else {
                Modifier.combinedClickable(
                    onClickLabel = stringResource(R.string.speed_dial_set_up),
                    onClick = onAssign,
                )
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.speed_dial_change)) },
                onClick = { menu = false; onAssign() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.speed_dial_remove), color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onRemove() },
            )
        }
    }
}
