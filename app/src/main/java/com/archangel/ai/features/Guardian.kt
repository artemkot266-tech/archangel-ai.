package com.archangel.ai.features

import java.net.URI

/** «Защитник»: быстрые признаки мошеннических ссылок — без интернета и без ИИ. */
object Guardian {
    data class Verdict(val danger: Int, val flags: List<String>, val urls: List<String>)

    private val brands = listOf(
        "privat24", "privatbank", "monobank", "binance", "google", "paypal", "apple", "telegram", "whatsapp",
        "instagram", "facebook", "olx", "novaposhta", "nova-poshta", "diia", "oschadbank", "pumb", "raiffeisen",
        "bybit", "okx", "coinbase", "metamask", "trustwallet", "steam",
    )
    private val official = mapOf(
        "privat24" to listOf("privat24.ua", "privatbank.ua"), "privatbank" to listOf("privatbank.ua"),
        "monobank" to listOf("monobank.ua"), "binance" to listOf("binance.com"), "google" to listOf("google.com"),
        "paypal" to listOf("paypal.com"), "apple" to listOf("apple.com"), "telegram" to listOf("telegram.org", "t.me"),
        "whatsapp" to listOf("whatsapp.com"), "instagram" to listOf("instagram.com"), "facebook" to listOf("facebook.com"),
        "olx" to listOf("olx.ua"), "novaposhta" to listOf("novaposhta.ua"), "nova-poshta" to listOf("novaposhta.ua"),
        "diia" to listOf("diia.gov.ua"), "oschadbank" to listOf("oschadbank.ua"), "bybit" to listOf("bybit.com"),
        "okx" to listOf("okx.com"), "coinbase" to listOf("coinbase.com"), "metamask" to listOf("metamask.io"),
        "steam" to listOf("steampowered.com", "steamcommunity.com"),
    )
    private val shorteners = listOf("bit.ly", "tinyurl", "cutt.ly", "t.co", "goo.gl", "is.gd", "clck.ru", "shorturl", "rb.gy")
    private val riskyTld = listOf(".xyz", ".top", ".click", ".icu", ".monster", ".cfd", ".rest", ".zip", ".mov", ".live", ".shop", ".buzz")
    private val scamWords = listOf(
        "выиграли", "выигрыш", "приз", "компенсац", "выплат", "срочно", "заблокир", "подтвердите карту", "cvv", "код из смс",
        "пин-код", "пинкод", "безопасный счёт", "безопасный счет", "служба безопасности", "инвестиц", "удвоим", "airdrop",
        "seed", "сид-фраз", "передайте код", "перейдите по ссылке",
    )

    fun analyze(text: String): Verdict {
        val urls = Regex("(https?://[^\\s]+|www\\.[^\\s]+)").findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()
        val flags = ArrayList<String>()
        var danger = 0
        for (u in urls) {
            val host = runCatching { URI(if (u.startsWith("www.")) "http://$u" else u).host?.lowercase() }.getOrNull() ?: continue
            if (u.startsWith("http://")) { flags += "$host — без шифрования (http)"; danger += 1 }
            if (host.startsWith("xn--") || host.contains(".xn--")) { flags += "$host — замаскированные буквы (punycode)"; danger += 3 }
            if (Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(host)) { flags += "$host — вместо имени сайта IP-адрес"; danger += 2 }
            if (shorteners.any { host.contains(it) }) { flags += "$host — сокращённая ссылка скрывает адрес"; danger += 1 }
            if (riskyTld.any { host.endsWith(it) }) { flags += "$host — подозрительная доменная зона"; danger += 1 }
            if (u.contains("@")) { flags += "в ссылке есть «@» — классический приём подмены"; danger += 2 }
            if (host.count { it == '.' } >= 4) { flags += "$host — слишком много поддоменов"; danger += 1 }
            brands.filter { host.replace("-", "").contains(it.replace("-", "")) }.forEach { b ->
                val ok = official[b].orEmpty().any { host == it || host.endsWith(".$it") }
                if (!ok) { flags += "$host — похож на «$b», но это не официальный сайт"; danger += 3 }
            }
        }
        val low = text.lowercase()
        scamWords.filter { low.contains(it) }.take(3).forEach { flags += "фраза «$it» — частый приём мошенников"; danger += 1 }
        return Verdict(danger, flags.distinct(), urls)
    }

    fun summary(v: Verdict): String = when {
        v.urls.isEmpty() && v.flags.isEmpty() -> "Явных признаков мошенничества не вижу."
        v.danger >= 3 -> "Похоже на мошенничество. Не переходите и не вводите данные. Причины: " + v.flags.joinToString("; ") + "."
        v.danger >= 1 -> "Есть подозрения: " + v.flags.joinToString("; ") + ". Будьте осторожны."
        else -> "Явных признаков опасности не вижу, но не вводите пароли и коды из SMS, если не уверены в сайте."
    }
}
