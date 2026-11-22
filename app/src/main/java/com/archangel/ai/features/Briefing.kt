package com.archangel.ai.features

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.archangel.ai.MainActivity
import com.archangel.ai.R
import java.util.Calendar

/** Утренний брифинг: в выбранное время приходит уведомление, нажал — Архангел зачитывает сводку. */
object BriefingScheduler {
    const val CHANNEL = "briefing"
    const val ACTION = "com.archangel.ai.action.BRIEFING"

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 7001, Intent(context, BriefingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending(context))
        }
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }
}

class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("archangel_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("briefing", false)) return
        // Сразу ставим следующий день
        BriefingScheduler.schedule(context, prefs.getInt("briefing_hour", 8), prefs.getInt("briefing_minute", 0))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(BriefingScheduler.CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(BriefingScheduler.CHANNEL, "Утренний брифинг", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
        val open = PendingIntent.getActivity(
            context, 7002,
            Intent(context, MainActivity::class.java).setAction(BriefingScheduler.ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, BriefingScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Утренний брифинг готов")
            .setContentText("Нажмите — Архангел расскажет о погоде, планах и рынке.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7003, n) }
    }
}
