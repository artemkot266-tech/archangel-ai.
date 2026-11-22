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

    /** YouTube: query = null — сначала спросить, что искать. */
    data class YouTube(val query: String?) : PhoneAction()
    /** Музыка: app/query = null — уточнить у пользователя. Пустой query — «что угодно». */
    data class Music(val app: MusicApp?, val query: String?) : PhoneAction()
    /** Такси: destination = null — спросить, куда едем. */
    data class Taxi(val destination: String?, val app: TaxiApp?) : PhoneAction()
    /** Заметка: text = null — спросить, что записать. */
    data class Note(val text: String?) : PhoneAction()
    object ShowNotes : PhoneAction()
    /** Напоминание: text/at = null — уточнить. at — время в миллисекундах. */
    data class Reminder(val text: String?, val at: Long?) : PhoneAction()
    object ShowReminders : PhoneAction()

    /** Память: «запомни, что я работаю до шести». */
    data class Remember(val fact: String?) : PhoneAction()
    data class Forget(val what: String) : PhoneAction()
    object ShowMemory : PhoneAction()
    /** Погода: 0 — сегодня, 1 — завтра. */
    data class Weather(val day: Int) : PhoneAction()
    object Briefing : PhoneAction()
    object Portfolio : PhoneAction()
    /** «Что с рынком?» — голосовая сводка по монетам, индексу страха и портфелю. */
    object MarketSummary : PhoneAction()
    /** Урок из раздела «Обучение». id = null — открыть список уроков. */
    data class Lesson(val id: String?) : PhoneAction()
    /** Уведомить, когда цена дойдёт до уровня. price = null — спросить. */
    data class PriceAlert(val symbol: String, val price: Double?) : PhoneAction()
    /** Событие в календаре. */
    data class CalendarAdd(val title: String?, val at: Long?) : PhoneAction()
    /** «Что у меня сегодня?» */
    object Agenda : PhoneAction()
    /** Умный дом: «включи свет в спальне». */
    data class SmartHome(val on: Boolean, val target: String) : PhoneAction()
    /** «Защитник»: проверить ссылку или текст на мошенничество. */
    data class GuardCheck(val text: String?) : PhoneAction()
    /** «Что мне написали?» */
    object ReadMessages : PhoneAction()
    /** «Где телефон?» — громкий сигнал (on = true) или «нашёл» — выключить (on = false). */
    data class FindPhone(val on: Boolean = true) : PhoneAction()
}

enum class VolumeDir { UP, DOWN, MUTE, MAX }

enum class AppScreen { CORE, CHAT, TASKS, MARKET, SETTINGS }

/** Музыкальные приложения: как называем, пакет, понимает ли «играй по запросу». */
enum class MusicApp(val title: String, val pkg: String, val playFromSearch: Boolean, val words: List<String>) {
    YOUTUBE_MUSIC("YouTube Music", "com.google.android.apps.youtube.music", true,
        listOf("ютуб музык", "youtube music", "ютюб музык", "yt music")),
    SPOTIFY("Spotify", "com.spotify.music", true, listOf("спотиф", "spotify", "спотик")),
    YANDEX("Яндекс Музыка", "ru.yandex.music", true, listOf("яндекс", "yandex")),
    APPLE("Apple Music", "com.apple.android.music", true, listOf("эпл", "apple")),
    DEEZER("Deezer", "deezer.android.app", true, listOf("дизер", "deezer")),
    SOUNDCLOUD("SoundCloud", "com.soundcloud.android", true, listOf("саундклауд", "soundcloud")),
    YOUTUBE("YouTube", "com.google.android.youtube", false, listOf("ютуб", "youtube", "ютюб")),
    ;

    companion object {
        /** «в спотифае» → SPOTIFY. Длинные названия проверяем раньше коротких («ютуб музыка» раньше «ютуб»). */
        fun fromText(s: String): MusicApp? {
            val t = s.lowercase()
            return entries.firstOrNull { app -> app.words.any { t.contains(it) } }
        }
    }
}

enum class TaxiApp(val title: String, val pkg: String, val words: List<String>) {
    UKLON("Uklon", "ua.com.uklontaxi", listOf("уклон", "uklon")),
    BOLT("Bolt", "ee.mtakso.client", listOf("болт", "bolt")),
    UBER("Uber", "com.ubercab", listOf("убер", "uber")),
    ;

    companion object {
        fun fromText(s: String): TaxiApp? {
            val t = s.lowercase()
            return entries.firstOrNull { app -> app.words.any { t.contains(it) } }
        }
    }
}
