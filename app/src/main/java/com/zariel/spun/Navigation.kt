package com.zariel.spun

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.zariel.spun.playback.NowPlayingViewModel
import com.zariel.spun.ui.nowplaying.NowPlayingScreen

@Composable
fun MainNavigation(
    onPickMusic: () -> Unit,
    nowPlayingViewModel: NowPlayingViewModel,
) {
    val backStack = rememberNavBackStack(NowPlaying)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<NowPlaying> {
                NowPlayingScreen(
                    viewModel = nowPlayingViewModel,
                    onPickMusic = onPickMusic,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}
