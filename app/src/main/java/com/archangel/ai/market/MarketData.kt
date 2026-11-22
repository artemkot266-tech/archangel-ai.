package com.archangel.ai.market

import com.archangel.ai.ai.Http

/** Данные рынка помимо биржи: индекс страха и жадности, новости. Ключи не нужны. */
object MarketData {
    private var fngCache: Pair<Long, FearGreed>? = null
    private var newsCache: Pair<Long, List<NewsItem>>? = null

    /** Индекс обновляется раз в сутки — кешируем на час. */
    suspend fun fearGreed(): FearGreed? {
        fngCache?.let { (t, v) -> if (System.currentTimeMillis() - t < 3_600_000) return v }
        val res = runCatching { Http.request("https://api.alternative.me/fng/?limit=1") }.getOrNull() ?: return null
        if (!res.ok) return null
        return Analytics.parseFearGreed(res.body)?.also { fngCache = System.currentTimeMillis() to it }
    }

    /** Свежие новости: ForkLog по-русски, при сбое — Cointelegraph. Кеш 15 минут. */
    suspend fun news(): List<NewsItem> {
        newsCache?.let { (t, v) -> if (System.currentTimeMillis() - t < 900_000 && v.isNotEmpty()) return v }
        for (url in listOf("https://forklog.com/feed", "https://cointelegraph.com/rss")) {
            val res = runCatching { Http.request(url, headers = mapOf("User-Agent" to "Mozilla/5.0 (Android) ArchangelAI")) }.getOrNull() ?: continue
            if (!res.ok) continue
            val items = Analytics.parseRss(res.body)
            if (items.isNotEmpty()) {
                newsCache = System.currentTimeMillis() to items
                return items
            }
        }
        return emptyList()
    }
}
