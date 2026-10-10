package com.zariel.spun.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Spun shared spacing, size, and shape tokens
//
// Use these constants throughout the UI rather than hardcoding dp values so
// the design can be adjusted consistently in one place.
// ---------------------------------------------------------------------------

object SpunDimensions {
    // Spacing
    val SpacingXS  =  4.dp
    val SpacingS   =  8.dp
    val SpacingM   = 16.dp
    val SpacingL   = 24.dp
    val SpacingXL  = 32.dp
    val SpacingXXL = 48.dp

    // Touch target (minimum 48 dp per accessibility guidelines)
    val TouchTarget = 48.dp

    // Now-playing screen
    val Player3DHeightFraction = 0.52f   // fraction of screen height taken by the 3D view
    val ArtworkSize = 72.dp

    // Shapes
    val ShapeS  = RoundedCornerShape( 6.dp)
    val ShapeM  = RoundedCornerShape(12.dp)
    val ShapeL  = RoundedCornerShape(20.dp)
    val ShapeXL = RoundedCornerShape(28.dp)

    // Progress bar
    val ProgressBarHeight   = 2.dp
    val ProgressBarHeightActive = 4.dp
}
