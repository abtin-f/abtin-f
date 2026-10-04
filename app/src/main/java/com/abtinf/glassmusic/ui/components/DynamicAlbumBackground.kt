package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.abtinf.glassmusic.data.AudioArt
import com.abtinf.glassmusic.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, alpha)

/**
 * Two colours derived from the track artwork (Palette), animated so they glide when the song changes.
 * Returns (deep base colour, brighter accent colour).
 */
@Composable
fun rememberArtColors(track: Track?): Pair<Color, Color> {
    val ctx = LocalContext.current
    val fallback = remember(track?.seed) {
        val (a, b, _) = artPalette(track?.seed ?: 0)
        a.darken(0.55f) to b
    }
    // Not keyed by track: the old colours stay until the new ones are ready, so the background glides instead of flashing.
    var extracted by remember { mutableStateOf<Pair<Color, Color>?>(null) }
    LaunchedEffect(track?.id) {
        val t = track
        val uri = t?.artUri
        if (t == null || uri == null) { extracted = null; return@LaunchedEffect }
        val request = ImageRequest.Builder(ctx).data(AudioArt(uri, "album:${t.albumId}")).size(160).allowHardware(false).build()
        val bmp = (ctx.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        if (bmp == null) { extracted = null; return@LaunchedEffect }
        val palette = withContext(Dispatchers.Default) { Palette.from(bmp).maximumColorCount(16).generate() }
        val base = palette.darkMutedSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        val accent = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
        extracted = if (base != null && accent != null) Color(base.rgb).darken(0.8f) to Color(accent.rgb) else null
    }
    val target = extracted ?: fallback
    val c1 by animateColorAsState(target.first, tween(900), label = "artBase")
    val c2 by animateColorAsState(target.second, tween(900), label = "artAccent")
    return c1 to c2
}

/** Full-bleed background: heavily blurred artwork + palette gradient + dark scrim. Cross-fades on song change. */
@Composable
fun DynamicAlbumBackground(track: Track?, modifier: Modifier = Modifier, scrimAlpha: Float = 0.38f) {
    val (base, accent) = rememberArtColors(track)
    Box(modifier.background(Color.Black)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.85f), base, base.darken(0.6f)))),
        )
        Crossfade(targetState = track, animationSpec = tween(900), label = "bgArt") { t ->
            // The picture is blurred beyond recognition, so a 160 px decode (shared with the palette lookup) is plenty.
            ArtworkImage(
                seed = t?.seed ?: 0,
                artUri = t?.artUri,
                artKey = t?.let { "album:${it.albumId}" },
                maxEdge = 160,
                corner = 0.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.7f)
                    .blur(72.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = scrimAlpha * 0.6f), Color.Black.copy(alpha = scrimAlpha), Color.Black.copy(alpha = scrimAlpha + 0.22f)),
                    ),
                ),
        )
    }
}
