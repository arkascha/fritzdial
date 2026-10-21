package info.christianreiner.fritzdial.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Text the ViewModel hands to the UI (status, errors, notes). It is only turned into a string when
 * shown, in the language in use then – so switching the language also changes texts already on screen.
 * Arguments may be [UiText] themselves (e.g. "your phone" inside "Pick up %1$s …").
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
    /** Text that is not ours to translate: from the router, the system or an exception. */
    data class Raw(val text: String) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Res -> resources.getString(id, *args.map { it.resolveArg(resources) }.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, *args.map { it.resolveArg(resources) }.toTypedArray())
        is Raw -> text
    }

    private fun Any.resolveArg(resources: Resources): Any = if (this is UiText) resolve(resources) else this
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    // Read like stringResource() does, so a configuration (language) change is noticed.
    LocalConfiguration.current
    return resolve(LocalContext.current.resources)
}
