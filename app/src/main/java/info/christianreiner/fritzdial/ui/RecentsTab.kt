package info.christianreiner.fritzdial.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.tr064.RecentCall

/**
 * The number picker's "Recent" tab: the numbers dialled, received or missed last, from the
 * FRITZ!Box' call list and – optionally, switched on in the settings – from the phone's own call
 * log. Each number appears once, at its latest call. Tapping one puts it into the dialer.
 */
@Composable
fun RecentsTab(
    boxCalls: List<RecentCall>,
    boxCallsLoaded: Boolean,
    /** Name of the FritzBox in use (as in the settings), shown as the source of its calls. */
    boxName: String?,
    phoneCalls: List<RecentCall>?,
    /** The optional "this phone's calls" feature is switched on in the settings. */
    callLogEnabled: Boolean,
    /** Reading the phone's call log failed; shown with a way out. */
    callLogProblem: String?,
    busy: Boolean,
    onPick: (String) -> Unit,
    /** Re-reads the phone's call log (when coming back to the app, a call may have been made). */
    onReloadCallLog: () -> Unit,
    onDisableCallLog: () -> Unit,
) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onReloadCallLog() }

    val countryIso = remember { deviceCountryIso(context) }
    val calls = remember(boxCalls, phoneCalls, callLogEnabled) {
        val phone = if (callLogEnabled) phoneCalls.orEmpty() else emptyList()
        mergeRecents(boxCalls + phone) { numberKey(it, countryIso) }
    }

    Column(Modifier.fillMaxSize()) {
        if (callLogEnabled && callLogProblem != null) {
            CallLogProblemCard(
                problem = callLogProblem,
                onOpenSettings = { context.startActivity(DeviceContacts.appSettingsIntent(context)) },
                onTurnOff = onDisableCallLog,
            )
        }

        when {
            calls.isEmpty() && busy -> Message("Loading call list…")
            calls.isEmpty() && !boxCallsLoaded ->
                Message("The call list could not be loaded. Tap reload to try again.")
            calls.isEmpty() -> Message("No recent calls yet.")
            else -> {
                // Only the phone's calls are shown: say so, rather than silently leave the box' out.
                if (!boxCallsLoaded && !busy) {
                    Text(
                        "The FritzBox call list could not be loaded. Tap reload to try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val now = remember(calls) { System.currentTimeMillis() }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(calls) { call ->
                        RecentRow(call, now, boxName, onPick)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Name (or number) and number on the left. On the right, top line: when the call was and how it
 * went (dialled / received / missed); bottom line: where it was recorded (the FritzBox' name and a
 * router icon, or a phone icon). The icons have a fixed size and sit at the right edge, so the times
 * of all rows end at the same position – a right-aligned column.
 */
@Composable
private fun RecentRow(call: RecentCall, now: Long, boxName: String?, onPick: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(call.name ?: call.number, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = call.name?.let { { Text(call.number, maxLines = 1) } },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(
                            call.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString(),
                        // Tabular figures: digits of equal width, so the times line up neatly.
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(8.dp))
                    DirectionIcon(call.direction)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (call.source == RecentCall.Source.FRITZBOX) {
                        Text(
                            boxName ?: "FritzBox",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 96.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        when (call.source) {
                            RecentCall.Source.FRITZBOX -> RouterIcon
                            RecentCall.Source.PHONE -> SmartphoneIcon
                        },
                        contentDescription = when (call.source) {
                            RecentCall.Source.FRITZBOX -> "Recorded by ${boxName ?: "the FritzBox"}"
                            RecentCall.Source.PHONE -> "Recorded by this phone"
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
            }
        },
        modifier = Modifier.clickable { onPick(call.number) },
    )
}

/** Arrow for how the call went: dialled (up), received (down, green), missed (red). */
@Composable
private fun DirectionIcon(direction: RecentCall.Direction) {
    val (icon, description, tint) = when (direction) {
        RecentCall.Direction.OUTGOING -> Triple(CallMadeIcon, "Dialled", MaterialTheme.colorScheme.primary)
        RecentCall.Direction.INCOMING -> Triple(CallReceivedIcon, "Received", CallGreen)
        RecentCall.Direction.MISSED -> Triple(CallMissedIcon, "Missed", MaterialTheme.colorScheme.error)
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(ICON_SIZE))
}

/** Both icons share one size, so they sit exactly above each other at the right edge. */
private val ICON_SIZE = 18.dp

/** Shown when "this phone's calls" is switched on but reading the call log failed. */
@Composable
private fun CallLogProblemCard(
    problem: String,
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(problem, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTurnOff) { Text("Turn off") }
                TextButton(onClick = onOpenSettings) { Text("App settings") }
            }
        }
    }
}
