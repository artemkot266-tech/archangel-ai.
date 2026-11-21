package com.archangel.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Экран «чёрного ящика». Намеренно без своих шрифтов и шейдеров:
 * он должен открыться, даже если сломалось именно оформление.
 */
@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0908))
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Text("Архангел упал при прошлом запуске", color = Color(0xFFEDE6DA), fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Скопируйте отчёт и отправьте его в чат с Claude — по нему ошибку можно найти и исправить. " +
                "Безопасный режим графики уже включён.",
            color = Color(0xFF9C948A), fontSize = 15.sp,
        )
        Spacer(Modifier.height(16.dp))
        SelectionContainer(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF131110), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(report, color = Color(0xFFEDE6DA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { clipboard.setText(AnnotatedString(report)); copied = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A1F), contentColor = Color(0xFF0A0908)),
                modifier = Modifier.weight(1f),
            ) { Text(if (copied) "Скопировано" else "Скопировать отчёт") }
            OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f)) {
                Text("Открыть приложение", color = Color(0xFFEDE6DA))
            }
        }
    }
}
