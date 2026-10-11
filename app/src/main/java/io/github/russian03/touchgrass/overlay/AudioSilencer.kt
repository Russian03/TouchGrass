package io.github.russian03.touchgrass.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Deja la app de debajo en silencio mientras se ve la pantalla de bloqueo: le quita el foco
 * de audio (la mayoría de reproductores se pausan) y silencia la música por si no lo hace.
 * El silencio es un interruptor, no un nivel de volumen: el volumen del usuario no se toca.
 */
class AudioSilencer(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { restoreNow() }

    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .build()

    private var silenced = false
    private var mutedByUs = false

    fun silence() {
        handler.removeCallbacks(restoreRunnable)
        if (silenced) return
        silenced = true
        audio.requestAudioFocus(focus)
        // Si el usuario ya lo tenía en silencio, al acabar se deja como estaba.
        mutedByUs = !audio.isStreamMute(AudioManager.STREAM_MUSIC)
        if (mutedByUs) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
    }

    /** Devuelve el sonido con un pequeño margen, para que no se oiga el reel mientras se cierra. */
    fun restore() {
        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, RESTORE_DELAY_MS)
    }

    fun restoreNow() {
        handler.removeCallbacks(restoreRunnable)
        if (!silenced) return
        silenced = false
        audio.abandonAudioFocusRequest(focus)
        if (mutedByUs) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        mutedByUs = false
    }

    private companion object {
        const val RESTORE_DELAY_MS = 400L
    }
}
