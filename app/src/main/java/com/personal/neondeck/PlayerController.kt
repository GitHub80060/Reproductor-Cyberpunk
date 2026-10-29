package com.personal.neondeck

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/** Puente entre la pantalla y el servicio de reproducción. Guarda el estado que dibuja la interfaz. */
class PlayerController(private val ctx: Context) : Player.Listener {

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private var lastQueue: List<Long> = emptyList()

    var connected by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var mediaId by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var position by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, MusicService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener(
            {
                try {
                    val c = f.get()
                    controller = c
                    c.addListener(this)
                    connected = true
                    sync()
                } catch (_: Exception) {
                    connected = false
                }
            },
            ContextCompat.getMainExecutor(ctx)
        )
    }

    fun release() {
        controller?.removeListener(this)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        connected = false
    }

    override fun onEvents(player: Player, events: Player.Events) {
        sync()
    }

    private fun sync() {
        val c = controller ?: return
        playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED
        mediaId = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() && it != MediaItem.DEFAULT_MEDIA_ID }
        title = c.mediaMetadata.title?.toString().orEmpty()
        artist = c.mediaMetadata.artist?.toString().orEmpty()
        duration = if (c.duration == C.TIME_UNSET) 0L else c.duration
        position = c.currentPosition
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
    }

    fun refreshPosition() {
        val c = controller ?: return
        position = c.currentPosition
        if (c.duration != C.TIME_UNSET) duration = c.duration
    }

    /** Reproduce la lista [tracks] empezando por la canción [index]. */
    fun playQueue(tracks: List<Track>, index: Int, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (index !in tracks.indices) return
        val ids = tracks.map { it.id }

        if (shuffleOn != null) c.shuffleModeEnabled = shuffleOn

        if (ids == lastQueue && c.mediaItemCount == tracks.size) {
            c.seekTo(index, 0L)
        } else {
            val items = tracks.map { it.toMediaItem() }
            c.setMediaItems(items, index, 0L)
            lastQueue = ids
        }
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        // Si ya pasaron unos segundos, vuelve al inicio de la canción; si no, va a la anterior
        if (c.currentPosition > 3000) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        position = ms
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(albumArtUri(albumId))
                    .build()
            )
            .build()
}
