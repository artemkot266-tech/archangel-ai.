package com.archangel.ai.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.archangel.ai.ArchangelApp
import com.archangel.ai.MainActivity
import com.archangel.ai.R

/**
 * Режим «умной колонки»: служба с постоянным значком в шторке.
 * Пока она работает, Архангел слушает своё имя и выполняет команды голосом,
 * даже когда приложение свёрнуто или экран выключен.
 */
class ListeningService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val app = application as ArchangelApp
            app.settings.update { it.copy(standbyBackground = false) }
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel(this)
        val app = application as ArchangelApp
        val name = app.settings.current.wakeName.ifBlank { "Архангел" }
        val open = PendingIntent.getActivity(
            this, 9001, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 9002, Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("$name слушает")
            .setContentText("Скажите «$name» и команду")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Выключить", stop)
            .build()
        try {
            ServiceCompat.startForeground(
                this, 9003, n,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // Android запрещает включать микрофон из фона — служба стартует, только когда приложение открыто
            stopSelf()
            return START_NOT_STICKY
        }
        app.assistant.backgroundActive = true
        app.assistant.refreshStandby(300)
        return START_STICKY
    }

    override fun onDestroy() {
        val app = application as ArchangelApp
        app.assistant.backgroundActive = false
        app.assistant.refreshStandby(0)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "listening"
        private const val ACTION_STOP = "com.archangel.ai.action.STOP_LISTENING"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CHANNEL) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL, "Фоновое прослушивание", NotificationManager.IMPORTANCE_LOW)
                            .apply { setShowBadge(false) }
                    )
                }
            }
        }

        /** Запускать только из открытого приложения: так требует Android для микрофона. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ListeningService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListeningService::class.java))
        }
    }
}
