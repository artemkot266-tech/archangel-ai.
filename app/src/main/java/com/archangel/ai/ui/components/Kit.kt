package com.archangel.ai.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archangel.ai.CrashGuard
import com.archangel.ai.ui.theme.Ash
import com.archangel.ai.ui.theme.Bone
import com.archangel.ai.ui.theme.Ember
import com.archangel.ai.ui.theme.Hairline
import com.archangel.ai.ui.theme.Obsidian
import com.archangel.ai.ui.theme.Smoke
import com.archangel.ai.ui.theme.Surface1
import com.archangel.ai.ui.theme.TextStyles
import kotlinx.coroutines.delay
import kotlin.math.sqrt

/** Короткая служебная подпись. */
@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Ash, style: TextStyle = TextStyles.Label) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1)
}

/** Заголовок раздела в стиле журнальной вёрстки: «02 — ДИАЛОГ ·········· 14». */
@Composable
fun SectionHeader(index: String, title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonoLabel("$index — $title", color = Bone)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Hairline)
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            MonoLabel(trailing)
        }
    }
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

/** Кнопка-капсула. accent = залитая углём, иначе — тонкий контур. */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    filledBone: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 48.dp,
) {
    val bg = when {
        !enabled -> Color.Transparent
        accent -> Ember
        filledBone -> Bone
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> Smoke
        accent || filledBone -> Obsidian
        else -> Bone
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = bg,
        border = if (accent || filledBone) null else BorderStroke(1.dp, Hairline),
        modifier = modifier.defaultMinSize(minHeight = height),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = TextStyles.Button, color = fg, maxLines = 1)
        }
    }
}

/** Круглая кнопка-иконка 52 dp. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 52.dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (accent && enabled) Ember else Color.Transparent,
        border = if (accent && enabled) null else BorderStroke(1.dp, Hairline),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, contentDescription = description,
                tint = when {
                    !enabled -> Smoke
                    accent -> Obsidian
                    else -> Bone
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Маленький «чип» быстрой команды. */
@Composable
fun Chip(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Bone else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, Hairline),
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = TextStyles.BodySmall, color = if (selected) Obsidian else Bone, maxLines = 1)
        }
    }
}

/** Панель с тонкой рамкой — вместо «карточек с тенями». */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

/** Фон всех экранов: ровный нейтральный тёмный, без эффектов. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Obsidian), content = content)
}
