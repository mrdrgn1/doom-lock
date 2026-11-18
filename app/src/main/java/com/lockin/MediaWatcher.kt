package com.lockin

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads whichever music app is playing so the lock screen can show the track and skip it.
 *
 * This is the one thing that needs Android's "notification access": music apps publish what
 * they are playing inside their notification, and this service is what Android lets an app
 * read. Nothing else in the notification list is kept or stored.
 */
class MediaWatcher : NotificationListenerService() {

    companion object {
        @Volatile
        private var controller: MediaController? = null

        @Volatile
        var title: String? = null
            private set

        @Volatile
        var artist: String? = null
            private set

        /** Set by the lock screen while it is visible; cleared when it leaves. */
        var listener: (() -> Unit)? = null

        private val main = Handler(Looper.getMainLooper())

        /** True once "notification access" has been switched on for Lock In. */
        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val mine = ComponentName(context, MediaWatcher::class.java)
            return enabled.split(':').any { entry ->
                val component = ComponentName.unflattenFromString(entry)
                component != null &&
                    component.packageName == mine.packageName &&
                    component.className == mine.className
            }
        }

        fun skipNext() {
            runCatching { controller?.transportControls?.skipToNext() }
        }

        /** Re-reads the active music session, e.g. when the lock screen appears. */
        fun refresh(context: Context) {
            if (!hasAccess(context)) {
                clear()
                notifyChange()
                return
            }
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            if (manager == null) {
                clear()
                notifyChange()
                return
            }
            val component = ComponentName(context, MediaWatcher::class.java)
            val sessions = runCatching { manager.getActiveSessions(component) }
                .getOrNull()
                .orEmpty()
            val active = sessions.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            if (active == null) {
                clear()
            } else {
                val metadata = active.metadata
                controller = active
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            }
            notifyChange()
        }

        private fun clear() {
            controller = null
            title = null
            artist = null
        }

        private fun notifyChange() {
            val callback = listener ?: return
            main.post { callback() }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refresh(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh(this)
    }
}
