package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    /** Offer "Open settings" (only when the error is settings related and they are not open already). */
    offerSettings: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(error.title.asString()) },
        text = { Text(error.detail.asString()) },
        confirmButton = {
            Row {
                if (offerSettings && error.settingsRelated) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                }
                if (canRetry) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
