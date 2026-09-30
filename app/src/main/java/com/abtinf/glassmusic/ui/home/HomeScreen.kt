package com.abtinf.glassmusic.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.AlbumCard
import com.abtinf.glassmusic.ui.components.ArtistCircle
import com.abtinf.glassmusic.ui.components.LibrarySongRow
import com.abtinf.glassmusic.ui.components.PlaylistTile
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

private val Tile = 136.dp

@Composable
fun HomeScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onRequestPermission: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenMetadata: (Long) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val am = LocalAm.current
    val home by vm.home.collectAsState()
    val library by vm.library.collectAsState()
    val player by vm.playback.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(am.background),
        contentPadding = PaddingValues(bottom = bottomPad),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Avatar(onClick = { vm.setShowSettings(true) })
            }
        }
        item { Text("Home", style = AmType.LargeTitle, color = am.text, modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp)) }
        if (!library.hasPermission) {
            item { PermissionBanner(library.isDemo, onRequestPermission) }
        }
        if (home.hotArtists.isNotEmpty()) {
            item { SectionHeader("Hot Artists", Modifier.padding(top = 4.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.hotArtists, key = { it.id }) { a -> ArtistCircle(a, Tile, onClick = { onOpenArtist(a.id) }) }
                }
            }
        }
        if (home.picks.isNotEmpty()) {
            item { SectionHeader("New Release Playlists", Modifier.padding(top = 28.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.picks, key = { it.key }) { pick ->
                        PlaylistTile(pick, Tile, onClick = {
                            if (pick is com.abtinf.glassmusic.ui.components.HomePick.UserPlaylist) onOpenPlaylist(pick.playlist.id)
                            else if (pick is com.abtinf.glassmusic.ui.components.HomePick.AlbumPick) onOpenAlbum(pick.album.id)
                            else if (pick is com.abtinf.glassmusic.ui.components.HomePick.ArtistMix) onOpenArtist(pick.artist.id)
                            else vm.play(pick.tracks, 0)
                        })
                    }
                }
            }
        }
        if (home.hotSongs.isNotEmpty()) {
            item { SectionHeader("Hot Songs", Modifier.padding(top = 28.dp), chevron = true, onClick = { onOpenList("songs") }) }
            items(home.hotSongs, key = { "hot${it.id}" }) { t ->
                LibrarySongRow(
                    t, onOpen = { onOpenMetadata(t.id) },
                    onPlay = { vm.play(home.hotSongs, home.hotSongs.indexOf(t)) },
                    onLongPress = { vm.showTrackMenu(t) },
                    isCurrent = player.current?.id == t.id,
                )
            }
        }
        if (home.recentlyAdded.isNotEmpty()) {
            item { SectionHeader("Recently Added", Modifier.padding(top = 28.dp), chevron = true, onClick = { onOpenList("albums") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.recentlyAdded, key = { "ra${it.id}" }) { a ->
                        AlbumCard(a.title, a.artist, a.cover, 140.dp, onClick = { onOpenAlbum(a.id) }, corner = 10.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF6E6E73), Color(0xFF2C2C2E))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AmIcons.Person, "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionBanner(demo: Boolean, onRequest: () -> Unit) {
    val am = LocalAm.current
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(am.surface)
            .clickable(onClick = onRequest)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Play your own music", style = AmType.Title, color = am.text)
            Spacer(Modifier.height(2.dp))
            Text(
                if (demo) "Allow access to audio on this device. Until then a sample library is shown." else "Allow access to audio files on this device.",
                style = AmType.Caption, color = am.secondary,
            )
        }
        Text("Allow", style = AmType.Title, color = AmAccent, modifier = Modifier.padding(start = 12.dp))
    }
}
