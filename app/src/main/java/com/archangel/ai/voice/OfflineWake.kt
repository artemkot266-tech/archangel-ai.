package com.archangel.ai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.math.max

/**
 * Офлайн-слух на Vosk: ждёт только имя помощника, полностью на телефоне.
 *
 * Микрофон открыт одной сессией через AudioRecord — без системного распознавателя,
 * поэтому нет сигналов, всплывающих уведомлений и видео не ставится на паузу.
 * Словарь ограничен именами помощника — распознавание лёгкое и точное.
 */
class OfflineWake(
    private val context: Context,
    /** Услышал имя. rest — что сказано после имени в той же фразе (может быть пусто). Главный поток. */
    private val onWake: (rest: String) -> Unit,
) {
    enum class State { MISSING, DOWNLOADING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val dir = File(context.filesDir, "vosk-ru")
    @Volatile private var running = false
    private var thread: Thread? = null
    private var model: Model? = null

    val active get() = running

    fun state(): State = if (File(dir, "am").exists() || File(dir, "conf").exists()) State.READY else State.MISSING

    /** Скачивает и распаковывает русскую модель (~45 МБ). progress: 0..1. Блокирующий вызов — запускать в фоне. */
    fun download(progress: (Float) -> Unit): Boolean {
        val tmp = File(context.cacheDir, "vosk-ru.zip")
        return try {
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 45_000_000L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val p = (done.toFloat() / total).coerceAtMost(0.98f)
                        if (p - shown >= 0.01f) { shown = p; progress(p) }
                    }
                }
            }
            val staging = File(context.filesDir, "vosk-ru.tmp").apply { deleteRecursively(); mkdirs() }
            ZipInputStream(tmp.inputStream().buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    // Архив содержит папку vosk-model-small-ru-0.22/… — срезаем её
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isEmpty() || rel.contains("..")) continue
                    val f = File(staging, rel)
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            dir.deleteRecursively()
            staging.renameTo(dir)
            progress(1f)
            state() == State.READY
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    fun delete() { stop(); model?.close(); model = null; dir.deleteRecursively() }

    fun start(names: List<String>) {
        if (running || state() != State.READY) return
        running = true
        val words = grammarWords(names)
        thread = Thread({ loop(words) }, "archangel-offline-wake").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread = null
    }

    private fun loop(words: List<String>) {
        val m = try {
            model ?: run {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Model(dir.absolutePath).also { model = it }
            }
        } catch (e: Throwable) {
            running = false
            return
        }
        // Свободное распознавание (без словаря из одного слова): иначе любой похожий звук
        // «натягивается» на имя и микрофон включается сам по себе
        val rec = try { Recognizer(m, RATE.toFloat()) } catch (e: Throwable) { running = false; return }
        rec.setWords(true)
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, RATE))
        } catch (e: Exception) {
            null
        }
        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) {
            audio?.release(); rec.close(); running = false; return
        }
        try {
            audio.startRecording()
            val buf = ByteArray(3200) // 100 мс
            while (running) {
                val n = audio.read(buf, 0, buf.size)
                if (n <= 0) continue
                // Решение — только по законченной фразе, никаких срабатываний «на полуслове»
                if (!rec.acceptWaveForm(buf, n)) continue
                val rest = match(rec.result, words) ?: continue
                running = false
                main.post { onWake(rest) }
                break
            }
        } catch (e: Exception) {
            running = false
        } finally {
            runCatching { audio.stop() }
            audio.release()
            rec.close()
        }
    }

    companion object {
        const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip"
        const val MODEL_SIZE_MB = 45
        private const val RATE = 16_000

        /** Слова для словаря: «архангел» всегда + имя, которое выбрал пользователь (по словам, в нижнем регистре). */
        fun grammarWords(names: List<String>): List<String> =
            (names + "архангел").flatMap { it.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}]+")) }
                .filter { it.length >= 3 }.distinct()

        /** Минимальная уверенность распознавателя в слове-имени. */
        const val MIN_CONF = 0.8

        /**
         * Разбирает итог Vosk ({"result":[{"word","conf"}…],"text"}). Имя должно быть отдельным словом
         * с уверенностью ≥ MIN_CONF и стоять в начале фразы (не дальше второго слова) — «я смотрел
         * фильм про архангела» не сработает. Возвращает слова после имени или null.
         */
        fun match(json: String, words: List<String>): String? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val arr = o.optJSONArray("result") ?: return null
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            val idx = list.indexOfFirst { w ->
                w.optString("word").lowercase().replace('ё', 'е') in words && w.optDouble("conf", 0.0) >= MIN_CONF
            }
            if (idx < 0 || idx > 1) return null
            return list.drop(idx + 1).joinToString(" ") { it.optString("word") }.trim()
        }
    }
}
