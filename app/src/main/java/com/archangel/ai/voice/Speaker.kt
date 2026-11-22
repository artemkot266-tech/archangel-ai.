package com.archangel.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.archangel.ai.ai.FriendlyException
import com.archangel.ai.ai.Http
import com.archangel.ai.data.AppSettings
import com.archangel.ai.data.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Голос системного синтезатора, который можно выбрать в настройках. */
data class VoiceOption(val name: String, val label: String)

/**
 * Голос Архангела.
 * — Системный синтезатор (бесплатно, офлайн): выбирается лучший голос, тон чуть ниже — спокойнее и «солиднее».
 * — ElevenLabs (по ключу): студийный голос, как в кино. Если сеть или ключ подведут — молча
 *   переключаемся на системный голос, чтобы ответ всё равно прозвучал.
 * Все колбэки приходят на главный поток.
 */
class Speaker(
    context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
    private val onVoicesReady: (List<VoiceOption>) -> Unit = {},
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val focus = AudioFocus.forSpeech(app)
    private fun started() { focus.acquire(); onStart() }
    private fun finished() { focus.release(); onDone() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ready = false
    private var counter = 0
    private var player: MediaPlayer? = null
    private var cloudJob: Job? = null
    /** Фраза, которую попросили сказать до готовности синтезатора (например, приветствие при запуске). */
    private var pending: Pair<String, AppSettings>? = null

    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            runCatching { tts.language = Locale.getDefault() }
            main.post {
                onVoicesReady(voiceOptions())
                pending?.let { (text, s) -> pending = null; speak(text, s) }
            }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { started() } }
            override fun onDone(utteranceId: String?) { main.post { finished() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { finished() } }
            override fun onError(utteranceId: String?, errorCode: Int) { main.post { finished() } }
        })
        runCatching {
            tts.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
        }
    }

    /** Голоса для языка телефона: сначала лучшие и офлайн-голоса. */
    fun voiceOptions(): List<VoiceOption> {
        if (!ready) return emptyList()
        val lang = Locale.getDefault().language
        val voices: List<Voice> = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { it.locale.language == lang && !it.features.orEmpty().contains("notInstalled") }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
        return voices.mapIndexed { i, v ->
            VoiceOption(v.name, "Голос ${i + 1}" + if (v.isNetworkConnectionRequired) " · онлайн" else "")
        }
    }

    /** @return false, если озвучить нечем (тогда интерфейс просто показывает текст). */
    fun speak(text: String, s: AppSettings): Boolean {
        val clean = text
            .replace(Regex("[*_#`>|]"), "")
            .replace(Regex("\\[(.*?)]\\(.*?\\)"), "$1")
            .trim()
            .take(3500)
        if (clean.isEmpty()) return false
        stop()
        if (s.voiceEngine == VoiceEngine.ELEVENLABS && s.elevenKey.isNotBlank()) {
            speakEleven(clean, s)
            return true
        }
        return speakSystem(clean, s)
    }

    private fun speakSystem(text: String, s: AppSettings): Boolean {
        if (!ready) {
            pending = text to s
            return true
        }
        runCatching {
            val wanted = tts.voices?.firstOrNull { it.name == s.systemVoice }
            if (wanted != null) tts.voice = wanted else tts.language = Locale.getDefault()
        }
        tts.setPitch(s.voicePitch)
        tts.setSpeechRate(s.speechRate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${counter++}")
        return true
    }

    private fun speakEleven(text: String, s: AppSettings) {
        started()
        cloudJob = scope.launch {
            try {
                val file = synthesizeEleven(text, s)
                play(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // ElevenLabs недоступен — говорим системным голосом
                if (!speakSystem(text, s)) finished()
            }
        }
    }

    private suspend fun synthesizeEleven(text: String, s: AppSettings): File {
        val body = JSONObject()
            .put("text", text)
            .put("model_id", "eleven_multilingual_v2")
            .put(
                "voice_settings",
                JSONObject().put("stability", 0.55).put("similarity_boost", 0.75).put("style", 0.15)
                    .put("speed", s.speechRate.coerceIn(0.7f, 1.2f).toDouble()),
            )
        val res = Http.requestBytes(
            url = "https://api.elevenlabs.io/v1/text-to-speech/${s.elevenVoiceId}?output_format=mp3_44100_128",
            headers = mapOf(
                "xi-api-key" to s.elevenKey,
                "Content-Type" to "application/json",
                "Accept" to "audio/mpeg",
            ),
            body = body.toString(),
        )
        if (!res.ok || res.bytes.isEmpty()) throw FriendlyException("ElevenLabs: ошибка ${res.code}")
        return withContext(Dispatchers.IO) {
            File(app.cacheDir, "voice_${counter++ % 4}.mp3").apply { writeBytes(res.bytes) }
        }
    }

    private fun play(file: File) {
        releasePlayer()
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnCompletionListener { releasePlayer(); finished() }
        mp.setOnErrorListener { _, _, _ -> releasePlayer(); finished(); true }
        mp.setDataSource(file.absolutePath)
        mp.prepare()
        mp.start()
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun stop() {
        pending = null
        cloudJob?.cancel()
        cloudJob = null
        releasePlayer()
        if (ready) tts.stop()
        focus.release()
    }

    fun release() {
        stop()
        scope.cancel()
        tts.shutdown()
    }
}
