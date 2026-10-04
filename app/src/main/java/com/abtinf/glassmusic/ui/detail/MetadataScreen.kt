package com.abtinf.glassmusic.ui.detail

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.formatTime
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.glass
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm
import java.text.DateFormat
import java.util.Date

/** Track info page: big cover, Play / Delete, and a card listing every tag the device knows. */
@Composable
fun MetadataScreen(trackId: Long, vm: MusicViewModel, bottomPad: Dp, onBack: () -> Unit) {
    val am = LocalAm.current
    val ctx = LocalContext.current
    val library by vm.library.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val track = library.trackById[trackId]
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) { vm.refreshLibrary(); onBack() }
    }
    if (track == null) { Box(Modifier.fillMaxSize().background(am.background)); return }
    val albumTracks = library.albumById[track.albumId]?.tracks?.size ?: 1

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize().background(am.background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
            item { Spacer(Modifier.statusBarsPadding().height(72.dp)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f), corner = 14.dp, elevation = 12.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(track.title, style = AmType.Title.copy(fontSize = 22.sp), color = am.text, textAlign = TextAlign.Center)
                    Text(track.artist, style = AmType.Title.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), color = am.secondary, textAlign = TextAlign.Center)
                    Text(if (albumTracks == 1) "Single" else track.album, style = AmType.Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal), color = am.secondary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        listOfNotNull(
                            track.format + if (track.bitrateKbps > 0) " ${track.bitrateKbps}kbps" else "",
                            formatTime(track.durationMs), "On this device",
                        ).joinToString("  •  "),
                        style = AmType.Caption.copy(fontSize = 13.sp), color = am.secondary,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RedPill("Play", AmIcons.Play, Modifier.weight(1f)) { vm.playInContext(track) }
                    RedPill("Delete", AmIcons.Trash, Modifier.weight(1f)) { confirmDelete = true }
                }
            }
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(am.surface).padding(20.dp),
                ) {
                    Text("Metadata", style = AmType.Title, color = am.text)
                    Spacer(Modifier.height(8.dp))
                    val date = if (track.dateAdded > 0) DateFormat.getDateInstance().format(Date(track.dateAdded * 1000)) else "—"
                    val fields = listOf(
                        "Track name" to track.title, "Artist" to track.artist, "Album" to track.album,
                        "Album artist" to track.albumArtist.ifBlank { track.artist },
                        "Track number" to (track.trackNo.takeIf { it > 0 } ?: 1).toString(),
                        "Track total" to albumTracks.toString(), "Disc number" to track.discNo.toString(),
                        "Duration" to formatTime(track.durationMs), "Format" to track.format,
                        "Bitrate" to if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "—",
                        "Size" to if (track.sizeBytes > 0) "%.1f MB".format(track.sizeBytes / 1_048_576.0) else "—",
                        "Year" to if (track.year > 0) track.year.toString() else "—", "Date added" to date,
                    )
                    fields.forEachIndexed { i, (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                            Text(k, style = AmType.Caption.copy(fontSize = 13.sp), color = am.secondary)
                            Text(v, style = AmType.Body.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal), color = am.text, modifier = Modifier.padding(vertical = 4.dp))
                            if (i < fields.lastIndex) HorizontalDivider(color = am.separator)
                        }
                    }
                }
            }
        }

        }

        // glass top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(AmIcons.ChevronLeft, "Back", onBack)
            Text("Metadata", style = AmType.Title, color = am.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Box {
                GlassCircleButton(AmIcons.More, "More") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; vm.showPlaylistPicker(listOf(track)) })
                    DropdownMenuItem(text = { Text(if (track.id in favorites) "Remove from favorites" else "Favorite") }, onClick = { menu = false; vm.toggleFavorite(track) })
                    DropdownMenuItem(text = { Text("Share") }, onClick = {
                        menu = false
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}")
                        runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                }
            }
        }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${track.title}”?") },
            text = { Text("The file will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val uri = track.uri?.let(Uri::parse)
                    if (uri == null) notice = "Sample songs have no file to delete."
                    else runCatching {
                        val sender = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(uri)).intentSender
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }.onFailure { notice = "Couldn't delete this file." }
                }) { Text("Delete", color = AmAccent) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    notice?.let { msg ->
        AlertDialog(onDismissRequest = { notice = null }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } })
    }
}

@Composable
private fun GlassCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun RedPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp)).background(AmAccent).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AmType.Title, color = Color.White)
    }
}
