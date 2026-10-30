package com.personal.neondeck

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Servicio que reproduce la música en segundo plano.
 * Muestra la notificación con controles y deja usar los botones de los audífonos / pantalla de bloqueo.
 */
class MusicService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa si desconectas los audífonos
            .setWakeMode(C.WAKE_MODE_LOCAL)    // sigue sonando con la pantalla apagada
            .build()

        // Los efectos (ecualizador, graves, volumen) se enganchan a la sesión de audio del reproductor
        Fx.attach(applicationContext, player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                Fx.attach(applicationContext, audioSessionId)
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si cierras la app desde recientes y no está sonando nada, el servicio se apaga
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Fx.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private class Callback : MediaSession.Callback {
        // Cuando la pantalla manda canciones, nos aseguramos de que conserven su dirección (uri)
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // Solo aceptamos canciones con dirección content:// (las de la carpeta elegida).
            // Se descartan file://, http:// y cualquier otra cosa que intente colarse desde otra app.
            val resolved = mediaItems.mapNotNull { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                when {
                    uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT -> null
                    item.localConfiguration == null -> item.buildUpon().setUri(uri).build()
                    else -> item
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
