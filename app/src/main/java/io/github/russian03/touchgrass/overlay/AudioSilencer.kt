package io.github.russian03.touchgrass.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Deja la app de debajo en silencio mientras se ve la pantalla de bloqueo o la capa de Inicio: le quita el
 * foco de audio (la mayoría de reproductores se pausan) y silencia la música por si no lo hace.
 * El silencio es un interruptor, no un nivel de volumen: el volumen del usuario no se toca.
 *
 * Es uno solo para todo el servicio y cada pantalla se apunta como dueña del silencio ([silence] y
 * [restore] llevan el `owner`): el sonido solo vuelve cuando ninguna lo necesita. Con uno por pantalla, al
 * pasar de la capa de Inicio a la pausa de Reels, la capa devolvía el sonido bajo la pausa.
 */
class AudioSilencer(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val restoreRunnable = Runnable { if (owners.isEmpty()) restoreAllNow() }

    private val focus =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .build()

    /** Pantallas que ahora mismo necesitan el silencio. */
    private val owners = mutableSetOf<Any>()
    private var silenced = false
    private var mutedByUs = false

    /** Si el usuario toca el volumen mientras se silencia, el sonido vuelve: se vuelve a silenciar al momento. */
    private val volumeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = enforce()
        }
    private var receiverRegistered = false

    /** [owner] necesita el silencio: se pone (si no estaba) y no se devuelve el sonido hasta que nadie lo necesite. */
    fun silence(owner: Any) {
        owners += owner
        handler.removeCallbacks(restoreRunnable)
        if (silenced) return
        silenced = true
        audio.requestAudioFocus(focus)
        // Si el usuario ya lo tenía en silencio, al acabar se deja como estaba.
        mutedByUs = !audio.isStreamMute(AudioManager.STREAM_MUSIC)
        if (mutedByUs) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                volumeReceiver,
                IntentFilter().apply {
                    addAction(VOLUME_CHANGED_ACTION)
                    addAction(STREAM_MUTE_CHANGED_ACTION)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    /**
     * Si se está silenciando y el sonido ha vuelto (el usuario ha tocado el volumen), lo silencia otra vez.
     * Si el usuario ya lo tenía en silencio antes, no hay nada que mantener.
     */
    fun enforce() {
        if (!silenced || !mutedByUs) return
        if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        }
    }

    /**
     * [owner] ya no necesita el silencio. Si nadie más lo necesita, devuelve el sonido con un pequeño margen,
     * para que no se oiga el reel mientras se cierra.
     */
    fun restore(owner: Any) {
        owners -= owner
        if (owners.isNotEmpty()) return
        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, RESTORE_DELAY_MS)
    }

    /** Como [restore], pero al momento (el servicio se apaga). */
    fun restoreNow(owner: Any) {
        owners -= owner
        if (owners.isEmpty()) restoreAllNow()
    }

    private fun restoreAllNow() {
        handler.removeCallbacks(restoreRunnable)
        if (receiverRegistered) {
            receiverRegistered = false
            runCatching { context.unregisterReceiver(volumeReceiver) }
        }
        if (!silenced) return
        silenced = false
        audio.abandonAudioFocusRequest(focus)
        if (mutedByUs) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        mutedByUs = false
    }

    private companion object {
        const val RESTORE_DELAY_MS = 400L

        // Avisos del sistema (sin constante pública) de que el volumen o su silencio han cambiado.
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val STREAM_MUTE_CHANGED_ACTION = "android.media.STREAM_MUTE_CHANGED_ACTION"
    }
}
