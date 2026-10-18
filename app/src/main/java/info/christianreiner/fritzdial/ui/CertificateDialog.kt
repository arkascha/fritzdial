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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Text(if (prompt.changed) "Certificate has changed!" else "Trust this certificate?")
        },
        text = {
            CertificateDetails(
                info = prompt.certificate,
                intro = if (prompt.changed) {
                    "The router presents a different certificate than the one you trusted before. " +
                        "This happens after a factory reset or a changed box name – but it could also " +
                        "mean someone is intercepting the connection. Only continue if you can explain it."
                } else {
                    "The router uses a self-signed certificate that cannot be verified automatically. " +
                        "Compare the SHA-256 fingerprint with the one your browser shows for " +
                        "https://fritz.box (or the certificate in the FRITZ!Box web UI) before trusting it."
                },
                warning = prompt.changed,
                initiallyExpanded = false,
            )
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text(if (prompt.changed) "Trust new certificate" else "Trust") }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Cancel") }
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
        title = { Text("Trusted certificate") },
        text = { CertificateDetails(info = info, intro = null, warning = false, initiallyExpanded = true) },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = onForget) {
                Text("Forget", color = MaterialTheme.colorScheme.error)
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
                "This certificate is outside its validity period.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // The fingerprint is always visible: it is what the user compares before trusting.
        SelectionContainer {
            Field("SHA-256 fingerprint", info.sha256, mono = true)
        }

        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide certificate details" else "Show certificate details")
        }

        if (expanded) {
            Column(
                modifier = Modifier.bringIntoViewRequester(detailsRequester),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Subject", info.subject)
                        Field("Issuer", if (info.selfSigned) "${info.issuer} (self-signed)" else info.issuer)
                        if (info.subjectAltNames.isNotEmpty()) {
                            Field("Alternative names", info.subjectAltNames.joinToString("\n"))
                        }
                        Field("Valid", "${info.validFrom}\n– ${info.validUntil}")
                        Field("Serial number", info.serialNumber, mono = true)
                        Field("Key / signature", "${info.publicKey}, ${info.signatureAlgorithm}")
                        Field("SHA-1 fingerprint", info.sha1, mono = true)
                    }
                }
                Text(
                    "Certificate (PEM)",
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
