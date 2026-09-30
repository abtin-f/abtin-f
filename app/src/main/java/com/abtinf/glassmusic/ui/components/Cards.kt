package com.abtinf.glassmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Album
import com.abtinf.glassmusic.data.Artist
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Square artwork with title/subtitle underneath (Recently Played, album grids...). */
@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    cover: Track?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
    cover2: @Composable (() -> Unit)? = null,
) {
    val am = LocalAm.current
    Column(modifier.width(size).clickable(onClick = onClick)) {
        if (cover2 != null) {
            Box(Modifier.size(size)) { cover2() }
        } else {
            Artwork(cover, Modifier.size(size), corner, elevation = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = AmType.Body.copy(fontSize = 14.sp), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = AmType.Caption, color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the "Top Picks for You" carousel can show. */
sealed interface HomePick {
    val key: String
    val tracks: List<Track>

    data class Replay(override val tracks: List<Track>, val artists: List<String>) : HomePick { override val key = "replay" }
    data class AlbumPick(val album: Album) : HomePick {
        override val key = "album${album.id}"
        override val tracks get() = album.tracks
    }
    data class ArtistMix(val artist: Artist) : HomePick {
        override val key = "artist${artist.id}"
        override val tracks get() = artist.tracks
    }
    data class Favorites(override val tracks: List<Track>) : HomePick { override val key = "favorites" }
}

val FeaturedCardWidth = 172.dp
val FeaturedCardHeight = 232.dp

/** Large portrait card (≈172×232dp, 16dp corners) with text integrated into the artwork. */
@Composable
fun FeaturedCard(pick: HomePick, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .size(FeaturedCardWidth, FeaturedCardHeight)
            .shadow(6.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        when (pick) {
            is HomePick.Replay -> ReplayFace(pick)
            is HomePick.AlbumPick -> ArtFace(pick.album.cover, "Listen Again", pick.album.title, pick.album.artist)
            is HomePick.ArtistMix -> ArtFace(pick.artist.cover, "Made for You", "${pick.artist.name} Mix", "${pick.tracks.size} songs")
            is HomePick.Favorites -> FavoritesFace(pick)
        }
    }
}

@Composable
private fun ReplayFace(pick: HomePick.Replay) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF00D4C8), Color(0xFFFF3D9A), Color(0xFFFF8F2A)))),
    ) {
        Column(Modifier.padding(14.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AmIcons.Note, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("Music", style = AmType.Body.copy(fontSize = 13.sp), color = Color.White)
            }
            Spacer(Modifier.height(32.dp))
            Text("Replay", style = AmType.Title.copy(fontSize = 22.sp), color = Color.White)
            Text("All\nTime", style = AmType.LargeTitle.copy(fontSize = 44.sp, lineHeight = 46.sp), color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("Made for You", style = AmType.Tiny, color = Color.White.copy(alpha = 0.85f))
            Text(
                pick.artists.take(5).joinToString(", ").ifEmpty { "Your favourite songs" },
                style = AmType.Tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavoritesFace(pick: HomePick.Favorites) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFF5E7A), Color(0xFFFA2D48), Color(0xFF8E1130))))) {
        Icon(AmIcons.StarFilled, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(150.dp).align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("Made for You", style = AmType.Tiny, color = Color.White.copy(alpha = 0.85f))
            Text("Favorites Mix", style = AmType.Title, color = Color.White)
            Text("${pick.tracks.size} songs", style = AmType.Caption, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Artwork fills the whole card; a soft scrim carries the caption at the bottom. */
@Composable
private fun ArtFace(cover: Track, eyebrow: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize()) {
        Artwork(cover, Modifier.fillMaxSize(), corner = 0.dp)
        Box(
            Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(eyebrow, style = AmType.Tiny, color = Color.White.copy(alpha = 0.75f))
            Text(title, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val am = LocalAm.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = AmType.Section, color = am.text)
        if (chevron) Icon(AmIcons.ChevronRight, null, tint = am.tertiary, modifier = Modifier.size(18.dp))
    }
}
