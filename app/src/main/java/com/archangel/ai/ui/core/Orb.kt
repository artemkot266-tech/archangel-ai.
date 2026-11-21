package com.archangel.ai.ui.core

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.archangel.ai.CrashGuard
import com.archangel.ai.core.CoreState
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.EmberGlow
import com.archangel.ai.ui.theme.Frost
import com.archangel.ai.ui.theme.Platinum
import com.archangel.ai.ui.theme.Steel
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Визуальные параметры сферы для каждого состояния. */
private data class OrbLook(val core: Color, val rim: Color, val energy: Float, val swirl: Float)

private fun lookFor(state: CoreState) = when (state) {
    CoreState.IDLE -> OrbLook(Platinum, Bone, 0.12f, 0.10f)
    CoreState.LISTENING -> OrbLook(Ember, EmberGlow, 0.55f, 0.35f)
    CoreState.THINKING -> OrbLook(Steel, Frost, 0.40f, 1.00f)
    CoreState.SPEAKING -> OrbLook(Color(0xFFFF7A3D), Color(0xFFFFE0C7), 0.50f, 0.45f)
}

/** Пропорции холста сцены (ширина / высота): сфера сверху, лужа снизу. */
const val ORB_ASPECT = 0.82f

/**
 * Ядро — жидкий металл.
 * В покое сфера «провисает», с неё срываются капли и падают в лужу, в которой отражается сфера.
 * Когда Архангел начинает работать, лужа поднимается струёй и вливается в сферу — она становится
 * цельной и живой. На Android 13+ — GPU-шейдер (AGSL), на старых версиях — упрощённая отрисовка.
 * [level] — громкость голоса 0…1.
 */
@Composable
fun Orb(
    state: CoreState,
    level: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val look = lookFor(state)
    val core by animateColorAsState(look.core, tween(700), label = "core")
    val rim by animateColorAsState(look.rim, tween(700), label = "rim")
    val energy by animateFloatAsState(look.energy, tween(700), label = "energy")
    val swirl by animateFloatAsState(look.swirl, tween(900), label = "swirl")
    // 0 — покой (лужа и капли), 1 — работа (цельная сфера). Переход неспешный, чтобы была видна струя.
    val active by animateFloatAsState(
        if (state == CoreState.IDLE) 0f else 1f,
        tween(if (state == CoreState.IDLE) 1600 else 1100, easing = FastOutSlowInEasing),
        label = "active",
    )
    val smoothLevel by animateFloatAsState(level, tween(120), label = "level")
    val speaking by rememberUpdatedState(state == CoreState.SPEAKING)

    // Своё «время» с переменной скоростью: при смене состояния нет рывков.
    var phase by remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(0.6f + swirl * 1.6f)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase += (now - last) / 1_000_000_000f * speed
                last = now
            }
        }
    }

    var m = modifier.semantics { contentDescription = "Ядро Архангела: ${state.label}" }
    if (onClick != null) {
        m = m.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
    }

    // Шейдер компилируется на самом телефоне; если драйвер его не примет — рисуем запасную сцену.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !CrashGuard.safeGraphics) {
            runCatching { OrbShader() }.getOrNull()
        } else null
    }
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Canvas(m) {
            val voice = if (speaking) 0.35f + 0.3f * sin(phase * 7f) * sin(phase * 2.3f) else smoothLevel
            shader.draw(this, phase, voice, energy, swirl, active, core, rim)
        }
    } else {
        Canvas(m) {
            val voice = if (speaking) 0.35f + 0.3f * sin(phase * 7f) * sin(phase * 2.3f) else smoothLevel
            drawFallbackScene(phase, voice, energy, active, core, rim)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OrbShader {
    private val shader = RuntimeShader(ORB_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(
        scope: DrawScope, time: Float, level: Float, energy: Float, swirl: Float, active: Float,
        core: Color, rim: Color,
    ) {
        shader.setFloatUniform("uSize", scope.size.width, scope.size.height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uLevel", level.coerceIn(0f, 1f))
        shader.setFloatUniform("uEnergy", energy)
        shader.setFloatUniform("uSwirl", swirl)
        shader.setFloatUniform("uActive", active.coerceIn(0f, 1f))
        shader.setFloatUniform("uCore", core.red, core.green, core.blue)
        shader.setFloatUniform("uRim", rim.red, rim.green, rim.blue)
        scope.drawRect(brush)
    }
}

// ───────────────────────── Запасная сцена для Android 8–12 ─────────────────────────

private fun mixF(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun hash1(n: Float): Float { val v = sin(n * 91.345f) * 47453.21f; return v - floor(v) }

private fun DrawScope.drawFallbackScene(t: Float, level: Float, energy: Float, active: Float, core: Color, rim: Color) {
    val unit = size.width / 2f                     // как в шейдере: 1.0 = половина ширины
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun px(x: Float) = cx + x * unit
    fun py(y: Float) = cy + y * unit

    val r = mixF(0.45f, 0.52f, active) + 0.05f * level + 0.012f * sin(t * 1.3f)
    val sc = Offset(px(0f), py(-0.16f - 0.02f * active))
    val rad = r * unit
    val pa = mixF(0.70f, 0.30f, active) * unit
    val pb = mixF(0.105f, 0.035f, active) * unit
    val pc = Offset(px(0f), py(0.84f))

    // Лужа: тёмное зеркало с ободком и отражением сферы
    drawOval(
        brush = Brush.verticalGradient(listOf(Color(0xFF2A2A2E), Color(0xFF0E0E10)), startY = pc.y - pb, endY = pc.y + pb),
        topLeft = Offset(pc.x - pa, pc.y - pb), size = Size(pa * 2, pb * 2),
    )
    drawOval(
        brush = Brush.radialGradient(listOf(core.copy(alpha = 0.55f), Color.Transparent), center = pc, radius = pa * 0.7f),
        topLeft = Offset(pc.x - pa * 0.7f, pc.y - pb * 0.8f), size = Size(pa * 1.4f, pb * 1.6f),
    )
    drawOval(rim.copy(alpha = 0.5f), Offset(pc.x - pa, pc.y - pb), Size(pa * 2, pb * 2), style = Stroke(1.5f))

    // Капли (только в покое)
    if (active < 0.99f) {
        val y1 = 0.84f - 0.105f
        for (i in 0 until 3) {
            val tt = t * 0.45f + i / 3f
            val cyc = floor(tt)
            val ph = tt - cyc
            val dx = (hash1(cyc * 7.13f + i * 3.1f) - 0.5f) * 0.26f
            val y0 = -0.16f + sqrt((r * r - dx * dx).coerceAtLeast(0f)) * 1.12f
            val fall = ((ph - 0.35f).coerceAtLeast(0f)) / 0.65f
            val yy = y0 + 0.015f + (y1 - y0) * fall * fall
            val dr = 0.045f * (ph / 0.35f).coerceIn(0f, 1f) * (1 - 0.25f * fall) * (1 - active) * unit
            if (dr > 0.5f) {
                drawOval(
                    Brush.radialGradient(listOf(rim, Color(0xFF3A3A40)), center = Offset(px(dx) - dr * 0.3f, py(yy) - dr * 0.3f), radius = dr * 1.6f),
                    topLeft = Offset(px(dx) - dr, py(yy) - dr * (1 + 0.6f * fall)),
                    size = Size(dr * 2, dr * 2 * (1 + 0.6f * fall)),
                )
            }
        }
    }

    // Свечение и тело сферы
    drawCircle(
        brush = Brush.radialGradient(listOf(rim.copy(alpha = 0.22f + 0.25f * energy), Color.Transparent), center = sc, radius = rad * 1.8f),
        radius = rad * 1.8f, center = sc,
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = 0.95f), core.copy(alpha = 0.5f), Color(0xFF0B0B0C)),
            center = sc + Offset(-rad * 0.25f, -rad * 0.3f), radius = rad * 1.35f,
        ),
        radius = rad, center = sc,
    )
    for (i in 0 until 3) {
        val a = t * (0.6f + i * 0.25f) + i * 2.1f
        val p = sc + Offset(kotlin.math.cos(a) * rad * 0.35f, sin(a * 1.3f) * rad * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(listOf(rim.copy(alpha = 0.35f), Color.Transparent), center = p, radius = rad * 0.6f),
            radius = rad * 0.6f, center = p,
        )
    }
    drawCircle(rim.copy(alpha = 0.55f), radius = rad, center = sc, style = Stroke(width = rad * 0.02f))
    val hl = sc + Offset(-rad * 0.35f, -rad * 0.42f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = hl, radius = rad * 0.28f),
        radius = rad * 0.28f, center = hl,
    )
}

/*
 * AGSL-шейдер сцены «жидкий металл».
 * Геометрия — 2D-поля расстояний (SDF), склеенные «мягким минимумом» (smin): так капли
 * вытягиваются из сферы и сливаются с лужей как настоящая жидкость. Нормали берутся из градиента
 * поля, металл отражает «небо», внутри сферы — плазма из fbm-шума с искажением пространства.
 * В луже отражается сфера (сжатая по вертикали, как отражение на плоскости под углом).
 */
private const val ORB_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float uLevel;
uniform float uEnergy;
uniform float uSwirl;
uniform float uActive;
uniform float3 uCore;
uniform float3 uRim;

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.21);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float sphereR() { return mix(0.45, 0.52, uActive) + 0.05 * uLevel + 0.012 * sin(uTime * 1.3); }
float2 sphereC() { return float2(0.0, -0.16 - 0.02 * uActive); }
float2 puddleC() { return float2(0.0, 0.84); }
float puddleA() { return mix(0.70, 0.30, uActive); }
float puddleB() { return mix(0.105, 0.035, uActive); }

float sdSphere(float2 p) {
    float2 q = p - sphereC();
    float ang = atan(q.y, q.x);
    float live = 0.6 + 0.8 * uActive;
    float wob = 0.020 * live * (uEnergy + uLevel) * sin(ang * 5.0 + uTime * 2.0)
              + 0.018 * uLevel * sin(ang * 9.0 - uTime * 3.1)
              + 0.010 * (uSwirl + uActive) * sin(ang * 3.0 + uTime * 2.6)
              + 0.006 * uActive * sin(ang * 13.0 + uTime * 5.0);
    // В покое низ сферы провисает — капля вот-вот оторвётся
    if (q.y > 0.0) {
        q.y = q.y / (1.0 + 0.12 * (1.0 - uActive));
    }
    return length(q) - (sphereR() + wob);
}

float sdPuddle(float2 p) {
    float a = puddleA();
    float b = puddleB();
    float2 q = p - puddleC();
    q.y += 0.006 * sin(q.x * 38.0 - uTime * 3.0) * (1.0 - 0.5 * uActive);
    float k0 = length(float2(q.x / a, q.y / b));
    float k1 = length(float2(q.x / (a * a), q.y / (b * b))) + 0.000001;
    return k0 * (k0 - 1.0) / k1;
}

float sdDrops(float2 p) {
    float d = 10.0;
    float R = sphereR();
    float cy = sphereC().y;
    float y1 = puddleC().y - puddleB();
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float tt = uTime * 0.45 + fi / 3.0;
        float cyc = floor(tt);
        float ph = tt - cyc;
        float dx = (hash1(cyc * 7.13 + fi * 3.1) - 0.5) * 0.26;
        float y0 = cy + sqrt(max(R * R - dx * dx, 0.0)) * (1.0 + 0.12 * (1.0 - uActive));
        float grow = smoothstep(0.0, 0.35, ph);
        float fall = max(ph - 0.35, 0.0) / 0.65;
        float y = y0 + 0.015 + (y1 - y0) * fall * fall;
        float r = 0.045 * grow * (1.0 - 0.25 * fall) * (1.0 - uActive);
        float2 q = float2(p.x - dx, (p.y - y) / (1.0 + 0.6 * fall));
        // Капли нет — уводим её поле далеко, чтобы smin не рисовал «призрак»
        float hide = 0.3 * (1.0 - smoothstep(0.0, 0.02, r));
        d = min(d, length(q) - r + hide);
    }
    return d;
}

float sdStream(float2 p) {
    float s = sin(3.14159265 * clamp(uActive, 0.0, 1.0));
    float2 a = float2(0.0, puddleC().y);
    float2 b = float2(0.0, sphereC().y + sphereR() * 0.6);
    float2 pa = p - a;
    float2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float w = 0.055 * s * (0.6 + 0.4 * sin(h * 12.0 - uTime * 8.0));
    float hide = 0.3 * (1.0 - smoothstep(0.0, 0.15, s));
    return length(pa - ba * h) - w + hide;
}

float sdScene(float2 p) {
    float d = smin(sdSphere(p), sdDrops(p), 0.07);
    d = smin(d, sdPuddle(p), 0.06);
    return smin(d, sdStream(p), 0.08);
}

float3 plasma(float2 p) {
    float R = sphereR();
    float2 q = (p - sphereC()) / R;
    float z = sqrt(max(1.0 - dot(q, q), 0.0));
    float t = uTime * 0.22;
    float2 pp = q * (1.5 + 0.4 * uSwirl);
    float2 q1 = float2(fbm(pp + float2(t, 0.0)), fbm(pp + float2(5.2, 1.3) - t));
    float2 w = float2(
        fbm(pp + 3.0 * q1 + float2(1.7, 9.2) + t * 1.3),
        fbm(pp + 3.0 * q1 + float2(8.3, 2.8) - t)
    );
    float f = fbm(pp + 3.5 * w);
    float3 deep = float3(0.035, 0.03, 0.028);
    float3 col = mix(deep, uCore, smoothstep(0.28, 0.92, f));
    col += uCore * pow(z, 5.0) * (0.18 + 0.35 * uLevel);
    return col;
}

/** Цвет сферы в точке — и для самой сферы, и для её отражения в луже. */
float3 sphereShade(float2 p) {
    float R = sphereR();
    float2 q = (p - sphereC()) / R;
    float z = sqrt(max(1.0 - dot(q, q), 0.0));
    float3 col = plasma(p);
    col += uRim * pow(1.0 - z, 2.4) * (0.85 + 0.7 * uEnergy);
    float3 l = normalize(float3(-0.45, -0.65, 0.62));
    col += float3(pow(max(dot(float3(q, z), l), 0.0), 36.0) * 0.6);
    return col;
}

/** Жидкий металл: отражение «неба» с горизонтом, френель и блик. */
float3 metal(float3 n) {
    float up = -n.y;
    float3 sky = mix(float3(0.02, 0.02, 0.022), uRim, smoothstep(-0.3, 0.9, up));
    sky += uCore * 0.35 * exp(-pow((up + 0.05) * 6.0, 2.0));
    float fres = pow(1.0 - n.z, 2.4);
    float3 col = mix(float3(0.07, 0.06, 0.056), sky, 0.35 + 0.65 * fres);
    float3 l = normalize(float3(-0.45, -0.65, 0.62));
    col += float3(pow(max(dot(n, l), 0.0), 36.0) * 0.8);
    return col;
}

half4 main(float2 fragCoord) {
    float scale = 0.5 * min(uSize.x, uSize.y);
    float2 p = (fragCoord - 0.5 * uSize) / scale;

    float d = sdScene(p);
    float ds = sdSphere(p);
    float2 c = sphereC();
    float R = sphereR();

    // Свечение вокруг сферы (гаснет до края холста)
    float glow = exp(-max(ds, 0.0) * 6.5) * (0.28 + 0.45 * uEnergy + 0.3 * uLevel);
    glow *= 1.0 - smoothstep(0.62, 0.98, length(p - c));

    if (d > 0.02) {
        return half4(half3(uRim * glow), half(glow));
    }

    // Нормаль из градиента поля расстояний
    float e = 0.004;
    float2 g = float2(
        sdScene(p + float2(e, 0.0)) - sdScene(p - float2(e, 0.0)),
        sdScene(p + float2(0.0, e)) - sdScene(p - float2(0.0, e))
    );
    g = g / (length(g) + 0.000001);
    float wS = 1.0 - smoothstep(-0.03, 0.03, ds);
    float thick = mix(0.06, R, wS);
    float depth = clamp(-d / thick, 0.0, 1.0);
    float nz = sqrt(1.0 - (1.0 - depth) * (1.0 - depth));
    float3 n = float3(g * sqrt(max(1.0 - nz * nz, 0.0)), nz);

    float3 col = metal(n);
    if (wS > 0.001) {
        col = mix(col, sphereShade(p), wS);
    }

    // Отражение сферы в луже
    float inP = (1.0 - smoothstep(-0.01, 0.01, sdPuddle(p))) * (1.0 - wS);
    if (inP > 0.001) {
        float a = puddleA();
        float b = puddleB();
        float top = puddleC().y - b;
        float k = clamp((p.y - top) / (2.0 * b), 0.0, 1.0);
        float2 rp = float2(
            p.x * (R / max(a, 0.001)) * 1.25 + 0.012 * sin(p.y * 90.0 - uTime * 3.0),
            c.y + R * 1.05 - k * 2.0 * R * 1.05
        );
        float rs = sdSphere(rp);
        float rin = 1.0 - smoothstep(-0.01, 0.01, rs);
        float3 refl = float3(0.0);
        if (rin > 0.001) {
            refl = sphereShade(rp) * rin;
        }
        refl += uRim * exp(-max(rs, 0.0) * 6.5) * (0.25 + 0.4 * uEnergy) * (1.0 - rin);
        col = mix(col, col * 0.3 + refl, inP * 0.95);
    }

    float aa = 1.0 - smoothstep(-0.012, 0.012, d);
    float3 outCol = col * aa + uRim * glow * (1.0 - aa);
    float alpha = max(aa, glow);
    return half4(half3(outCol), half(alpha));
}
"""
