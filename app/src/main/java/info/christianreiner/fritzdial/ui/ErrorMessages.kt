package info.christianreiner.fritzdial.ui

import info.christianreiner.fritzdial.R
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
    val title: UiText,
    val detail: UiText,
    val settingsRelated: Boolean,
)

/** Turns technical exceptions into messages that say what is wrong and what to check. */
fun describeError(e: Throwable, config: FritzConfig): BoxError {
    val target = "${config.host}:${config.port}"
    return when (e) {
        is AuthenticationException -> BoxError(
            uiText(R.string.error_login_title),
            uiText(
                R.string.error_login,
                config.username.ifEmpty { null } ?: uiText(R.string.error_empty_user),
            ),
            settingsRelated = true,
        )
        is OtherBoxException -> BoxError(
            uiText(R.string.error_other_box_title),
            uiText(R.string.error_other_box, e.host, e.boxName),
            settingsRelated = true,
        )
        is SecondFactorNotConfirmedException -> BoxError(
            uiText(R.string.error_not_confirmed_title),
            uiText(R.string.error_not_confirmed),
            settingsRelated = false,
        )
        is Tr064Exception -> when (e.errorCode) {
            Tr064Exception.SECOND_FACTOR_BLOCKED -> BoxError(
                uiText(R.string.error_confirmation_blocked_title),
                uiText(R.string.error_confirmation_blocked),
                settingsRelated = false,
            )
            Tr064Exception.SECOND_FACTOR_BUSY -> BoxError(
                uiText(R.string.error_confirmation_busy_title),
                uiText(R.string.error_confirmation_busy),
                settingsRelated = false,
            )
            606 -> BoxError(
                uiText(R.string.error_not_authorized_title),
                uiText(R.string.error_not_authorized, config.username),
                settingsRelated = true,
            )
            401 -> BoxError(
                uiText(R.string.error_no_dial_help_title),
                uiText(R.string.error_no_dial_help),
                settingsRelated = false,
            )
            else -> BoxError(
                e.errorCode?.let { uiText(R.string.error_router_code_title, it.toString()) }
                    ?: uiText(R.string.error_router_title),
                // The box' own description (English, from the TR-064 fault).
                UiText.Raw(e.errorDescription ?: e.message.orEmpty()),
                settingsRelated = false,
            )
        }
        is HttpStatusException -> if (e.code == 503) BoxError(
            uiText(R.string.error_router_busy_title),
            uiText(R.string.error_router_busy),
            settingsRelated = false,
        ) else BoxError(
            uiText(R.string.error_unexpected_title),
            uiText(R.string.error_unexpected, target, e.code),
            settingsRelated = true,
        )
        is UnknownHostException -> BoxError(
            uiText(R.string.error_not_found_title),
            uiText(R.string.error_not_found, config.host),
            settingsRelated = true,
        )
        is ConnectException, is NoRouteToHostException -> BoxError(
            uiText(R.string.error_cannot_connect_title),
            uiText(R.string.error_cannot_connect, target, FritzConfig.HTTP_PORT, FritzConfig.HTTPS_PORT),
            settingsRelated = true,
        )
        is SocketTimeoutException -> BoxError(
            uiText(R.string.error_no_answer_title),
            uiText(R.string.error_no_answer, target),
            settingsRelated = true,
        )
        is SSLException -> BoxError(
            uiText(R.string.error_ssl_title),
            if (config.useHttps) uiText(R.string.error_ssl_https, target, FritzConfig.HTTPS_PORT, e.message.orEmpty())
            else UiText.Raw(e.message.orEmpty()),
            settingsRelated = true,
        )
        else -> {
            val message = e.message ?: e.javaClass.simpleName
            BoxError(
                uiText(R.string.error_connection_title),
                if (!config.useHttps && config.port == FritzConfig.HTTPS_PORT) {
                    uiText(R.string.error_connection_https_port, message, FritzConfig.HTTPS_PORT)
                } else {
                    UiText.Raw(message)
                },
                settingsRelated = true,
            )
        }
    }
}
