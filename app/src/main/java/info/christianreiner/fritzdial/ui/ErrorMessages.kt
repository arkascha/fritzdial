package info.christianreiner.fritzdial.ui

import info.christianreiner.fritzdial.tr064.AuthenticationException
import info.christianreiner.fritzdial.tr064.FritzConfig
import info.christianreiner.fritzdial.tr064.HttpStatusException
import info.christianreiner.fritzdial.tr064.Tr064Exception
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * An error shown to the user.
 * [settingsRelated] is true when fixing it most likely means changing the router settings
 * (host, port, HTTPS, user, password) – the UI then offers a shortcut to the settings.
 */
data class BoxError(
    val title: String,
    val detail: String,
    val settingsRelated: Boolean,
)

/** Turns technical exceptions into messages that say what is wrong and what to check. */
fun describeError(e: Throwable, config: FritzConfig): BoxError {
    val target = "${config.host}:${config.port}"
    return when (e) {
        is AuthenticationException -> BoxError(
            "Login failed",
            "The router rejected user name “${config.username.ifEmpty { "(empty)" }}” " +
                "or the password. If you log in to the box with a password only, use the " +
                "auto-generated user name shown under System › FRITZ!Box Users.",
            settingsRelated = true,
        )
        is SecondFactorNotConfirmedException -> BoxError(
            "Not confirmed",
            "The FRITZ!Box did not receive the confirmation in time. Tap Retry and confirm on the box " +
                "(press its button, or dial the shown code on a connected phone).",
            settingsRelated = false,
        )
        is Tr064Exception -> when (e.errorCode) {
            Tr064Exception.SECOND_FACTOR_BLOCKED -> BoxError(
                "Confirmation blocked",
                "The FRITZ!Box currently blocks confirmations (too many attempts). Wait a few minutes and try again.",
                settingsRelated = false,
            )
            Tr064Exception.SECOND_FACTOR_BUSY -> BoxError(
                "Confirmation busy",
                "Another confirmation is already running on the FRITZ!Box. Finish or wait for it, then retry.",
                settingsRelated = false,
            )
            606 -> BoxError(
                "Not authorized",
                "User “${config.username}” is logged in but may not use dial help. " +
                    "Grant it the telephony permission under System › FRITZ!Box Users.",
                settingsRelated = true,
            )
            401 -> BoxError(
                "Dial help not available",
                "The router does not offer this function via TR-064 (outdated firmware?).",
                settingsRelated = false,
            )
            else -> BoxError(
                "Router error ${e.errorCode ?: ""}".trim(),
                e.errorDescription ?: e.message.orEmpty(),
                settingsRelated = false,
            )
        }
        is HttpStatusException -> if (e.code == 503) BoxError(
            "Router busy",
            "The router temporarily refuses requests – after failed logins it blocks further " +
                "attempts for a while. Wait a minute and try again.",
            settingsRelated = false,
        ) else BoxError(
            "Unexpected answer",
            "The router at $target answered with HTTP ${e.code}. Is this really the TR-064 port?",
            settingsRelated = true,
        )
        is UnknownHostException -> BoxError(
            "Router not found",
            "The host name “${config.host}” cannot be resolved. Check the host name and that " +
                "this phone is in your home network (Wi-Fi or VPN).",
            settingsRelated = true,
        )
        is ConnectException, is NoRouteToHostException -> BoxError(
            "Cannot connect",
            "Nothing answers at $target. Check host and port " +
                "(${FritzConfig.HTTP_PORT} for HTTP, ${FritzConfig.HTTPS_PORT} for HTTPS) and that " +
                "“Allow access for applications” is enabled on the box.",
            settingsRelated = true,
        )
        is SocketTimeoutException -> BoxError(
            "No answer",
            "The router at $target did not answer in time. Check host and port, and that this " +
                "phone is in your home network.",
            settingsRelated = true,
        )
        is SSLException -> BoxError(
            "Secure connection failed",
            (if (config.useHttps) "HTTPS to $target failed. Does the box use HTTPS on this port " +
                "(usually ${FritzConfig.HTTPS_PORT})? " else "") + (e.message ?: ""),
            settingsRelated = true,
        )
        else -> BoxError(
            "Connection problem",
            (e.message ?: e.javaClass.simpleName) +
                if (!config.useHttps && config.port == FritzConfig.HTTPS_PORT)
                    " – port ${FritzConfig.HTTPS_PORT} expects HTTPS." else "",
            settingsRelated = true,
        )
    }
}
