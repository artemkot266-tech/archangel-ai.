package com.archangel.ai.features

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalEvent(val title: String, val begin: Long, val allDay: Boolean)

/** Календарь телефона: создать событие (через приложение календаря) и прочитать планы на день. */
object CalendarHelper {
    fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Открывает календарь с заполненным событием — пользователь видит его и нажимает «Сохранить». */
    fun insert(context: Context, title: String, begin: Long): Boolean = try {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    fun eventsForDay(context: Context, dayOffset: Int = 0): List<CalEvent> {
        if (!canRead(context)) return emptyList()
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, end)
        }.build()
        val out = ArrayList<CalEvent>()
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(CalEvent(c.getString(0) ?: "Событие", c.getLong(1), c.getInt(2) == 1))
                }
            }
        }
        return out
    }

    fun describe(events: List<CalEvent>): String {
        if (events.isEmpty()) return "В календаре на сегодня ничего нет."
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        return "В календаре: " + events.take(6).joinToString("; ") {
            if (it.allDay) "${it.title} (весь день)" else "${fmt.format(Date(it.begin))} — ${it.title}"
        } + "."
    }
}
