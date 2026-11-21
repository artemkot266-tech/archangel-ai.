package com.archangel.ai.phone

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Найденный контакт для звонка/SMS. */
data class Contact(val name: String, val number: String)

/**
 * Выполняет действия на телефоне через стандартные интенты Android.
 * Никаких скрытых привилегий: всё, что делает Архангел, видно на экране.
 */
class PhoneController(private val context: Context) {

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    val canReadContacts get() = has(Manifest.permission.READ_CONTACTS)

    /** «маме» → «Мама». Ищем по началу слов, срезая падежные окончания. */
    fun findContact(query: String): Contact? {
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 3) return Contact(query, digits)
        if (!canReadContacts) return null
        val q = query.lowercase().replace('ё', 'е').trim()
        val stems = listOf(q, q.dropLast(1), q.dropLast(2)).filter { it.length >= 2 }.distinct()
        val all = ArrayList<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                all.add(Contact(name, number))
            }
        }
        for (stem in stems) {
            all.firstOrNull { it.name.lowercase().replace('ё', 'е') == stem }?.let { return it }
            all.firstOrNull { c ->
                c.name.lowercase().replace('ё', 'е').split(' ', '-').any { it.startsWith(stem) }
            }?.let { return it }
        }
        return null
    }

    /** Выполняет действие и возвращает короткий отчёт для чата/озвучки. */
    fun execute(action: PhoneAction): String = when (action) {
        is PhoneAction.Call -> {
            val contact = findContact(action.target)
            when {
                contact == null && !canReadContacts ->
                    "Не могу найти «${action.target}»: разрешите доступ к контактам."
                contact == null -> "Контакт «${action.target}» не найден."
                has(Manifest.permission.CALL_PHONE) &&
                    start(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(contact.number)}"))) ->
                    "Звоню: ${contact.name}."
                start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.number)}"))) ->
                    "Открыл номер ${contact.name} — нажмите вызов."
                else -> "Не получилось открыть звонилку."
            }
        }
        is PhoneAction.Sms -> {
            val contact = findContact(action.target)
            if (contact == null) {
                if (canReadContacts) "Контакт «${action.target}» не найден."
                else "Не могу найти «${action.target}»: разрешите доступ к контактам."
            } else {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.number)}"))
                    .putExtra("sms_body", action.text)
                if (start(intent)) "Сообщение для ${contact.name} готово — нажмите «Отправить»."
                else "Не нашёл приложение для SMS."
            }
        }
        is PhoneAction.OpenApp -> openApp(action.name)
        is PhoneAction.Alarm -> {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply { if (action.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, action.label) }
            if (start(intent)) "Будильник на %02d:%02d.".format(action.hour, action.minute)
            else "Не нашёл приложение часов."
        }
        is PhoneAction.Timer -> {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply { if (action.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, action.label) }
            if (start(intent)) "Таймер на ${formatDuration(action.seconds)}." else "Не нашёл приложение часов."
        }
        is PhoneAction.Flashlight -> setTorch(action.on)
        is PhoneAction.Volume -> setVolume(action.dir)
        is PhoneAction.Search -> {
            val ok = start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query)) ||
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(action.query))))
            if (ok) "Ищу: ${action.query}." else "Не нашёл браузер."
        }
        is PhoneAction.Navigate -> {
            val ok = start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(action.destination)))) ||
                start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.destination))))
            if (ok) "Прокладываю маршрут: ${action.destination}." else "Не нашёл приложение карт."
        }
        // Эти действия обрабатывает само приложение (навигация и биржа).
        is PhoneAction.Market, is PhoneAction.Order, is PhoneAction.Screen -> ""
    }

    private val appAliases = mapOf(
        "телеграм" to "telegram", "телеграмм" to "telegram", "тг" to "telegram",
        "ютуб" to "youtube", "ютюб" to "youtube", "вайбер" to "viber", "ватсап" to "whatsapp",
        "вотсап" to "whatsapp", "инстаграм" to "instagram", "инсту" to "instagram", "тикток" to "tiktok",
        "хром" to "chrome", "браузер" to "chrome", "карты" to "maps", "гугл карты" to "maps",
        "камеру" to "camera", "камера" to "camera", "галерею" to "photos", "галерея" to "gallery",
        "калькулятор" to "calculator", "часы" to "clock", "почту" to "gmail", "почта" to "gmail",
        "бинанс" to "binance", "монобанк" to "monobank", "моно" to "monobank", "приват" to "privat24",
        "спотифай" to "spotify", "нетфликс" to "netflix", "дискорд" to "discord", "плей маркет" to "play store",
        "настройки" to "settings", "музыку" to "music", "музыка" to "music", "контакты" to "contacts",
    )

    private fun openApp(spoken: String): String {
        val name = spoken.lowercase().trim().removePrefix("приложение ").trim()
        val wanted = listOfNotNull(name, appAliases[name], name.dropLast(1).takeIf { it.length > 3 })
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            wanted.any { label == it }
        } ?: apps.firstOrNull { ri ->
            val label = ri.loadLabel(pm).toString().lowercase()
            val pkg = ri.activityInfo.packageName.lowercase()
            wanted.any { label.contains(it) || pkg.contains(it.replace(" ", "")) }
        }
        if (match == null) return "Приложение «$spoken» не найдено."
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "Не получается открыть «$spoken»."
        return if (start(intent)) "Открываю ${match.loadLabel(pm)}." else "Не получается открыть «$spoken»."
    }

    private fun setTorch(on: Boolean): String = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) "У телефона нет вспышки."
        else {
            cm.setTorchMode(id, on)
            if (on) "Фонарик включён." else "Фонарик выключен."
        }
    } catch (e: Exception) {
        "Фонарик сейчас занят другим приложением."
    }

    private fun setVolume(dir: VolumeDir): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        return try {
            when (dir) {
                VolumeDir.UP -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); "Громче."
                }
                VolumeDir.DOWN -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Тише."
                }
                VolumeDir.MUTE -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); "Звук выключен."
                }
                VolumeDir.MAX -> {
                    am.setStreamVolume(stream, am.getStreamMaxVolume(stream), AudioManager.FLAG_SHOW_UI); "Громкость на максимуме."
                }
            }
        } catch (e: SecurityException) {
            "Система не разрешает менять громкость в режиме «Не беспокоить»."
        }
    }

    companion object {
        fun formatDuration(sec: Int): String = when {
            sec % 3600 == 0 -> "${sec / 3600} ч"
            sec % 60 == 0 -> "${sec / 60} мин"
            sec > 60 -> "${sec / 60} мин ${sec % 60} с"
            else -> "$sec с"
        }
    }
}
