package com.archangel.ai.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Озвучка ответов системным синтезатором речи. Колбэки приходят на главный поток. */
class Speaker(
    context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) runCatching { tts.language = Locale.getDefault() }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { onStart() } }
            override fun onDone(utteranceId: String?) { main.post { onDone() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { onDone() } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { onDone() } }
        })
    }

    /** @return false, если синтезатор недоступен (тогда просто молчим). */
    fun speak(text: String, rate: Float): Boolean {
        if (!ready) return false
        val clean = text
            .replace(Regex("[*_#`>|]"), "")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .take(3500)
        tts.setSpeechRate(rate)
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "u${counter++}")
        return true
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun release() {
        tts.stop()
        tts.shutdown()
    }
}
