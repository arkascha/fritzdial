package info.christianreiner.fritzdial.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import info.christianreiner.fritzdial.data.DeviceContacts
import info.christianreiner.fritzdial.tr064.RecentCall

/**
 * The numbers dialled, received or missed last: from the FRITZ!Box' call list and – optionally, switched on in the
 * settings – from the phone's own call log. Each number appears once, at its latest call.
 * Tapping one puts it into the dialer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    boxCalls: List<RecentCall>,
    boxCallsLoaded: Boolean,
    phoneCalls: List<RecentCall>?,
    /** The optional "this phone's calls" feature is switched on in the settings. */
    callLogEnabled: Boolean,
    /** Reading the phone's call log failed; shown with a way out. */
    callLogProblem: String?,
    busy: Boolean,
    onPick: (String) -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recent numbers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload recent numbers")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

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
                    val now = remember(calls) { System.currentTimeMillis() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(calls) { call ->
                            RecentRow(call, now, onPick)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Direction, name (or number) and number on the left; when and where the call was on the right. */
@Composable
private fun RecentRow(call: RecentCall, now: Long, onPick: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(call.name ?: call.number) },
        supportingContent = call.name?.let { { Text(call.number) } },
        leadingContent = {
            when (call.direction) {
                RecentCall.Direction.OUTGOING ->
                    Icon(CallMadeIcon, contentDescription = "Dialled", tint = MaterialTheme.colorScheme.primary)
                RecentCall.Direction.INCOMING ->
                    Icon(CallReceivedIcon, contentDescription = "Received", tint = CallGreen)
                RecentCall.Direction.MISSED ->
                    Icon(CallMissedIcon, contentDescription = "Missed", tint = MaterialTheme.colorScheme.error)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        call.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    when (call.source) {
                        RecentCall.Source.FRITZBOX -> "FritzBox"
                        RecentCall.Source.PHONE -> "This phone"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable { onPick(call.number) },
    )
}

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
