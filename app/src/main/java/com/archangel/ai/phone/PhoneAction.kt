package com.archangel.ai.phone

/** Действие, которое ассистент может выполнить на телефоне или бирже. */
sealed class PhoneAction {
    /** Требует явного «Да» от пользователя перед выполнением. */
    open val needsConfirmation: Boolean = false

    data class Call(val target: String) : PhoneAction() {
        override val needsConfirmation = true
    }
    data class Sms(val target: String, val text: String) : PhoneAction() {
        override val needsConfirmation = true
    }
    data class OpenApp(val name: String) : PhoneAction()
    data class Alarm(val hour: Int, val minute: Int, val label: String = "") : PhoneAction()
    data class Timer(val seconds: Int, val label: String = "") : PhoneAction()
    data class Flashlight(val on: Boolean) : PhoneAction()
    data class Volume(val dir: VolumeDir) : PhoneAction()
    data class Search(val query: String) : PhoneAction()
    data class Navigate(val destination: String) : PhoneAction()
    /** Открыть график монеты в разделе «Рынок». */
    data class Market(val symbol: String) : PhoneAction()
    /** Рыночный ордер на сумму в USDT. Всегда с подтверждением. */
    data class Order(val buy: Boolean, val symbol: String, val usdt: Double) : PhoneAction() {
        override val needsConfirmation = true
    }
    /** Переход по разделам самого приложения. */
    data class Screen(val screen: AppScreen) : PhoneAction()
}

enum class VolumeDir { UP, DOWN, MUTE, MAX }

enum class AppScreen { CORE, CHAT, MARKET, SETTINGS }
