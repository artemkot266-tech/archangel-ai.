package com.archangel.ai.features

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.archangel.ai.R

/**
 * «Где телефон?» — громкий сигнал будильника на полную громкость, вибрация
 * и уведомление с кнопкой «Нашёл». Сам выключается через [DURATION_MS].
 * Звучит через поток будильника, поэтому слышно даже в беззвучном режиме.
 */
object PhoneFinder {
    const val DURATION_MS = 30_000L
    private const val CHANNEL = "find_phone"
    private const val NOTIFY_ID = 7301

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var savedVolume: Int = -1
    private val autoStop = Runnable { appContext?.let { stop(it) } }
    private var appContext: Context? = null

    val isRinging: Boolean get() = player != null

    /** Включает сигнал. Возвращает false, если звук не удалось запустить. */
    fun start(context: Context): Boolean {
        val ctx = context.applicationContext
        appContext = ctx
        stop(ctx)
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val mp = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        player = mp
        vibrate(ctx, true)
        showNotification(ctx)
        main.postDelayed(autoStop, DURATION_MS)
        if (mp == null) {
            // Звук не запустился — хотя бы вибрация и уведомление.
            return false
        }
        return true
    }

    /** Выключает сигнал и возвращает громкость будильника как было. */
    fun stop(context: Context) {
        val ctx = context.applicationContext
        main.removeCallbacks(autoStop)
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
        vibrate(ctx, false)
        if (savedVolume >= 0) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }
            savedVolume = -1
        }
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID) }
    }

    private fun vibrator(ctx: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }.getOrNull()

    private fun vibrate(ctx: Context, on: Boolean) {
        val v = vibrator(ctx) ?: return
        runCatching {
            if (on) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
            else v.cancel()
        }
    }

    private fun showNotification(ctx: Context) = runCatching {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Поиск телефона", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Кнопка «Нашёл» для сигнала «Где телефон?»"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val stopIntent = PendingIntent.getBroadcast(
            ctx, NOTIFY_ID, Intent(ctx, PhoneFinderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Я здесь!")
            .setContentText("Нажмите, чтобы выключить сигнал")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(stopIntent)
            .setDeleteIntent(stopIntent)
            .addAction(0, "Нашёл", stopIntent)
            .build()
        nm.notify(NOTIFY_ID, n)
    }
}

/** Кнопка «Нашёл» в уведомлении. */
class PhoneFinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PhoneFinder.stop(context)
}
