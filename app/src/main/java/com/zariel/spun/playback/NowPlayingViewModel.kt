package com.zariel.spun.playback

import android.media.MediaMetadata
import android.media.session.PlaybackState as AndroidPlaybackState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single ViewModel that reconciles internal (ExoPlayer) and external (media session)
 * playback state into one [NowPlayingState] for all UI and 3D rendering consumers.
 *
 * Source priority:
 *  1. If an external session is actively playing, prefer it.
 *  2. If internal ExoPlayer is playing, use it.
 *  3. If an external session exists but is paused, prefer external (user paused it there).
 *  4. Fall back to internal paused state.
 *  5. Otherwise: [PlaybackSource.None].
 *
 * The ViewModel does not own the [PlaybackController]; it receives it so that the
 * controller lifetime remains tied to the Activity (which manages the MediaController
 * connection lifecycle).
 */
class NowPlayingViewModel(
    private val playbackController: PlaybackController,
) : ViewModel() {

    /**
     * Derived state combining internal + external sources. Uses [combine] so any upstream
     * change triggers exactly one downstream emission.
     */
    val state: StateFlow<NowPlayingState> =
        combine(
            playbackController.playbackState,
            ExternalMediaSessions.activeController,
        ) { internal, externalController ->
            buildState(internal, externalController)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NowPlayingState(),
        )

    /**
     * Expose a writable flag so the Player3DView (or future scrub UI) can signal that
     * the user has begun/ended scrubbing without the View needing a direct ViewModel reference.
     * Keeping this here avoids polluting ExternalMediaSessions with UI concerns.
     */
    private val _isScrubbing = MutableStateFlow(false)
    val isScrubbing: StateFlow<Boolean> = _isScrubbing.asStateFlow()

    init {
        // Poll the external controller's position every 500 ms when it's the active source,
        // because Android's legacy media session API doesn't push position updates continuously.
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                val current = state.value
                if (current.source == PlaybackSource.External && current.isPlaying) {
                    // Force a re-emission by poking the activeController flow.
                    // The combine above will re-run buildState.
                    ExternalMediaSessions.refreshPosition()
                }
            }
        }
    }

    // --- Playback controls -----------------------------------------------------------------

    fun playPause() {
        when (state.value.source) {
            PlaybackSource.Internal -> {
                if (state.value.isPlaying) playbackController.pause()
                else playbackController.resume()
            }
            PlaybackSource.External -> ExternalMediaSessions.playPause()
            PlaybackSource.None -> Unit
        }
    }

    fun beginScrub() {
        _isScrubbing.value = true
        ExternalMediaSessions.beginScrub()
    }

    fun seekBy(deltaMs: Long) {
        when (state.value.source) {
            PlaybackSource.External -> ExternalMediaSessions.seekBy(deltaMs)
            PlaybackSource.Internal -> playbackController.seekBy(deltaMs)
            PlaybackSource.None -> Unit
        }
    }

    fun endScrub() {
        _isScrubbing.value = false
        ExternalMediaSessions.endScrub()
    }

    fun seekToFraction(fraction: Float) {
        val duration = state.value.durationMs
        if (duration > 0) {
            val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
            when (state.value.source) {
                PlaybackSource.External -> ExternalMediaSessions.seekTo(targetMs)
                PlaybackSource.Internal -> playbackController.seekTo(targetMs)
                PlaybackSource.None -> Unit
            }
        }
    }

    // --- State construction ----------------------------------------------------------------

    private fun buildState(
        internal: PlaybackState,
        externalController: android.media.session.MediaController?,
    ): NowPlayingState {
        val externalState = externalController?.playbackState
        val externalMeta = externalController?.metadata
        val externalIsPlaying =
            externalState?.state == AndroidPlaybackState.STATE_PLAYING

        val internalIsPlaying = internal.isPlaying
        val internalHasTrack = internal.title.isNotEmpty() && internal.title != "Nothing playing"

        // Source selection
        val source = when {
            externalIsPlaying -> PlaybackSource.External
            internalIsPlaying -> PlaybackSource.Internal
            externalController != null -> PlaybackSource.External
            internalHasTrack -> PlaybackSource.Internal
            else -> PlaybackSource.None
        }

        return when (source) {
            PlaybackSource.External -> {
                NowPlayingState(
                    source = PlaybackSource.External,
                    title = externalMeta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                    artist = externalMeta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                    album = externalMeta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                    artwork = externalMeta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?.let { bitmapToBytes(it) }
                        ?: externalMeta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?.let { bitmapToBytes(it) },
                    isPlaying = externalIsPlaying,
                    durationMs = externalMeta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L,
                    positionMs = externalState?.position ?: -1L,
                    externalSourceLabel = externalController?.packageName?.toAppLabel() ?: "",
                )
            }

            PlaybackSource.Internal -> {
                NowPlayingState(
                    source = PlaybackSource.Internal,
                    title = internal.title.takeIf { it != "Nothing playing" } ?: "",
                    artist = internal.artist,
                    album = internal.album,
                    artwork = internal.artwork,
                    isPlaying = internalIsPlaying,
                    // Duration and position are not yet surfaced by PlaybackController.
                    // They will be added in Phase 4 when the progress bar is wired up.
                    durationMs = -1L,
                    positionMs = -1L,
                )
            }

            PlaybackSource.None -> NowPlayingState()
        }
    }

    private fun bitmapToBytes(bitmap: android.graphics.Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    private fun String.toAppLabel(): String = when {
        contains("youtube") -> "YouTube Music"
        contains("spotify") -> "Spotify"
        contains("echo") -> "Echo"
        contains("vlc") -> "VLC"
        else -> substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
