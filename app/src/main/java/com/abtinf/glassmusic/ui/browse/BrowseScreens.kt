package com.abtinf.glassmusic.ui.browse

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
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.AlbumCard
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.components.SongRow
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

@Composable
private fun ScreenTitle(title: String) {
    val am = LocalAm.current
    Text(
        title, style = AmType.LargeTitle, color = am.text,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun NewScreen(vm: MusicViewModel, bottomPad: Dp, onOpenPlayground: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val player by vm.playerState.collectAsState()
    val recent = library.recentlyAddedTracks
    val recents by vm.home.collectAsState()
    val seen = recents.recentlyPlayed.map { it.id }.toSet()
    val fresh = recent.filter { it.id !in seen }.take(6)

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { ScreenTitle("New") }
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0B2A5B), Color(0xFF0E6E6A), Color(0xFF1B9E77))))
                    .clickable(onClick = onOpenPlayground)
                    .padding(20.dp),
            ) {
                Icon(AmIcons.Sparkle, null, tint = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(140.dp).align(Alignment.CenterEnd))
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("Playlist Playground", style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(4.dp))
                    Text("Create New Playlists in Seconds.", style = AmType.Section.copy(fontSize = 24.sp, lineHeight = 28.sp), color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Turn ideas into playlists made from your own library.", style = AmType.Caption, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        item { SectionHeader("Recently Added", Modifier.padding(top = 16.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.recentlyAddedAlbums.take(12), key = { it.id }) { a ->
                    AlbumCard(a.title, a.artist, a.cover, 160.dp, onClick = { onOpenAlbum(a.id) }, corner = 10.dp)
                }
            }
        }
        if (fresh.isNotEmpty()) {
            item { SectionHeader("Something New to You", Modifier.padding(top = 24.dp)) }
            items(fresh, key = { it.id }) { t ->
                SongRow(
                    t, onClick = { vm.play(fresh, fresh.indexOf(t)) },
                    isCurrent = player.current?.id == t.id, isPlaying = player.isPlaying, onMore = { vm.showTrackMenu(it) },
                )
            }
        }
    }
}

@Composable
fun RadioScreen(vm: MusicViewModel, bottomPad: Dp) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val favorites by vm.favorites.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { ScreenTitle("Radio") }
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5E7A), Color(0xFFFA2D48), Color(0xFF7A1038))))
                    .clickable { vm.shuffle(library.tracks) }
                    .padding(20.dp),
            ) {
                Icon(AmIcons.Radio, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(150.dp).align(Alignment.CenterEnd))
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("Station", style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.8f))
                    Text("Shuffle All", style = AmType.LargeTitle, color = Color.White)
                    Text("Your whole library, endlessly mixed", style = AmType.Caption, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        item { SectionHeader("Artist Stations", Modifier.padding(top = 16.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.artists, key = { it.id }) { artist ->
                    AlbumCard(
                        "${artist.name} Station", "${artist.tracks.size} songs", artist.cover, 140.dp,
                        onClick = { vm.shuffle(artist.tracks) }, corner = 70.dp,
                    )
                }
            }
        }
        if (favorites.isNotEmpty()) {
            val favTracks = favorites.mapNotNull { library.trackById[it] }
            item { SectionHeader("For You", Modifier.padding(top = 24.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        AlbumCard("Favorites Station", "${favTracks.size} songs", favTracks.firstOrNull(), 140.dp, onClick = { vm.shuffle(favTracks) })
                    }
                }
            }
        }
        item { SectionHeader("Album Stations", Modifier.padding(top = 24.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.albums, key = { it.id }) { a ->
                    AlbumCard(a.title, "Album Station", a.cover, 140.dp, onClick = { vm.shuffle(a.tracks) })
                }
            }
        }
    }
}
