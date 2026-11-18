package com.auraplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auraplayer.app.data.model.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isConnected: Boolean = false
)

class PlaybackController(
    private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionJob: Job? = null
    private var pendingPlayAction: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncFromPlayer(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPositionPolling()
            } else {
                stopPositionPolling()
            }
            mediaController?.let(::syncFromPlayer)
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, AuraPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    controller.addListener(playerListener)
                    syncFromPlayer(controller)
                    if (controller.isPlaying) {
                        startPositionPolling()
                    }
                    pendingPlayAction?.invoke()
                    pendingPlayAction = null
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /**
     * Loads available tracks into the Playback Queue, preserving exact positional index
     * even when a playlist contains duplicate tracks, and skipping Unavailable entries.
     */
    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val clampedStart = startIndex.coerceIn(0, tracks.lastIndex)

        val indexedAvailable = tracks.mapIndexedNotNull { originalIdx, track ->
            if (track.isAvailable) originalIdx to track else null
        }
        if (indexedAvailable.isEmpty()) return

        val resolvedIndex = indexedAvailable
            .indexOfFirst { (originalIdx, _) -> originalIdx >= clampedStart }
            .let { if (it >= 0) it else 0 }

        val availableTracks = indexedAvailable.map { it.second }

        _state.update {
            it.copy(
                queue = availableTracks,
                currentIndex = resolvedIndex,
                currentTrack = availableTracks[resolvedIndex]
            )
        }

        val action = {
            val controller = mediaController
            if (controller != null) {
                val items = availableTracks.mapIndexed { idx, track ->
                    track.toMediaItem(queueIndex = idx)
                }
                controller.setMediaItems(items, resolvedIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }

        if (mediaController != null) {
            action()
        } else {
            pendingPlayAction = action
            connect()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3000L || !controller.hasPreviousMediaItem()) {
            controller.seekTo(0L)
        } else {
            controller.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceAtLeast(0L)
        controller.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
    }

    fun skipToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.seekTo(index, 0L)
            controller.play()
        }
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun syncFromPlayer(player: Player) {
        val index = player.currentMediaItemIndex
        val activeTrack = _state.value.queue.getOrNull(index)
        val rawDuration = player.duration
        val durationMs = if (rawDuration > 0L) rawDuration else (activeTrack?.durationMs ?: 0L)

        _state.update {
            it.copy(
                currentTrack = activeTrack,
                currentIndex = index,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    else -> RepeatMode.OFF
                },
                isConnected = true
            )
        }
    }

    private fun startPositionPolling() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _state.update {
                        it.copy(
                            positionMs = controller.currentPosition.coerceAtLeast(0L),
                            durationMs = if (controller.duration > 0L) {
                                controller.duration
                            } else {
                                it.durationMs
                            }
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun Track.toMediaItem(queueIndex: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .apply {
                albumArtUri?.let { setArtworkUri(Uri.parse(it)) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId("${queueIndex}_$id")
            .setUri(Uri.parse(uri))
            .setMediaMetadata(metadata)
            .build()
    }
}
