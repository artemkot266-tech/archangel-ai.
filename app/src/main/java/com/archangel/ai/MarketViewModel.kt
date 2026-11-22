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

    // ── Итоги: история сделок и прибыль
    var pnl by mutableStateOf<com.archangel.ai.market.PnlReport?>(null)
        private set
    var pnlLoading by mutableStateOf(false)
        private set
    var pnlMessage by mutableStateOf("")
        private set
    /** 7 — неделя, 30 — месяц, 0 — всё время. */
    var pnlDays by mutableStateOf(30)

    fun loadPnl(days: Int = pnlDays) {
        pnlDays = days
        val s = graph.settings.current
        if (!s.exchangeReady) { pnlMessage = "Подключите биржу в настройках."; return }
        pnlLoading = true
        pnlMessage = ""
        viewModelScope.launch {
            try {
                val rep = com.archangel.ai.market.PnlLoader.load(graph.binance, s, dcaPlans.map { it.symbol }, days)
                pnl = rep
                if (rep.coins.isEmpty()) pnlMessage = "Сделок пока нет."
            } catch (e: FriendlyException) {
                pnlMessage = e.message.orEmpty()
            } catch (e: Exception) {
                pnlMessage = "Не удалось посчитать: ${e.javaClass.simpleName}"
            } finally {
                pnlLoading = false
            }
        }
    }

    // ── Песочница
    private val paperStore = com.archangel.ai.market.PaperStore(app)
    var paper by mutableStateOf(paperStore.load())
        private set
    var paperPrices by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    var paperMessage by mutableStateOf("")
        private set

    fun reloadPaper() {
        paper = paperStore.load()
        val symbols = paper.trades.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return
        viewModelScope.launch {
            paperPrices = runCatching { graph.binance.tickers(symbols) }.getOrDefault(emptyList()).associate { it.symbol to it.last }
        }
    }

    fun paperTrade(buy: Boolean, usdt: Double) {
        val price = ticker?.last ?: run { paperMessage = "Нет цены — обновите график."; return }
        val res = if (buy) com.archangel.ai.market.PaperEngine.buy(paper, symbol, usdt, price)
        else com.archangel.ai.market.PaperEngine.sell(paper, symbol, usdt, price)
        if (res.error.isNotEmpty()) { paperMessage = res.error; return }
        paperStore.save(res.state)
        res.realized?.let { if (graph.settings.current.tiltGuard) com.archangel.ai.market.TiltGuard.record(graph, it) }
        val t = res.trade!!
        journalStore.add(com.archangel.ai.market.JournalEntry(System.currentTimeMillis(), t.time, symbol, buy, t.quote, price, "", true))
        paperMessage = (if (buy) "Куплено " else "Продано ") + "%.6f".format(Locale.US, t.qty) + " ${symbol.removeSuffix("USDT")} по ${Indicators.formatPrice(price)}." +
            (res.realized?.let { " Результат: ${if (it >= 0) "+" else "−"}${Indicators.formatPrice(kotlin.math.abs(it))} USDT." } ?: "")
        reloadPaper(); reloadJournal(); refreshTilt()
    }

    fun resetPaper() { paper = paperStore.reset(); paperPrices = emptyMap(); paperMessage = "Песочница обнулена: 1000 виртуальных долларов." }

    // ── Дневник
    private val journalStore = com.archangel.ai.market.JournalStore(app)
    var journal by mutableStateOf(journalStore.all())
        private set
    var journalPrices by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    var journalReview by mutableStateOf("")
        private set
    var reviewing by mutableStateOf(false)
        private set

    fun reloadJournal() {
        journal = journalStore.all()
        val symbols = journal.map { it.symbol }.distinct()
        if (symbols.isEmpty()) return
        viewModelScope.launch {
            journalPrices = runCatching { graph.binance.tickers(symbols) }.getOrDefault(emptyList()).associate { it.symbol to it.last }
        }
    }

    fun setReason(id: Long, reason: String) { journalStore.setReason(id, reason); reloadJournal() }
    fun removeEntry(id: Long) { journalStore.remove(id); reloadJournal() }

    /** ИИ смотрит дневник: какие причины входа приносили прибыль, а какие — нет. */
    fun reviewJournal() {
        val entries = journal.filter { it.reason.isNotBlank() }.take(60)
        if (entries.size < 3) { journalReview = "Нужно хотя бы 3 записи с причинами — тогда будет что сравнить."; return }
        reviewing = true
        journalReview = ""
        viewModelScope.launch {
            try {
                val fmt = SimpleDateFormat("dd.MM", Locale.US)
                val lines = entries.joinToString("\n") { e ->
                    val now = journalPrices[e.symbol]
                    val res = now?.let { (if (e.buy) (it - e.price) else (e.price - it)) / e.price * 100 }
                    "${fmt.format(Date(e.time))} ${if (e.buy) "покупка" else "продажа"} ${e.symbol} по ${Indicators.formatPrice(e.price)}" +
                        (res?.let { " → сейчас ${"%+.1f".format(Locale.US, it)}%" } ?: "") +
                        (if (e.paper) " (песочница)" else "") + ". Причина: ${e.reason}"
                }
                journalReview = graph.brain.ask(
                    graph.settings.current,
                    system = "Ты — тренер трейдера. Пиши по-русски, коротко и по-доброму, без markdown-таблиц.",
                    turns = listOf(Turn("user",
                        "Вот мой дневник сделок с причинами входа и результатом на сегодня:\n$lines\n\n" +
                            "Сгруппируй причины по смыслу. Какие причины чаще дают плюс, какие — минус? " +
                            "Какие 2–3 правила мне стоит себе поставить? Последней строкой: «Это не финансовый совет».")),
                    maxTokens = 900,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: FriendlyException) {
                journalReview = e.message.orEmpty()
            } catch (e: Exception) {
                journalReview = "Не удалось разобрать: ${e.javaClass.simpleName}"
            } finally {
                reviewing = false
            }
        }
    }

    // ── Защита от эмоций
    var tiltUntil by mutableStateOf(0L)
        private set

    fun refreshTilt() { tiltUntil = com.archangel.ai.market.TiltGuard.activeUntil(graph) }
    fun setTiltGuard(on: Boolean) { graph.settings.update { it.copy(tiltGuard = on) }; if (!on) { com.archangel.ai.market.TiltGuard.clear(graph); refreshTilt() } }
    fun endPause() { com.archangel.ai.market.TiltGuard.clear(graph); refreshTilt() }

    // ── Открытые ордера
    var openOrders by mutableStateOf<List<com.archangel.ai.market.OpenOrder>>(emptyList())
        private set
    var ordersMessage by mutableStateOf("")
        private set

    fun loadOpenOrders() {
        val s = graph.settings.current
        if (!s.exchangeReady) { openOrders = emptyList(); return }
        viewModelScope.launch {
            try {
                openOrders = graph.binance.openOrders(s.binanceKey, s.binanceSecret, s.binanceTestnet)
                ordersMessage = ""
            } catch (e: FriendlyException) {
                ordersMessage = e.message.orEmpty()
            } catch (e: Exception) {
                ordersMessage = "Не удалось загрузить ордера: ${e.javaClass.simpleName}"
            }
        }
    }

    /** Отмена одного ордера кнопкой (часть OCO снимает обе части). */
    fun cancel(o: com.archangel.ai.market.OpenOrder) {
        val s = graph.settings.current
        viewModelScope.launch {
            try {
                graph.binance.cancelOrder(o.symbol, o.orderId, s.binanceKey, s.binanceSecret, s.binanceTestnet)
                ordersMessage = "Ордер отменён."
            } catch (e: FriendlyException) {
                ordersMessage = e.message.orEmpty()
            } catch (e: Exception) {
                ordersMessage = "Не удалось отменить: ${e.javaClass.simpleName}"
            }
            loadOpenOrders(); loadBalances()
        }
    }

    // ── Покупки по плану (DCA)
    private val dcaStore = com.archangel.ai.market.DcaStore(app)
    var dcaPlans by mutableStateOf(dcaStore.all())
        private set

    fun reloadDca() { dcaPlans = dcaStore.all() }

    fun addDca(usdt: Double, weekday: Int?) {
        val plan = com.archangel.ai.market.DcaPlan(System.currentTimeMillis(), symbol, usdt, weekday)
        dcaStore.add(plan)
        com.archangel.ai.market.DcaScheduler.schedule(graph, plan)
        reloadDca()
    }

    fun removeDca(id: Long) {
        dcaStore.remove(id)
        com.archangel.ai.market.DcaScheduler.cancel(graph, id)
        reloadDca()
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
                loadOpenOrders()
            } catch (e: FriendlyException) {
                balanceMessage = e.message.orEmpty()
            } catch (e: Exception) {
                balanceMessage = "Не удалось получить баланс: ${e.javaClass.simpleName}"
            }
        }
    }
}
