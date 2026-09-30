package com.abtinf.glassmusic.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.abtinf.glassmusic.ui.components.LocalBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.formatTime
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.AlbumCard
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.DynamicAlbumBackground
import com.abtinf.glassmusic.ui.components.Equalizer
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.components.glass
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Album, artist and playlist detail: cover fading into a tinted, blurred page. [kind] is album / artist / playlist. */
@Composable
fun DetailScreen(
    kind: String,
    id: String,
    vm: MusicViewModel,
    bottomPad: Dp,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditPlaylist: (String) -> Unit,
    onOpenMetadata: (Long) -> Unit,
) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val player by vm.playerState.collectAsState()
    val playlists by vm.playlists.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    val playlist = if (kind == "playlist") playlists.firstOrNull { it.id == id } else null
    val album = if (kind == "album") library.albumById[id.toLongOrNull()] else null
    val artist = if (kind == "artist") library.artistById[id.toLongOrNull()] else null

    val tracks: List<Track> = album?.tracks ?: artist?.tracks ?: playlist?.let { vm.resolve(it) } ?: emptyList()
    val title = album?.title ?: artist?.name ?: playlist?.name ?: ""
    val subtitle = album?.artist ?: if (playlist != null) "Playlist" else if (artist != null) "Artist" else ""
    val minutes = (tracks.sumOf { it.durationMs } / 60_000).toInt().coerceAtLeast(1)
    val formats = tracks.map { it.format }.distinct()
    val meta = "${tracks.size} downloaded  •  $minutes min" +
        (tracks.firstOrNull { it.bitrateKbps > 0 }?.let { "  •  ${formats.singleOrNull() ?: "Mixed"} ${if (formats.size == 1) "${it.bitrateKbps}kbps" else ""}".trimEnd() } ?: "")
    val cover = tracks.firstOrNull()

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        DynamicAlbumBackground(cover, Modifier.fillMaxSize(), scrimAlpha = 0.25f)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                            },
                    ) {
                        Artwork(cover, Modifier.fillMaxSize(), corner = 0.dp)
                    }
                    Column(Modifier.offset(y = (-56).dp).fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = AmType.Title.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = AmType.Title.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal), color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(meta, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeaderPill("Play", AmIcons.Play, true, Modifier.weight(1f)) { vm.play(tracks, 0) }
                            HeaderPill("Shuffle", AmIcons.Shuffle, false, Modifier.weight(1f)) { vm.shuffle(tracks) }
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier.offset(y = (-36).dp).padding(horizontal = 16.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)),
                ) {
                    tracks.forEachIndexed { i, t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.play(tracks, i) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(28.dp)) {
                                if (player.current?.id == t.id) Equalizer(player.isPlaying, Color.White)
                                else Text("${i + 1}", style = AmType.Body.copy(fontWeight = FontWeight.Normal), color = Color.White.copy(alpha = 0.75f))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = AmType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, style = AmType.Caption.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onOpenMetadata(t.id) }, contentAlignment = Alignment.Center) {
                                Icon(AmIcons.Play, "Play", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    if (tracks.isEmpty()) Text("Nothing here yet.", style = AmType.Caption, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(16.dp))
                }
            }
            if (artist != null) {
                val albums = library.albums.filter { it.tracks.first().artistId == artist.id }
                if (albums.isNotEmpty()) {
                    item { SectionHeader("Albums", Modifier.padding(top = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(albums, key = { it.id }) { a -> AlbumCard(a.title, "${a.tracks.size} songs", a.cover, 140.dp, onClick = { onOpenAlbum(a.id) }) }
                        }
                    }
                }
            }
        }

        }

        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTranslucent(AmIcons.ChevronLeft, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (playlist != null) {
                RoundTranslucent(AmIcons.Edit, "Edit") { onEditPlaylist(playlist.id) }
                Spacer(Modifier.width(8.dp))
                RoundTranslucent(AmIcons.Trash, "Delete") { confirmDelete = true }
            }
        }
    }
    }

    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${playlist.name}”?") },
            text = { Text("This playlist will be removed from your library. The songs stay on your device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(playlist.id); onBack() }) { Text("Delete", color = AmAccent) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoundTranslucent(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, Color.Black.copy(alpha = 0.32f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun HeaderPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (primary) Color.Black else Color.White
    Row(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.2f)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AmType.Title.copy(fontSize = 18.sp), color = fg)
    }
}
