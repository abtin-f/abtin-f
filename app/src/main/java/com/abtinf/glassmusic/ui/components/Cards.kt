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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Album
import com.abtinf.glassmusic.data.Artist
import com.abtinf.glassmusic.data.Playlist
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Square artwork with title/subtitle underneath (Recently Added, album grids...). */
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
            Artwork(cover, Modifier.size(size), corner)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = AmType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = AmType.Caption.copy(fontSize = 13.sp), color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the "New Release Playlists" carousel on Home can show. */
sealed interface HomePick {
    val key: String
    val tracks: List<Track>
    val title: String
    val subtitle: String

    data class Replay(override val tracks: List<Track>, val artists: List<String>) : HomePick {
        override val key = "replay"
        override val title = "Replay All Time"
        override val subtitle get() = artists.take(4).joinToString(", ").ifEmpty { "Your favourite songs" }
    }
    data class AlbumPick(val album: Album) : HomePick {
        override val key = "album${album.id}"
        override val tracks get() = album.tracks
        override val title get() = album.title
        override val subtitle get() = album.artist
    }
    data class ArtistMix(val artist: Artist) : HomePick {
        override val key = "artist${artist.id}"
        override val tracks get() = artist.tracks
        override val title get() = "${artist.name} Mix"
        override val subtitle get() = "${artist.tracks.size} songs"
    }
    data class Favorites(override val tracks: List<Track>) : HomePick {
        override val key = "favorites"
        override val title = "Favorites Mix"
        override val subtitle get() = "${tracks.size} songs"
    }
    data class UserPlaylist(val playlist: Playlist, override val tracks: List<Track>) : HomePick {
        override val key = "playlist${playlist.id}"
        override val title get() = playlist.name
        override val subtitle get() = "${tracks.size} songs"
    }
}

/** Square playlist tile: rounded artwork with title and a one-line subtitle. */
@Composable
fun PlaylistTile(pick: HomePick, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val am = LocalAm.current
    Column(modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size)) {
            when (pick) {
                is HomePick.Replay -> GradientTile(listOf(Color(0xFF00D4C8), Color(0xFFFF3D9A), Color(0xFFFF8F2A)), "Replay", "All Time")
                is HomePick.Favorites -> GradientTile(listOf(Color(0xFFFF5E7A), Color(0xFFE8384A), Color(0xFF8E1130)), "Favorites", "Mix")
                is HomePick.AlbumPick -> Artwork(pick.album.cover, Modifier.fillMaxSize(), 12.dp)
                is HomePick.ArtistMix -> Artwork(pick.artist.cover, Modifier.fillMaxSize(), 12.dp)
                is HomePick.UserPlaylist -> CoverMosaic(pick.tracks, Modifier.fillMaxSize(), 12.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(pick.title, style = AmType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(pick.subtitle, style = AmType.Caption.copy(fontSize = 11.sp), color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GradientTile(colors: List<Color>, top: String, bottom: String) {
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(colors), androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(10.dp),
    ) {
        Icon(AmIcons.Note, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(14.dp).align(Alignment.TopStart))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(top, style = AmType.Title.copy(fontSize = 14.sp), color = Color.White)
            Text(bottom, style = AmType.LargeTitle.copy(fontSize = 24.sp, lineHeight = 26.sp), color = Color.White)
        }
    }
}

/** Circular artist avatar with the name centred underneath. */
@Composable
fun ArtistCircle(artist: Artist, size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val am = LocalAm.current
    Column(modifier.width(size).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(artist.cover, Modifier.size(size), corner = size / 2)
        Spacer(Modifier.height(10.dp))
        Text(
            artist.name, style = AmType.Body.copy(fontSize = 14.sp), color = am.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = AmType.Section.copy(fontSize = 17.sp), color = am.text)
        if (chevron) Icon(AmIcons.ChevronRight, null, tint = am.tertiary, modifier = Modifier.size(18.dp))
    }
}
