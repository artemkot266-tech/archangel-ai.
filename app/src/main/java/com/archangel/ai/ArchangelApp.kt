package com.archangel.ai

import android.app.Application
import com.archangel.ai.ai.BrainClient
import com.archangel.ai.data.ChatStore
import com.archangel.ai.data.SettingsStore
import com.archangel.ai.market.BinanceClient
import com.archangel.ai.phone.PhoneController

/** Общие сервисы приложения (простая ручная DI без библиотек). */
class ArchangelApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var chatStore: ChatStore
        private set
    val brain = BrainClient()
    val binance = BinanceClient()
    lateinit var phone: PhoneController
        private set

    /** Один ассистент на всё приложение: экран и фоновая служба работают с ним же. */
    private val assistantStore = androidx.lifecycle.ViewModelStore()
    val assistant: AssistantViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(
            assistantStore,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(this),
        )[AssistantViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this) // первым делом — чтобы поймать даже ошибку при старте
        com.archangel.ai.voice.SpeechInput.recoverSound(this) // звук не должен остаться заглушённым после сбоя
        settings = SettingsStore(this)
        chatStore = ChatStore(this)
        phone = PhoneController(this)
        com.archangel.ai.tasks.ReminderScheduler.ensureChannel(this)
    }
}
