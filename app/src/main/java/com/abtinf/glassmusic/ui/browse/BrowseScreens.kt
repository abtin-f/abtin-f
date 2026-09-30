package com.abtinf.glassmusic.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.abtinf.glassmusic.ui.components.ArtistCircle
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Repo: playlist playground, endless stations and recently added albums (everything generated offline). */
@Composable
fun RepoScreen(vm: MusicViewModel, bottomPad: Dp, onOpenPlayground: () -> Unit, onOpenAlbum: (Long) -> Unit) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()
    val favorites by vm.favorites.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item { Text("Repo", style = AmType.LargeTitle, color = am.text, modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, top = 64.dp, bottom = 12.dp)) }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(190.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0B2A5B), Color(0xFF0E6E6A), Color(0xFF1B9E77))))
                    .clickable(onClick = onOpenPlayground).padding(20.dp),
            ) {
                Icon(AmIcons.Sparkle, null, tint = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(130.dp).align(Alignment.CenterEnd))
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("Playlist Playground", style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(4.dp))
                    Text("Create New Playlists in Seconds.", style = AmType.Section.copy(fontSize = 22.sp, lineHeight = 27.sp), color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Turn ideas into playlists made from your own library.", style = AmType.Caption, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFF5E7A), Color(0xFFE8384A), Color(0xFF7A1038))))
                    .clickable { vm.shuffle(library.tracks) }.padding(20.dp),
            ) {
                Icon(AmIcons.Radio, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(100.dp).align(Alignment.CenterEnd))
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("Station", style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.8f))
                    Text("Shuffle All", style = AmType.LargeTitle.copy(fontSize = 26.sp), color = Color.White)
                }
            }
        }
        item { SectionHeader("Artist Stations", Modifier.padding(top = 16.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.artists, key = { it.id }) { artist ->
                    ArtistCircle(artist, 120.dp, onClick = { vm.shuffle(artist.tracks) })
                }
            }
        }
        if (favorites.isNotEmpty()) {
            val favTracks = favorites.mapNotNull { library.trackById[it] }
            item { SectionHeader("For You", Modifier.padding(top = 24.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { AlbumCard("Favorites Station", "${favTracks.size} songs", favTracks.firstOrNull(), 140.dp, onClick = { vm.shuffle(favTracks) }) }
                }
            }
        }
        item { SectionHeader("Recently Added", Modifier.padding(top = 24.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library.recentlyAddedAlbums.take(12), key = { "ra${it.id}" }) { a ->
                    AlbumCard(a.title, a.artist, a.cover, 140.dp, onClick = { onOpenAlbum(a.id) }, corner = 10.dp)
                }
            }
        }
    }
}
