package com.archangel.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Обёртка над системным распознаванием речи.
 * Все вызовы — с главного потока (так требует SpeechRecognizer).
 */
class SpeechInput(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
    /** Код ошибки распознавания (нужен режиму ожидания, чтобы отличать тишину от сбоя). */
    private val onErrorCode: (Int) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set
    /** Все варианты последней распознанной фразы (имя могло попасть только во второй вариант). */
    var lastAlternatives: List<String> = emptyList()
        private set

    val available get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Тишина в режиме ожидания. Распознавание перезапускается каждые несколько секунд,
     * и система каждый раз пищит. Пока ждём имя — держим системные звуки и медиа заглушёнными.
     * Если пользователь сам включил музыку — сразу возвращаем звук и больше не глушим.
     */
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("archangel_audio", Context.MODE_PRIVATE)
    private var muted = false
    private var holding = false
    private var musicTicks = 0
    private val focus = AudioFocus.forListening(context)

    private val watchdog = object : Runnable {
        override fun run() {
            if (!holding) return
            // Писк длится доли секунды; «музыка» дольше 2 секунд — это пользователь что-то включил
            if (audio.isMusicActive) musicTicks++ else musicTicks = 0
            if (musicTicks >= 2 && muted) unmute()
            mainHandler.postDelayed(this, 1000)
        }
    }

    /** Включить/выключить тихий режим ожидания. */
    fun holdQuiet(on: Boolean) {
        if (on == holding) return
        holding = on
        mainHandler.removeCallbacks(watchdog)
        if (on) {
            musicTicks = 0
            if (!audio.isMusicActive) mute()
            mainHandler.postDelayed(watchdog, 1000)
        } else unmute()
    }

    private fun mute() {
        if (muted) return
        runCatching {
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            muted = true
            prefs.edit().putBoolean("muted", true).apply()
        }
    }

    fun unmute() {
        if (!muted) return
        muted = false
        runCatching {
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
        }
        prefs.edit().putBoolean("muted", false).apply()
    }

    /** Офлайн-распознавание на устройстве сломалось (нет русской модели) — больше не пробуем. */
    private var onDeviceBroken = false

    private fun onDeviceAvailable(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 && !onDeviceBroken &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    /**
     * @param background режим ожидания имени: по возможности распознаём прямо на телефоне —
     * без интернета, быстрее и экономнее для батареи.
     */
    fun start(background: Boolean = false) {
        if (!available) {
            onError("На телефоне нет службы распознавания речи. Установите приложение Google.")
            return
        }
        stop()
        val onDevice = background && onDeviceAvailable()
        val r = if (onDevice && android.os.Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Обычный диапазон −2…10 дБ → 0…1 для анимации ядра
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                focus.release()
                onLevel(0f)
                // Нет офлайн-модели для языка — переходим на обычное распознавание
                if (onDevice && (error == 12 || error == 13 || error == SpeechRecognizer.ERROR_CLIENT)) onDeviceBroken = true
                onErrorCode(error)
                onError(describe(error))
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                focus.release()
                onLevel(0f)
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                lastAlternatives = list
                val text = list.firstOrNull().orEmpty()
                if (text.isBlank()) onError("Не расслышал. Попробуйте ещё раз.") else onFinal(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, if (background) 3 else 1)
            .apply { if (background) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) }
        isListening = true
        // Музыку ставим на паузу только при обычном прослушивании, не в режиме ожидания
        if (!background) focus.acquire()
        r.startListening(intent)
    }

    /** Закончить запись и получить результат. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        isListening = false
        focus.release()
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    private fun describe(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышал. Попробуйте ещё раз."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Распознаванию нужен интернет."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Микрофон занят. Попробуйте ещё раз."
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука."
        12, 13 -> "Этот язык не поддерживается распознаванием."
        else -> "Распознавание прервалось (код $code)."
    }

    companion object {
        /** Если приложение закрылось, пока звук был заглушён, — вернуть звук при следующем запуске. */
        fun recoverSound(context: Context) {
            val p = context.getSharedPreferences("archangel_audio", Context.MODE_PRIVATE)
            if (!p.getBoolean("muted", false)) return
            val a = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            runCatching {
                a.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
                a.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            }
            p.edit().putBoolean("muted", false).apply()
        }

        /** Тишина или неразборчивая речь — для режима ожидания это норма, а не сбой. */
        fun isSilence(code: Int) = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }
}
