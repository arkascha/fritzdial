package info.christianreiner.fritzdial.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.AppLanguage
import java.util.Locale

/**
 * The app's language: the system's ("Auto"), or one of the languages FritzDial is translated into,
 * each with its national flag. Choosing one goes back and redraws the app in it (see [AppLanguage]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // The current choice (null = Auto).
    val current = remember { AppLanguage.current(context) }
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.settings_language_system)) +
        AppLanguage.supported
    // "Auto" leads to the system language, so it gets that one's flag.
    val systemLocale = Resources.getSystem().configuration.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.selectableGroup()) {
                options.forEach { (tag, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { RadioButton(selected = tag == current, onClick = null) },
                        // At the right edge, so the flags line up whatever the length of the names.
                        trailingContent = {
                            Text(
                                AppLanguage.flag(tag?.let(Locale::forLanguageTag) ?: systemLocale),
                                fontSize = 22.sp,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = tag == current, role = Role.RadioButton) {
                                // Back to the settings first, so they are what gets redrawn in the new language.
                                onBack()
                                if (tag != current) context.findActivity()?.let { AppLanguage.set(it, tag) }
                            },
                    )
                }
            }
        }
    }
}

/** The activity behind a (possibly wrapped) context. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
