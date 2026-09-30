package com.abtinf.glassmusic.ui.library

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.AlbumCard
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.CoverMosaic
import com.abtinf.glassmusic.ui.components.LibrarySongRow
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

private data class Category(val kind: String, val label: String, val icon: ImageVector)

private val categories = listOf(
    Category("playlists", "Playlists", AmIcons.Playlist),
    Category("artists", "Artists", AmIcons.Mic),
    Category("albums", "Albums", AmIcons.Album),
    Category("songs", "Songs", AmIcons.Note),
)

@Composable
fun LibraryScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenList: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpenSearch), contentAlignment = Alignment.Center) {
                    Icon(AmIcons.Search, "Search", tint = AmAccent, modifier = Modifier.size(24.dp))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpenList("downloaded") }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AmIcons.Download, null, tint = AmAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Downloads", style = AmType.Body.copy(fontSize = 16.sp), color = AmAccent)
                }
            }
        }
        item { Text("Library", style = AmType.LargeTitle, color = am.text, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)) }
        items(categories, key = { it.kind }) { c ->
            Column {
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clickable { onOpenList(c.kind) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, null, tint = AmAccent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(c.label, style = AmType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal), color = am.text, modifier = Modifier.weight(1f))
                    Icon(AmIcons.ChevronRight, null, tint = am.secondary, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = am.separator)
            }
        }
        item { SectionHeader("Recently Added", Modifier.padding(top = 28.dp)) }
        val albums = library.recentlyAddedAlbums.take(8).chunked(2)
        items(albums, key = { row -> "la${row.first().id}" }) { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { a ->
                    Box(Modifier.weight(1f)) {
                        AlbumCard(a.title, a.artist, a.cover, 160.dp, onClick = { onOpenAlbum(a.id) }, modifier = Modifier.fillMaxWidth(), corner = 6.dp)
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
    onOpenMetadata: (Long) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val player by vm.playerState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val title = when (kind) {
        "downloaded" -> "Downloads"
        else -> categories.firstOrNull { it.kind == kind }?.label ?: "Library"
    }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 } }
    val barAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "barAlpha")

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Title") }
    var lossless by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(am.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = bottomPad)) {
            item { Spacer(Modifier.statusBarsPadding().height(56.dp)) }
            item { Text(title, style = AmType.LargeTitle, color = am.text, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp)) }
            when (kind) {
                "songs", "downloaded" -> {
                    item {
                        Row(
                            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp)
                                .clip(RoundedCornerShape(26.dp)).background(am.surface).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AmIcons.Search, null, tint = am.secondary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Search your library", style = AmType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal), color = am.secondary)
                                BasicTextField(
                                    value = query, onValueChange = { query = it }, singleLine = true,
                                    textStyle = AmType.Body.copy(fontSize = 17.sp, color = am.text, fontWeight = FontWeight.Normal),
                                    cursorBrush = SolidColor(AmAccent), modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    val songs = library.tracks
                        .filter { t -> query.isBlank() || t.title.contains(query, true) || t.artist.contains(query, true) || t.album.contains(query, true) }
                        .filter { !lossless || it.format == "FLAC" || it.format == "WAV" }
                        .let { l ->
                            when (sort) {
                                "Artist" -> l.sortedBy { it.artist.lowercase() }
                                "Recently Added" -> l.sortedByDescending { it.dateAdded }
                                "Duration" -> l.sortedByDescending { it.durationMs }
                                else -> l.sortedBy { it.title.lowercase() }
                            }
                        }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${songs.size} tracks", style = AmType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), color = am.secondary)
                            Spacer(Modifier.weight(1f))
                            Box {
                                Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable { filterMenu = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(AmIcons.Filter, null, tint = AmAccent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Filters", style = AmType.Title.copy(fontSize = 15.sp), color = AmAccent)
                                }
                                DropdownMenu(filterMenu, { filterMenu = false }) {
                                    listOf("Title", "Artist", "Recently Added", "Duration").forEach { s ->
                                        DropdownMenuItem(text = { Text(if (s == sort) "✓  $s" else s) }, onClick = { sort = s; filterMenu = false })
                                    }
                                    DropdownMenuItem(text = { Text(if (lossless) "✓  Lossless only" else "Lossless only") }, onClick = { lossless = !lossless; filterMenu = false })
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onNewPlaylist).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AmIcons.Plus, null, tint = AmAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create playlist", style = AmType.Title.copy(fontSize = 15.sp), color = AmAccent)
                            }
                        }
                    }
                    items(songs, key = { "s${it.id}" }) { t ->
                        LibrarySongRow(
                            t, onOpen = { onOpenMetadata(t.id) },
                            onPlay = { vm.play(songs, songs.indexOf(t)) },
                            onLongPress = { vm.showTrackMenu(t) },
                            isCurrent = player.current?.id == t.id,
                        )
                    }
                }
                "albums" -> {
                    items(library.albums.chunked(2), key = { row -> "al${row.first().id}" }) { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { a ->
                                Box(Modifier.weight(1f)) {
                                    AlbumCard(a.title, a.artist, a.cover, 160.dp, onClick = { onOpenAlbum(a.id) }, modifier = Modifier.fillMaxWidth(), corner = 6.dp)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                "artists" -> items(library.artists, key = { "ar${it.id}" }) { a ->
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).clickable { onOpenArtist(a.id) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(a.cover, Modifier.size(56.dp), corner = 28.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = AmType.Title.copy(fontWeight = FontWeight.Normal), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.tracks.size} songs", style = AmType.Caption, color = am.secondary)
                        }
                        Icon(AmIcons.ChevronRight, null, tint = am.secondary, modifier = Modifier.size(18.dp))
                    }
                }
                "playlists" -> {
                    item {
                        Row(
                            Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onNewPlaylist).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(am.surface), contentAlignment = Alignment.Center) {
                                Icon(AmIcons.Sparkle, null, tint = AmAccent, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text("Create playlist", style = AmType.Title, color = AmAccent)
                        }
                    }
                    items(playlists, key = { "pl${it.id}" }) { p ->
                        val tracks = vm.resolve(p)
                        Row(
                            Modifier.fillMaxWidth().height(76.dp).clickable { onOpenPlaylist(p.id) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverMosaic(tracks, Modifier.size(56.dp), corner = 8.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = AmType.Title.copy(fontWeight = FontWeight.Normal), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${tracks.size} songs", style = AmType.Caption, color = am.secondary)
                            }
                            Icon(AmIcons.ChevronRight, null, tint = am.secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (playlists.isEmpty()) item {
                        Text("No playlists yet. Tap Create playlist to try Playlist Playground.", style = AmType.Caption, color = am.secondary, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        TopBar(title, barAlpha, onBack)
    }
}

/** Red back chevron that grows a centred/leading small title once the large title scrolls away. */
@Composable
fun TopBar(title: String, titleAlpha: Float, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val am = LocalAm.current
    Box(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f * titleAlpha), Color.Transparent)))
            .statusBarsPadding().height(56.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(AmIcons.ChevronLeft, "Back", tint = AmAccent, modifier = Modifier.size(24.dp))
            }
            Text(title, style = AmType.Title, color = am.text, modifier = Modifier.alpha(titleAlpha))
        }
    }
}
