package com.zariel.spun.playback

/**
 * Which audio source is currently active for display and reel control.
 *
 * [Internal] — ExoPlayer running inside Spun's own PlaybackService.
 * [External] — An external app's media session discovered via NotificationListenerService.
 * [None]     — No active source; player is idle.
 */
enum class PlaybackSource {
    Internal,
    External,
    None,
}

/**
 * Canonical now-playing state exposed to all UI and 3D rendering surfaces.
 *
 * This is the single source of truth. The UI must never read directly from
 * [PlaybackController] or [ExternalMediaSessions] — use [NowPlayingViewModel] instead.
 */
data class NowPlayingState(
    val source: PlaybackSource = PlaybackSource.None,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false,
    /** Duration in milliseconds; -1 if unknown. */
    val durationMs: Long = -1L,
    /** Current position in milliseconds; -1 if unknown. */
    val positionMs: Long = -1L,
    /** Human-readable label for the external source app, if applicable. */
    val externalSourceLabel: String = "",
) {
    val hasTrack: Boolean get() = title.isNotEmpty() || source != PlaybackSource.None

    // ByteArray equality is structural; use contentEquals to keep data class semantics correct.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NowPlayingState) return false
        return source == other.source &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            artwork.contentEquals(other.artwork) &&
            isPlaying == other.isPlaying &&
            durationMs == other.durationMs &&
            positionMs == other.positionMs &&
            externalSourceLabel == other.externalSourceLabel
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + (artwork?.contentHashCode() ?: 0)
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + positionMs.hashCode()
        result = 31 * result + externalSourceLabel.hashCode()
        return result
    }
}

private fun ByteArray?.contentEquals(other: ByteArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}
