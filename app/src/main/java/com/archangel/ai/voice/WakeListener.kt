package com.archangel.ai.voice

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Тихий режим ожидания (Android 13+).
 *
 * Обычное распознавание Android слушает кусками по несколько секунд, и каждый перезапуск —
 * это сигнал и всплывающий значок микрофона. Здесь микрофон открыт одной непрерывной сессией,
 * а распознавание запускается, только когда звучит голос. Звук отдаётся распознавателю
 * напрямую (EXTRA_AUDIO_SOURCE), сам он микрофон не открывает — поэтому нет ни сигналов, ни мигания.
 * Последние 1,5 секунды хранятся в буфере, чтобы не потерять начало фразы («Архангел…»).
 */
@RequiresApi(33)
class WakeListener(
    private val context: Context,
    /** Распознанная фраза: все варианты. */
    private val onPhrase: (List<String>) -> Unit,
    /** Этот способ на телефоне не работает — нужно вернуться к обычному. */
    private val onUnsupported: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var streaming = false
    @Volatile private var recognizing = false
    private var thread: Thread? = null
    private var queue: LinkedBlockingQueue<ByteArray>? = null
    private var recognizer: SpeechRecognizer? = null
    private var readSide: ParcelFileDescriptor? = null
    private var failures = 0

    val active get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "archangel-wake").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        endStream()
        thread = null
        main.post { finishRecognizer() }
    }

    // ───────── Запись и детектор голоса (фоновый поток)

    private fun loop() {
        val rate = RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, rate))
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            running = false
            main.post { onUnsupported() }
            return
        }
        try {
            rec.startRecording()
            val frame = ShortArray(FRAME)
            val ring = ArrayDeque<ByteArray>()
            var noise = 200.0
            var loud = 0
            var quiet = 0
            var streamStart = 0L
            while (running) {
                val n = rec.read(frame, 0, frame.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += frame[i].toDouble() * frame[i]
                val rms = sqrt(sum / n)
                val bytes = toBytes(frame, n)
                if (!streaming) {
                    ring.addLast(bytes)
                    if (ring.size > RING_FRAMES) ring.removeFirst()
                    if (recognizing) continue // ждём результата прошлой фразы
                    if (rms > max(noise * 3.0, 450.0)) loud++ else {
                        loud = 0
                        noise = (noise * 0.98 + rms * 0.02).coerceIn(60.0, 3000.0)
                    }
                    if (loud >= 6) { // ~120 мс голоса
                        beginStream(ring.toList())
                        ring.clear()
                        loud = 0; quiet = 0
                        streamStart = System.currentTimeMillis()
                    }
                } else {
                    queue?.offer(bytes)
                    if (rms > max(noise * 2.5, 380.0)) quiet = 0 else quiet++
                    // Фраза закончилась: 1 секунда тишины или 8 секунд всего
                    if (quiet >= 50 || System.currentTimeMillis() - streamStart > 8_000) endStream()
                }
            }
        } catch (e: Exception) {
            running = false
            main.post { onUnsupported() }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private fun toBytes(frame: ShortArray, n: Int): ByteArray {
        val b = ByteArray(n * 2)
        for (i in 0 until n) {
            b[2 * i] = (frame[i].toInt() and 0xFF).toByte()
            b[2 * i + 1] = (frame[i].toInt() shr 8 and 0xFF).toByte()
        }
        return b
    }

    private fun beginStream(prefix: List<ByteArray>) {
        val pipe = try { ParcelFileDescriptor.createPipe() } catch (e: IOException) { return }
        val q = LinkedBlockingQueue<ByteArray>()
        prefix.forEach { q.offer(it) }
        queue = q
        streaming = true
        recognizing = true
        // Отдельный поток пишет звук в трубу — запись с микрофона никогда не ждёт
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                    while (true) {
                        val chunk = q.take()
                        if (chunk.isEmpty()) break
                        out.write(chunk)
                    }
                }
            } catch (e: Exception) {
                // распознаватель закрыл трубу — это нормально
            }
        }, "archangel-wake-pipe").apply { isDaemon = true; start() }
        main.post { startRecognizer(pipe[0]) }
    }

    private fun endStream() {
        if (!streaming) return
        streaming = false
        queue?.offer(ByteArray(0)) // конец звука → распознаватель выдаёт результат
        queue = null
    }

    // ───────── Распознавание (главный поток)

    private fun startRecognizer(read: ParcelFileDescriptor) {
        if (!running) { runCatching { read.close() }; recognizing = false; return }
        finishRecognizer()
        readSide = read
        val onDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val r = try {
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            fail(); return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                failures = 0
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                finishRecognizer()
                if (list.isNotEmpty()) onPhrase(list)
            }

            override fun onError(error: Int) {
                val silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                finishRecognizer()
                if (silence) failures = 0 else fail()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            finishRecognizer(); fail()
        }
    }

    private fun finishRecognizer() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        readSide?.let { runCatching { it.close() } }
        readSide = null
        endStream()
        recognizing = false
    }

    /** Три настоящих сбоя подряд — считаем, что телефон так не умеет. */
    private fun fail() {
        recognizing = false
        failures++
        if (failures >= 3) {
            running = false
            onUnsupported()
        }
    }

    companion object {
        private const val RATE = 16_000
        private const val FRAME = 320 // 20 мс
        private const val RING_FRAMES = 75 // 1,5 с

        fun supported() = Build.VERSION.SDK_INT >= 33
    }
}
