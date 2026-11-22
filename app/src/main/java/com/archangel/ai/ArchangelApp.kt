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

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this) // первым делом — чтобы поймать даже ошибку при старте
        settings = SettingsStore(this)
        chatStore = ChatStore(this)
        phone = PhoneController(this)
        com.archangel.ai.tasks.ReminderScheduler.ensureChannel(this)
    }
}
