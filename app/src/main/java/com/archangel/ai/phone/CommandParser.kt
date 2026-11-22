package com.archangel.ai.phone

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Быстрый разбор голосовых команд прямо на телефоне — работает без ИИ и без интернета.
 * Всё, что сюда не подошло, уходит «мозгу», который может вернуть действие тегом
 * [[ACTION {...}]] (см. [parseActionTag]).
 */
object CommandParser {

    private val coins = linkedMapOf(
        "биткоин" to "BTC", "биткойн" to "BTC", "биток" to "BTC", "битка" to "BTC", "btc" to "BTC", "bitcoin" to "BTC",
        "эфириум" to "ETH", "эфир" to "ETH", "eth" to "ETH", "ethereum" to "ETH",
        "солана" to "SOL", "солану" to "SOL", "sol" to "SOL", "solana" to "SOL",
        "бнб" to "BNB", "bnb" to "BNB",
        "рипл" to "XRP", "xrp" to "XRP",
        "доги" to "DOGE", "додж" to "DOGE", "doge" to "DOGE",
        "тон" to "TON", "ton" to "TON",
        "кардано" to "ADA", "ada" to "ADA",
        "трон" to "TRX", "trx" to "TRX",
        "лайткоин" to "LTC", "ltc" to "LTC",
        "полкадот" to "DOT", "dot" to "DOT",
        "чейнлинк" to "LINK", "link" to "LINK",
        "пепе" to "PEPE", "pepe" to "PEPE",
    )

    /** «биткоина», «эфиром» → тикер пары к USDT, например BTCUSDT. */
    fun coinToSymbol(word: String): String? {
        val w = word.lowercase().trim().trim('.', ',', '?', '!')
        if (w.isEmpty()) return null
        coins[w]?.let { return it + "USDT" }
        coins.entries.firstOrNull { (k, _) ->
            k.length >= 4 && k.first() in 'а'..'я' && w.startsWith(k.dropLast(1)) && w.length - k.length <= 3
        }?.let { return it.value + "USDT" }
        val t = w.uppercase().replace("/", "").replace("-", "")
        if (t.matches(Regex("[A-Z0-9]{2,10}USDT"))) return t
        return null
    }

    /** Убирает обращение в начале: «Архангел, …», «Джарвис …». */
    fun stripWakeName(s: String, wakeNames: List<String>): String {
        var t = s
        for (n in wakeNames.map { it.lowercase().trim() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            if (t.startsWith(n)) {
                t = t.removePrefix(n).trim(' ', ',', '.', '!')
                break
            }
        }
        return t
    }

    /**
     * Ищет имя помощника во фразе — с допуском ошибок распознавания
     * («архангела», «джервис» вместо «джарвис»). Возвращает позицию сразу после имени или null.
     */
    fun findWakeName(text: String, wakeNames: List<String>): Int? {
        val norm = text.lowercase().replace('ё', 'е')
        val names = wakeNames.map { it.lowercase().replace('ё', 'е').trim() }.filter { it.length >= 2 }
        names.mapNotNull { n -> norm.indexOf(n).takeIf { it >= 0 }?.let { it + n.length } }.minOrNull()?.let { end ->
            // «архангелА позови» — окончание (до 2 букв) относится к имени, а не к команде
            var e = end
            while (e < norm.length && e - end < 2 && norm[e].isLetter()) e++
            return if (e < norm.length && norm[e].isLetter()) end else e
        }
        // Нечёткое совпадение по отдельным словам (только для однословных имён от 4 букв)
        val single = names.filter { ' ' !in it && it.length >= 4 }
        for (m in Regex("[\\p{L}]+").findAll(norm)) {
            val w = m.value
            for (n in single) {
                val allowed = if (n.length >= 6) 2 else 1
                // Падежное окончание: «архангела», «джарвису» — отбрасываем до 2 букв в конце
                val stem = if (n.length >= 5 && w.length in (n.length + 1)..(n.length + 2) && w.startsWith(n.dropLast(1))) w.take(n.length) else w
                if (kotlin.math.abs(stem.length - n.length) <= allowed && distance(stem, n) <= allowed) return m.range.last + 1
            }
        }
        return null
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun normalize(raw: String, wakeNames: List<String> = listOf("архангел")): String =
        stripWakeName(
            raw.lowercase().replace('ё', 'е').trim().trimEnd('.', '!', '?'),
            wakeNames + "архангел",
        )

    fun parse(raw: String, wakeNames: List<String> = listOf("архангел"), now: Calendar = Calendar.getInstance()): PhoneAction? {
        val s = normalize(raw, wakeNames)
        if (s.isEmpty()) return null

        // Разделы приложения
        Regex("^(открой|покажи|перейди в|перейди на)\\s+(рынок|биржу|графики?|трейдинг)$").find(s)
            ?.let { return PhoneAction.Screen(AppScreen.MARKET) }
        Regex("^(открой|покажи)\\s+(настройки архангела|настройки приложения)$").find(s)
            ?.let { return PhoneAction.Screen(AppScreen.SETTINGS) }

        // «Где телефон?» — громкий сигнал; «нашёл» — выключить
        Regex("(?iu)^(где (же )?(мой |ты,? )?(телефон|телефончик|смартфон|мобильник|мобила)|где ты|ау|отзовись|подай голос|найди (мой )?(телефон|смартфон)|(я )?(потерял|потеряла) (свой |мой )?(телефон|смартфон)|позвони на (мой )?телефон)$")
            .find(s)?.let { return PhoneAction.FindPhone(true) }
        Regex("(?iu)^(нашел|нашла|нашли|я тебя нашел|я тебя нашла|хватит звонить|выключи сигнал|стоп сигнал)$")
            .find(s)?.let { return PhoneAction.FindPhone(false) }

        // «Защитник»: проверка ссылок и подозрительных сообщений
        val hasUrl = Regex("(https?://|www\\.)\\S+").containsMatchIn(s)
        val guardWords = Regex("(ссылк|сайт|сообщени|смс|номер|развод|мошен|безопасн)").containsMatchIn(s)
        if ((s.startsWith("провер") && (hasUrl || guardWords)) || s.startsWith("это развод") || s.startsWith("это мошен")) {
            val rest = Regex("(?iu)^\\s*([\\p{L}]+[,!]\\s+)?(проверь|проверить|провери)?\\s*(ссылку|сайт|сообщение|смс|это|номер)?\\s*[:,]?\\s*")
                .replace(raw.trim(), "")
            return PhoneAction.GuardCheck(rest.trim().ifEmpty { null })
        }

        // Сообщения из уведомлений
        Regex("^(что мне (написали|пишут|пришло)|прочитай (мои |новые |последние )?(сообщения|уведомления)|(есть )?новые сообщения|кто мне (написал|писал))").find(s)?.let {
            return PhoneAction.ReadMessages
        }

        // Умный дом: «включи свет в спальне», «выключи кондиционер»
        Regex("^(включи|выключи|зажги|погаси|запусти|останови)\\s+(.*?)(свет|лампу|лампы|люстру|подсветку|розетку|кондиционер|обогреватель|телевизор|пылесос|чайник|вентилятор|увлажнитель|бойлер|телик|гирлянду)(.*)$").find(s)?.let { m ->
            val verb = m.groupValues[1]
            val on = verb in setOf("включи", "зажги", "запусти")
            val target = (m.groupValues[2] + m.groupValues[3] + m.groupValues[4]).trim()
            return PhoneAction.SmartHome(on, target)
        }

        // Память
        Regex("^(что ты (обо мне|про меня) (знаешь|помнишь)|что ты помнишь)").find(s)?.let { return PhoneAction.ShowMemory }
        Regex("^запомни\\s*[:,]?\\s*(что\\s+)?(.*)$").find(s)?.let { return PhoneAction.Remember(it.groupValues[2].trim().ifEmpty { null }) }
        Regex("^забудь\\s*[:,]?\\s*(что\\s+|про\\s+|о\\s+)?(.+)$").find(s)?.let { return PhoneAction.Forget(it.groupValues[2].trim()) }

        // Погода и брифинг
        Regex("^(какая|какой|что с|что по|скажи|покажи|расскажи)?\\s*(будет\\s+)?(сегодня\\s+|завтра\\s+)?(погода|погоду|прогноз)").find(s)?.let {
            return PhoneAction.Weather(if (s.contains("завтра")) 1 else 0)
        }
        Regex("^(брифинг|сводка|сводку|утренний брифинг|дай сводку|расскажи сводку|что нового сегодня)").find(s)?.let { return PhoneAction.Briefing }
        Regex("^(что у меня (сегодня|на сегодня|по планам)|какие у меня планы|мои планы на сегодня|расписание на сегодня)").find(s)?.let {
            return PhoneAction.Agenda
        }

        // Портфель и ценовые уведомления
        Regex("^((покажи|какой|какая|что с|открой)\\s+)?(мой\\s+)?(портфель|баланс)|^сколько (у меня )?денег на бирже").find(s)?.let {
            return PhoneAction.Portfolio
        }
        Regex("^(сообщи|уведоми|предупреди|скажи|напиши)\\s+(мне\\s+)?(когда|если)\\s+(\\S+)\\s+(.*)$").find(s)?.let { m ->
            val sym = coinToSymbol(m.groupValues[4])
            if (sym != null) {
                val price = Regex("([0-9][0-9 ]*(?:[.,][0-9]+)?)\\s*(тысяч|тыс|к)?").find(m.groupValues[5])?.let { pm ->
                    val base = pm.groupValues[1].replace(" ", "").replace(',', '.').toDoubleOrNull()
                    if (base != null && pm.groupValues[2].isNotEmpty()) base * 1000 else base
                }
                return PhoneAction.PriceAlert(sym, price)
            }
        }

        // Календарь: «добавь в календарь встречу завтра в 15»
        Regex("^(добавь|создай|запиши|поставь)\\s+(в календарь|событие|встречу в календарь)\\s*[:,]?\\s*(.*)$").find(s)?.let {
            val (at, rest) = parseWhen(it.groupValues[3], now)
            return PhoneAction.CalendarAdd(rest.trim().ifEmpty { null }, at)
        }

        // Заметки и напоминания
        Regex("^(покажи|прочитай|открой|какие)\\s+(у меня\\s+)?(мои\\s+)?заметки").find(s)?.let { return PhoneAction.ShowNotes }
        Regex("^(покажи|прочитай|открой|какие)\\s+(у меня\\s+)?(мои\\s+)?напоминания").find(s)?.let { return PhoneAction.ShowReminders }
        Regex("^(запиши|сделай|создай|добавь|новая)\\s+(мне\\s+)?(заметку|запись|заметка)\\s*[:,]?\\s*(.*)$").find(s)?.let {
            return PhoneAction.Note(it.groupValues[4].trim().ifEmpty { null })
        }
        Regex("^(заметка|запиши)\\s*[:,]?\\s+(.+)$").find(s)?.let { return PhoneAction.Note(it.groupValues[2].trim()) }
        Regex("^(напомни|напоминание|поставь напоминание)(\\s+мне)?\\s*[:,]?\\s*(.*)$").find(s)?.let {
            val (at, rest) = parseWhen(it.groupValues[3], now)
            return PhoneAction.Reminder(cleanReminderText(rest), at)
        }

        // Такси: «вызови такси до вокзала через уклон»
        Regex("^(вызови|закажи|вызвать|заказать|нужно|нужно такси)\\s+(мне\\s+)?такси\\s*(.*)$").find(s)?.let {
            var rest = it.groupValues[3]
            val app = TaxiApp.fromText(rest)
            rest = rest.replace(Regex("(через|в|на)\\s+(уклон|uklon|болт|bolt|убер|uber)[\\p{L}\\d_]*"), "").trim()
            val dest = Regex("^(до|в|на|к|ко)\\s+(.+)$").find(rest)?.groupValues?.get(2)?.trim()
            return PhoneAction.Taxi(dest?.ifEmpty { null }, app)
        }

        // Музыка: «включи музыку», «поставь музыку в спотифае», «включи imagine dragons в спотифай»
        Regex("^(включи|поставь|запусти|играй|сыграй|давай)\\s+(мне\\s+)?(музыку|музон|песню|песни|трек|плейлист|радио)\\s*(.*)$").find(s)?.let {
            val rest = it.groupValues[4].trim()
            val app = MusicApp.fromText(rest)
            val q = stripAppMention(rest).ifEmpty { null }
            return PhoneAction.Music(app, q)
        }
        Regex("^(включи|поставь|запусти|играй)\\s+(.+?)\\s+(в|на|через)\\s+(спотифа[\\p{L}\\d_]*|spotify|ютуб музык[\\p{L}\\d_]*|youtube music|яндекс музык[\\p{L}\\d_]*|саундклауд[\\p{L}\\d_]*|soundcloud|дизер[\\p{L}\\d_]*|deezer)$").find(s)?.let {
            return PhoneAction.Music(MusicApp.fromText(it.groupValues[4]), it.groupValues[2].trim())
        }

        // YouTube: «открой ютуб», «включи на ютубе гайд по доте 2», «найди на ютубе …»
        Regex("^(открой|запусти|включи|найди|покажи|поставь)?\\s*(мне\\s+)?(на\\s+|в\\s+)?(ютуб|youtube|ютюб)[\\p{L}\\d_]*\\s*(.*)$").find(s)?.let {
            val verb = it.groupValues[1]
            val rest = it.groupValues[5].trim()
            if (rest.startsWith("музык")) return PhoneAction.Music(MusicApp.YOUTUBE_MUSIC, null)
            if (verb.isNotEmpty() || it.groupValues[3].isNotEmpty() || s.startsWith("ютуб") || s.startsWith("youtube")) {
                val q = rest.removePrefix("и ").removePrefix("найди ").removePrefix("включи ").trim()
                return PhoneAction.YouTube(q.ifEmpty { null })
            }
        }

        // Звонок
        Regex("^(позвони|набери|звони|вызови)\\s+(.+)$").find(s)?.let {
            return PhoneAction.Call(it.groupValues[2].trim())
        }

        // SMS: «отправь смс саше текст привет», «напиши маме что я опоздаю».
        // Без слова «смс/сообщение» требуем «что», иначе «напиши стих о море» станет SMS.
        Regex("^(напиши|отправь)\\s+(смс|сообщение|эсэмэску)\\s+(\\S+)\\s+(?:что|текст|:)?\\s*(.+)$").find(s)?.let {
            return PhoneAction.Sms(it.groupValues[3], it.groupValues[4].trim())
        }
        Regex("^(напиши|отправь)\\s+(\\S+)\\s+что\\s+(.+)$").find(s)?.let {
            return PhoneAction.Sms(it.groupValues[2], it.groupValues[3].trim())
        }

        // Будильник: «поставь будильник на 7:30», «разбуди меня в 6»
        Regex("(будильник|разбуди)[^0-9]*(\\d{1,2})(?:[:. ](\\d{2}))?").find(s)?.let {
            val h = it.groupValues[2].toInt()
            val m = it.groupValues[3].toIntOrNull() ?: 0
            if (h in 0..23 && m in 0..59) return PhoneAction.Alarm(h, m)
        }

        // Таймер: «таймер на 5 минут», «засеки 30 секунд»
        Regex("(таймер|засеки)[^0-9]*(\\d+)\\s*(сек|мин|час)").find(s)?.let {
            val n = it.groupValues[2].toInt()
            val sec = when {
                it.groupValues[3].startsWith("сек") -> n
                it.groupValues[3].startsWith("мин") -> n * 60
                else -> n * 3600
            }
            if (sec in 1..86_400) return PhoneAction.Timer(sec)
        }

        // Фонарик
        if (Regex("(включи|зажги)\\s+(фонарик|фонарь)").containsMatchIn(s)) return PhoneAction.Flashlight(true)
        if (Regex("(выключи|погаси)\\s+(фонарик|фонарь)").containsMatchIn(s)) return PhoneAction.Flashlight(false)

        // Громкость
        when {
            Regex("(громче|прибавь звук|увеличь громкость)").containsMatchIn(s) -> return PhoneAction.Volume(VolumeDir.UP)
            Regex("(тише|убавь звук|уменьши громкость)").containsMatchIn(s) -> return PhoneAction.Volume(VolumeDir.DOWN)
            Regex("(выключи звук|без звука|беззвучн)").containsMatchIn(s) -> return PhoneAction.Volume(VolumeDir.MUTE)
            Regex("(звук на максимум|максимальная громкость|на полную)").containsMatchIn(s) -> return PhoneAction.Volume(VolumeDir.MAX)
        }

        // Навигация
        Regex("^(маршрут|проложи маршрут|как доехать|как добраться|навигация|поехали)\\s+(до|в|к|на)?\\s*(.+)$").find(s)?.let {
            return PhoneAction.Navigate(it.groupValues[3].trim())
        }

        // Сделки: «купи биткоин на 20 долларов», «продай эфир на 15»
        Regex("^(купи|продай)\\s+(\\S+)\\s+на\\s+([0-9]+(?:[.,][0-9]+)?)").find(s)?.let {
            val symbol = coinToSymbol(it.groupValues[2])
            val amount = it.groupValues[3].replace(',', '.').toDoubleOrNull()
            if (symbol != null && amount != null && amount > 0) {
                return PhoneAction.Order(buy = it.groupValues[1] == "купи", symbol = symbol, usdt = amount)
            }
        }

        // Рынок: «график биткоина», «курс эфира», «анализ солану»
        Regex("^(?:покажи\\s+)?(график|курс|цена|цену|анализ|разбор)\\s+(\\S+)").find(s)?.let {
            coinToSymbol(it.groupValues[2])?.let { sym -> return PhoneAction.Market(sym) }
        }

        // Открыть приложение
        Regex("^(открой|запусти|включи)\\s+(.+)$").find(s)?.let {
            return PhoneAction.OpenApp(it.groupValues[2].trim())
        }

        // Поиск
        Regex("^(найди в интернете|загугли|погугли|поищи|найди)\\s+(.+)$").find(s)?.let {
            return PhoneAction.Search(it.groupValues[2].trim())
        }

        return null
    }

    /** «imagine dragons в спотифае» → «imagine dragons». */
    fun stripAppMention(s: String): String =
        s.replace(Regex("\\s*(в|на|через)\\s+(спотифа[\\p{L}\\d_]*|spotify|ютуб[\\p{L}\\d_]*\\s*музык[\\p{L}\\d_]*|youtube music|ютуб[\\p{L}\\d_]*|youtube|яндекс\\s*музык[\\p{L}\\d_]*|яндекс[\\p{L}\\d_]*|эпл[\\p{L}\\d_]*|apple music|дизер[\\p{L}\\d_]*|deezer|саундклауд[\\p{L}\\d_]*|soundcloud)\\s*$"), "")
            .trim()

    /** Ответ на уточняющий вопрос: «включи гайд по доте» → «гайд по доте». */
    fun cleanAnswer(raw: String, wakeNames: List<String> = emptyList()): String {
        var s = normalize(raw, wakeNames)
        val lead = Regex("^(давай|пожалуйста|ну|хочу|найди|включи|поставь|открой|покажи|запусти|в|на|про|это)\\s+")
        repeat(3) { s = s.replace(lead, "") }
        return s.trim()
    }

    /** «что угодно», «любую», «на твой вкус» — пользователь доверил выбор. */
    fun isAnything(s: String) =
        Regex("(что угодно|любую|любое|любой|на твой вкус|на свой вкус|без разницы|неважно|что-нибудь|что нибудь)").containsMatchIn(s.lowercase())

    fun isCancel(s: String) =
        Regex("^(отмена|отмени|не надо|ничего|стоп|забудь|неважно, отмена|не нужно)").containsMatchIn(s.lowercase().trim())

    // ───────────── Время для напоминаний ─────────────

    private fun cleanReminderText(s: String): String? =
        s.trim(' ', ',', '.', ':')
            .replace(Regex("^(о том,?\\s+что|о том,?\\s+чтобы|что|чтобы|про|о|об)\\s+"), "")
            .trim()
            .ifEmpty { null }

    /**
     * Ищет время в фразе и возвращает (момент в мс или null, остаток текста).
     * Понимает: «через 20 минут», «через час», «через полчаса», «в 18:30», «завтра в 9 утра»,
     * «сегодня в 7 вечера», «послезавтра», «завтра».
     */
    fun parseWhen(text: String, now: Calendar = Calendar.getInstance()): Pair<Long?, String> {
        var s = " ${text.lowercase().replace('ё', 'е')} "
        Regex("\\s+через\\s+(\\d+|пару|полчаса|час|минуту)\\s*(секунд[\\p{L}\\d_]*|сек|минут[\\p{L}\\d_]*|мин|час[\\p{L}\\d_]*|ч)?\\s+").find(s)?.let { m ->
            val nRaw = m.groupValues[1]
            val unit = m.groupValues[2]
            val minutes = when {
                nRaw == "полчаса" -> 30
                nRaw == "час" -> 60
                nRaw == "минуту" -> 1
                nRaw == "пару" -> if (unit.startsWith("час")) 120 else 2
                unit.startsWith("сек") -> 0
                unit.startsWith("час") || unit == "ч" -> nRaw.toInt() * 60
                else -> nRaw.toInt()
            }
            val seconds = if (unit.startsWith("сек")) nRaw.toIntOrNull() ?: 0 else minutes * 60
            val c = now.clone() as Calendar
            c.add(Calendar.SECOND, seconds)
            s = s.replace(m.value, " ")
            return c.timeInMillis to s.trim()
        }
        val day = Regex("\\s(сегодня|завтра|послезавтра)\\s").find(s)?.groupValues?.get(1)
        val timeM = Regex("\\s(?:в|на)\\s+(\\d{1,2})(?:[:. ](\\d{2}))?\\s*(утра|дня|вечера|ночи|часов|час|часа)?\\s").find(s)
        if (day == null && timeM == null) return null to text.trim()
        val c = now.clone() as Calendar
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        var hour = 9
        var minute = 0
        if (timeM != null) {
            hour = timeM.groupValues[1].toInt()
            minute = timeM.groupValues[2].toIntOrNull() ?: 0
            when (timeM.groupValues[3]) {
                "дня" -> if (hour in 1..11) hour += 12
                "вечера" -> if (hour in 1..11) hour += 12
                "ночи" -> if (hour == 12) hour = 0
            }
            if (hour !in 0..23 || minute !in 0..59) return null to text.trim()
            s = s.replace(timeM.value, " ")
        }
        when (day) {
            "завтра" -> c.add(Calendar.DAY_OF_YEAR, 1)
            "послезавтра" -> c.add(Calendar.DAY_OF_YEAR, 2)
        }
        if (day != null) s = s.replace(Regex("\\s$day\\s"), " ")
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        // «в 7» без дня, а 7 уже прошло — значит, завтра
        if (day == null && c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis to s.trim()
    }

    /** «завтра в 09:00», «сегодня в 18:30», «22 ноября в 10:00». */
    fun describeTime(at: Long, now: Calendar = Calendar.getInstance()): String {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val time = SimpleDateFormat("HH:mm", Locale.US).format(c.time)
        val today = now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            .let { it.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR) && it.get(Calendar.YEAR) == c.get(Calendar.YEAR) }
        return when {
            today -> "сегодня в $time"
            tomorrow -> "завтра в $time"
            else -> SimpleDateFormat("d MMMM 'в' HH:mm", Locale("ru")).format(c.time)
        }
    }

    // ───────────── Теги действий от ИИ ─────────────

    private val tagRegex = Regex("\\[\\[ACTION\\s*(\\{.*?\\})\\s*]]", RegexOption.DOT_MATCHES_ALL)

    /** Убирает тег действия из ответа ИИ и возвращает (чистый текст, действие). */
    fun extractActionTag(reply: String): Pair<String, PhoneAction?> {
        val m = tagRegex.find(reply) ?: return reply.trim() to null
        val clean = reply.replace(m.value, "").trim()
        return clean to parseActionTag(m.groupValues[1])
    }

    fun parseActionTag(json: String): PhoneAction? = try {
        val o = JSONObject(json)
        fun str(k: String) = o.optString(k).trim().ifEmpty { null }
        when (o.optString("type")) {
            "call" -> PhoneAction.Call(o.getString("target"))
            "sms" -> PhoneAction.Sms(o.getString("target"), o.optString("text"))
            "open_app" -> PhoneAction.OpenApp(o.getString("name"))
            "alarm" -> PhoneAction.Alarm(o.getInt("hour"), o.optInt("minute", 0), o.optString("label"))
            "timer" -> PhoneAction.Timer(o.getInt("seconds"), o.optString("label"))
            "flashlight" -> PhoneAction.Flashlight(o.optBoolean("on", true))
            "volume" -> PhoneAction.Volume(
                when (o.optString("dir")) {
                    "down" -> VolumeDir.DOWN; "mute" -> VolumeDir.MUTE; "max" -> VolumeDir.MAX; else -> VolumeDir.UP
                }
            )
            "search" -> PhoneAction.Search(o.getString("query"))
            "navigate" -> PhoneAction.Navigate(o.getString("destination"))
            "market" -> PhoneAction.Market(o.getString("symbol").uppercase())
            "order" -> PhoneAction.Order(
                buy = o.optString("side").uppercase() != "SELL",
                symbol = o.getString("symbol").uppercase(),
                usdt = o.getDouble("usdt"),
            ).takeIf { it.usdt > 0 }
            "youtube" -> PhoneAction.YouTube(str("query"))
            "music" -> PhoneAction.Music(str("app")?.let { MusicApp.fromText(it) }, if (o.has("query")) o.optString("query") else null)
            "taxi" -> PhoneAction.Taxi(str("destination"), str("app")?.let { TaxiApp.fromText(it) })
            "note" -> PhoneAction.Note(str("text"))
            "reminder" -> PhoneAction.Reminder(
                str("text"),
                str("at")?.let { runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(it)?.time }.getOrNull() },
            )
            "calendar" -> PhoneAction.CalendarAdd(
                str("title"),
                str("at")?.let { runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(it)?.time }.getOrNull() },
            )
            "price_alert" -> PhoneAction.PriceAlert(o.getString("symbol").uppercase(), o.optDouble("price").takeIf { !it.isNaN() })
            "remember" -> PhoneAction.Remember(str("fact"))
            "find_phone" -> PhoneAction.FindPhone(o.optBoolean("on", true))
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /** Описание возможностей для системной подсказки «мозгу». */
    val ACTION_PROMPT = """
Если пользователь просит выполнить действие на телефоне или бирже, добавь в САМЫЙ КОНЕЦ ответа одну строку:
[[ACTION {json}]]
Варианты json (ровно один):
{"type":"call","target":"имя контакта или номер"}
{"type":"sms","target":"имя или номер","text":"текст"}
{"type":"open_app","name":"название приложения"}
{"type":"alarm","hour":7,"minute":30,"label":"подпись"}
{"type":"timer","seconds":300,"label":"подпись"}
{"type":"flashlight","on":true}
{"type":"volume","dir":"up|down|mute|max"}
{"type":"search","query":"запрос"}
{"type":"navigate","destination":"адрес или место"}
{"type":"youtube","query":"что найти"}
{"type":"music","app":"spotify|youtube music|youtube|яндекс","query":"что включить"}
{"type":"taxi","destination":"куда","app":"uklon|bolt|uber"}
{"type":"note","text":"текст заметки"}
{"type":"reminder","text":"о чём","at":"2026-01-31T09:00"}
{"type":"calendar","title":"событие","at":"2026-01-31T15:00"}
{"type":"price_alert","symbol":"BTCUSDT","price":100000}
{"type":"find_phone","on":true}  (пользователь ищет телефон: громкий сигнал; on:false — выключить)
{"type":"remember","fact":"факт о пользователе — ТОЛЬКО если он сам попросил запомнить"}
{"type":"market","symbol":"BTCUSDT"}
{"type":"order","side":"BUY|SELL","symbol":"BTCUSDT","usdt":20}
Если для действия не хватает данных (что искать, что включить, куда ехать, когда напомнить) — НЕ добавляй тег,
а коротко задай один уточняющий вопрос. Поля, которых пользователь не назвал, просто не указывай.
Звонки, SMS и сделки пользователь подтверждает сам — не утверждай, что действие уже выполнено.
Если действие не нужно — тег не добавляй.
""".trimIndent()
}
