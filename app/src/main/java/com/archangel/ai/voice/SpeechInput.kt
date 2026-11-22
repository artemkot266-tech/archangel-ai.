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
) {
    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    val available get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Глушит системный «бип» при перезапуске микрофона в режиме ожидания (если не играет музыка). */
    var quiet = false
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var muted = false

    private fun muteBeep() {
        if (!quiet || audio.isMusicActive) return
        runCatching {
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            muted = true
        }
        mainHandler.postDelayed({ unmute() }, 700)
    }

    fun unmute() {
        if (!muted) return
        muted = false
        runCatching {
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
            audio.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    fun start() {
        if (!available) {
            onError("На телефоне нет службы распознавания речи. Установите приложение Google.")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
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
                onLevel(0f)
                onError(describe(error))
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                onLevel(0f)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
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
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        isListening = true
        muteBeep()
        r.startListening(intent)
    }

    /** Закончить запись и получить результат. */
    fun finish() {
        recognizer?.stopListening()
    }

    fun stop() {
        isListening = false
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
        else -> "Распознавание прервалось (код $code)."
    }
}
