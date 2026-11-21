package com.archangel.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.archangel.ai.ui.ArchangelRoot
import com.archangel.ai.ui.LaunchAction
import com.archangel.ai.ui.theme.ArchangelTheme

class MainActivity : ComponentActivity() {
    /** Действие из ярлыка на иконке («Голос», «Рынок»). UI забирает его и обнуляет. */
    private val launchAction = mutableStateOf<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тёмные прозрачные системные панели со светлыми значками — при любой теме телефона.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launchAction.value = LaunchAction.from(intent)
        setContent {
            ArchangelTheme {
                ArchangelRoot(
                    launchAction = launchAction.value,
                    onLaunchActionHandled = { launchAction.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchAction.value = LaunchAction.from(intent)
    }
}
