package com.abtinf.glassmusic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

private val RedChipBg = Color(0xFF3A1318)
private val GrayChipBg = Color(0xFF3A3A3C)

@Composable
fun Chip(text: String, fg: Color, bg: Color?, modifier: Modifier = Modifier) {
    Text(
        text, style = AmType.Tiny.copy(fontSize = 10.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .then(if (bg != null) Modifier.clip(RoundedCornerShape(4.dp)).background(bg) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Song row of the Songs list: artwork, title, artist, info chips (Downloaded / album / format + bitrate) and a
 * round play button. Tapping the row opens the track's metadata, the play button starts playback.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongRow(
    track: Track,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    showDivider: Boolean = true,
) {
    val am = LocalAm.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track, Modifier.size(56.dp), corner = 6.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title, style = AmType.Body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    color = if (isCurrent) AmAccent else am.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(track.artist, style = AmType.Caption.copy(fontSize = 13.sp), color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Downloaded", AmAccent, RedChipBg)
                    Chip(track.album, am.secondary, null, Modifier.widthIn(max = 54.dp))
                    val fmt = track.format + if (track.bitrateKbps > 0) " ${track.bitrateKbps}kbps" else ""
                    if (track.format == "FLAC" || track.format == "WAV") Chip(fmt, AmAccent, RedChipBg)
                    else Chip(fmt, Color(0xFFD1D1D6), GrayChipBg)
                }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF151517)).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AmIcons.Play, "Play", tint = AmAccent, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = 84.dp), color = am.separator)
    }
}
