package com.archangel.ai.features

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Http
import org.json.JSONObject
import kotlin.math.roundToInt

data class WeatherNow(val tempNow: Int, val textNow: String, val days: List<WeatherDay>)
data class WeatherDay(val min: Int, val max: Int, val text: String, val rainChance: Int)

/** Погода от Open-Meteo: бесплатно, без ключа. Место — последнее известное местоположение телефона. */
object Weather {
    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    fun lastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    suspend fun fetch(context: Context): WeatherNow {
        val loc = lastLocation(context)
            ?: throw FriendlyException(
                if (hasLocationPermission(context)) "Не могу определить, где вы: включите геолокацию и откройте карты на минуту."
                else "Чтобы знать погоду, мне нужен доступ к примерному местоположению."
            )
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}&longitude=${loc.longitude}" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
            "&timezone=auto&forecast_days=2"
        val res = Http.request(url)
        if (!res.ok) throw FriendlyException("Сервис погоды недоступен.")
        val o = JSONObject(res.body)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val days = (0 until d.getJSONArray("time").length()).map { i ->
            WeatherDay(
                min = d.getJSONArray("temperature_2m_min").getDouble(i).roundToInt(),
                max = d.getJSONArray("temperature_2m_max").getDouble(i).roundToInt(),
                text = codeText(d.getJSONArray("weather_code").getInt(i)),
                rainChance = d.getJSONArray("precipitation_probability_max").optInt(i, 0),
            )
        }
        return WeatherNow(cur.getDouble("temperature_2m").roundToInt(), codeText(cur.getInt("weather_code")), days)
    }

    fun describe(w: WeatherNow, day: Int): String {
        val d = w.days.getOrNull(day) ?: return "Прогноза на этот день нет."
        val rain = if (d.rainChance >= 40) " Вероятность осадков ${d.rainChance}% — зонт не помешает." else ""
        return if (day == 0) {
            "Сейчас ${signed(w.tempNow)}°, ${w.textNow}. Днём до ${signed(d.max)}°, ночью ${signed(d.min)}°.$rain"
        } else {
            "Завтра ${d.text}, от ${signed(d.min)}° до ${signed(d.max)}°.$rain"
        }
    }

    private fun signed(t: Int) = if (t > 0) "+$t" else "$t"

    /** Коды WMO → по-русски. */
    fun codeText(code: Int) = when (code) {
        0 -> "ясно"
        1 -> "в основном ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55, 56, 57 -> "морось"
        61, 63, 65 -> "дождь"
        66, 67 -> "ледяной дождь"
        71, 73, 75, 77 -> "снег"
        80, 81, 82 -> "ливни"
        85, 86 -> "снегопад"
        95, 96, 99 -> "гроза"
        else -> "без осадков"
    }
}
