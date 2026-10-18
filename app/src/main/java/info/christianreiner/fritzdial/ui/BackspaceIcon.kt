package info.christianreiner.fritzdial.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material "backspace" glyph (⌫, Apache 2.0), defined here so the large
 * material-icons-extended library is not needed for a single icon.
 */
internal val BackspaceIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Backspace",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).addPath(
        pathData = addPathNodes(
            "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15" +
                "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12 9,8.41 10.41,7 14,10.59 17.59,7 " +
                "19,8.41 15.41,12 19,15.59z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "contacts" glyph (address book card, Apache 2.0), for the phone book button. */
internal val ContactsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Contacts",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M20,0H4v2h16V0zM4,24h16v-2H4v2z" +
                "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2z" +
                "M12,6.75c1.24,0 2.25,1.01 2.25,2.25s-1.01,2.25 -2.25,2.25S9.75,10.24 9.75,9 10.76,6.75 12,6.75z" +
                "M17,17H7v-1.5c0,-1.67 3.33,-2.5 5,-2.5s5,0.83 5,2.5V17z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
