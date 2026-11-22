package com.archangel.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Как умная колонка: пока Архангел говорит — музыка приглушается,
 * пока слушает — встаёт на паузу. Потом всё возвращается само.
 */
class AudioFocus(context: Context, gain: Int) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val request = AudioFocusRequest.Builder(gain)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()
    private var held = false

    fun acquire() {
        if (held) return
        held = runCatching { audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }.getOrDefault(false)
    }

    fun release() {
        if (!held) return
        held = false
        runCatching { audio.abandonAudioFocusRequest(request) }
    }

    companion object {
        fun forSpeech(context: Context) = AudioFocus(context, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        fun forListening(context: Context) = AudioFocus(context, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    }
}
