package info.christianreiner.fritzdial.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import info.christianreiner.fritzdial.MainActivity
import info.christianreiner.fritzdial.R
import info.christianreiner.fritzdial.data.SettingsRepository

/**
 * Quick settings tile "FritzDial": opens the dialer. Its subtitle names the FritzBox in use
 * (Android 10 and later).
 */
class DialerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = SettingsRepository(this).activeProfile()?.name
        }
        tile.updateTile()
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

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val number = SettingsRepository(this).lastDialledNumber
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
