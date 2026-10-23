package info.christianreiner.fritzdial.ui

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.tr064.DialingCodes
import info.christianreiner.fritzdial.tr064.PhonebookContact
import info.christianreiner.fritzdial.tr064.RecentCall
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * The number picker's "Recent" tab: the numbers dialled, received or missed last, from the
 * FRITZ!Box' call list (if used for the box in use) and – optionally, switched on in the settings – from the phone's own call
 * log. Each number appears once, at its latest call. Tapping one puts it into the dialer.
 */
@Composable
fun RecentsTab(
    boxCalls: List<RecentCall>,
    boxCallsLoaded: Boolean,
    /** Name of the FritzBox in use (as in the settings), shown as the source of its calls. */
    boxName: String?,
    /** Country and area code of the box in use, for recognising a number written differently. */
    dialingCodes: DialingCodes?,
    /** The contacts of the phone books in use, for the names of calls that come without one. */
    contacts: List<PhonebookContact>,
    /** The call list of the box in use is switched on in the settings. */
    boxCallsEnabled: Boolean,
    phoneCalls: List<RecentCall>?,
    /** The optional "this phone's calls" feature is switched on in the settings. */
    callLogEnabled: Boolean,
    /** Reading the phone's call log failed; shown with a way out. */
    callLogProblem: UiText?,
    busy: Boolean,
    onPick: (String) -> Unit,
    /** Re-reads the phone's call log (when coming back to the app, a call may have been made). */
    onReloadCallLog: () -> Unit,
    onDisableCallLog: () -> Unit,
) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onReloadCallLog() }

    val countryIso = remember { deviceCountryIso(context) }
    val calls = remember(boxCalls, boxCallsEnabled, phoneCalls, callLogEnabled, dialingCodes, contacts) {
        val box = if (boxCallsEnabled) boxCalls else emptyList()
        val phone = if (callLogEnabled) phoneCalls.orEmpty() else emptyList()
        val key = { number: String -> numberKey(number, countryIso, dialingCodes) }
        nameRecents(mergeRecents(box + phone, key), contacts, key)
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
            !boxCallsEnabled && !callLogEnabled -> Message(stringResource(R.string.recents_none_enabled))
            calls.isEmpty() && busy -> Message(stringResource(R.string.recents_loading))
            calls.isEmpty() && boxCallsEnabled && !boxCallsLoaded ->
                Message(stringResource(R.string.recents_load_failed))
            calls.isEmpty() -> Message(stringResource(R.string.recents_empty))
            else -> {
                // Only the phone's calls are shown: say so, rather than silently leave the box' out.
                if (boxCallsEnabled && !boxCallsLoaded && !busy) {
                    Text(
                        stringResource(R.string.recents_box_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val now = remember(calls) { System.currentTimeMillis() }
                val times = rememberRelativeTimes()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(calls) { call ->
                        RecentRow(call, times.format(call.time, now), boxName, onPick)
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
private fun RecentRow(call: RecentCall, time: String, boxName: String?, onPick: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(call.name ?: call.number, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = call.name?.let { { Text(call.number, maxLines = 1) } },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        time,
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
                            boxName ?: stringResource(R.string.fritzbox),
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
                            RecentCall.Source.FRITZBOX -> stringResource(R.string.recents_recorded_by_box, boxName ?: stringResource(R.string.fritzbox))
                            RecentCall.Source.PHONE -> stringResource(R.string.recents_recorded_by_phone)
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
        RecentCall.Direction.OUTGOING -> Triple(CallMadeIcon, R.string.recents_dialled, MaterialTheme.colorScheme.primary)
        RecentCall.Direction.INCOMING -> Triple(CallReceivedIcon, R.string.recents_received, CallGreen)
        RecentCall.Direction.MISSED -> Triple(CallMissedIcon, R.string.recents_missed, MaterialTheme.colorScheme.error)
    }
    Icon(icon, contentDescription = stringResource(description), tint = tint, modifier = Modifier.size(ICON_SIZE))
}

/** Both icons share one size, so they sit exactly above each other at the right edge. */
private val ICON_SIZE = 18.dp

/** Shown when "this phone's calls" is switched on but reading the call log failed. */
@Composable
private fun CallLogProblemCard(
    problem: UiText,
    onOpenSettings: () -> Unit,
    onTurnOff: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(problem.asString(), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onTurnOff) { Text(stringResource(R.string.turn_off)) }
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.app_settings)) }
            }
        }
    }
}

/**
 * "now", "14 min. ago", "3 hr. ago", "yesterday", "3 days ago", then the date – in the app's language.
 * (DateUtils' relative times always use the system language, even when the app has its own.)
 */
private class RelativeTimes(private val locale: Locale) {
    private val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_FOR_STANDALONE,
    )
    private val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMyy"), locale)

    fun format(time: Long, now: Long): String {
        val minutes = (now - time) / 60_000
        val days = daysBetween(time, now)
        return when {
            minutes < 1 -> formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
            minutes < 60 -> formatter.format(minutes.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
            days == 0 -> formatter.format((minutes / 60).toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS)
            days == 1 -> formatter.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            days < 7 -> formatter.format(days.toDouble(), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS)
            else -> date.format(Date(time))
        }
    }

    /** Calendar days from [time] to [now] (0 = same day), in the phone's time zone. */
    private fun daysBetween(time: Long, now: Long): Int {
        val zone = ZoneId.systemDefault()
        val from = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt()
    }
}

@Composable
private fun rememberRelativeTimes(): RelativeTimes {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { RelativeTimes(locale) }
}
