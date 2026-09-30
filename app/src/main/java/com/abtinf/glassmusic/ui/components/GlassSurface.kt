package com.abtinf.glassmusic.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * The content glass surfaces should refract/blur. Provide it with
 * `CompositionLocalProvider(LocalBackdrop provides backdrop)` and mark the content to sample with
 * `Modifier.layerBackdrop(backdrop)`. Without one, [glass] falls back to a flat translucent fill.
 */
val LocalBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Liquid-glass surface built on AndroidLiquidGlass (Kyant0, Apache-2.0): backdrop blur + vibrancy
 * (Android 12+), lens refraction (Android 13+), specular highlight and soft shadow.
 * [fill] is the tint laid over the blurred backdrop.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    fill: Color,
    highlight: Color = Color.White,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 0.75.dp,
): Modifier {
    val backdrop = LocalBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape, clip = false, ambientColor = Color(0x22000000), spotColor = Color(0x33000000)) else Modifier)
            .clip(shape)
            .background(fill)
            .border(
                borderWidth,
                Brush.linearGradient(listOf(highlight.copy(alpha = 0.75f), highlight.copy(alpha = 0.06f), highlight.copy(alpha = 0.4f)), start = Offset.Zero, end = Offset.Infinite),
                shape,
            )
    }
    val tint = fill.copy(alpha = fill.alpha * 0.6f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
            val h = minOf(14.dp.toPx(), size.minDimension / 2f)
            lens(refractionHeight = h, refractionAmount = h * 1.6f)
        },
        shadow = if (elevation > 0.dp) {
            { Shadow(radius = elevation * 1.6f) }
        } else null,
        onDrawSurface = { drawRect(tint) },
    )
}
