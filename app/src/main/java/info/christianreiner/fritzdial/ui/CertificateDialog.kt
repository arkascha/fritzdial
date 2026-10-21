package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.tr064.CertificateInfo

/**
 * Asks whether to trust the certificate the router presented.
 * The full certificate (PEM) can be expanded and selected/copied for comparison.
 */
@Composable
fun TrustCertificateDialog(
    prompt: CertificatePrompt,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(stringResource(if (prompt.changed) R.string.cert_changed_title else R.string.cert_trust_title))
        },
        text = {
            CertificateDetails(
                info = prompt.certificate,
                intro = stringResource(if (prompt.changed) R.string.cert_changed_text else R.string.cert_trust_text),
                warning = prompt.changed,
                initiallyExpanded = false,
            )
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(stringResource(if (prompt.changed) R.string.cert_trust_new else R.string.cert_trust)) }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Read-only view of the currently trusted certificate. */
@Composable
fun ViewCertificateDialog(
    info: CertificateInfo,
    onClose: () -> Unit,
    onForget: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.connection_trusted_cert)) },
        text = { CertificateDetails(info = info, intro = null, warning = false, initiallyExpanded = true) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = onForget) {
                Text(stringResource(R.string.forget), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CertificateDetails(
    info: CertificateInfo,
    intro: String?,
    warning: Boolean,
    initiallyExpanded: Boolean,
) {
    var expanded by rememberSaveable(info.sha256) { mutableStateOf(initiallyExpanded) }
    val scroll = rememberScrollState()
    val detailsRequester = remember { BringIntoViewRequester() }

    // When the details are expanded, scroll them into view – otherwise they appear below the fold
    // of the dialog and it looks as if nothing happened.
    LaunchedEffect(expanded) {
        if (expanded && !initiallyExpanded) {
            withFrameNanos { }
            detailsRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier.verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        intro?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (info.expired) {
            Text(
                stringResource(R.string.cert_expired),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // The fingerprint is always visible: it is what the user compares before trusting.
        SelectionContainer {
            Field(stringResource(R.string.cert_sha256), info.sha256, mono = true)
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.cert_hide_details else R.string.cert_show_details))
        }

        if (expanded) {
            Column(
                modifier = Modifier.bringIntoViewRequester(detailsRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(stringResource(R.string.cert_subject), info.subject)
                        Field(
                            stringResource(R.string.cert_issuer),
                            if (info.selfSigned) stringResource(R.string.cert_self_signed, info.issuer) else info.issuer,
                        )
                        if (info.subjectAltNames.isNotEmpty()) {
                            Field(stringResource(R.string.cert_alt_names), info.subjectAltNames.joinToString("\n"))
                        }
                        Field(stringResource(R.string.cert_valid), "${info.validFrom}\n– ${info.validUntil}")
                        Field(stringResource(R.string.cert_serial), info.serialNumber, mono = true)
                        Field(stringResource(R.string.cert_key), "${info.publicKey}, ${info.signatureAlgorithm}")
                        Field(stringResource(R.string.cert_sha1), info.sha1, mono = true)
                    }
                }
                Text(
                    stringResource(R.string.cert_pem),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        info.pem,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}
