package com.abtinf.glassmusic.ui.detail

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
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

/** Album, artist and playlist detail. [kind] is "album", "artist" or "playlist". */
@Composable
fun DetailScreen(
    kind: String,
    id: String,
    vm: MusicViewModel,
    bottomPad: Dp,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onEditPlaylist: (String) -> Unit,
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
    val subtitle = when {
        album != null -> "${album.artist} · ${formatTotal(tracks)}"
        artist != null -> formatTotal(tracks)
        else -> formatTotal(tracks)
    }

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(AmIcons.ChevronLeft, "Back", tint = AmAccent, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (playlist != null) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { onEditPlaylist(playlist.id) }, contentAlignment = Alignment.Center) {
                            Icon(AmIcons.Edit, "Edit", tint = AmAccent, modifier = Modifier.size(24.dp))
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                            Icon(AmIcons.Trash, "Delete", tint = AmAccent, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val artMod = Modifier.size(232.dp)
                when {
                    playlist != null -> CoverMosaic(tracks, artMod, corner = 12.dp, elevation = 12.dp)
                    tracks.isNotEmpty() -> Artwork(tracks.first(), artMod, corner = if (artist != null) 116.dp else 12.dp, elevation = 12.dp)
                    else -> Spacer(artMod)
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = AmType.Section.copy(fontSize = 24.sp), color = am.text, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Text(subtitle, style = AmType.Caption, color = am.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                Spacer(Modifier.height(16.dp))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionPill("Play", AmIcons.Play, Modifier.weight(1f)) { vm.play(tracks, 0) }
                    ActionPill("Shuffle", AmIcons.Shuffle, Modifier.weight(1f)) { vm.shuffle(tracks) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(tracks, key = { i, t -> "${t.id}#$i" }) { i, t ->
            SongRow(
                t, onClick = { vm.play(tracks, i) },
                isCurrent = player.current?.id == t.id, isPlaying = player.isPlaying,
                showArtwork = album == null, number = i + 1,
                showDuration = album != null,
                onMore = { vm.showTrackMenu(it, playlist?.id) },
            )
        }
        if (artist != null) {
            val albums = library.albums.filter { it.tracks.first().artistId == artist.id }
            if (albums.isNotEmpty()) {
                item { SectionHeader("Albums", Modifier.padding(top = 24.dp)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(albums, key = { it.id }) { a -> AlbumCard(a.title, "${a.tracks.size} songs", a.cover, 140.dp, onClick = { onOpenAlbum(a.id) }) }
                    }
                }
            }
        }
        if (tracks.isEmpty()) item {
            Text("Nothing here yet.", style = AmType.Caption, color = am.secondary, modifier = Modifier.padding(20.dp))
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
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val am = LocalAm.current
    Row(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(am.surface).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = AmAccent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AmType.Title, color = AmAccent)
    }
}
