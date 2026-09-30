package com.abtinf.glassmusic.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.formatTotal
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.AlbumCard
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.CoverMosaic
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.components.SongRow
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

private data class Category(val kind: String, val label: String, val icon: ImageVector)

private val categories = listOf(
    Category("playlists", "Playlists", AmIcons.Playlist),
    Category("artists", "Artists", AmIcons.Person),
    Category("albums", "Albums", AmIcons.Album),
    Category("songs", "Songs", AmIcons.Note),
    Category("downloaded", "Downloaded", AmIcons.Download),
)

@Composable
fun LibraryScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenList: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlayground: () -> Unit,
) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Library", style = AmType.LargeTitle, color = am.text, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpenPlayground), contentAlignment = Alignment.Center) {
                    Icon(AmIcons.Plus, "New playlist", tint = AmAccent, modifier = Modifier.size(28.dp))
                }
            }
        }
        items(categories, key = { it.kind }) { c ->
            Column {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clickable { onOpenList(c.kind) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, null, tint = AmAccent, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(c.label, style = AmType.Title.copy(fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = am.text, modifier = Modifier.weight(1f))
                    Icon(AmIcons.ChevronRight, null, tint = am.tertiary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(Modifier.padding(start = 62.dp), color = am.separator)
            }
        }
        item { SectionHeader("Recently Added", Modifier.padding(top = 24.dp)) }
        val albums = library.recentlyAddedAlbums.take(8).chunked(2)
        items(albums, key = { row -> row.first().id }) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { a ->
                    Box(Modifier.weight(1f)) {
                        AlbumCard(a.title, a.artist, a.cover, 160.dp, onClick = { onOpenAlbum(a.id) }, modifier = Modifier.fillMaxWidth(), cover2 = null)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Songs, Downloaded, Albums, Artists and Playlists lists. */
@Composable
fun LibraryListScreen(
    kind: String,
    vm: MusicViewModel,
    bottomPad: Dp,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val player by vm.playerState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val title = categories.firstOrNull { it.kind == kind }?.label ?: "Library"

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { BackTitle(title, onBack) }
        when (kind) {
            "songs", "downloaded" -> {
                val songs = library.tracks.sortedBy { it.title.lowercase() }
                item {
                    Text(
                        (if (kind == "downloaded") "Available offline · " else "") + formatTotal(songs),
                        style = AmType.Caption, color = am.secondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                    SongRow(
                        t, onClick = { vm.play(songs, i) },
                        isCurrent = player.current?.id == t.id, isPlaying = player.isPlaying, onMore = { vm.showTrackMenu(it) },
                    )
                }
            }
            "albums" -> {
                items(library.albums.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { a ->
                            Box(Modifier.weight(1f)) {
                                AlbumCard(a.title, a.artist, a.cover, 160.dp, onClick = { onOpenAlbum(a.id) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            "artists" -> items(library.artists, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().height(72.dp).clickable { onOpenArtist(a.id) }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(a.cover, Modifier.size(56.dp), corner = 28.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = AmType.Title, color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.tracks.size} songs", style = AmType.Caption, color = am.secondary)
                    }
                    Icon(AmIcons.ChevronRight, null, tint = am.tertiary, modifier = Modifier.size(18.dp))
                }
            }
            "playlists" -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onNewPlaylist).padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(am.surface), contentAlignment = Alignment.Center) {
                            Icon(AmIcons.Sparkle, null, tint = AmAccent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text("New Playlist", style = AmType.Title, color = AmAccent)
                    }
                }
                items(playlists, key = { it.id }) { p ->
                    val tracks = vm.resolve(p)
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).clickable { onOpenPlaylist(p.id) }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverMosaic(tracks, Modifier.size(56.dp), corner = 8.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = AmType.Title, color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${tracks.size} songs", style = AmType.Caption, color = am.secondary)
                        }
                        Icon(AmIcons.ChevronRight, null, tint = am.tertiary, modifier = Modifier.size(18.dp))
                    }
                }
                if (playlists.isEmpty()) item {
                    Text("No playlists yet. Tap New Playlist to try Playlist Playground.", style = AmType.Caption, color = am.secondary, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Composable
fun BackTitle(title: String, onBack: () -> Unit) {
    val am = LocalAm.current
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Box(Modifier.padding(start = 8.dp, top = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(AmIcons.ChevronLeft, "Back", tint = AmAccent, modifier = Modifier.size(26.dp))
        }
        Text(title, style = AmType.LargeTitle, color = am.text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
}
