package info.christianreiner.fritzdial.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import info.christianreiner.fritzdial.MainActivity
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.BoxRecognition
import info.christianreiner.fritzdial.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick settings tile "FritzDial": opens the dialer. Its subtitle (Android 10 and later) names the
 * FritzBox reachable from the current network – looked for each time the shade is pulled down, like
 * the app does – or says that none is.
 */
class DialerTileService : TileService() {

    /** Runs the look for the box while the tile is shown. */
    private var scope: CoroutineScope? = null

    override fun onTileAdded() = rememberAdded(FritzDialTile.DIALER, true)

    override fun onTileRemoved() = rememberAdded(FritzDialTile.DIALER, false)

    override fun onStartListening() {
        super.onStartListening()
        // Shown, so it is there – also when it was added before FritzDial kept track.
        rememberAdded(FritzDialTile.DIALER, true)
        val tile = qsTile ?: return
        tile.label = getString(FritzDialTile.DIALER.label)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val settings = SettingsRepository(this)
        val profiles = settings.profiles()
        if (profiles.isEmpty()) return
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also {
            it.launch {
                val recognition = BoxRecognition.recognise(this@DialerTileService, profiles)
                // As in the app: a connection without device id still counts when its host answered.
                val box = recognition.match ?: settings.activeProfile()?.takeIf { p ->
                    p.deviceId == null && recognition.found.any { box -> box.host.equals(p.config.host, ignoreCase = true) }
                }
                qsTile?.apply {
                    subtitle = box?.name ?: getString(R.string.tile_no_box)
                    updateTile()
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        openApp(Intent(this, MainActivity::class.java))
    }
}

/**
 * Quick settings tile "Redial": opens the dialer with the number dialled last. It does not dial by
 * itself – a tap in the pulled-down shade is easily made by accident – so the green button still
 * has to be pressed. Unavailable until something was dialled.
 */
class RedialTileService : TileService() {

    override fun onTileAdded() = rememberAdded(FritzDialTile.REDIAL, true)

    override fun onTileRemoved() = rememberAdded(FritzDialTile.REDIAL, false)

    override fun onStartListening() {
        super.onStartListening()
        // Shown, so it is there – also when it was added before FritzDial kept track.
        rememberAdded(FritzDialTile.REDIAL, true)
        val tile = qsTile ?: return
        val number = SettingsRepository(this).lastDialledNumber
        tile.label = getString(FritzDialTile.REDIAL.label)
        tile.state = if (number == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = number ?: getString(R.string.tile_redial_none)
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val number = SettingsRepository(this).lastDialledNumber ?: return
        // A tel: link, as from Contacts: the dialer takes the number over.
        openApp(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null), this, MainActivity::class.java))
    }

    companion object {
        /** Lets the tile show the new number the next time the shade is pulled down. */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, RedialTileService::class.java))
            }
        }
    }
}

/** The tiles FritzDial offers, for adding them from the settings. */
enum class FritzDialTile(val service: Class<out TileService>, val label: Int, val icon: Int) {
    DIALER(DialerTileService::class.java, R.string.app_name, R.drawable.ic_tile_dialer),
    REDIAL(RedialTileService::class.java, R.string.tile_redial, R.drawable.ic_tile_redial),
}

/*
 * Each tile sets its label itself whenever shown: Android's system UI keeps the label it read from
 * the manifest, and does not always read it again after an update (or a language change).
 */

/** Whether [tile] is in the quick settings, as far as its service learned it (Android cannot be asked). */
fun isTileAdded(context: Context, tile: FritzDialTile): Boolean = SettingsRepository(context).isTileAdded(tile.name)

private fun TileService.rememberAdded(tile: FritzDialTile, added: Boolean) =
    SettingsRepository(this).setTileAdded(tile.name, added)

/** How a request to add a tile ended. */
enum class TileAddResult { ADDED, ALREADY_ADDED, NOT_ADDED, FAILED }

/** Whether this Android lets an app offer to add its tile (Android 13 and later). */
@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val canRequestAddTile: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Asks Android to add [tile] to the quick settings: Android shows its own dialog, and [onResult]
 * gets the outcome. Only on Android 13 and later (see [canRequestAddTile]).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun requestAddTile(context: Context, tile: FritzDialTile, onResult: (TileAddResult) -> Unit) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return onResult(TileAddResult.FAILED)
    runCatching {
        statusBar.requestAddTileService(
            ComponentName(context, tile.service),
            context.getString(tile.label),
            Icon.createWithResource(context, tile.icon),
            context.mainExecutor,
        ) { code ->
            if (code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                code == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                SettingsRepository(context).setTileAdded(tile.name, true)
            }
            onResult(
                when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddResult.ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileAddResult.ALREADY_ADDED
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileAddResult.NOT_ADDED
                    // Errors (e.g. a request already showing, or no status bar to add to).
                    else -> TileAddResult.FAILED
                }
            )
        }
    }.onFailure { onResult(TileAddResult.FAILED) }
}

/** Opens FritzDial from a tile, collapsing the shade; on a locked phone only once it is unlocked. */
private fun TileService.openApp(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val start = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
    if (isLocked) unlockAndRun(start) else start()
}
