package com.archangel.ai.ui.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archangel.ai.market.Indicators
import com.archangel.ai.market.PnlPoint
import com.archangel.ai.market.Portfolio
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.TextStyles
import java.util.Locale

/** Оттенки в рамках палитры: кость → пепел → дым, первый сектор — акцент. */
private val slices = listOf(Ember, Bone, Color(0xFFC9C9CE), Ash, Color(0xFF7A7A80), Smoke, Color(0xFF3A3A3F))

/** Кольцевая диаграмма распределения портфеля с легендой. */
@Composable
fun AllocationDonut(p: Portfolio, modifier: Modifier = Modifier) {
    val items = p.items.take(6).let { top ->
        val rest = p.items.drop(6).sumOf { it.value }
        top.map { it.asset to it.value } + if (rest > 0) listOf("другое" to rest) else emptyList()
    }
    if (p.total <= 0 || items.isEmpty()) return
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 16.dp.toPx()
                var start = -90f
                items.forEachIndexed { i, (_, v) ->
                    val sweep = (v / p.total * 360).toFloat()
                    drawArc(
                        slices[i % slices.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke),
                    )
                    start += sweep
                }
            }
            Text("${items.size}", style = TextStyles.Mono.copy(fontSize = 13.sp), color = Smoke)
        }
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, (name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(slices[i % slices.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$name · ${String.format(Locale.US, "%.0f", v / p.total * 100)}%",
                        style = TextStyles.Mono.copy(fontSize = 12.sp),
                    )
                }
            }
        }
    }
}

/** Линия накопленной зафиксированной прибыли. Выше нуля — светлая, ниже — акцент. */
@Composable
fun PnlCurve(points: List<PnlPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    Canvas(modifier.fillMaxWidth().height(120.dp).padding(vertical = 6.dp)) {
        val values = listOf(0.0) + points.map { it.cumulative }
        val hi = values.max(); val lo = values.min()
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        fun y(v: Double) = (size.height * (1 - (v - lo) / span)).toFloat()
        val step = size.width / (values.size - 1)
        drawLine(Hairline, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1f)
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
        val last = values.last()
        drawPath(path, if (last >= 0) Bone else Ember, style = Stroke(2.dp.toPx()))
    }
}

/** «+12.40 USDT» / «−3.10 USDT». */
fun signedUsd(v: Double) = (if (v >= 0) "+" else "−") + Indicators.formatPrice(kotlin.math.abs(v)) + " USDT"
