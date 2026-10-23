package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import info.christianreiner.fritzdial.R

/**
 * Modal error: a failed connection blocks all use of the app, so it is shown on top of
 * whatever screen is open, with the obvious next steps as buttons.
 */
@Composable
fun ErrorDialog(
    error: BoxError,
    canRetry: Boolean,
    /** Offer "Open settings" (only when the error is settings related and the settings are not open). */
    offerSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settingsButton = offerSettings && error.settingsRelated
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(error.title.asString()) },
        text = { Text(error.detail.asString()) },
        confirmButton = {
            if (settingsButton && canRetry) {
                // Three buttons do not fit side by side (in German, "Wiederholen" got squeezed into
                // two lines): stacked, the main action first, as Material does for long buttons.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            } else {
                when {
                    canRetry -> TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    settingsButton -> TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                    else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        },
        dismissButton = if ((settingsButton && canRetry) || (!canRetry && !settingsButton)) null else {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
        },
    )
}
