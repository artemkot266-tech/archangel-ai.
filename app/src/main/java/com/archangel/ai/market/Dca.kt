package com.archangel.ai.market

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.archangel.ai.MainActivity
import com.archangel.ai.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * План регулярной покупки (DCA). В назначенное время приходит уведомление,
 * покупка совершается только после подтверждения в приложении.
 * weekday — Calendar.DAY_OF_WEEK, null — каждый день.
 */
data class DcaPlan(val id: Long, val symbol: String, val usdt: Double, val weekday: Int?, val hour: Int = 10, val minute: Int = 0) {
    val coin get() = symbol.removeSuffix("USDT")
    val schedule get() = (if (weekday == null) "каждый день" else "каждый " + weekdayName(weekday)) +
        " в %02d:%02d".format(hour, minute)

    companion object {
        fun weekdayName(d: Int) = when (d) {
            Calendar.MONDAY -> "понедельник"; Calendar.TUESDAY -> "вторник"; Calendar.WEDNESDAY -> "среду"
            Calendar.THURSDAY -> "четверг"; Calendar.FRIDAY -> "пятницу"; Calendar.SATURDAY -> "субботу"
            else -> "воскресенье"
        }

        /** Следующий момент запуска после now. */
        fun nextRun(plan: DcaPlan, now: Calendar = Calendar.getInstance()): Long {
            val c = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, plan.hour); set(Calendar.MINUTE, plan.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (!c.after(now)) c.add(Calendar.DAY_OF_YEAR, 1)
            if (plan.weekday != null) while (c.get(Calendar.DAY_OF_WEEK) != plan.weekday) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
    }
}

class DcaStore(context: Context) {
    private val file = File(context.filesDir, "dca.json")

    fun all(): List<DcaPlan> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a ->
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                DcaPlan(
                    o.getLong("id"), o.getString("symbol"), o.getDouble("usdt"),
                    if (o.has("weekday")) o.getInt("weekday") else null, o.optInt("hour", 10), o.optInt("minute", 0),
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(p: DcaPlan) = save(all() + p)
    fun remove(id: Long) = save(all().filter { it.id != id })
    fun byId(id: Long) = all().firstOrNull { it.id == id }

    private fun save(list: List<DcaPlan>) {
        val a = JSONArray()
        list.forEach { p ->
            a.put(JSONObject().put("id", p.id).put("symbol", p.symbol).put("usdt", p.usdt)
                .put("hour", p.hour).put("minute", p.minute).apply { p.weekday?.let { put("weekday", it) } })
        }
        runCatching { file.writeText(a.toString()) }
    }
}

object DcaScheduler {
    private const val CHANNEL = "dca"
    const val ACTION = "com.archangel.ai.action.DCA"

    /** План, который пользователь открыл из уведомления (обрабатывает интерфейс). */
    @Volatile var pendingPlan: Long? = null

    private fun pi(context: Context, id: Long) = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(),
        Intent(context, DcaReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(context: Context, plan: DcaPlan) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Неточный будильник: для покупки по плану плюс-минус несколько минут не важны
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, DcaPlan.nextRun(plan), pi(context, plan.id)) }
    }

    fun cancel(context: Context, id: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(context, id))
    }

    fun rescheduleAll(context: Context) = DcaStore(context).all().forEach { schedule(context, it) }

    fun notify(context: Context, plan: DcaPlan) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Покупки по плану", android.app.NotificationManager.IMPORTANCE_HIGH))
            }
        }
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context, (plan.id % Int.MAX_VALUE).toInt(),
            Intent(context, MainActivity::class.java).setAction(ACTION).putExtra("plan_id", plan.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Покупка по плану: ${plan.coin} на ${Indicators.formatPrice(plan.usdt)} USDT")
            .setContentText("Нажмите, чтобы подтвердить покупку")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((plan.id % Int.MAX_VALUE).toInt(), n) }
    }
}

class DcaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val plan = DcaStore(context).byId(intent.getLongExtra("id", -1)) ?: return
        DcaScheduler.notify(context, plan)
        DcaScheduler.schedule(context, plan) // следующий раз
    }
}
