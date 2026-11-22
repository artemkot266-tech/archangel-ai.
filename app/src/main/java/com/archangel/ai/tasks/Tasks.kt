package com.archangel.ai.tasks

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.archangel.ai.MainActivity
import com.archangel.ai.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Long, val text: String, val created: Long)
data class Reminder(val id: Long, val text: String, val at: Long)

/** Заметки и напоминания — простые JSON-файлы во внутренней памяти приложения. */
class TaskStore(private val context: Context) {
    private val notesFile = File(context.filesDir, "notes.json")
    private val remindersFile = File(context.filesDir, "reminders.json")

    fun notes(): List<Note> = read(notesFile).map {
        Note(it.getLong("id"), it.getString("text"), it.optLong("created"))
    }.sortedByDescending { it.created }

    fun reminders(): List<Reminder> = read(remindersFile).map {
        Reminder(it.getLong("id"), it.getString("text"), it.getLong("at"))
    }.sortedBy { it.at }

    fun addNote(text: String): Note {
        val n = Note(System.currentTimeMillis(), text, System.currentTimeMillis())
        write(notesFile, (notes() + n).map { JSONObject().put("id", it.id).put("text", it.text).put("created", it.created) })
        return n
    }

    fun deleteNote(id: Long) =
        write(notesFile, notes().filter { it.id != id }.map { JSONObject().put("id", it.id).put("text", it.text).put("created", it.created) })

    fun addReminder(text: String, at: Long): Reminder {
        val r = Reminder(System.currentTimeMillis(), text, at)
        saveReminders(reminders() + r)
        ReminderScheduler.schedule(context, r)
        return r
    }

    fun deleteReminder(id: Long) {
        ReminderScheduler.cancel(context, id)
        saveReminders(reminders().filter { it.id != id })
    }

    /** Убирает сработавшие напоминания (старше минуты). */
    fun pruneFired() = saveReminders(reminders().filter { it.at > System.currentTimeMillis() - 60_000 })

    private fun saveReminders(list: List<Reminder>) =
        write(remindersFile, list.map { JSONObject().put("id", it.id).put("text", it.text).put("at", it.at) })

    private fun read(f: File): List<JSONObject> = try {
        if (!f.exists()) emptyList() else JSONArray(f.readText()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(f: File, items: List<JSONObject>) {
        val a = JSONArray(); items.forEach { a.put(it) }
        runCatching { f.writeText(a.toString()) }
    }
}

/** Ставит напоминания в системный будильник (AlarmManager) — сработают, даже если приложение закрыто. */
object ReminderScheduler {
    const val CHANNEL = "reminders"

    private fun pending(context: Context, id: Long, text: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("text", text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, r.id, r.text)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, id))
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Напоминания Архангела" }
                )
            }
        }
    }
}

/** Срабатывание напоминания: показывает уведомление. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text").orEmpty().ifBlank { "Напоминание" }
        val id = intent.getLongExtra("id", 0L)
        ReminderScheduler.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Архангел напоминает")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) runCatching { NotificationManagerCompat.from(context).notify(id.toInt(), n) }
        TaskStore(context).pruneFired()
    }
}

/** После перезагрузки телефона будильники сбрасываются — ставим напоминания заново. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = TaskStore(context)
        store.pruneFired()
        store.reminders().forEach { ReminderScheduler.schedule(context, it) }
    }
}
