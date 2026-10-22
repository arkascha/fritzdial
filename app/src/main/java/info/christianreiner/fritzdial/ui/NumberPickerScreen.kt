package info.christianreiner.fritzdial.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.christianreiner.fritzdial.R

/**
 * Picks a number for the dialer: tab "Recent" (calls from the box' call list and the phone's call
 * log), tab "Speed dial" and tab "Phone books". Reload refreshes them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerScreen(
    /** The tab shown; kept in the view model, as setting up a speed dial switches tabs. */
    tab: PickerTab,
    onTabChange: (PickerTab) -> Unit,
    busy: Boolean,
    onReload: () -> Unit,
    onBack: () -> Unit,
    recents: @Composable () -> Unit,
    phonebooks: @Composable () -> Unit,
    speedDials: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onReload, enabled = !busy) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.picker_reload))
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
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                PickerTabItem(tab, PickerTab.RECENT, R.string.picker_tab_recent, HistoryIcon, onTabChange)
                PickerTabItem(tab, PickerTab.SPEED_DIALS, R.string.speed_dial, Icons.Default.Star, onTabChange)
                PickerTabItem(tab, PickerTab.PHONEBOOKS, R.string.phone_books, ContactsIcon, onTabChange)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when (tab) {
                PickerTab.RECENT -> recents()
                PickerTab.SPEED_DIALS -> speedDials()
                PickerTab.PHONEBOOKS -> phonebooks()
            }
        }
    }
}

/**
 * One tab: icon and title. The title is never cut off: it wraps onto a second line and, if a word
 * still does not fit (long translation, large font), gets smaller.
 */
@Composable
private fun PickerTabItem(
    selected: PickerTab,
    tab: PickerTab,
    @StringRes title: Int,
    icon: ImageVector,
    onSelect: (PickerTab) -> Unit,
) {
    Tab(selected = selected == tab, onClick = { onSelect(tab) }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Icon(icon, contentDescription = null)
            val style = MaterialTheme.typography.titleSmall
            BasicText(
                stringResource(title),
                style = style.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
