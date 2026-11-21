package com.archangel.ai.phone

import org.json.JSONObject

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
        // Падежи: «биткоина», «эфира», «солану»
        coins.entries.firstOrNull { (k, _) ->
            k.length >= 4 && k.first() in 'а'..'я' && w.startsWith(k.dropLast(1)) && w.length - k.length <= 3
        }
            ?.let { return it.value + "USDT" }
        // Уже тикер: «BTCUSDT» или «btc/usdt»
        val t = w.uppercase().replace("/", "").replace("-", "")
        if (t.matches(Regex("[A-Z0-9]{2,10}USDT"))) return t
        return null
    }

    fun parse(raw: String): PhoneAction? {
        val s = raw.lowercase()
            .replace('ё', 'е')
            .trim()
            .trimEnd('.', '!', '?')
            .removePrefix("архангел").trim(' ', ',')
        if (s.isEmpty()) return null

        // Разделы приложения
        Regex("^(открой|покажи|перейди в|перейди на)\\s+(рынок|биржу|графики?|трейдинг)$").find(s)
            ?.let { return PhoneAction.Screen(AppScreen.MARKET) }
        Regex("^(открой|покажи)\\s+(настройки архангела|настройки приложения)$").find(s)
            ?.let { return PhoneAction.Screen(AppScreen.SETTINGS) }

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
        if (Regex("(включи|зажги)\\s+(фонарик|фонарь|свет)").containsMatchIn(s)) return PhoneAction.Flashlight(true)
        if (Regex("(выключи|погаси)\\s+(фонарик|фонарь|свет)").containsMatchIn(s)) return PhoneAction.Flashlight(false)

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

    private val tagRegex = Regex("\\[\\[ACTION\\s*(\\{.*?\\})\\s*]]", RegexOption.DOT_MATCHES_ALL)

    /** Убирает тег действия из ответа ИИ и возвращает (чистый текст, действие). */
    fun extractActionTag(reply: String): Pair<String, PhoneAction?> {
        val m = tagRegex.find(reply) ?: return reply.trim() to null
        val clean = reply.replace(m.value, "").trim()
        return clean to parseActionTag(m.groupValues[1])
    }

    fun parseActionTag(json: String): PhoneAction? = try {
        val o = JSONObject(json)
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
{"type":"market","symbol":"BTCUSDT"}
{"type":"order","side":"BUY|SELL","symbol":"BTCUSDT","usdt":20}
Звонки, SMS и сделки пользователь подтверждает сам — не утверждай, что действие уже выполнено.
Если действие не нужно — тег не добавляй.
""".trimIndent()
}
