package com.archangel.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Turn
import com.archangel.ai.market.Balance
import com.archangel.ai.market.Candle
import com.archangel.ai.market.Indicators
import com.archangel.ai.market.Ticker
import com.archangel.ai.market.Portfolio
import com.archangel.ai.market.AlertStore
import com.archangel.ai.market.AlertScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Раздел «Рынок»: котировки, свечи, индикаторы, ИИ-разбор и баланс биржи. */
class MarketViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app as ArchangelApp

    var symbol by mutableStateOf("BTCUSDT")
        private set
    var interval by mutableStateOf("1h")
        private set
    var candles by mutableStateOf<List<Candle>>(emptyList())
        private set
    var ticker by mutableStateOf<Ticker?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set

    var analysis by mutableStateOf("")
        private set
    var analyzing by mutableStateOf(false)
        private set

    var balances by mutableStateOf<List<Balance>>(emptyList())
        private set
    var balanceMessage by mutableStateOf("")
        private set
    var portfolio by mutableStateOf<Portfolio?>(null)
        private set

    /** Уровни поддержки/сопротивления для текущего графика. */
    val levels: List<com.archangel.ai.market.Level>
        get() = ticker?.let { t -> com.archangel.ai.market.Analytics.levels(candles, t.last) } ?: emptyList()

    /** Тренд на нескольких таймфреймах: 15м, 1ч, 4ч, 1д. */
    var trends by mutableStateOf<List<Pair<String, com.archangel.ai.market.Trend>>>(emptyList())
        private set
    var fearGreed by mutableStateOf<com.archangel.ai.market.FearGreed?>(null)
        private set
    var news by mutableStateOf<List<com.archangel.ai.market.NewsItem>>(emptyList())
        private set

    /** Раздел «Обучение»: null — закрыт, "" — список уроков, иначе id урока. */
    var academy by mutableStateOf<String?>(null)

    private fun loadExtras(sym: String) {
        viewModelScope.launch {
            val list = listOf("15m" to "15м", "1h" to "1ч", "4h" to "4ч", "1d" to "1д").mapNotNull { (iv, label) ->
                runCatching { graph.binance.klines(sym, iv, 120) }.getOrNull()?.let { label to com.archangel.ai.market.Analytics.trend(it) }
            }
            if (sym == symbol) trends = list
        }
        viewModelScope.launch { fearGreed = com.archangel.ai.market.MarketData.fearGreed() }
        viewModelScope.launch {
            val all = com.archangel.ai.market.MarketData.news()
            if (sym == symbol) news = com.archangel.ai.market.Analytics.newsFor(sym, all)
        }
    }

    // ── Автосигналы
    fun setSignals(on: Boolean) {
        graph.settings.update { it.copy(signalsEnabled = on) }
        com.archangel.ai.market.SignalScheduler.apply(graph, on)
    }

    fun toggleWatch(sym: String) {
        graph.settings.update { st ->
            val list = if (sym in st.watchlist) st.watchlist - sym else st.watchlist + sym
            st.copy(watchlist = list)
        }
    }

    private val alertStore = AlertStore(app)
    var alerts by mutableStateOf(alertStore.all())
        private set

    fun reloadAlerts() { alerts = alertStore.all() }

    fun addAlert(price: Double) {
        val now = ticker?.last ?: return
        alertStore.add(symbol, price, above = price > now)
        AlertScheduler.ensure(graph)
        reloadAlerts()
    }

    fun removeAlert(id: Long) { alertStore.remove(id); reloadAlerts() }

    private var loadJob: Job? = null
    private var analysisJob: Job? = null

    init {
        refresh()
        loadExtras(symbol)
    }

    fun select(newSymbol: String) {
        val s = newSymbol.uppercase().trim()
        if (s.isEmpty() || s == symbol) return
        symbol = s
        analysis = ""
        analysisJob?.cancel(); analyzing = false
        candles = emptyList(); ticker = null
        trends = emptyList(); news = emptyList()
        refresh()
        loadExtras(s)
    }

    fun changeInterval(newInterval: String) {
        if (newInterval == interval) return
        interval = newInterval
        analysis = ""
        refresh()
    }

    /** Полная загрузка с индикатором. */
    fun refresh() = load(showSpinner = true)

    /** Тихое автообновление раз в несколько секунд. */
    fun refreshQuietly() = load(showSpinner = false)

    private fun load(showSpinner: Boolean) {
        // Тихое обновление не мешает текущей загрузке; явная — отменяет старую (например, при смене монеты).
        if (loadJob?.isActive == true) {
            if (showSpinner) loadJob?.cancel() else return
        }
        val sym = symbol
        val iv = interval
        if (showSpinner) loading = true
        loadJob = viewModelScope.launch {
            try {
                val c = graph.binance.klines(sym, iv, 200)
                val t = graph.binance.ticker(sym)
                if (sym == symbol && iv == interval) {
                    candles = c; ticker = t; error = ""
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FriendlyException) {
                error = e.message.orEmpty()
            } catch (e: Exception) {
                error = "Не удалось загрузить данные: ${e.javaClass.simpleName}"
            } finally {
                loading = false
            }
        }
    }

    fun analyze() {
        val data = candles
        if (data.size < 60) {
            analysis = "Недостаточно данных для разбора."
            return
        }
        analysisJob?.cancel()
        analyzing = true
        analysis = ""
        analysisJob = viewModelScope.launch {
            try {
                val s = graph.settings.current
                val prompt = buildPrompt(symbol, interval, data, ticker)
                analysis = graph.brain.ask(
                    s,
                    system = "Ты — опытный технический аналитик крипторынка. Пиши по-русски, структурно и конкретно, " +
                        "с ценовыми уровнями. Без markdown-таблиц. Не выдумывай данные, которых нет во входе.",
                    turns = listOf(Turn("user", prompt)),
                    maxTokens = 1100,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: FriendlyException) {
                analysis = e.message.orEmpty()
            } catch (e: Exception) {
                analysis = "Не удалось получить разбор: ${e.javaClass.simpleName}"
            } finally {
                analyzing = false
            }
        }
    }

    private fun buildPrompt(sym: String, iv: String, c: List<Candle>, t: Ticker?): String {
        val closes = c.map { it.close }
        val last = closes.last()
        val sma20 = Indicators.sma(closes, 20).last()
        val sma50 = Indicators.sma(closes, 50).last()
        val sma200 = Indicators.sma(closes, 200).last()
        val rsi = Indicators.rsi(closes).last()
        val macd = Indicators.macd(closes)
        val sr = Indicators.supportResistance(c, 50)
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.US)
        val f = Indicators::formatPrice
        val recent = c.takeLast(40).joinToString("\n") {
            "${fmt.format(Date(it.openTime))} O=${f(it.open)} H=${f(it.high)} L=${f(it.low)} C=${f(it.close)} V=${"%.0f".format(Locale.US, it.volume)}"
        }
        return buildString {
            appendLine("Пара: $sym, таймфрейм: $iv. Данные Binance.")
            appendLine("Последняя цена: ${f(last)}")
            t?.let { appendLine("За 24 ч: ${"%.2f".format(Locale.US, it.changePct)}%, макс ${f(it.high)}, мин ${f(it.low)}") }
            appendLine("SMA20=${sma20?.let(f) ?: "—"}, SMA50=${sma50?.let(f) ?: "—"}, SMA200=${sma200?.let(f) ?: "—"}")
            appendLine("RSI14=${rsi?.let { "%.1f".format(Locale.US, it) } ?: "—"}")
            appendLine(
                "MACD=${macd.macd.last()?.let(f) ?: "—"}, сигнальная=${macd.signal.last()?.let(f) ?: "—"}, " +
                    "гистограмма=${macd.hist.last()?.let(f) ?: "—"}"
            )
            sr?.let { appendLine("Мин/макс последних 50 свечей: ${f(it.first)} / ${f(it.second)}") }
            val lv = com.archangel.ai.market.Analytics.levels(c, last)
            if (lv.isNotEmpty()) appendLine("Уровни: " + lv.joinToString("; ") { "${if (it.support) "поддержка" else "сопротивление"} ${f(it.price)} (${it.reason})" })
            if (trends.isNotEmpty()) appendLine("Тренд по таймфреймам: " + trends.joinToString(", ") { "${it.first} — ${it.second.label}" })
            fearGreed?.let { appendLine("Индекс страха и жадности: ${it.value} (${it.label})") }
            appendLine()
            appendLine("Последние 40 свечей:")
            appendLine(recent)
            appendLine()
            appendLine(
                "Сделай разбор: 1) тренд и его сила; 2) ключевые уровни поддержки и сопротивления; " +
                    "3) что говорят RSI и MACD; 4) два сценария (рост/падение) с уровнями входа, цели и стопа; " +
                    "5) главные риски. Коротко, по пунктам. Последней строкой: «Это не финансовый совет»."
            )
        }
    }

    fun loadBalances() {
        val s = graph.settings.current
        if (!s.exchangeReady) {
            balances = emptyList()
            balanceMessage = "Биржа не подключена — добавьте ключ Binance в настройках."
            return
        }
        balanceMessage = "Загружаю баланс…"
        viewModelScope.launch {
            try {
                balances = graph.binance.balances(s.binanceKey, s.binanceSecret, s.binanceTestnet)
                balanceMessage = if (balances.isEmpty()) "Баланс пуст." else ""
                portfolio = runCatching { graph.binance.portfolio(s.binanceKey, s.binanceSecret, s.binanceTestnet) }.getOrNull()
            } catch (e: FriendlyException) {
                balanceMessage = e.message.orEmpty()
            } catch (e: Exception) {
                balanceMessage = "Не удалось получить баланс: ${e.javaClass.simpleName}"
            }
        }
    }
}
