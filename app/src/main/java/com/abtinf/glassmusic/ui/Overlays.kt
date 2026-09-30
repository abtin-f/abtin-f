package com.abtinf.glassmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.CoverMosaic
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(
    menu: TrackMenu,
    vm: MusicViewModel,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val am = LocalAm.current
    val favorites by vm.favorites.collectAsState()
    val t = menu.track
    val isFav = t.id in favorites
    ModalBottomSheet(
        onDismissRequest = vm::dismissMenu,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = am.background,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(t, Modifier.size(56.dp), corner = 8.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.title, style = AmType.Title, color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.artist} · ${t.album}", style = AmType.Caption, color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = am.separator)
            MenuRow(AmIcons.PlayNext, "Play Next") { vm.playNext(t); vm.dismissMenu() }
            MenuRow(AmIcons.AddToQueue, "Add to Queue") { vm.addToQueue(t); vm.dismissMenu() }
            MenuRow(AmIcons.Playlist, "Add to Playlist…") { vm.dismissMenu(); vm.showPlaylistPicker(listOf(t)) }
            MenuRow(if (isFav) AmIcons.StarFilled else AmIcons.Star, if (isFav) "Remove from Favorites" else "Add to Favorites") {
                vm.toggleFavorite(t); vm.dismissMenu()
            }
            MenuRow(AmIcons.Album, "Go to Album") { vm.dismissMenu(); onOpenAlbum(t.albumId) }
            MenuRow(AmIcons.Person, "Go to Artist") { vm.dismissMenu(); onOpenArtist(t.artistId) }
            if (menu.playlistId != null) {
                MenuRow(AmIcons.Trash, "Remove from Playlist", destructive = true) {
                    vm.removeFromPlaylist(menu.playlistId, t); vm.dismissMenu()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val am = LocalAm.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (destructive) AmAccent else am.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = AmType.Title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = if (destructive) AmAccent else am.text)
    }
}

/** Add songs to an existing playlist or to a brand-new one. */
@Composable
fun PlaylistPickerDialog(tracks: List<Track>, vm: MusicViewModel) {
    val playlists by vm.playlists.collectAsState()
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::dismissPicker,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                OutlinedTextField(newName, { newName = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(if (playlists.isEmpty()) 0.dp else 200.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        val resolved = vm.resolve(p)
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clickable { vm.addToPlaylist(p.id, tracks); vm.dismissPicker() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverMosaic(resolved, Modifier.size(40.dp), corner = 6.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = newName.isNotBlank(),
                onClick = { vm.createPlaylist(newName.trim(), tracks); vm.dismissPicker() },
            ) { Text("Create", color = AmAccent) }
        },
        dismissButton = { TextButton(onClick = vm::dismissPicker) { Text("Cancel") } },
    )
}

@Composable
fun SettingsDialog(vm: MusicViewModel, onRequestPermission: () -> Unit) {
    val state by vm.playerState.collectAsState()
    val lib by vm.library.collectAsState()
    AlertDialog(
        onDismissRequest = { vm.setShowSettings(false) },
        title = { Text("Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AutoMix", style = AmType.Title)
                        Text("Fade songs into each other like a DJ.", style = AmType.Caption)
                    }
                    Switch(
                        checked = state.autoMix, onCheckedChange = { vm.setAutoMix(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = AmAccent),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Library", style = AmType.Title)
                Text(
                    "${lib.tracks.size} songs · " + if (lib.isDemo) "sample library (no music found on this device)" else "on this device",
                    style = AmType.Caption,
                )
                Box(Modifier.height(4.dp))
                if (!lib.hasPermission) {
                    TextButton(onClick = onRequestPermission) { Text("Allow access to music", color = AmAccent) }
                }
                TextButton(onClick = { vm.refreshLibrary() }) { Text("Rescan library", color = AmAccent) }
            }
        },
        confirmButton = { TextButton(onClick = { vm.setShowSettings(false) }) { Text("Done", color = AmAccent) } },
    )
}
