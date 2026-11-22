package com.archangel.ai.phone

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.archangel.ai.R

/**
 * Открытие приложений, когда Архангел работает в фоне.
 *
 * Android 10+ запрещает фоновым приложениям открывать окна, а Android 15+ разрешает это
 * приложению с «Поверх других окон», только если его окно сейчас видно. Поэтому на долю секунды
 * показываем невидимое окошко 1×1 и из-под него открываем нужное приложение.
 * Если разрешения нет — присылаем уведомление: нажатие на него всегда открывает приложение.
 */
object BackgroundLauncher {
    private const val CHANNEL = "open_from_background"
    private val main = Handler(Looper.getMainLooper())

    enum class Way { OVERLAY, NOTIFICATION, FAILED }

    fun canOverlay(context: Context) = Settings.canDrawOverlays(context)

    fun launch(context: Context, intent: Intent, label: String): Way {
        val app = context.applicationContext
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canOverlay(app)) {
            val ok = runCatching { viaOverlay(app, intent) }.isSuccess
            // Запасной вариант: тихое уведомление, само исчезнет через 15 секунд
            notify(app, intent, label, quiet = true)
            return if (ok) Way.OVERLAY else Way.NOTIFICATION
        }
        return if (notify(app, intent, label, quiet = false)) Way.NOTIFICATION else Way.FAILED
    }

    private fun viaOverlay(context: Context, intent: Intent) {
        val wm = context.getSystemService(WindowManager::class.java)
        val view = View(context)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(view, params)
        // Даём окну появиться, открываем приложение, потом убираем окошко
        main.postDelayed({
            runCatching { context.startActivity(intent) }
            main.postDelayed({ runCatching { wm.removeView(view) } }, 1_200)
        }, 120)
    }

    private fun notify(context: Context, intent: Intent, label: String, quiet: Boolean): Boolean {
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Открыть из фона", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            })
        }
        val pi = PendingIntent.getActivity(context, 7401, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(label)
            .setContentText("Нажмите, чтобы открыть")
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .setContentIntent(pi)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(7402, n) }.isSuccess
    }
}
