package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.formatTime
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

@Composable
fun SongRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    showArtwork: Boolean = true,
    number: Int? = null,
    showDuration: Boolean = false,
    onMore: ((Track) -> Unit)? = null,
) {
    val am = LocalAm.current
    Row(
        modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Artwork(track, Modifier.size(48.dp), corner = 6.dp)
                if (isCurrent) EqualizerOverlay(isPlaying)
            }
        } else {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                if (isCurrent) {
                    Equalizer(isPlaying, AmAccent)
                } else {
                    Text(number?.toString().orEmpty(), style = AmType.Body, color = am.secondary)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title, style = AmType.Body.copy(fontSize = 16.sp),
                color = if (isCurrent) AmAccent else am.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(track.artist, style = AmType.Caption.copy(fontSize = 14.sp), color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showDuration) Text(formatTime(track.durationMs), style = AmType.Caption, color = am.secondary)
        if (onMore != null) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { onMore(track) }, contentAlignment = Alignment.Center) {
                Icon(AmIcons.More, "More", tint = am.secondary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun EqualizerOverlay(playing: Boolean) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Equalizer(playing, Color.White)
    }
}

/** Three-bar "now playing" indicator; bars animate while [playing]. */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(430), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(610), RepeatMode.Reverse), label = "c")
    Canvas(modifier.size(18.dp)) {
        val w = size.width / 5f
        val hs = if (playing) listOf(a, b, c) else listOf(0.3f, 0.3f, 0.3f)
        hs.forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(
                color, Offset(i * 2 * w, size.height - bh), Size(w, bh), CornerRadius(w / 2),
            )
        }
    }
}
