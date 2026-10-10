package com.zariel.spun.ui.nowplaying

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zariel.spun.playback.NowPlayingState
import com.zariel.spun.playback.NowPlayingViewModel
import com.zariel.spun.playback.PlaybackSource
import com.zariel.spun.player3d.Player3DView
import com.zariel.spun.theme.SpunDimensions

/**
 * Primary now-playing screen.
 *
 * Layout (top → bottom):
 *   – 3D player hero (≈52 % of screen height)
 *   – Track info: title, artist, source label
 *   – Playback controls: play/pause
 *   – Secondary actions: Choose Music
 */
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onPickMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── 3D player ──────────────────────────────────────────────────
            Player3DHero(
                state = state,
                onReelSeek = { deltaMs -> viewModel.seekBy(deltaMs) },
                onReelScrubStart = { viewModel.beginScrub() },
                onReelScrubEnd = { viewModel.endScrub() },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(SpunDimensions.Player3DHeightFraction),
            )

            // ── Track info + controls ──────────────────────────────────────
            TrackInfoPanel(
                state = state,
                onPlayPause = { viewModel.playPause() },
                onPickMusic = onPickMusic,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = SpunDimensions.SpacingM),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3D hero sub-composable
// ---------------------------------------------------------------------------

@Composable
private fun Player3DHero(
    state: NowPlayingState,
    onReelSeek: (Long) -> Unit,
    onReelScrubStart: () -> Unit,
    onReelScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Remember the view instance so Compose doesn't recreate it on recomposition.
    // State changes are pushed via updateNowPlaying() in the update block.
    val player3DView = remember(context) {
        Player3DView(
            appContext = context,
            onReelSeek = onReelSeek,
            onReelScrubStart = onReelScrubStart,
            onReelScrubEnd = onReelScrubEnd,
        )
    }

    AndroidView(
        factory = { player3DView },
        update = { view ->
            view.updateNowPlaying(state)
        },
        onRelease = { view ->
            view.destroy()
        },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// Track info + controls panel
// ---------------------------------------------------------------------------

@Composable
private fun TrackInfoPanel(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onPickMusic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {

        // Source indicator badge
        if (state.source == PlaybackSource.External && state.externalSourceLabel.isNotEmpty()) {
            SourceBadge(label = state.externalSourceLabel)
            Spacer(Modifier.height(SpunDimensions.SpacingS))
        }

        // Track title
        AnimatedContent(
            targetState = state.title,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            },
            label = "track-title",
        ) { title ->
            Text(
                text = title.ifEmpty { "Nothing playing" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(SpunDimensions.SpacingXS))

        // Artist + album
        if (state.artist.isNotEmpty()) {
            Text(
                text = buildString {
                    append(state.artist)
                    if (state.album.isNotEmpty()) append("  ·  ${state.album}")
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(SpunDimensions.SpacingL))

        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Play / pause
            PlayPauseButton(
                isPlaying = state.isPlaying,
                enabled = state.source != PlaybackSource.None,
                onClick = onPlayPause,
            )

            // Choose local music (secondary action)
            TextButton(onClick = onPickMusic) {
                Text(
                    text = "Choose music",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(SpunDimensions.SpacingM))
    }
}

// ---------------------------------------------------------------------------
// Reusable atoms
// ---------------------------------------------------------------------------

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(SpunDimensions.TouchTarget * 1.2f)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape,
            ),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun SourceBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(SpunDimensions.ShapeS)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = SpunDimensions.SpacingS, vertical = 4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
