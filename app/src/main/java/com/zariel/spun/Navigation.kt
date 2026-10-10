package com.zariel.spun

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.zariel.spun.playback.PlaybackController
import com.zariel.spun.ui.main.MainScreen

@Composable
fun MainNavigation(
  onPickMusic: () -> Unit,
                   playbackController: PlaybackController
) {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
             entryProvider =
             entryProvider {
               entry<Main> {
                 MainScreen(
                   onItemClick = { navKey -> backStack.add(navKey) },
                            onPickMusic = onPickMusic,
                            playbackController = playbackController,
                            modifier = Modifier
                            .safeDrawingPadding()
                            .padding(16.dp)
                 )
               }
             },
  )
}
