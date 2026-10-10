package com.zariel.spun.player3d

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ReelScratchAudio {

    private val sampleRate = 22050
    private val sampleCount = sampleRate * 2

    private val track: AudioTrack
        private var released = false
        private var currentVolume = 0f

        init {
            val samples = ShortArray(sampleCount)

            for (i in samples.indices) {
                val t = i.toDouble() / sampleRate

                // Motor rotation and gentle speed flutter.
                val flutter = sin(2.0 * PI * 4.0 * t)
                val phase =
                2.0 * PI * 105.0 * t + 0.025 * flutter

                // Layered harmonics create a mechanical whirr.

                val motor =
                0.52 * sin(phase) +
                0.24 * sin(phase * 2.0 + 0.15) +
                0.13 * sin(phase * 3.0 + 0.3) +
                0.07 * sin(phase * 5.0) +
                0.035 * sin(phase * 8.0) +
                0.025 * sin(phase * 11.0) +
                0.015 * sin(phase * 13.0)


                // Gentle cyclical variation, not radio-like static.
                val sound = motor * (0.94 + 0.06 * flutter)

                samples[i] = (sound * 15000)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
            }

            track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                )
                .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

            track.write(
                samples, 0, samples.size,
                AudioTrack.WRITE_BLOCKING
            )
            track.setLoopPoints(0, samples.size, -1)
            track.setVolume(0f)
        }

        fun update(delta: Double) {
            if (released) return

                val movement = abs(delta).toFloat()

                // Louder and faster as the reel moves more quickly.
                val targetVolume =
                (movement * 8f).coerceIn(0f, 0.45f)

                currentVolume += (targetVolume - currentVolume) * 0.35f
                track.setVolume(currentVolume)

                val speed = (0.8f + movement * 8f).coerceIn(0.8f, 1.8f)
                track.playbackRate = (sampleRate * speed).toInt()

//                track.playbackRate = sampleRate

                if (
                    movement > 0.001f &&
                    track.playState != AudioTrack.PLAYSTATE_PLAYING
                ) {
                    track.play()
                }
        }

        fun stop() {
            if (released) return

                currentVolume = 0f
                track.setVolume(0f)

                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }

                track.setPlaybackHeadPosition(0)
        }

        fun release() {
            if (released) return

                released = true
                track.release()
        }
}

