package info.christianreiner.fritzdial.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Password input with
 *  - an "eye" button to show / hide the password, and
 *  - the character just typed staying visible for [revealMillis] before it turns into a [mask].
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mask: Char = '\u2022', // bullet: vertically centred, unlike '*' which sits at cap height
    revealMillis: Long = 1000,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    // Index of the character to show in clear text, or -1. [revealCount] restarts the timer
    // for every keystroke, even when the index stays the same.
    var revealIndex by remember { mutableIntStateOf(-1) }
    var revealCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(revealCount) {
        if (revealIndex >= 0) {
            delay(revealMillis)
            revealIndex = -1
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            revealIndex = insertedCharIndex(value, new)
            if (revealIndex >= 0) revealCount++
            onValueChange(new)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        visualTransformation = if (visible) VisualTransformation.None
        else RevealOneTransformation(revealIndex, mask),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) VisibilityOffIcon else VisibilityIcon,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
    )
}

/**
 * If [new] is [old] with exactly one character inserted, returns that character's index, else -1.
 * Pasting, deleting or replacing text reveals nothing.
 */
internal fun insertedCharIndex(old: String, new: String): Int {
    if (new.length != old.length + 1) return -1
    var i = 0
    while (i < old.length && old[i] == new[i]) i++
    return if (old.substring(i) == new.substring(i + 1)) i else -1
}

/** Masks every character except the one at [revealIndex]. Length is unchanged, so offsets map 1:1. */
private class RevealOneTransformation(
    private val revealIndex: Int,
    private val mask: Char,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = buildString(text.length) {
            text.text.forEachIndexed { i, c -> append(if (i == revealIndex) c else mask) }
        }
        return TransformedText(AnnotatedString(shown), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) =
        other is RevealOneTransformation && other.revealIndex == revealIndex && other.mask == mask

    override fun hashCode() = revealIndex * 31 + mask.code
}

// Material "visibility" / "visibility_off" glyphs (Apache 2.0), defined here so the large
// material-icons-extended library is not needed for two icons.
private val VisibilityIcon: ImageVector by lazy {
    icon(
        "Visibility",
        "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5z" +
            "M12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z" +
            "m0,-8c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
    )
}

private val VisibilityOffIcon: ImageVector by lazy {
    icon(
        "VisibilityOff",
        "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75" +
            "c-1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7z" +
            "M2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 " +
            "4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27z" +
            "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 " +
            "0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z" +
            "m4.31,-0.78l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z",
    )
}

private fun icon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
