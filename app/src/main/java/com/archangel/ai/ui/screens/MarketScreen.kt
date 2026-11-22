package com.archangel.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.MarketViewModel
import com.archangel.ai.data.AppSettings
import com.archangel.ai.market.BinanceClient
import com.archangel.ai.market.Indicators
import com.archangel.ai.phone.PhoneAction
import com.archangel.ai.ui.components.Chip
import com.archangel.ai.ui.components.MonoLabel
import com.archangel.ai.ui.components.Panel
import com.archangel.ai.ui.components.Pill
import com.archangel.ai.ui.components.SectionHeader
import com.archangel.ai.ui.market.CandleChart
import com.archangel.ai.ui.market.RsiStrip
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.TextStyles
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MarketScreen(
    vm: MarketViewModel,
    settings: AppSettings,
    onOrder: (PhoneAction) -> Unit,
    onAlertAdded: () -> Unit = {},
    onSpeak: (String) -> Unit = {},
    onSignalsOn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Раздел «Обучение» открывается поверх рынка
    vm.academy?.let { current ->
        AcademyScreen(current, onOpen = { vm.academy = it }, onSpeak = onSpeak, modifier = modifier)
        return
    }
    // Автообновление котировок, пока экран открыт.
    LaunchedEffect(vm.symbol, vm.interval) {
        while (true) {
            delay(10_000); vm.refreshQuietly()
        }
    }
    LaunchedEffect(settings.exchangeReady, settings.binanceTestnet) { vm.loadBalances() }
    LaunchedEffect(Unit) { vm.reloadAlerts() }
    var alertPrice by rememberSaveable { mutableStateOf("") }

    var visible by rememberSaveable { mutableIntStateOf(64) }
    var amount by rememberSaveable { mutableStateOf("20") }
    var limitMode by rememberSaveable { mutableStateOf(false) }
    var limitPrice by rememberSaveable { mutableStateOf("") }
    var protect by rememberSaveable { mutableStateOf(false) }
    var stopPct by rememberSaveable { mutableIntStateOf(5) }
    var takePct by rememberSaveable { mutableIntStateOf(10) }
    LaunchedEffect(Unit) { vm.reloadDca(); vm.reloadPaper(); vm.reloadJournal(); vm.refreshTilt() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        SectionHeader(
            "03", "Рынок",
            trailing = if (settings.binanceTestnet) "Демо-счёт" else "Реальный счёт",
            modifier = Modifier.padding(vertical = 14.dp),
        )
        Pill("Обучение: от пополнения до стоп-лосса", onClick = { vm.academy = "" }, icon = Icons.Filled.School, height = 40.dp,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))

        // ── Монеты
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val list = (BinanceClient.FAVORITES + vm.symbol).distinct()
            list.forEach { s ->
                val active = s == vm.symbol
                Column(
                    Modifier
                        .clickable { vm.select(s) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.removeSuffix("USDT"),
                        style = TextStyles.Mono.copy(fontSize = 14.sp),
                        color = if (active) Bone else Smoke,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(if (active) Ember else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        // ── Цена
        val t = vm.ticker
        MonoLabel("${vm.symbol.removeSuffix("USDT")} / USDT · Binance")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                t?.let { Indicators.formatPrice(it.last) } ?: "—",
                style = TextStyles.Digits,
            )
            Spacer(Modifier.width(12.dp))
            if (t != null) {
                val up = t.changePct >= 0
                Text(
                    (if (up) "▲ " else "▼ ") + String.format(Locale.US, "%.2f%%", t.changePct),
                    style = TextStyles.Mono,
                    color = if (up) Bone else Ember,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
        }
        if (t != null) {
            MonoLabel("24ч · мин ${Indicators.formatPrice(t.low)} · макс ${Indicators.formatPrice(t.high)}", color = Smoke)
        }
        if (vm.error.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(vm.error, style = TextStyles.BodySmall.copy(color = Ember))
        }

        Spacer(Modifier.height(16.dp))
        // ── Таймфрейм и масштаб
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BinanceClient.INTERVALS.forEach { (code, label) ->
                    Chip(label, onClick = { vm.changeInterval(code) }, selected = vm.interval == code)
                }
            }
            Text(
                "−", style = TextStyles.Mono.copy(fontSize = 20.sp), color = Ash,
                modifier = Modifier
                    .clickable { visible = (visible + 16).coerceAtMost(180) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "+", style = TextStyles.Mono.copy(fontSize = 20.sp), color = Ash,
                modifier = Modifier
                    .clickable { visible = (visible - 16).coerceAtLeast(24) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.height(10.dp))
        // ── График
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            if (vm.candles.isEmpty()) {
                MonoLabel(if (vm.loading) "Загружаю свечи…" else "Нет данных")
            } else {
                CandleChart(vm.candles, visible, Modifier.fillMaxSize(), levels = vm.levels)
            }
        }
        Spacer(Modifier.height(8.dp))
        val rsiNow = Indicators.rsi(vm.candles.map { it.close }).lastOrNull()
        MonoLabel("RSI 14 · ${rsiNow?.let { String.format(Locale.US, "%.1f", it) } ?: "—"}", color = Smoke)
        Spacer(Modifier.height(4.dp))
        RsiStrip(vm.candles, visible, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        MonoLabel("—— SMA 20    – – SMA 50    касание — перекрестие, свайп — история", color = Smoke)
        if (vm.levels.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MonoLabel("пунктир: светлый — поддержка, оранжевый — сопротивление · цифра — касания", color = Smoke)
        }

        // ── Тренд по таймфреймам
        if (vm.trends.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MonoLabel("Тренд", color = Bone)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                vm.trends.forEach { (tf, tr) ->
                    val col = when (tr) {
                        com.archangel.ai.market.Trend.UP -> Bone
                        com.archangel.ai.market.Trend.DOWN -> Ember
                        com.archangel.ai.market.Trend.FLAT -> Smoke
                    }
                    Box(
                        Modifier
                            .border(1.dp, Hairline, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("$tf · ${tr.label}", style = TextStyles.Mono.copy(fontSize = 12.sp), color = col)
                    }
                }
            }
            val ups = vm.trends.count { it.second == com.archangel.ai.market.Trend.UP }
            val downs = vm.trends.count { it.second == com.archangel.ai.market.Trend.DOWN }
            Spacer(Modifier.height(6.dp))
            MonoLabel(
                when {
                    ups == vm.trends.size -> "Все таймфреймы вверх — тренд согласован."
                    downs == vm.trends.size -> "Все таймфреймы вниз — тренд согласован."
                    ups > 0 && downs > 0 -> "Таймфреймы спорят — осторожнее со входом."
                    else -> "Явного тренда нет."
                },
                color = Smoke,
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                if (vm.analyzing) "Анализирую…" else "ИИ-разбор",
                onClick = { vm.analyze() },
                accent = true,
                enabled = !vm.analyzing && vm.candles.isNotEmpty(),
                icon = Icons.Filled.AutoAwesome,
                modifier = Modifier.weight(1f),
            )
            Pill("Обновить", onClick = { vm.refresh() }, icon = Icons.Filled.Refresh)
        }

        if (vm.analysis.isNotBlank() || vm.analyzing) {
            Spacer(Modifier.height(16.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text("Разбор", style = TextStyles.Title)
                    MonoLabel("${vm.symbol} · ${vm.interval} · ${settings.brain.title}", color = Smoke)
                    Spacer(Modifier.height(10.dp))
                    if (vm.analyzing) Text("Изучаю свечи и индикаторы…", style = TextStyles.BodySmall)
                    else SelectionContainer { Text(vm.analysis, style = TextStyles.Body.copy(fontSize = 15.sp)) }
                }
            }
        }

        // ── Настроение и новости
        if (vm.fearGreed != null || vm.news.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    vm.fearGreed?.let { fg ->
                        MonoLabel("Индекс страха и жадности", color = Smoke)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${fg.value}", style = TextStyles.Title.copy(fontSize = 28.sp))
                            Spacer(Modifier.width(10.dp))
                            Text(fg.label, style = TextStyles.Body, color = if (fg.value >= 56) Ember else Bone)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Hairline, RoundedCornerShape(2.dp))) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fg.value / 100f)
                                    .height(4.dp)
                                    .background(Ember, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                    if (vm.news.isNotEmpty()) {
                        val uri = androidx.compose.ui.platform.LocalUriHandler.current
                        Spacer(Modifier.height(16.dp))
                        MonoLabel("Новости · ${vm.symbol.removeSuffix("USDT")}", color = Smoke)
                        vm.news.forEach { n ->
                            Text(
                                n.title,
                                style = TextStyles.BodySmall.copy(fontSize = 14.sp),
                                color = Bone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = n.link.isNotBlank()) { runCatching { uri.openUri(n.link) } }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // ── Автосигналы
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.0", "Автосигналы")
        Spacer(Modifier.height(6.dp))
        com.archangel.ai.ui.components.ToggleRow(
            "Сигналы раз в час",
            settings.signalsEnabled,
            sub = "Пробой уровня, всплеск объёма, RSI в крайней зоне, разворот MACD. Только уведомления — сделок не делаю.",
        ) { on ->
            vm.setSignals(on)
            if (on) onSignalsOn()
        }
        if (settings.signalsEnabled) {
            Spacer(Modifier.height(6.dp))
            MonoLabel("Следить за монетами:", color = Smoke)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (BinanceClient.FAVORITES + settings.watchlist).distinct().forEach { sym ->
                    Chip(sym.removeSuffix("USDT"), onClick = { vm.toggleWatch(sym) }, selected = sym in settings.watchlist)
                }
            }
        }

        // ── Уведомления о цене
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.1", "Уведомить о цене")
        Spacer(Modifier.height(10.dp))
        Text("Сообщу уведомлением, когда ${vm.symbol.removeSuffix("USDT")} дойдёт до цены. Голосом: «сообщи, когда биткоин будет 100 тысяч».", style = TextStyles.BodySmall)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .background(Surface1, RoundedCornerShape(16.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (alertPrice.isEmpty()) Text(t?.let { "Например, ${Indicators.formatPrice(it.last * 1.05)}" } ?: "Цена", style = TextStyles.Mono.copy(color = Smoke))
                BasicTextField(
                    value = alertPrice,
                    onValueChange = { v -> alertPrice = v.filter { it.isDigit() || it == '.' || it == ',' }.take(14) },
                    textStyle = TextStyles.Mono,
                    cursorBrush = SolidColor(Ember),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val alertValue = alertPrice.replace(',', '.').toDoubleOrNull()
            Pill("Добавить", enabled = alertValue != null && alertValue > 0 && t != null, onClick = {
                alertValue?.let { vm.addAlert(it); onAlertAdded() }
                alertPrice = ""
            })
        }
        vm.alerts.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${a.symbol.removeSuffix("USDT")} ${if (a.above) "выше" else "ниже"} ${Indicators.formatPrice(a.price)}",
                    style = TextStyles.Mono, modifier = Modifier.weight(1f),
                )
                Text("убрать", style = TextStyles.Label, color = Ash, modifier = Modifier.clickable { vm.removeAlert(a.id) }.padding(8.dp))
            }
        }
        if (vm.alerts.isNotEmpty()) {
            Text("Цена проверяется каждые 15 минут, даже когда приложение закрыто.", style = TextStyles.BodySmall.copy(color = Smoke))
        }

        // ── Сделка
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.2", "Сделка")
        Spacer(Modifier.height(14.dp))
        // Тип ордера
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("По рынку", onClick = { limitMode = false }, selected = !limitMode)
            Chip("Лимит", onClick = {
                limitMode = true
                if (limitPrice.isBlank()) vm.ticker?.let { limitPrice = Indicators.formatPrice(it.last).replace("\u2009", "") }
            }, selected = limitMode)
        }
        Spacer(Modifier.height(12.dp))
        MonoLabel("Сумма, USDT")
        Spacer(Modifier.height(8.dp))
        NumberField(amount, { amount = it })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("10", "25", "50", "100").forEach { v -> Chip(v, onClick = { amount = v }, selected = amount == v) }
        }
        if (limitMode) {
            Spacer(Modifier.height(12.dp))
            MonoLabel("Цена, USDT · ордер исполнится, когда рынок до неё дойдёт")
            Spacer(Modifier.height(8.dp))
            NumberField(limitPrice, { limitPrice = it })
        }
        val usdt = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
        val limit = if (limitMode) limitPrice.replace(',', '.').replace(" ", "").toDoubleOrNull() else null
        val entry = limit ?: vm.ticker?.last ?: 0.0

        // Защита
        Spacer(Modifier.height(10.dp))
        com.archangel.ai.ui.components.ToggleRow(
            "Стоп-лосс и тейк-профит",
            protect,
            sub = if (limitMode) "Для лимитного ордера защиту ставят после исполнения — кнопкой «Защитить» в портфеле."
            else "Сразу после покупки ставлю OCO-ордер: продажа на цели или при падении до стопа.",
        ) { protect = it }
        if (protect && !limitMode) {
            MonoLabel("Стоп, %", color = Smoke)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 8, 10).forEach { v -> Chip("−$v", onClick = { stopPct = v }, selected = stopPct == v) }
            }
            Spacer(Modifier.height(10.dp))
            MonoLabel("Цель, %", color = Smoke)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20).forEach { v -> Chip("+$v", onClick = { takePct = v }, selected = takePct == v) }
            }
            if (entry > 0) {
                val (sl, tp) = com.archangel.ai.market.OrderMath.protection(entry, stopPct / 100.0, takePct / 100.0)
                Spacer(Modifier.height(8.dp))
                MonoLabel("стоп ${Indicators.formatPrice(sl)} · цель ${Indicators.formatPrice(tp)} · прибыль/риск ${"%.1f".format(Locale.US, takePct.toDouble() / stopPct)}", color = Bone)
            }
        }

        // Калькулятор риска
        if (usdt > 0) {
            val st = (if (protect && !limitMode) stopPct else 5) / 100.0
            val risk = com.archangel.ai.market.OrderMath.riskUsd(usdt, st)
            val deposit = vm.portfolio?.total ?: 0.0
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    MonoLabel("Риск сделки", color = Smoke)
                    Spacer(Modifier.height(4.dp))
                    val share = if (deposit > 0) risk / deposit * 100 else null
                    Text(
                        "Если сработает стоп −${(st * 100).toInt()}%, потеряете ≈${Indicators.formatPrice(risk)} USDT" +
                            (share?.let { " — это ${"%.1f".format(Locale.US, it)}% депозита." } ?: "."),
                        style = TextStyles.BodySmall.copy(fontSize = 14.sp),
                        color = if ((share ?: 0.0) > 2.0) Ember else Bone,
                    )
                    if (share != null && share > 2.0) {
                        Spacer(Modifier.height(4.dp))
                        Text("Больше 2% депозита на одну сделку — рискованно.", style = TextStyles.BodySmall, color = Ember)
                    }
                    if (deposit > 0) {
                        val rec = com.archangel.ai.market.OrderMath.positionSize(deposit, 0.01, st)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Сумма при риске 1% депозита: ${Indicators.formatPrice(rec)} USDT — подставить",
                            style = TextStyles.BodySmall.copy(fontSize = 13.sp),
                            color = Smoke,
                            modifier = Modifier.clickable { amount = String.format(Locale.US, "%.2f", rec) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val stopArg = if (protect && !limitMode) stopPct / 100.0 else null
        val takeArg = if (protect && !limitMode) takePct / 100.0 else null
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                "Купить", filledBone = true, enabled = usdt > 0 && (!limitMode || (limit ?: 0.0) > 0),
                onClick = { onOrder(PhoneAction.Order(true, vm.symbol, usdt, limit, stopArg, takeArg)) },
                modifier = Modifier.weight(1f),
            )
            Pill(
                "Продать", accent = true, enabled = usdt > 0 && (!limitMode || (limit ?: 0.0) > 0),
                onClick = { onOrder(PhoneAction.Order(false, vm.symbol, usdt, limit)) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Каждый ордер исполняется только после вашего подтверждения. " +
                if (settings.binanceTestnet) "Сейчас включён демо-счёт — деньги ненастоящие."
                else "Включён РЕАЛЬНЫЙ счёт.",
            style = TextStyles.BodySmall.copy(color = if (settings.binanceTestnet) Smoke else Ember),
        )

        // ── Открытые ордера
        if (settings.exchangeReady) {
            Spacer(Modifier.height(28.dp))
            SectionHeader("03.4", "Открытые ордера", trailing = vm.openOrders.size.takeIf { it > 0 }?.toString())
            Spacer(Modifier.height(8.dp))
            if (vm.openOrders.isEmpty()) Text("Открытых ордеров нет.", style = TextStyles.BodySmall)
            vm.openOrders.take(20).forEach { o ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${if (o.buy) "Покупка" else "Продажа"} ${o.symbol.removeSuffix("USDT")} · ${o.kind}",
                            style = TextStyles.Body.copy(fontSize = 15.sp),
                            color = if (o.buy) Bone else Ember,
                        )
                        val at = if (o.stopPrice > 0) "стоп ${Indicators.formatPrice(o.stopPrice)}" else "по ${Indicators.formatPrice(o.price)}"
                        MonoLabel("${String.format(Locale.US, "%.6f", o.origQty).trimEnd('0').trimEnd('.')} · $at" +
                            if (o.orderListId >= 0) " · OCO" else "", color = Smoke)
                    }
                    Chip("Отменить", onClick = { vm.cancel(o) })
                }
            }
            if (vm.ordersMessage.isNotBlank()) Text(vm.ordersMessage, style = TextStyles.BodySmall)
        }

        // ── Покупки по плану (DCA)
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.5", "Покупки по плану")
        Spacer(Modifier.height(8.dp))
        Text(
            "Регулярная покупка на одинаковую сумму. В назначенный день пришлю уведомление — покупка только после подтверждения. " +
                "Голосом: «покупай эфир на 10 долларов каждый понедельник».",
            style = TextStyles.BodySmall,
        )
        vm.dcaPlans.forEach { pl ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pl.coin} · ${Indicators.formatPrice(pl.usdt)} USDT · ${pl.schedule}", style = TextStyles.Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                Chip("Удалить", onClick = { vm.removeDca(pl.id) })
            }
        }
        Spacer(Modifier.height(10.dp))
        MonoLabel("Новый план: ${vm.symbol.removeSuffix("USDT")} на ${amount.ifBlank { "0" }} USDT", color = Smoke)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val days = listOf<Pair<String, Int?>>(
                "Каждый день" to null, "Пн" to java.util.Calendar.MONDAY, "Вт" to java.util.Calendar.TUESDAY,
                "Ср" to java.util.Calendar.WEDNESDAY, "Чт" to java.util.Calendar.THURSDAY, "Пт" to java.util.Calendar.FRIDAY,
                "Сб" to java.util.Calendar.SATURDAY, "Вс" to java.util.Calendar.SUNDAY,
            )
            days.forEach { (label, d) ->
                Chip(label, onClick = { if (usdt > 0) { vm.addDca(usdt, d); onAlertAdded() } })
            }
        }
        Spacer(Modifier.height(4.dp))
        MonoLabel("Нажмите день — план добавится (время — 10:00).", color = Smoke)

        // ── Баланс
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.3", "Портфель", trailing = if (settings.binanceTestnet) "демо" else "live")
        Spacer(Modifier.height(12.dp))
        vm.portfolio?.let { p ->
            Text(Indicators.formatPrice(p.total) + " USDT", style = TextStyles.Digits.copy(fontSize = 30.sp))
            Text(
                "за сутки " + (if (p.change24 >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", p.change24),
                style = TextStyles.Mono, color = if (p.change24 >= 0) Bone else Ember,
            )
            Spacer(Modifier.height(14.dp))
            com.archangel.ai.ui.market.AllocationDonut(p)
            com.archangel.ai.market.Pnl.concentrationHint(p)?.let { hint ->
                Spacer(Modifier.height(8.dp))
                Text(hint, style = TextStyles.BodySmall, color = Ember)
            }
            Spacer(Modifier.height(10.dp))
            p.items.take(10).forEach { h ->
                val share = if (p.total > 0) (h.value / p.total).toFloat() else 0f
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.asset, style = TextStyles.Mono, modifier = Modifier.width(64.dp))
                    Box(Modifier.weight(1f).height(4.dp).background(Hairline, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxWidth(share.coerceIn(0.01f, 1f)).height(4.dp).background(Bone, RoundedCornerShape(2.dp)))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(Indicators.formatPrice(h.value), style = TextStyles.Mono)
                    if (h.asset !in setOf("USDT", "USDC", "FDUSD", "BUSD", "TUSD", "DAI")) {
                        Spacer(Modifier.width(8.dp))
                        Chip("Защитить", onClick = { onOrder(PhoneAction.Protect(h.asset + "USDT", 0.05, 0.10)) })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (vm.balanceMessage.isNotBlank()) Text(vm.balanceMessage, style = TextStyles.BodySmall)
        vm.balances.take(12).forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(b.asset, style = TextStyles.Mono, modifier = Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%.6f", b.free).trimEnd('0').trimEnd('.'),
                    style = TextStyles.Mono,
                )
                if (b.locked > 0) {
                    Spacer(Modifier.width(8.dp))
                    MonoLabel("+${String.format(Locale.US, "%.4f", b.locked)} в ордерах", color = Smoke)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Pill("Обновить баланс", onClick = { vm.loadBalances() })
        // ── Песочница
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.7", "Песочница", trailing = "виртуальные $")
        Spacer(Modifier.height(8.dp))
        Text(
            "Торговля на 1000 виртуальных долларов по живым ценам, комиссия 0,1% — как на бирже. Ключи не нужны. " +
                "Голосом: «купи в песочнице биткоин на 100».",
            style = TextStyles.BodySmall,
        )
        Spacer(Modifier.height(10.dp))
        val eq = com.archangel.ai.market.PaperEngine.equity(vm.paper, vm.paperPrices + (vm.ticker?.let { mapOf(vm.symbol to it.last) } ?: emptyMap()))
        val change = eq - vm.paper.start
        Text(Indicators.formatPrice(eq) + " $", style = TextStyles.Digits.copy(fontSize = 28.sp))
        MonoLabel(
            "${com.archangel.ai.ui.market.signedUsd(change).removeSuffix(" USDT")} $ с начала · свободно ${Indicators.formatPrice(vm.paper.cash)} $",
            color = if (change >= 0) Bone else Ember,
        )
        val positions = vm.paper.trades.map { it.symbol }.distinct()
            .map { it to com.archangel.ai.market.PaperEngine.held(vm.paper, it) }.filter { it.second > 0 }
        positions.forEach { (sym, q) ->
            val px = vm.paperPrices[sym] ?: if (sym == vm.symbol) vm.ticker?.last else null
            MonoLabel("${sym.removeSuffix("USDT")} · ${String.format(Locale.US, "%.6f", q).trimEnd('0').trimEnd('.')}" +
                (px?.let { " · ${Indicators.formatPrice(q * it)} $" } ?: ""), color = Smoke)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Купить ${vm.symbol.removeSuffix("USDT")} на ${Indicators.formatPrice(usdt)} $", onClick = { vm.paperTrade(true, usdt) }, enabled = usdt > 0, modifier = Modifier.weight(1f), height = 42.dp)
            Pill("Продать", onClick = { vm.paperTrade(false, usdt) }, enabled = usdt > 0, height = 42.dp)
        }
        if (vm.paperMessage.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(vm.paperMessage, style = TextStyles.BodySmall)
        }
        Spacer(Modifier.height(6.dp))
        Chip("Обнулить песочницу", onClick = { vm.resetPaper() })

        // ── Дневник и защита от эмоций
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.8", "Дневник трейдера")
        Spacer(Modifier.height(6.dp))
        com.archangel.ai.ui.components.ToggleRow(
            "Защита от эмоций",
            settings.tiltGuard,
            sub = "После трёх убыточных сделок подряд предложу паузу на сутки и буду напоминать перед каждой сделкой.",
        ) { vm.setTiltGuard(it) }
        if (vm.tiltUntil > 0) {
            Spacer(Modifier.height(6.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text("Пауза до ${java.text.SimpleDateFormat("HH:mm d MMM", Locale("ru")).format(java.util.Date(vm.tiltUntil))}", style = TextStyles.Body, color = Ember)
                    Text("Три сделки подряд в минус. Лучшее, что можно сделать сейчас, — ничего. Рынок никуда не денется.", style = TextStyles.BodySmall)
                    Spacer(Modifier.height(6.dp))
                    Chip("Закончить паузу", onClick = { vm.endPause() })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Каждая сделка попадает сюда. Допишите, почему вошли, — через пару недель станет видно, какие причины работают.", style = TextStyles.BodySmall)
        val fmtJ = java.text.SimpleDateFormat("dd.MM HH:mm", Locale.US)
        vm.journal.take(15).forEach { e ->
            Spacer(Modifier.height(10.dp))
            val now = vm.journalPrices[e.symbol]
            val res = now?.let { (if (e.buy) (it - e.price) else (e.price - it)) / e.price * 100 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (e.buy) "Покупка" else "Продажа"} ${e.symbol.removeSuffix("USDT")} · ${Indicators.formatPrice(e.price)}" + if (e.paper) " · песочница" else "",
                    style = TextStyles.Body.copy(fontSize = 15.sp), modifier = Modifier.weight(1f),
                )
                res?.let { Text(String.format(Locale.US, "%+.1f%%", it), style = TextStyles.Mono, color = if (it >= 0) Bone else Ember) }
            }
            MonoLabel(fmtJ.format(java.util.Date(e.time)) + " · ${Indicators.formatPrice(e.usdt)} USDT", color = Smoke)
            if (e.reason.isNotBlank()) Text("«${e.reason}»", style = TextStyles.BodySmall.copy(fontSize = 14.sp))
            else {
                var draft by rememberSaveable(e.id) { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).border(1.dp, Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (draft.isEmpty()) Text("Почему вошли?", style = TextStyles.BodySmall, color = Smoke)
                        BasicTextField(draft, { draft = it.take(200) }, textStyle = TextStyles.BodySmall.copy(color = Bone),
                            cursorBrush = SolidColor(Ember), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(8.dp))
                    Chip("OK", onClick = { if (draft.isNotBlank()) vm.setReason(e.id, draft) })
                }
            }
        }
        if (vm.journal.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Pill(if (vm.reviewing) "Разбираю…" else "Разобрать дневник с ИИ", onClick = { vm.reviewJournal() },
                enabled = !vm.reviewing, icon = Icons.Filled.AutoAwesome)
            if (vm.journalReview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.fillMaxWidth()) { SelectionContainer { Text(vm.journalReview, style = TextStyles.Body.copy(fontSize = 15.sp)) } }
            }
        }

        // ── Итоги
        if (settings.exchangeReady) {
            Spacer(Modifier.height(28.dp))
            SectionHeader("03.6", "Итоги", trailing = if (settings.binanceTestnet) "демо" else "live")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7 to "Неделя", 30 to "Месяц", 0 to "Всё время").forEach { (d, label) ->
                    Chip(label, onClick = { vm.loadPnl(d) }, selected = vm.pnlDays == d && vm.pnl != null)
                }
            }
            Spacer(Modifier.height(10.dp))
            val rep = vm.pnl
            when {
                vm.pnlLoading -> Text("Считаю по истории сделок…", style = TextStyles.BodySmall)
                rep == null -> Pill("Посчитать прибыль", onClick = { vm.loadPnl() })
                else -> {
                    Text(com.archangel.ai.ui.market.signedUsd(rep.total), style = TextStyles.Digits.copy(fontSize = 28.sp),
                        color = if (rep.total >= 0) Bone else Ember)
                    MonoLabel(
                        "зафиксировано ${com.archangel.ai.ui.market.signedUsd(rep.realized)} · на бумаге ${com.archangel.ai.ui.market.signedUsd(rep.unrealized)}",
                        color = Smoke,
                    )
                    if (rep.wins + rep.losses > 0) {
                        MonoLabel("продажи в плюс: ${rep.wins} · в минус: ${rep.losses}", color = Smoke)
                    }
                    com.archangel.ai.ui.market.PnlCurve(rep.curve)
                    rep.coins.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.coin, style = TextStyles.Mono)
                                if (c.held > 0) MonoLabel("средняя покупка ${Indicators.formatPrice(c.avgCost)}", color = Smoke)
                            }
                            Text(com.archangel.ai.ui.market.signedUsd(c.total), style = TextStyles.Mono, color = if (c.total >= 0) Bone else Ember)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "«На бумаге» — прибыль по текущей цене, если продать сейчас. Монеты, купленные не на этом счёте, в расчёт не входят.",
                        style = TextStyles.BodySmall.copy(color = Smoke),
                    )
                }
            }
            if (vm.pnlMessage.isNotBlank()) Text(vm.pnlMessage, style = TextStyles.BodySmall)
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Графики и индикаторы точные — это данные биржи. Разбор ИИ — мнение модели, а не гарантия и не финансовый совет.",
            style = TextStyles.BodySmall.copy(color = Smoke),
        )
        Spacer(Modifier.height(32.dp))
    }
}


@Composable
private fun NumberField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(Surface1, RoundedCornerShape(18.dp))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(14)) },
            textStyle = TextStyles.Digits.copy(fontSize = 28.sp),
            cursorBrush = SolidColor(Ember),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
