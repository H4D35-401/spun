package com.zariel.spun.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.zariel.spun.data.DefaultDataRepository
import com.zariel.spun.playback.PlaybackController
import com.zariel.spun.theme.SpunTheme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

import androidx.compose.ui.viewinterop.AndroidView
import com.zariel.spun.player3d.Player3DView



@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
               onPickMusic: () -> Unit,
               playbackController: PlaybackController,
               modifier: Modifier = Modifier,
               viewModel: MainScreenViewModel = viewModel {
                 MainScreenViewModel(DefaultDataRepository())
               },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val playbackState by playbackController.playbackState.collectAsStateWithLifecycle()

  when (state) {
    MainScreenUiState.Loading -> {
      // Blank
    }

    is MainScreenUiState.Success -> {
      MainScreen(
        data = (state as MainScreenUiState.Success).data,
                 playbackState = playbackState,
                 playbackController = playbackController,
                 onPickMusic = onPickMusic,
                 onPause = { playbackController.pause() },
                 onResume = { playbackController.resume() },
                 modifier = modifier
      )
    }

    is MainScreenUiState.Error -> {
      Text(
        "Error loading data: " +
        (state as MainScreenUiState.Error).throwable.message
      )
    }
  }
}

@Composable
internal fun MainScreen(
  data: List<String>,
  playbackState: com.zariel.spun.playback.PlaybackState,
  playbackController: PlaybackController,
  onPickMusic: () -> Unit,
                        onPause: () -> Unit,
                        onResume: () -> Unit,
                        modifier: Modifier = Modifier
) {
  Column(modifier) {

    data.forEach { Greeting(it) }

    Text(text = playbackState.title)

    if (playbackState.artist.isNotEmpty()) {
      Text(text = playbackState.artist)
    }

    playbackState.artwork?.let { artwork ->
      BitmapFactory.decodeByteArray(
        artwork,
        0,
        artwork.size
      )?.let { bitmap ->
        Image(
          bitmap = bitmap.asImageBitmap(),
              contentDescription = "Album artwork",
              modifier = Modifier.size(240.dp)
        )
      }
    }

    Button(onClick = onPickMusic) {
      Text("Choose Music")
    }

    Button(
      onClick = {
        if (playbackState.isPlaying) {
          onPause()
        } else {
          onResume()
        }
      }
    ) {
      Text(
        if (playbackState.isPlaying) {
          "Pause"
        } else {
          "Resume"
        }
      )
    }

    AndroidView(
      factory = { context ->
        Player3DView(
          appContext = context,
          onReelSeek = { deltaMs -> playbackController.seekBy(deltaMs) },
          onReelScrubStart = {},
          onReelScrubEnd = {},
        )
      },
      update = { view ->
        view.updateNowPlaying(
          com.zariel.spun.playback.NowPlayingState(
            title = playbackState.title,
            artist = playbackState.artist,
            album = playbackState.album,
            artwork = playbackState.artwork,
            isPlaying = playbackState.isPlaying,
          )
        )
      },
      onRelease = { view ->
        view.destroy()
      },
      modifier = Modifier.size(300.dp)
    )
  }
}

@Composable
fun Greeting(
  name: String,
  modifier: Modifier = Modifier
) {
  Text(
    text = "Hello $name!",
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  SpunTheme {
    // Preview intentionally omitted for now because
    // PlaybackController requires a real MediaSession.
  }
}
