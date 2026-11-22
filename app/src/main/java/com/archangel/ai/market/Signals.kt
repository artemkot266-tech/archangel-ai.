package com.archangel.ai.market

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.archangel.ai.ArchangelApp
import java.util.concurrent.TimeUnit

/**
 * Автосигналы: раз в час Архангел смотрит часовые свечи монет из списка
 * и пишет, только если случилось что-то важное. Сделок не совершает.
 */
object SignalScheduler {
    private const val WORK = "market_signals"

    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<SignalWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class SignalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ArchangelApp
        val s = app.settings.current
        if (!s.signalsEnabled) return Result.success()
        val seen = applicationContext.getSharedPreferences("archangel_signals", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (symbol in s.watchlist.take(10)) {
            val candles = runCatching { app.binance.klines(symbol, "1h", 120) }.getOrNull() ?: continue
            val closed = candles.dropLast(1) // последняя свеча ещё не закрыта
            for (sig in Analytics.detectSignals(symbol, closed)) {
                // Один и тот же тип сигнала по монете — не чаще раза в 12 часов
                val k = "${sig.symbol}_${sig.key}"
                if (now - seen.getLong(k, 0) < 12 * 3_600_000L) continue
                seen.edit().putLong(k, now).apply()
                AlertScheduler.notify(applicationContext, k.hashCode(), sig.title, sig.text)
            }
        }
        return Result.success()
    }
}
