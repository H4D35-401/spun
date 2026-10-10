package com.zariel.spun.playback

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow



//remove
//import android.media.audiofx.Visualizer

data class PlaybackState(
    val title: String = "Nothing playing",
    val artist: String = "",
    val album: String = "",
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false
)

class PlaybackController(context: Context) {

    private val appContext = context.applicationContext

 //remove   //private var visualizer: Visualizer? = null

    private val controllerFuture: ListenableFuture<MediaController>

        private val _playbackState = MutableStateFlow(PlaybackState())
        val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

        private val _audioLevel = MutableStateFlow(0f)
        val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

        private val playerListener = object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                updateState()
            }
/* remove
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                android.util.Log.d(
                    "SpunAudio",
                    "Audio session ID = $audioSessionId"
                )

                setupVisualizer(audioSessionId)
            }*/
        }

        init {
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackService::class.java)
            )

            controllerFuture = MediaController.Builder(
                appContext,
                sessionToken
            ).buildAsync()

            controllerFuture.addListener(
                {
                    try {
                        controllerFuture.get().addListener(playerListener)
                        updateState()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                { command -> command.run() }
            )
        }

        private fun updateState() {
            if (!controllerFuture.isDone) return

                val controller = controllerFuture.get()

          /* val audioSessionId = controller.audioSessionId   //remove

                if (audioSessionId != 0) {
                    setupVisualizer(audioSessionId)
                } */

                val metadata = controller.mediaMetadata


                _playbackState.value = PlaybackState(
                    title = metadata.title?.toString() ?: "Unknown title",
                                                     artist = metadata.artist?.toString() ?: "",
                                                     album = metadata.albumTitle?.toString() ?: "",
                                                     artwork = metadata.artworkData,
                                                     isPlaying = controller.isPlaying
                )
        }

 /*       private fun setupVisualizer(audioSessionId: Int) {
            android.util.Log.d(
                "SpunAudio",
                "Setting up Visualizer for session $audioSessionId"
            )


            visualizer?.release()
            visualizer = null

            if (audioSessionId == 0) return

                try {
                    val v = Visualizer(audioSessionId)

                    v.captureSize = Visualizer.getCaptureSizeRange()[0]

                    v.setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {

                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveform == null) return

                                    var sum = 0.0

                                    for (sample in waveform) {
                                        val centered = sample.toInt()
                                        sum += centered * centered
                                    }

                                    val rms =
                                    kotlin.math.sqrt(
                                        sum / waveform.size
                                    ).toFloat()

                                    _audioLevel.value =
                                    (rms / 128f).coerceIn(0f, 1f)

                                    android.util.Log.d(
                                        "SpunAudio",
                                        "level=${_audioLevel.value}"
                                    )
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) = Unit
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                                             true,
                                             false
                    )

                    v.enabled = true
                    visualizer = v

                } catch (e: Exception) {
                    android.util.Log.e(
                        "SpunAudio",
                        "Visualizer failed",
                        e
                    )
                }
        }
*/
        fun play(uri: Uri) {
            controllerFuture.addListener(
                {
                    try {
                        val controller = controllerFuture.get()
                        val retriever = MediaMetadataRetriever()

                        try {
                            retriever.setDataSource(appContext, uri)

                            val title = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_TITLE
                            )

                            val artist = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_ARTIST
                            )

                            val album = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_ALBUM
                            )

                            val artwork = retriever.embeddedPicture

                            val mediaMetadata = MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .setAlbumTitle(album)
                            .setArtworkData(artwork)
                            .build()

                            val mediaItem = MediaItem.Builder()
                            .setUri(uri)
                            .setMediaMetadata(mediaMetadata)
                            .build()

                            controller.setMediaItem(mediaItem)

                        } finally {
                            retriever.release()
                        }

                        controller.prepare()
                        controller.play()

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                { command -> command.run() }
            )
        }

        fun pause() {
            if (controllerFuture.isDone) {
                controllerFuture.get().pause()
            }
        }

        fun resume() {
            if (controllerFuture.isDone) {
                controllerFuture.get().play()
            }
        }

        fun release() {
            //visualizer?.release()             //remove
            //visualizer = null                 //remove

            if (controllerFuture.isDone) {
                controllerFuture.get().removeListener(playerListener)
            }

            MediaController.releaseFuture(controllerFuture)
        }
}
