package com.abtinf.glassmusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.abtinf.glassmusic.data.AudioArt
import com.abtinf.glassmusic.data.Track
import java.util.Random

/** Deterministic three-colour palette for a given seed (used for demo art and as colour fallback). */
fun artPalette(seed: Int): Triple<Color, Color, Color> {
    val r = Random(seed.toLong())
    val h = r.nextFloat() * 360f
    val h2 = (h + 40f + r.nextFloat() * 80f) % 360f
    val h3 = (h2 + 60f + r.nextFloat() * 120f) % 360f
    return Triple(Color.hsv(h, 0.75f, 0.95f), Color.hsv(h2, 0.8f, 0.85f), Color.hsv(h3, 0.6f, 1f))
}

/** Procedural cover (gradient + circles) used for the sample library and while / when a real cover is missing. */
private class GeneratedArtPainter(seed: Int) : Painter() {
    private val colors = artPalette(seed)
    private val shapes = run {
        val r = Random(seed.toLong() * 7 + 3)
        List(3) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.25f + r.nextFloat() * 0.5f) }
    }

    override val intrinsicSize: Size get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        val (a, b, c) = colors
        drawRect(Brush.linearGradient(listOf(a, b), start = Offset.Zero, end = Offset(size.width, size.height)))
        shapes.forEachIndexed { i, s ->
            drawCircle(
                color = if (i % 2 == 0) c.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.18f),
                radius = size.minDimension * s[2],
                center = Offset(size.width * s[0], size.height * s[1]),
            )
        }
        drawCircle(Color.Black.copy(alpha = 0.12f), size.minDimension * 0.18f, center, style = Stroke(size.minDimension * 0.02f))
    }
}

/**
 * [artKey] makes all songs of one album share a single decoded cover; [maxEdge] caps the decode size for
 * images that end up blurred or tiny.
 */
@Composable
fun ArtworkImage(
    seed: Int,
    artUri: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    elevation: Dp = 0.dp,
    artKey: String? = null,
    maxEdge: Int? = null,
) {
    val shape = RoundedCornerShape(corner)
    val generated = remember(seed) { GeneratedArtPainter(seed) }
    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape),
    ) {
        if (artUri == null) {
            Image(generated, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val ctx = LocalContext.current
            val model = remember(artUri, artKey, maxEdge) {
                val art = AudioArt(artUri, artKey ?: artUri)
                if (maxEdge == null) art else ImageRequest.Builder(ctx).data(art).size(maxEdge).build()
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                placeholder = generated,
                error = generated,
                fallback = generated,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun Artwork(track: Track?, modifier: Modifier = Modifier, corner: Dp = 8.dp, elevation: Dp = 0.dp) {
    ArtworkImage(track?.seed ?: 0, track?.artUri, modifier, corner, elevation, artKey = track?.artKey)
}

/** 2x2 mosaic for playlists (falls back to a single cover when fewer than four tracks). */
@Composable
fun CoverMosaic(tracks: List<Track>, modifier: Modifier = Modifier, corner: Dp = 8.dp, elevation: Dp = 0.dp) {
    val covers = remember(tracks) { tracks.distinctBy { it.albumId }.take(4) }
    if (covers.size < 4) {
        Artwork(covers.firstOrNull(), modifier, corner, elevation)
    } else {
        val shape = RoundedCornerShape(corner)
        Box(modifier.then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier).clip(shape)) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                for (row in 0..1) {
                    androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                        for (col in 0..1) {
                            Artwork(covers[row * 2 + col], Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
                        }
                    }
                }
            }
        }
    }
}
