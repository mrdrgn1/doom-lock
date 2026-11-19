package com.lockin

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Pull down the shade, tap "Lock In": the session starts (DND on, session saved) and the focus
 * screen opens. Tapping again while a session is running just reopens whichever screen fits.
 */
class LockInTileService : TileService() {

    companion object {
        private val TARGETS = longArrayOf(
            30L * 60_000L, 60L * 60_000L, 90L * 60_000L,
            120L * 60_000L, 180L * 60_000L, 0L
        )

        /** Asks the system to re-read the tile, so the active state stays in sync. */
        fun refresh(context: Context) {
            runCatching {
                TileService.requestListeningState(
                    context, ComponentName(context, LockInTileService::class.java)
                )
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        SessionStore.load(this)
        val tile = qsTile ?: return
        tile.state = if (SessionStore.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Lock In"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        SessionStore.load(this)
        if (SessionStore.isActive) {
            open(
                if (SessionStore.phase == Phase.PAUSED) {
                    PauseActivity::class.java
                } else {
                    LockActivity::class.java
                }
            )
            return
        }
        val index = getSharedPreferences("lockin_settings", Context.MODE_PRIVATE)
            .getInt("target_index", 3)
            .coerceIn(0, TARGETS.lastIndex)
        SessionStore.start(this, System.currentTimeMillis(), TARGETS[index])
        Dnd.alarmsOnly(this)
        open(LockActivity::class.java)
    }

    private fun open(activity: Class<*>) {
        val intent = Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 51, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
