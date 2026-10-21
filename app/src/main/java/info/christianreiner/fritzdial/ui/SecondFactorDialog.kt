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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import info.christianreiner.fritzdial.R
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
        title = { Text(stringResource(R.string.second_factor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.second_factor_intro))
                if (challenge.button) {
                    Text(stringResource(R.string.second_factor_button))
                }
                challenge.dtmfCode?.let { code ->
                    // The translated sentence, with the code to dial in bold where %1$s stands.
                    val sentence = stringResource(
                        if (challenge.button) R.string.second_factor_or_dial else R.string.second_factor_dial
                    )
                    val (before, after) = sentence.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    Text(
                        buildAnnotatedString {
                            append(before)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
                                append(code)
                            }
                            append(after)
                        }
                    )
                }
                if (!challenge.button && challenge.dtmfCode == null) {
                    Text(
                        challenge.otherMethods.takeIf { it.isNotEmpty() }
                            ?.let { stringResource(R.string.second_factor_other_methods, it.joinToString()) }
                            ?: stringResource(R.string.second_factor_other)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.second_factor_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
