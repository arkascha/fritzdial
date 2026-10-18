package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import info.christianreiner.fritzdial.tr064.SecondFactorChallenge

/**
 * Shown while the FRITZ!Box waits for a confirmation on the device itself (FRITZ!OS
 * "additional confirmation", TR-064 error 866). Closes by itself once confirmed.
 */
@Composable
fun SecondFactorDialog(challenge: SecondFactorChallenge, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Confirm on the FRITZ!Box") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your FRITZ!Box asks you to confirm this step before it carries it out.")
                if (challenge.button) {
                    Text("• Press a button on the FRITZ!Box.")
                }
                challenge.dtmfCode?.let { code ->
                    Text(
                        buildAnnotatedString {
                            append(if (challenge.button) "• Or dial " else "• Dial ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
                                append(code)
                            }
                            append(" on a phone connected to the FRITZ!Box.")
                        }
                    )
                }
                if (!challenge.button && challenge.dtmfCode == null) {
                    Text(
                        "Confirm as shown on the FRITZ!Box" +
                            challenge.otherMethods.takeIf { it.isNotEmpty() }
                                ?.joinToString(prefix = " (", postfix = ").") .orEmpty()
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "Waiting for confirmation…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
