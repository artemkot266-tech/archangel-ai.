package com.archangel.ai.ui.core

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * «Режим бога»: нанотехнологичный слой поверх жидкой сферы.
 * Сама сфера не меняется — слой рисуется сверху с аддитивным смешиванием:
 * шестигранная нано-оболочка с бегущими волнами, сканирующий луч, живая корона,
 * рой нанороботов на орбитах с «нейросвязями» и ударная волна при запуске.
 */
internal object NanoLayer {
    private val GOLD = Color(0xFFFFC86B)
    private val CYAN = Color(0xFF6FE7FF)
    private val VIOLET = Color(0xFFB59BFF)

    /** Геометрия сферы — точно как в основном шейдере. */
    fun sphere(size: Size, time: Float, level: Float, active: Float): Pair<Offset, Float> {
        val scale = 0.5f * minOf(size.width, size.height)
        val cy = -0.16f - 0.02f * active
        val r = (0.45f + (0.52f - 0.45f) * active) + 0.05f * level + 0.012f * sin(time * 1.3f)
        return Offset(size.width / 2f, size.height / 2f + cy * scale) to r * scale
    }

    /** Мягкое свечение за сферой (рисуется до неё). */
    fun DrawScope.backGlow(center: Offset, r: Float, time: Float, level: Float, god: Float) {
        if (god <= 0.01f) return
        val pulse = 0.75f + 0.25f * sin(time * 1.6f)
        val tint = lerp(GOLD, CYAN, 0.5f + 0.5f * sin(time * 0.35f))
        drawCircle(
            Brush.radialGradient(
                0f to tint.copy(alpha = 0.32f * god * pulse * (0.8f + 0.6f * level)),
                0.45f to tint.copy(alpha = 0.10f * god),
                1f to Color.Transparent,
                center = center, radius = r * 2.8f,
            ),
            radius = r * 2.8f, center = center,
        )
    }

    /** Рой нанороботов, связи между ними и орбиты с «пакетами данных». */
    fun DrawScope.swarm(center: Offset, r: Float, time: Float, level: Float, god: Float) {
        if (god <= 0.01f) return
        val planes = listOf(
            Triple(0.35f, 0.0f, 0.55f),   // наклон, поворот, скорость
            Triple(1.05f, 1.1f, -0.42f),
            Triple(0.75f, 2.3f, 0.33f),
        )
        // Орбиты — тонкие эллипсы
        planes.forEachIndexed { k, (tilt, yaw, _) ->
            val rad = r * (1.28f + 0.1f * k)
            rotate(yaw * 57.2958f, center) {
                drawOval(
                    color = (if (k == 1) CYAN else GOLD).copy(alpha = 0.16f * god),
                    topLeft = Offset(center.x - rad, center.y - rad * cos(tilt)),
                    size = Size(rad * 2, rad * 2 * cos(tilt)),
                    style = Stroke(width = 1.2f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        // Частицы
        val n = 60
        val xs = FloatArray(n); val ys = FloatArray(n); val front = BooleanArray(n)
        for (i in 0 until n) {
            val (tilt, yaw, speed) = planes[i % 3]
            val h = hash(i * 7.31f)
            val rad = r * (1.18f + 0.32f * h)
            val th = time * speed * (0.8f + 0.5f * hash(i * 3.17f)) + i * 2.399f
            // Точка на наклонённой орбите → поворот плоскости → проекция
            val x0 = cos(th) * rad
            val y0 = sin(th) * rad * cos(tilt)
            val z0 = sin(th) * rad * sin(tilt)
            val x = x0 * cos(yaw) - y0 * sin(yaw)
            val y = x0 * sin(yaw) + y0 * cos(yaw)
            xs[i] = center.x + x; ys[i] = center.y + y
            // За сферой и внутри её силуэта — не видно
            front[i] = z0 >= 0 || hypot(x, y) > r * 1.02f
        }
        // Нейросвязи между близкими частицами
        val link = r * 0.42f
        for (i in 0 until n) {
            if (!front[i]) continue
            for (j in i + 1 until n) {
                if (!front[j]) continue
                val d = hypot(xs[i] - xs[j], ys[i] - ys[j])
                if (d < link) {
                    val a = (1f - d / link) * 0.35f * god
                    drawLine(lerp(GOLD, CYAN, hash(i + j * 0.37f)).copy(alpha = a), Offset(xs[i], ys[i]), Offset(xs[j], ys[j]), 1f, blendMode = BlendMode.Plus)
                }
            }
        }
        for (i in 0 until n) {
            if (!front[i]) continue
            val c = when (i % 3) { 0 -> GOLD; 1 -> CYAN; else -> VIOLET }
            val tw = 0.55f + 0.45f * sin(time * (2f + 3f * hash(i * 1.7f)) + i)
            val s = (1.4f + 2.2f * hash(i * 5.1f)) * (1f + 0.8f * level)
            drawCircle(c.copy(alpha = 0.22f * god * tw), radius = s * 3.2f, center = Offset(xs[i], ys[i]), blendMode = BlendMode.Plus)
            drawCircle(c.copy(alpha = 0.9f * god * tw), radius = s, center = Offset(xs[i], ys[i]), blendMode = BlendMode.Plus)
        }
        // «Пакеты данных» — яркие точки, бегущие по орбитам
        planes.forEachIndexed { k, (tilt, yaw, speed) ->
            val rad = r * (1.28f + 0.1f * k)
            for (m in 0 until 2) {
                val th = time * speed * 2.4f + m * PI.toFloat() + k
                val x0 = cos(th) * rad; val y0 = sin(th) * rad * cos(tilt)
                if (sin(th) * sin(tilt) < 0 && hypot(x0, y0) < r) continue
                val p = Offset(center.x + x0 * cos(yaw) - y0 * sin(yaw), center.y + x0 * sin(yaw) + y0 * cos(yaw))
                drawCircle(Color.White.copy(alpha = 0.35f * god), radius = 7f, center = p, blendMode = BlendMode.Plus)
                drawCircle(Color.White.copy(alpha = 0.95f * god), radius = 2.4f, center = p, blendMode = BlendMode.Plus)
            }
        }
    }

    private fun hash(n: Float): Float { val v = sin(n * 127.1f) * 43758.547f; return v - kotlin.math.floor(v) }
    private fun lerp(a: Color, b: Color, t: Float) = Color(
        a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
    )
}

/** Шейдер нано-оболочки, короны и ударной волны. Отдельный от основного: если не скомпилируется — просто без него. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class NanoShader {
    private val shader = RuntimeShader(NANO_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float, level: Float, god: Float, boot: Float, center: Offset, r: Float) {
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uLevel", level.coerceIn(0f, 1f))
        shader.setFloatUniform("uGod", god.coerceIn(0f, 1f))
        shader.setFloatUniform("uBoot", boot.coerceIn(0f, 1f))
        shader.setFloatUniform("uC", center.x, center.y)
        shader.setFloatUniform("uR", r)
        scope.drawRect(brush, blendMode = BlendMode.Plus)
    }
}

private const val NANO_AGSL = """
uniform float uTime;
uniform float uLevel;
uniform float uGod;
uniform float uBoot;
uniform float2 uC;
uniform float uR;

float3 pal(float t) {
    return float3(0.55, 0.55, 0.55) + float3(0.45, 0.45, 0.45) * cos(6.28318 * (float3(t, t, t) + float3(0.0, 0.33, 0.67)));
}

float4 hexGrid(float2 p) {
    float2 s = float2(1.0, 1.7320508);
    float4 hC = floor(float4(p.x, p.y, p.x - 0.5, p.y - 1.0) / float4(s.x, s.y, s.x, s.y)) + 0.5;
    float2 a = p - hC.xy * s;
    float2 b = p - (hC.zw + 0.5) * s;
    if (dot(a, a) < dot(b, b)) return float4(a.x, a.y, hC.x, hC.y);
    return float4(b.x, b.y, hC.z + 0.5, hC.w + 0.5);
}

float hexEdge(float2 h) {
    float2 k = abs(h);
    return 0.5 - max(dot(k, float2(0.5, 0.8660254)), k.x);
}

half4 main(float2 fc) {
    float2 q = (fc - uC) / uR;
    float r = length(q);
    float t = uTime;
    float3 col = float3(0.0, 0.0, 0.0);
    float a = 0.0;
    float ang = atan(q.y, q.x);
    // Грани — тёплое золото с переливом, вспыхивающие ячейки — бирюза (без шва по углу)
    float3 gold = float3(1.0, 0.78, 0.40);
    float3 cyan = float3(0.40, 0.92, 1.0);
    float3 tint = mix(gold, pal(0.55 + 0.12 * sin(t * 0.4) + 0.06 * sin(ang)), 0.35);

    // Нано-оболочка: шестигранная сетка на сфере, ячейки вспыхивают волнами
    if (r < 1.0) {
        float z = sqrt(1.0 - r * r);
        float2 uv = q / (0.35 + 0.65 * z) * 5.5 + float2(t * 0.35, 0.0);
        float4 h = hexGrid(uv);
        float e = hexEdge(h.xy);
        float lineV = smoothstep(0.06, 0.0, e);
        float wave = 0.5 + 0.5 * sin(h.z * 1.7 + h.w * 2.3 + t * 2.2);
        float pulse = pow(wave, 6.0);
        float fres = pow(1.0 - z, 1.5);
        float cell = smoothstep(0.45, 0.2, e) * pulse * 0.35;
        float boost = 0.55 + 0.9 * uLevel;
        float kl = lineV * (0.25 + 0.75 * fres) * boost;
        float kc = cell * boost;
        col += tint * kl + cyan * kc * 1.2;
        a += kl + kc;
        // Сканирующий луч
        float scanY = mod(t * 0.45, 2.6) - 1.3;
        float sv = (q.y - scanY) * 14.0;
        float scan = exp(-sv * sv) * (0.4 + lineV * 1.5);
        col += float3(0.6, 0.95, 1.0) * scan * 0.6;
        a += scan * 0.6;
    }

    // Живая корона
    float flick = 0.6 + 0.4 * sin(ang * 7.0 + t * 3.0) * sin(ang * 3.0 - t * 1.7);
    float aura = exp(-max(r - 1.0, 0.0) * 5.0) * smoothstep(0.92, 1.02, r) * (0.55 + 0.6 * uLevel) * flick;
    col += mix(tint, float3(1.0, 0.85, 0.5), 0.4) * aura;
    a += aura;

    // Тонкий переливающийся ореол
    float rv = (r - 1.12 - 0.03 * sin(t * 2.0)) * 40.0;
    float ring = exp(-rv * rv) * 0.5;
    col += pal(ang * 0.159 + t * 0.1) * ring;
    a += ring;

    // Ударная волна при запуске
    if (uBoot < 1.0) {
        float br = 1.0 + uBoot * 2.6;
        float bv = (r - br) * 10.0;
        float sw = exp(-bv * bv) * (1.0 - uBoot);
        col += float3(1.0, 0.9, 0.6) * sw * 1.5;
        a += sw;
    }

    a = clamp(a * uGod, 0.0, 1.0);
    col = col * uGod;
    return half4(half3(col), half(a));
}
"""
