package com.appathy.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf

data class NotifItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val clearable: Boolean
)

class LauncherNotificationService : NotificationListenerService() {

    companion object {
        val items = mutableStateListOf<NotifItem>()
        private var instance: LauncherNotificationService? = null

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun dismissAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )
            }.getOrNull() ?: return false
            return enabled.contains(context.packageName)
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun labelOf(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

    private fun refresh() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val mapped = active.mapNotNull { sbn ->
            val extras = sbn.notification?.extras ?: return@mapNotNull null
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return@mapNotNull null
            NotifItem(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = labelOf(sbn.packageName),
                title = title,
                text = text,
                clearable = sbn.isClearable
            )
        }
        items.clear()
        items.addAll(mapped)
    }
}

data class NowPlaying(
    val title: String,
    val artist: String,
    val appLabel: String,
    val isPlaying: Boolean
)

object MediaInfo {

    fun current(context: Context): NowPlaying? {
        if (!LauncherNotificationService.isEnabled(context)) return null
        return runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(context, LauncherNotificationService::class.java)
            val sessions: List<MediaController> = manager.getActiveSessions(component)
            val controller = sessions.firstOrNull() ?: return null
            val metadata = controller.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                .orEmpty()
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                .orEmpty()
            if (title.isBlank() && artist.isBlank()) return null
            val label = runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString()
            }.getOrDefault(controller.packageName)
            val playing = controller.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
            NowPlaying(title, artist, label, playing)
        }.getOrNull()
    }
}
