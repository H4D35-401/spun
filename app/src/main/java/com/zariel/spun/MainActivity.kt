package com.zariel.spun

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zariel.spun.playback.NowPlayingViewModel
import com.zariel.spun.playback.PlaybackController
import com.zariel.spun.theme.SpunTheme

class MainActivity : ComponentActivity() {

    private lateinit var playbackController: PlaybackController

    /**
     * NowPlayingViewModel is created here with a custom factory so it can receive
     * the [PlaybackController] whose lifetime is tied to the Activity. The ViewModel
     * itself survives configuration changes; the controller does not need to.
     */
    private val nowPlayingViewModel: NowPlayingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return NowPlayingViewModel(playbackController) as T
            }
        }
    }

    private val musicPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { playbackController.play(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        playbackController = PlaybackController(this)

        enableEdgeToEdge()

        setContent {
            SpunTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainNavigation(
                        onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                        nowPlayingViewModel = nowPlayingViewModel,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}
