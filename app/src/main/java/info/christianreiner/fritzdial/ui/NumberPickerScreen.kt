package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Picks a number for the dialer: tab "Recent" (calls from the box' call list and the phone's call
 * log) and tab "Phone books". Reload refreshes both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerScreen(
    busy: Boolean,
    onReload: () -> Unit,
    onBack: () -> Unit,
    recents: @Composable () -> Unit,
    phonebooks: @Composable () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_RECENT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload recent calls and phone books")
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
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == TAB_RECENT,
                    onClick = { tab = TAB_RECENT },
                    text = { Text("Recent") },
                    icon = { Icon(HistoryIcon, contentDescription = null) },
                )
                Tab(
                    selected = tab == TAB_PHONEBOOKS,
                    onClick = { tab = TAB_PHONEBOOKS },
                    text = { Text("Phone books") },
                    icon = { Icon(ContactsIcon, contentDescription = null) },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (tab) {
                TAB_RECENT -> recents()
                else -> phonebooks()
            }
        }
    }
}

private const val TAB_RECENT = 0
private const val TAB_PHONEBOOKS = 1
