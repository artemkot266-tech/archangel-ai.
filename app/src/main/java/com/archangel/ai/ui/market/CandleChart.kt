package com.archangel.ai.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.market.Candle
import com.archangel.ai.market.Indicators
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.GeistMono
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.HairlineSoft
import com.archangel.ai.ui.theme.MarketDown
import com.archangel.ai.ui.theme.MarketUp
import com.archangel.ai.ui.theme.Obsidian
import com.archangel.ai.ui.theme.Smoke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Свечной график: рост — полые свечи цвета кости, падение — залитые углём.
 * SMA 20 — сплошная, SMA 50 — пунктир, внизу объём.
 * Свайп влево/вправо — прокрутка истории, касание — перекрестие с OHLC.
 */
@Composable
fun CandleChart(candles: List<Candle>, visible: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    var offset by remember(candles.size) { mutableFloatStateOf(0f) } // сколько свечей сдвинуто от правого края
    var crossX by remember { mutableStateOf<Float?>(null) }

    val closes = remember(candles) { candles.map { it.close } }
    val sma20 = remember(candles) { Indicators.sma(closes, 20) }
    val sma50 = remember(candles) { Indicators.sma(closes, 50) }

    Canvas(
        modifier
            .semantics { contentDescription = "Свечной график" }
            .pointerInput(candles.size, visible) {
                detectHorizontalDragGestures(
                    onDragStart = { crossX = null },
                ) { change, drag ->
                    change.consume()
                    val slot = (size.width - 56.dp.toPx()) / visible
                    val maxOffset = (candles.size - visible).coerceAtLeast(0).toFloat()
                    offset = (offset + drag / slot).coerceIn(0f, maxOffset)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p -> crossX = if (crossX == null) p.x else null }
            }
    ) {
        if (candles.isEmpty()) return@Canvas
        val axisW = 56.dp.toPx()
        val plotW = size.width - axisW
        val volH = size.height * 0.16f
        val plotH = size.height - volH - 14.dp.toPx()
        val n = visible.coerceAtMost(candles.size)
        val end = (candles.size - offset.roundToInt()).coerceIn(n, candles.size)
        val start = end - n
        val window = candles.subList(start, end)
        val slot = plotW / n
        val bodyW = (slot * 0.62f).coerceAtLeast(1.5f)

        var hi = window.maxOf { it.high }
        var lo = window.minOf { it.low }
        val pad = (hi - lo) * 0.08
        hi += pad; lo -= pad
        if (hi - lo <= 0.0) { hi += 1.0; lo -= 1.0 }
        fun y(p: Double) = (plotH * (1 - (p - lo) / (hi - lo))).toFloat()
        fun x(i: Int) = slot * (i + 0.5f)

        val labelStyle = TextStyle(fontFamily = GeistMono, fontSize = 9.5.sp, color = Smoke)

        // Сетка и шкала цен
        for (k in 0..4) {
            val p = lo + (hi - lo) * k / 4
            val yy = y(p)
            drawLine(HairlineSoft, Offset(0f, yy), Offset(plotW, yy), 1f)
            drawLabel(measurer, Indicators.formatPrice(p), Offset(plotW + 6.dp.toPx(), yy - 6.dp.toPx()), labelStyle)
        }

        // Объём
        val maxVol = window.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
        window.forEachIndexed { i, c ->
            val h = (volH * (c.volume / maxVol)).toFloat()
            drawRect(
                (if (c.up) MarketUp else MarketDown).copy(alpha = 0.16f),
                topLeft = Offset(x(i) - bodyW / 2, size.height - h),
                size = Size(bodyW, h),
            )
        }

        // Скользящие средние
        drawSeries(sma20, start, end, ::y, ::x, Bone.copy(alpha = 0.75f), null)
        drawSeries(sma50, start, end, ::y, ::x, Ash.copy(alpha = 0.8f), PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

        // Свечи
        val stroke = 1.2.dp.toPx()
        window.forEachIndexed { i, c ->
            val cx = x(i)
            val color = if (c.up) MarketUp else MarketDown
            val top = y(maxOf(c.open, c.close))
            val bottom = y(minOf(c.open, c.close))
            val bodyH = (bottom - top).coerceAtLeast(1f)
            // фитиль рисуем вокруг тела, чтобы он не просвечивал сквозь полую свечу
            drawLine(color, Offset(cx, y(c.high)), Offset(cx, top), stroke)
            drawLine(color, Offset(cx, bottom), Offset(cx, y(c.low)), stroke)
            if (c.up) {
                drawRect(color, Offset(cx - bodyW / 2, top), Size(bodyW, bodyH), style = Stroke(stroke))
            } else {
                drawRect(color, Offset(cx - bodyW / 2, top), Size(bodyW, bodyH))
            }
        }

        // Линия последней цены с плашкой
        val last = candles[end - 1]
        val ly = y(last.close)
        val lastColor = if (last.up) MarketUp else MarketDown
        drawLine(lastColor.copy(alpha = 0.6f), Offset(0f, ly), Offset(plotW, ly), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        val tag = measurer.measure(Indicators.formatPrice(last.close), labelStyle.copy(color = Obsidian))
        drawRoundRect(
            lastColor,
            topLeft = Offset(plotW + 2.dp.toPx(), ly - tag.size.height / 2f - 3.dp.toPx()),
            size = Size(axisW - 2.dp.toPx(), tag.size.height + 6.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawText(tag, topLeft = Offset(plotW + 6.dp.toPx(), ly - tag.size.height / 2f))

        // Перекрестие
        crossX?.let { px ->
            val i = (px / slot).toInt().coerceIn(0, n - 1)
            val c = window[i]
            val cx = x(i)
            drawLine(Ash.copy(alpha = 0.6f), Offset(cx, 0f), Offset(cx, size.height), 1f)
            val f = Indicators::formatPrice
            val date = SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(c.openTime))
            val info = "$date  O ${f(c.open)}  H ${f(c.high)}  L ${f(c.low)}  C ${f(c.close)}"
            val layout = measurer.measure(info, labelStyle.copy(color = Bone))
            drawRoundRect(
                Obsidian.copy(alpha = 0.9f),
                topLeft = Offset(0f, 0f),
                size = Size(layout.size.width + 12.dp.toPx(), layout.size.height + 8.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(6.dp.toPx(), 4.dp.toPx()))
        }

        // Рамка области
        drawLine(Hairline, Offset(plotW, 0f), Offset(plotW, size.height), 1f)
    }
}

private fun DrawScope.drawLabel(m: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(m, text, topLeft = at, style = style)
}

private fun DrawScope.drawSeries(
    values: List<Double?>, start: Int, end: Int,
    y: (Double) -> Float, x: (Int) -> Float, color: Color, effect: PathEffect?,
) {
    val path = Path()
    var started = false
    for (i in start until end) {
        val v = values.getOrNull(i) ?: continue
        val px = x(i - start)
        val py = y(v)
        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
    }
    if (started) drawPath(path, color, style = Stroke(width = 1.3.dp.toPx(), pathEffect = effect))
}

/** Мини-панель RSI 14 с уровнями 30/70. */
@Composable
fun RsiStrip(candles: List<Candle>, visible: Int, modifier: Modifier = Modifier) {
    val rsi = remember(candles) { Indicators.rsi(candles.map { it.close }) }
    Canvas(modifier.semantics { contentDescription = "Индикатор RSI" }) {
        if (candles.isEmpty()) return@Canvas
        val axisW = 56.dp.toPx()
        val plotW = size.width - axisW
        val n = visible.coerceAtMost(candles.size)
        val start = candles.size - n
        val slot = plotW / n
        fun y(v: Double) = (size.height * (1 - v / 100.0)).toFloat()
        drawRect(Bone.copy(alpha = 0.035f), Offset(0f, y(70.0)), Size(plotW, y(30.0) - y(70.0)))
        drawLine(HairlineSoft, Offset(0f, y(70.0)), Offset(plotW, y(70.0)), 1f)
        drawLine(HairlineSoft, Offset(0f, y(30.0)), Offset(plotW, y(30.0)), 1f)
        drawSeries(rsi, start, candles.size, ::y, { i -> slot * (i + 0.5f) }, Bone.copy(alpha = 0.85f), null)
        drawLine(Hairline, Offset(plotW, 0f), Offset(plotW, size.height), 1f)
    }
}
