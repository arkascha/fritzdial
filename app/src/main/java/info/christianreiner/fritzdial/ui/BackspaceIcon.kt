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

/** Material "history" glyph (clock with a turning arrow, Apache 2.0), for the recent numbers. */
internal val HistoryIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7" +
                "s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42" +
                "C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
                "M12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "call_made" glyph (arrow up-right, Apache 2.0): a dialled call. */
internal val CallMadeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CallMade",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).addPath(
        pathData = addPathNodes("M9,5v2h6.59L4,18.59 5.41,20 17,8.41V15h2V5z"),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "call_received" glyph (arrow down-left, Apache 2.0): a received call. */
internal val CallReceivedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CallReceived",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).addPath(
        pathData = addPathNodes("M20,5.41L18.59,4 7,15.59V9H5v10h10v-2H8.41z"),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "call_missed" glyph (arrow down and back up, Apache 2.0): a missed call. */
internal val CallMissedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CallMissed",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).addPath(
        pathData = addPathNodes("M19.59,7L12,14.59 6.41,9H11V7H3v8h2v-4.59l7,7 9,-9z"),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "router" glyph (Apache 2.0): calls from the FritzBox' call list. */
internal val RouterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Router",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M20.2,5.9l0.8,-0.8C19.6,3.7 17.8,3 16,3s-3.6,0.7 -5,2.1l0.8,0.8C13,4.8 14.5,4.2 16,4.2" +
                "s3,0.6 4.2,1.7z" +
                "M19.3,6.7c-0.9,-0.9 -2.1,-1.4 -3.3,-1.4s-2.4,0.5 -3.3,1.4l0.8,0.8c0.7,-0.7 1.6,-1 2.5,-1 " +
                "0.9,0 1.8,0.3 2.5,1l0.8,-0.8z" +
                "M19,13h-2L17,9h-2v4L5,13c-1.1,0 -2,0.9 -2,2v4c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2v-4" +
                "c0,-1.1 -0.9,-2 -2,-2z" +
                "M8,18L6,18v-2h2v2zM11.5,18h-2v-2h2v2zM15,18h-2v-2h2v2z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/** Material "smartphone" glyph (Apache 2.0): calls from the phone's own call log. */
internal val SmartphoneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Smartphone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M17,1.01L7,1c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3" +
                "c0,-1.1 -0.9,-1.99 -2,-1.99zM17,19H7V5h10v14z"
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
