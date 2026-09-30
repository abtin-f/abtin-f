package com.abtinf.glassmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass surface: translucent fill, soft drop shadow and a specular edge highlight that
 * is brighter toward the top-left and bottom-right corners.
 */
fun Modifier.glass(
    shape: Shape,
    fill: Color,
    highlight: Color = Color.White,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.75.dp,
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000)) else Modifier)
    .clip(shape)
    .background(fill)
    .border(
        borderWidth,
        Brush.linearGradient(listOf(highlight.copy(alpha = 0.75f), highlight.copy(alpha = 0.06f), highlight.copy(alpha = 0.4f))),
        shape,
    )
