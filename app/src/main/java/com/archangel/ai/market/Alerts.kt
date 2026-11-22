package com.archangel.ai.market

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.archangel.ai.MainActivity
import com.archangel.ai.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** «Сообщи, когда биткоин будет выше 100 000». above — ждём роста (true) или падения (false). */
data class PriceAlertItem(val id: Long, val symbol: String, val price: Double, val above: Boolean)

class AlertStore(context: Context) {
    private val file = File(context.filesDir, "price_alerts.json")

    fun all(): List<PriceAlertItem> = try {
        if (!file.exists()) emptyList() else JSONArray(file.readText()).let { a ->
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                PriceAlertItem(o.getLong("id"), o.getString("symbol"), o.getDouble("price"), o.getBoolean("above"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(symbol: String, price: Double, above: Boolean): PriceAlertItem {
        val item = PriceAlertItem(System.currentTimeMillis(), symbol, price, above)
        save(all() + item)
        return item
    }

    fun remove(id: Long) = save(all().filter { it.id != id })

    private fun save(list: List<PriceAlertItem>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("symbol", it.symbol).put("price", it.price).put("above", it.above)) }
        runCatching { file.writeText(a.toString()) }
    }
}

object AlertScheduler {
    const val CHANNEL = "price_alerts"
    private const val WORK = "price_alerts"

    /** Проверка цен раз в 15 минут (чаще Android фоновые задачи не запускает). */
    fun ensure(context: Context) {
        val req = PeriodicWorkRequestBuilder<PriceAlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Цены", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).setAction("com.archangel.ai.action.MARKET").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

/** Фоновая проверка цен: сработавшее уведомление показывается и удаляется. */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = AlertStore(applicationContext)
        val alerts = store.all()
        if (alerts.isEmpty()) {
            AlertScheduler.cancel(applicationContext)
            return Result.success()
        }
        val client = BinanceClient()
        for (symbol in alerts.map { it.symbol }.distinct()) {
            val price = runCatching { client.ticker(symbol).last }.getOrNull() ?: continue
            alerts.filter { it.symbol == symbol }.forEach { a ->
                val hit = if (a.above) price >= a.price else price <= a.price
                if (hit) {
                    val coin = symbol.removeSuffix("USDT")
                    AlertScheduler.notify(
                        applicationContext, a.id.toInt(),
                        "$coin ${if (a.above) "выше" else "ниже"} ${Indicators.formatPrice(a.price)}",
                        "Сейчас ${Indicators.formatPrice(price)} USDT",
                    )
                    store.remove(a.id)
                }
            }
        }
        return Result.success()
    }
}
