package com.archangel.ai.features

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

data class IncomingMessage(val app: String, val title: String, val text: String, val time: Long)

/**
 * Читает уведомления мессенджеров, чтобы ответить на «что мне написали?».
 * Хранит последние сообщения только в памяти телефона и только пока приложение живо — никуда не отправляет.
 */
class NotificationWatcher : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MESSENGERS) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        synchronized(recent) {
            recent.removeAll { it.title == title && it.text == text }
            recent.add(IncomingMessage(appName, title, text, sbn.postTime))
            while (recent.size > 40) recent.removeAt(0)
        }
    }

    companion object {
        private val recent = ArrayList<IncomingMessage>()

        val MESSENGERS = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
            "com.whatsapp", "com.whatsapp.w4b", "com.viber.voip", "com.instagram.android",
            "com.facebook.orca", "com.google.android.apps.messaging", "com.samsung.android.messaging",
            "com.android.mms", "com.discord", "com.vkontakte.android", "com.skype.raider", "com.slack",
        )

        fun isEnabled(context: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun latest(maxAgeMs: Long = 12 * 60 * 60 * 1000L): List<IncomingMessage> = synchronized(recent) {
            recent.filter { System.currentTimeMillis() - it.time < maxAgeMs }.sortedByDescending { it.time }
        }
    }
}
