
package com.zariel.spun.playback

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExternalMediaSessions {
    private val _activeController =
    MutableStateFlow<MediaController?>(null)

    private var scrubbingController: MediaController? = null
    private var requestedPositionMs = 0L

    val activeController = _activeController.asStateFlow()

    internal fun update(controller: MediaController?) {
        _activeController.value = controller
    }

    fun seekTo(positionMs: Long): Boolean {
        val controller = _activeController.value ?: return false
        val state = controller.playbackState ?: return false

        if (state.actions and
            android.media.session.PlaybackState.ACTION_SEEK_TO == 0L
        ) {
            return false
        }

        controller.transportControls.seekTo(positionMs.coerceAtLeast(0L))
        return true
    }

    fun playPause(): Boolean {
        val controller = _activeController.value ?: return false
        val state = controller.playbackState

        if (state?.state ==
            android.media.session.PlaybackState.STATE_PLAYING
        ) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }

        return true
    }

    fun beginScrub(): Boolean {
        val controller = _activeController.value ?: return false
        val state = controller.playbackState ?: return false

        if (state.actions and
            android.media.session.PlaybackState.ACTION_SEEK_TO == 0L
        ) {
            return false
        }

        scrubbingController = controller
        requestedPositionMs = state.position.coerceAtLeast(0L)
        return true
    }

    fun seekBy(deltaMs: Long): Boolean {
        val controller = _activeController.value ?: return false
        val state = controller.playbackState ?: return false

        if (state.actions and
            android.media.session.PlaybackState.ACTION_SEEK_TO == 0L
        ) {
            return false
        }

        if (scrubbingController !== controller) {
            scrubbingController = controller
            requestedPositionMs = state.position.coerceAtLeast(0L)
        }

        requestedPositionMs =
        (requestedPositionMs + deltaMs).coerceAtLeast(0L)

        val metadata = controller.metadata
        if (metadata?.containsKey(
            android.media.MediaMetadata.METADATA_KEY_DURATION
        ) == true
        ) {
            val duration = metadata.getLong(
                android.media.MediaMetadata.METADATA_KEY_DURATION
            )

            if (duration > 0L) {
                requestedPositionMs =
                requestedPositionMs.coerceAtMost(duration)
            }
        }

        controller.transportControls.seekTo(requestedPositionMs)
        return true
    }

    fun endScrub() {
        scrubbingController = null
    }

    /**
     * Nudges the [activeController] flow so downstream collectors (e.g. NowPlayingViewModel)
     * re-read the latest position from the external controller. Called on a timer while an
     * external source is actively playing.
     */
    fun refreshPosition() {
        // Re-emit the current value to trigger combine() in NowPlayingViewModel.
        _activeController.value = _activeController.value
    }
}


class MediaSessionListener : NotificationListenerService() {

    private val sessionCallbacks =
    mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionListener =
    MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateSessions(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()

        val manager =
        getSystemService(MediaSessionManager::class.java)

        manager.addOnActiveSessionsChangedListener(
            sessionListener,
            ComponentName(this, MediaSessionListener::class.java)
        )

        updateSessions(manager.getActiveSessions(
            ComponentName(this, MediaSessionListener::class.java)
        ))

        Log.d("SpunMedia", "Media session listener connected")
    }

    private fun updateSessions(
        controllers: List<MediaController>?
    ) {
        val controllers = controllers.orEmpty()

        Log.d("SpunMedia", "Active media sessions: ${controllers.size}")
        controllers.forEach {
            Log.d("SpunMedia", "Session package: ${it.packageName}")
        }

        val current = controllers.toSet()

        sessionCallbacks.keys
        .filter { it !in current }
        .toList()
        .forEach { controller ->
            controller.unregisterCallback(sessionCallbacks.remove(controller)!!)
        }

        controllers.forEach { controller ->
            if (controller !in sessionCallbacks) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(
                        state: android.media.session.PlaybackState?
                    ) {
                        selectActive(controllers)
                    }

                    override fun onMetadataChanged(
                        metadata: android.media.MediaMetadata?
                    ) {
                        selectActive(controllers)
                    }
                }

                sessionCallbacks[controller] = callback
                controller.registerCallback(callback)
            }
        }

        selectActive(controllers)
    }

    private fun selectActive(controllers: List<MediaController>) {
        val externalControllers = controllers.filter {
            it.packageName != "com.zariel.spun"
        }

        val selected = externalControllers.firstOrNull {
            it.playbackState?.state ==
            android.media.session.PlaybackState.STATE_PLAYING
        } ?: externalControllers.firstOrNull()

        ExternalMediaSessions.update(selected)
    }


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshSessions()
    }

    private fun refreshSessions() {
        val manager =
        getSystemService(MediaSessionManager::class.java)

        updateSessions(manager.getActiveSessions(
            ComponentName(this, MediaSessionListener::class.java)
        ))
    }

    override fun onListenerDisconnected() {
        ExternalMediaSessions.update(null)
        super.onListenerDisconnected()
    }
}
