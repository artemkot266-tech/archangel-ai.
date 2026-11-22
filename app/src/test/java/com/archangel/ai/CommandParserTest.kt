package com.archangel.ai

import com.archangel.ai.phone.AppScreen
import com.archangel.ai.phone.CommandParser
import com.archangel.ai.phone.PhoneAction
import com.archangel.ai.phone.MusicApp
import com.archangel.ai.phone.TaxiApp
import com.archangel.ai.phone.VolumeDir
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {
    private fun p(s: String) = CommandParser.parse(s)

    @Test fun call() = assertEquals(PhoneAction.Call("маме"), p("Позвони маме"))
    @Test fun callWithWakeWord() = assertEquals(PhoneAction.Call("саше"), p("Архангел, набери Саше"))
    @Test fun alarm() = assertEquals(PhoneAction.Alarm(7, 30), p("Поставь будильник на 7:30"))
    @Test fun alarmHourOnly() = assertEquals(PhoneAction.Alarm(6, 0), p("разбуди меня в 6"))
    @Test fun timerMinutes() = assertEquals(PhoneAction.Timer(300), p("Таймер на 5 минут"))
    @Test fun timerSeconds() = assertEquals(PhoneAction.Timer(30), p("засеки 30 секунд"))
    @Test fun torchOn() = assertEquals(PhoneAction.Flashlight(true), p("Включи фонарик"))
    @Test fun torchOff() = assertEquals(PhoneAction.Flashlight(false), p("выключи фонарик"))
    @Test fun louder() = assertEquals(PhoneAction.Volume(VolumeDir.UP), p("сделай громче"))
    @Test fun openApp() = assertEquals(PhoneAction.OpenApp("телеграм"), p("Открой Телеграм"))
    @Test fun openMarketScreen() = assertEquals(PhoneAction.Screen(AppScreen.MARKET), p("открой рынок"))
    @Test fun chart() = assertEquals(PhoneAction.Market("BTCUSDT"), p("График биткоина"))
    @Test fun rate() = assertEquals(PhoneAction.Market("ETHUSDT"), p("курс эфира"))
    @Test fun buy() = assertEquals(PhoneAction.Order(true, "BTCUSDT", 20.0), p("Купи биткоин на 20 долларов"))
    @Test fun sell() = assertEquals(PhoneAction.Order(false, "SOLUSDT", 12.5), p("продай солану на 12,5"))
    @Test fun smsExplicit() = assertEquals(PhoneAction.Sms("саше", "привет"), p("отправь смс саше текст привет"))
    @Test fun smsWithChto() = assertEquals(PhoneAction.Sms("маме", "я опоздаю"), p("напиши маме что я опоздаю"))
    @Test fun poemIsNotSms() = assertNull(p("напиши стихотворение о море"))
    @Test fun questionGoesToBrain() = assertNull(p("Как работает биткоин?"))
    @Test fun navigate() = assertEquals(PhoneAction.Navigate("вокзала"), p("Проложи маршрут до вокзала"))
    @Test fun search() = assertEquals(PhoneAction.Search("рецепт борща"), p("найди рецепт борща"))
    @Test fun callNeedsConfirmation() = assertTrue(PhoneAction.Call("x").needsConfirmation)

    @Test fun actionTag() {
        val (text, action) = CommandParser.extractActionTag(
            "Ставлю будильник.\n[[ACTION {\"type\":\"alarm\",\"hour\":8,\"minute\":15,\"label\":\"работа\"}]]"
        )
        assertEquals("Ставлю будильник.", text)
        assertEquals(PhoneAction.Alarm(8, 15, "работа"), action)
    }

    @Test fun orderTag() {
        val (_, action) = CommandParser.extractActionTag(
            "Ок. [[ACTION {\"type\":\"order\",\"side\":\"SELL\",\"symbol\":\"ethusdt\",\"usdt\":15}]]"
        )
        assertEquals(PhoneAction.Order(false, "ETHUSDT", 15.0), action)
    }

    @Test fun noTag() {
        val (text, action) = CommandParser.extractActionTag("Просто ответ")
        assertEquals("Просто ответ", text)
        assertNull(action)
    }

    // ───── v0.10: YouTube, музыка, такси, заметки, напоминания, имя для вызова

    @Test fun youtubeAsks() = assertEquals(PhoneAction.YouTube(null), p("Открой ютуб"))
    @Test fun youtubeQuery() = assertEquals(PhoneAction.YouTube("гайд по доте 2"), p("включи на ютубе гайд по доте 2"))
    @Test fun youtubeFind() = assertEquals(PhoneAction.YouTube("как приготовить борщ"), p("найди на ютубе как приготовить борщ"))
    @Test fun musicAsks() = assertEquals(PhoneAction.Music(null, null), p("Архангел, включи музыку"))
    @Test fun musicInApp() = assertEquals(PhoneAction.Music(MusicApp.SPOTIFY, null), p("включи музыку в спотифае"))
    @Test fun musicArtistInApp() =
        assertEquals(PhoneAction.Music(MusicApp.SPOTIFY, "imagine dragons"), p("включи imagine dragons в спотифай"))
    @Test fun musicOnYoutube() = assertEquals(PhoneAction.Music(MusicApp.YOUTUBE, null), p("поставь музыку на ютубе"))
    @Test fun taxiAsks() = assertEquals(PhoneAction.Taxi(null, null), p("вызови такси"))
    @Test fun taxiFull() = assertEquals(PhoneAction.Taxi("вокзала", TaxiApp.UKLON), p("вызови такси до вокзала через уклон"))
    @Test fun noteAsks() = assertEquals(PhoneAction.Note(null), p("запиши заметку"))
    @Test fun noteText() = assertEquals(PhoneAction.Note("купить молоко"), p("запиши заметку: купить молоко"))
    @Test fun showNotes() = assertEquals(PhoneAction.ShowNotes, p("покажи мои заметки"))
    @Test fun customWakeName() = assertEquals(PhoneAction.Music(null, null), CommandParser.parse("Джарвис, включи музыку", listOf("Джарвис")))

    private fun now(): Calendar = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 22, 14, 0, 0); set(Calendar.MILLISECOND, 0)
    }

    @Test fun reminderTomorrow() {
        val a = CommandParser.parse("Напомни завтра в 9 позвонить в сервис", now = now()) as PhoneAction.Reminder
        assertEquals("позвонить в сервис", a.text)
        val c = Calendar.getInstance().apply { timeInMillis = a.at!! }
        assertEquals(23, c.get(Calendar.DAY_OF_MONTH)); assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun reminderIn20Minutes() {
        val a = CommandParser.parse("напомни через 20 минут выключить плиту", now = now()) as PhoneAction.Reminder
        assertEquals("выключить плиту", a.text)
        assertEquals(now().timeInMillis + 20 * 60_000, a.at)
    }

    @Test fun reminderEvening() {
        val a = CommandParser.parse("напомни в 7 вечера что надо забрать посылку", now = now()) as PhoneAction.Reminder
        assertEquals("надо забрать посылку", a.text)
        val c = Calendar.getInstance().apply { timeInMillis = a.at!! }
        assertEquals(22, c.get(Calendar.DAY_OF_MONTH)); assertEquals(19, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun reminderNoTime() = assertEquals(PhoneAction.Reminder("купить хлеб", null), p("напомни купить хлеб"))
    @Test fun reminderAsks() = assertEquals(PhoneAction.Reminder(null, null), p("напомни"))
    @Test fun answerCleanup() = assertEquals("гайд по доте 2", CommandParser.cleanAnswer("Включи гайд по доте 2"))
    @Test fun anything() = assertTrue(CommandParser.isAnything("да что угодно"))

    // ───── v0.11: память, погода, брифинг, портфель, цены, календарь
    @Test fun remember() = assertEquals(PhoneAction.Remember("я работаю до шести"), p("Запомни, что я работаю до шести"))
    @Test fun showMemory() = assertEquals(PhoneAction.ShowMemory, p("что ты обо мне знаешь"))
    @Test fun weatherToday() = assertEquals(PhoneAction.Weather(0), p("какая погода"))
    @Test fun weatherTomorrow() = assertEquals(PhoneAction.Weather(1), p("какая завтра погода"))
    @Test fun reminderAboutWeatherIsReminder() =
        assertTrue(p("напомни завтра в 8 посмотреть погоду") is PhoneAction.Reminder)
    @Test fun briefing() = assertEquals(PhoneAction.Briefing, p("брифинг"))
    @Test fun portfolio() = assertEquals(PhoneAction.Portfolio, p("покажи мой портфель"))
    @Test fun priceAlert() = assertEquals(PhoneAction.PriceAlert("BTCUSDT", 100000.0), p("сообщи когда биткоин будет 100 тысяч"))
    @Test fun priceAlertAsks() = assertEquals(PhoneAction.PriceAlert("ETHUSDT", null), p("предупреди если эфир упадёт"))
    @Test fun agenda() = assertEquals(PhoneAction.Agenda, p("что у меня сегодня"))
    @Test fun calendar() {
        val a = CommandParser.parse("добавь в календарь встречу с Олегом завтра в 15", now = now()) as PhoneAction.CalendarAdd
        assertEquals("встречу с олегом", a.title)
        val c = Calendar.getInstance().apply { timeInMillis = a.at!! }
        assertEquals(15, c.get(Calendar.HOUR_OF_DAY))
    }

    // ───── v0.12: умный дом, защитник, сообщения
    @Test fun lightOn() = assertEquals(PhoneAction.SmartHome(true, "свет в спальне"), p("включи свет в спальне"))
    @Test fun acOff() = assertEquals(PhoneAction.SmartHome(false, "кондиционер"), p("выключи кондиционер"))
    @Test fun torchStillTorch() = assertEquals(PhoneAction.Flashlight(true), p("включи фонарик"))
    @Test fun guardLink() = assertEquals(
        PhoneAction.GuardCheck("https://privat24-bonus.xyz/login"),
        CommandParser.parse("Проверь ссылку https://privat24-bonus.xyz/login"),
    )
    @Test fun guardAsks() = assertEquals(PhoneAction.GuardCheck(null), p("проверь ссылку"))
    @Test fun checkPortfolioIsNotGuard() = assertEquals(PhoneAction.Portfolio, p("мой портфель"))
    @Test fun messages() = assertEquals(PhoneAction.ReadMessages, p("что мне написали"))
    @Test fun guardianFlagsFake() {
        val v = com.archangel.ai.features.Guardian.analyze("https://privat24-bonus.xyz/login")
        assertTrue(v.danger >= 3)
    }
    @Test fun guardianOkOfficial() {
        val v = com.archangel.ai.features.Guardian.analyze("https://www.privat24.ua/")
        assertEquals(0, v.danger)
    }
}
