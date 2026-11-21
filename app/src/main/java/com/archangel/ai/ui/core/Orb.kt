package com.archangel.ai.ui.core

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.archangel.ai.core.CoreState
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.EmberGlow
import com.archangel.ai.ui.theme.Frost
import com.archangel.ai.ui.theme.Platinum
import com.archangel.ai.ui.theme.Steel
import kotlin.math.cos
import kotlin.math.sin

/** Визуальные параметры сферы для каждого состояния. */
private data class OrbLook(val core: Color, val rim: Color, val energy: Float, val swirl: Float)

private fun lookFor(state: CoreState) = when (state) {
    CoreState.IDLE -> OrbLook(Platinum, Bone, 0.12f, 0.10f)
    CoreState.LISTENING -> OrbLook(Ember, EmberGlow, 0.55f, 0.35f)
    CoreState.THINKING -> OrbLook(Steel, Frost, 0.40f, 1.00f)
    CoreState.SPEAKING -> OrbLook(Color(0xFFFF7A3D), Color(0xFFFFE0C7), 0.50f, 0.45f)
}

/**
 * Сфера-ядро. На Android 13+ — настоящий GPU-шейдер (AGSL): жидкий свет с вихрями,
 * френелевским ободом и бликом. На старых версиях — упрощённая версия на Canvas.
 * [level] — громкость голоса 0…1, сфера «дышит» вместе с речью.
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { OrbShader() }
        Canvas(m) {
            val voice = if (speaking) 0.35f + 0.3f * sin(phase * 7f) * sin(phase * 2.3f) else smoothLevel
            shader.draw(this, phase, voice, energy, swirl, core, rim)
        }
    } else {
        Canvas(m) {
            val voice = if (speaking) 0.35f + 0.3f * sin(phase * 7f) * sin(phase * 2.3f) else smoothLevel
            drawFallbackOrb(phase, voice, energy, core, rim)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OrbShader {
    private val shader = RuntimeShader(ORB_AGSL)
    private val brush = ShaderBrush(shader)

    fun draw(scope: DrawScope, time: Float, level: Float, energy: Float, swirl: Float, core: Color, rim: Color) {
        shader.setFloatUniform("uSize", scope.size.width, scope.size.height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uLevel", level.coerceIn(0f, 1f))
        shader.setFloatUniform("uEnergy", energy)
        shader.setFloatUniform("uSwirl", swirl)
        shader.setFloatUniform("uCore", core.red, core.green, core.blue)
        shader.setFloatUniform("uRim", rim.red, rim.green, rim.blue)
        scope.drawRect(brush)
    }
}

/** Запасная сфера для Android 8–12: слои радиальных градиентов, медленно плывущие внутри. */
private fun DrawScope.drawFallbackOrb(t: Float, level: Float, energy: Float, core: Color, rim: Color) {
    val c = center
    val base = size.minDimension * 0.31f * (1f + 0.06f * level + 0.012f * sin(t * 1.3f))
    // мягкое свечение вокруг
    drawCircle(
        brush = Brush.radialGradient(
            listOf(rim.copy(alpha = 0.22f + 0.25f * energy), Color.Transparent),
            center = c, radius = base * 1.9f,
        ),
        radius = base * 1.9f, center = c,
    )
    // тело сферы
    drawCircle(
        brush = Brush.radialGradient(
            listOf(core.copy(alpha = 0.95f), core.copy(alpha = 0.55f), Color(0xFF0A0908)),
            center = c + Offset(-base * 0.25f, -base * 0.3f), radius = base * 1.35f,
        ),
        radius = base, center = c,
    )
    // «жидкие» пятна
    for (i in 0 until 3) {
        val a = t * (0.6f + i * 0.25f) + i * 2.1f
        val p = c + Offset(cos(a) * base * 0.35f, sin(a * 1.3f) * base * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(listOf(rim.copy(alpha = 0.35f), Color.Transparent), center = p, radius = base * 0.6f),
            radius = base * 0.6f, center = p,
        )
    }
    // обод и блик
    drawCircle(rim.copy(alpha = 0.55f), radius = base, center = c, style = Stroke(width = base * 0.02f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            center = c + Offset(-base * 0.35f, -base * 0.42f), radius = base * 0.28f,
        ),
        radius = base * 0.28f, center = c + Offset(-base * 0.35f, -base * 0.42f),
    )
}

/*
 * AGSL-шейдер сферы.
 * Внутри — fbm-шум с двойным искажением пространства (domain warping, приём Иниго Килеса):
 * даёт ощущение живой плазмы. Сверху — френелевский обод, блик и деформация края от голоса.
 */
private const val ORB_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float uLevel;
uniform float uEnergy;
uniform float uSwirl;
uniform float3 uCore;
uniform float3 uRim;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    float scale = 0.5 * min(uSize.x, uSize.y);
    float2 uv = (fragCoord - 0.5 * uSize) / scale;
    float r = length(uv);
    float ang = atan(uv.y, uv.x);

    float radius = 0.60 + 0.06 * uLevel + 0.012 * sin(uTime * 1.3);
    float edge = radius
        + 0.022 * (uEnergy + uLevel) * sin(ang * 5.0 + uTime * 2.0)
        + 0.020 * uLevel * sin(ang * 9.0 - uTime * 3.1)
        + 0.010 * uSwirl * sin(ang * 3.0 + uTime * 4.0);
    float d = r - edge;

    // Свечение снаружи
    float glow = exp(-max(d, 0.0) * 6.5) * (0.28 + 0.45 * uEnergy + 0.3 * uLevel);

    if (d >= 0.0) {
        float3 g = uRim * glow;
        return half4(half3(g), half(glow));
    }

    float k = r / edge;
    float z = sqrt(max(1.0 - k * k, 0.0));
    float3 n = float3(uv / edge, z);

    float t = uTime * 0.22;
    float2 p = n.xy * (1.5 + 0.4 * uSwirl);
    float2 q = float2(fbm(p + float2(t, 0.0)), fbm(p + float2(5.2, 1.3) - t));
    float2 w = float2(
        fbm(p + 3.0 * q + float2(1.7, 9.2) + t * 1.3),
        fbm(p + 3.0 * q + float2(8.3, 2.8) - t)
    );
    float f = fbm(p + 3.5 * w);

    float fres = pow(1.0 - z, 2.4);
    float3 deep = float3(0.035, 0.03, 0.028);
    float3 col = mix(deep, uCore, smoothstep(0.28, 0.92, f));
    col += uCore * pow(z, 5.0) * (0.18 + 0.35 * uLevel);
    col += uRim * fres * (0.85 + 0.7 * uEnergy);

    float3 l = normalize(float3(-0.45, -0.65, 0.62));
    float spec = pow(max(dot(n, l), 0.0), 36.0);
    col += float3(spec * 0.6);

    float aa = smoothstep(0.0, -0.012, d);
    float3 outCol = col * aa + uRim * glow * (1.0 - aa);
    float alpha = max(aa, glow);
    return half4(half3(outCol), half(alpha));
}
"""
