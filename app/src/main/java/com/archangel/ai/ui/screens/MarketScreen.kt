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
    onOrder: (PhoneAction.Order) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Автообновление котировок, пока экран открыт.
    LaunchedEffect(vm.symbol, vm.interval) {
        while (true) {
            delay(10_000); vm.refreshQuietly()
        }
    }
    LaunchedEffect(settings.exchangeReady, settings.binanceTestnet) { vm.loadBalances() }

    var visible by rememberSaveable { mutableIntStateOf(64) }
    var amount by rememberSaveable { mutableStateOf("20") }

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
                CandleChart(vm.candles, visible, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        val rsiNow = Indicators.rsi(vm.candles.map { it.close }).lastOrNull()
        MonoLabel("RSI 14 · ${rsiNow?.let { String.format(Locale.US, "%.1f", it) } ?: "—"}", color = Smoke)
        Spacer(Modifier.height(4.dp))
        RsiStrip(vm.candles, visible, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(8.dp))
        MonoLabel("—— SMA 20    – – SMA 50    касание — перекрестие, свайп — история", color = Smoke)

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

        // ── Сделка
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.1", "Сделка")
        Spacer(Modifier.height(14.dp))
        MonoLabel("Сумма, USDT · рыночный ордер")
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(Surface1, RoundedCornerShape(18.dp))
                .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10) },
                textStyle = TextStyles.Digits.copy(fontSize = 32.sp),
                cursorBrush = SolidColor(Ember),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("10", "25", "50", "100").forEach { v -> Chip(v, onClick = { amount = v }, selected = amount == v) }
        }
        Spacer(Modifier.height(14.dp))
        val usdt = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                "Купить", filledBone = true, enabled = usdt > 0,
                onClick = { onOrder(PhoneAction.Order(true, vm.symbol, usdt)) },
                modifier = Modifier.weight(1f),
            )
            Pill(
                "Продать", accent = true, enabled = usdt > 0,
                onClick = { onOrder(PhoneAction.Order(false, vm.symbol, usdt)) },
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

        // ── Баланс
        Spacer(Modifier.height(28.dp))
        SectionHeader("03.2", "Баланс", trailing = if (settings.binanceTestnet) "демо" else "live")
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(18.dp))
        Text(
            "Графики и индикаторы точные — это данные биржи. Разбор ИИ — мнение модели, а не гарантия и не финансовый совет.",
            style = TextStyles.BodySmall.copy(color = Smoke),
        )
        Spacer(Modifier.height(32.dp))
    }
}

