package com.archangel.ai

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Чёрный ящик»: если приложение падает, текст ошибки сохраняется в файл,
 * а при следующем запуске показывается пользователю — его можно скопировать разработчику.
 * После падения включается безопасный режим графики (без GPU-шейдеров).
 */
object CrashGuard {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "archangel_crash"

    @Volatile
    var safeGraphics: Boolean = false
        private set

    fun install(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Безопасный режим живёт до выхода новой версии: в ней ошибка, скорее всего, исправлена.
        safeGraphics = prefs.getInt("safe_for_version", -1) == BuildConfig.VERSION_CODE
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val header = buildString {
                    appendLine("ARCHANGEL ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date()) + " · поток ${thread.name}")
                    appendLine()
                }
                File(app.filesDir, FILE).writeText(header + sw.toString().take(12_000))
                prefs.edit().putInt("safe_for_version", BuildConfig.VERSION_CODE).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
