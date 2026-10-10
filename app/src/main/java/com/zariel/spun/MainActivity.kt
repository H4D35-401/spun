package com.zariel.spun

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zariel.spun.playback.PlaybackController
import com.zariel.spun.theme.SpunTheme

class MainActivity : ComponentActivity() {

    private lateinit var playbackController: PlaybackController

        private val musicPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                playbackController.play(it)
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            playbackController = PlaybackController(this)

            enableEdgeToEdge()

            setContent {
                SpunTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(
                            onPickMusic = {
                                musicPicker.launch(arrayOf("audio/*"))
                            },
                            playbackController = playbackController
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
