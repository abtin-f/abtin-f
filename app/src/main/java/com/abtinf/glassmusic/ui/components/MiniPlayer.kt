package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Floating glass mini-player that sits above the bottom navigation and expands into Now Playing. */
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val am = LocalAm.current
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .glass(shape, am.glass, elevation = 8.dp)
            .clickable(onClick = onExpand)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track, Modifier.size(44.dp), corner = 8.dp)
        Spacer(Modifier.width(12.dp))
        Crossfade(track.id, Modifier.weight(1f), label = "miniText") {
            Column {
                Text(track.title, style = AmType.Body.copy(fontSize = 14.sp), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = AmType.Caption.copy(fontSize = 12.sp), color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MiniButton(onToggle) {
            Crossfade(isPlaying, label = "miniPlay") { playing ->
                Icon(if (playing) AmIcons.Pause else AmIcons.Play, if (playing) "Pause" else "Play", tint = am.text, modifier = Modifier.size(26.dp))
            }
        }
        MiniButton(onNext) { Icon(AmIcons.Forward, "Next", tint = am.text, modifier = Modifier.size(28.dp)) }
    }
}

@Composable
private fun MiniButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
