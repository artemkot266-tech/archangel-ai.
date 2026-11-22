package com.archangel.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.archangel.ai.ui.ArchangelRoot
import com.archangel.ai.ui.CrashScreen
import com.archangel.ai.ui.LaunchAction
import com.archangel.ai.ui.theme.ArchangelTheme

class MainActivity : ComponentActivity() {
    /** Действие из ярлыка на иконке («Голос», «Рынок»). UI забирает его и обнуляет. */
    private val launchAction = mutableStateOf<LaunchAction?>(null)
    /** Текст из «Поделиться» — для проверки «Защитником». */
    private val sharedText = mutableStateOf<String?>(null)

    private fun readShared(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тёмные прозрачные системные панели со светлыми значками — при любой теме телефона.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launchAction.value = LaunchAction.from(intent)
            readShared(intent)
        }
        val crash = CrashGuard.lastCrash(this)
        setContent {
            var report by androidx.compose.runtime.remember { mutableStateOf(crash) }
            val r = report
            if (r != null) {
                MaterialTheme {
                    CrashScreen(r, onContinue = {
                        CrashGuard.clear(this@MainActivity)
                        report = null
                    })
                }
            } else {
                ArchangelTheme {
                    ArchangelRoot(
                        launchAction = launchAction.value,
                        onLaunchActionHandled = { launchAction.value = null },
                        sharedText = sharedText.value,
                        onSharedHandled = { sharedText.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction.value = LaunchAction.from(intent)
        readShared(intent)
    }
}
