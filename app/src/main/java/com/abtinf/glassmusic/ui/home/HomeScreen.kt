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
import com.abtinf.glassmusic.ui.components.CoverMosaic
import com.abtinf.glassmusic.ui.components.FeaturedCard
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

@Composable
fun HomeScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onRequestPermission: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val am = LocalAm.current
    val home by vm.home.collectAsState()
    val library by vm.library.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().background(am.background),
        contentPadding = PaddingValues(bottom = bottomPad),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Home", style = AmType.LargeTitle, color = am.text, modifier = Modifier.weight(1f))
                Avatar(onClick = { vm.setShowSettings(true) })
            }
        }
        if (!library.hasPermission) {
            item { PermissionBanner(library.isDemo, onRequestPermission) }
        }
        if (home.picks.isNotEmpty()) {
            item { SectionHeader("Top Picks for You", Modifier.padding(top = 8.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.picks, key = { it.key }) { pick ->
                        FeaturedCard(pick, onClick = { vm.play(pick.tracks, 0) })
                    }
                }
            }
        }
        if (home.recentlyPlayed.isNotEmpty()) {
            item { SectionHeader("Recently Played", Modifier.padding(top = 24.dp), chevron = true, onClick = { onOpenList("songs") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.recentlyPlayed, key = { it.id }) { t ->
                        AlbumCard(t.title, t.artist, t, 120.dp, onClick = { vm.play(home.recentlyPlayed, home.recentlyPlayed.indexOf(t)) })
                    }
                }
            }
        }
        if (home.recentlyAdded.isNotEmpty()) {
            item { SectionHeader("Recently Added", Modifier.padding(top = 24.dp), chevron = true, onClick = { onOpenList("albums") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.recentlyAdded, key = { it.id }) { a ->
                        AlbumCard(a.title, a.artist, a.cover, 140.dp, onClick = { onOpenAlbum(a.id) })
                    }
                }
            }
        }
        if (home.playlists.isNotEmpty()) {
            item { SectionHeader("Your Playlists", Modifier.padding(top = 24.dp), chevron = true, onClick = { onOpenList("playlists") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(home.playlists, key = { it.id }) { p ->
                        val tracks = vm.resolve(p)
                        AlbumCard(p.name, "${tracks.size} songs", null, 140.dp, onClick = { onOpenPlaylist(p.id) }, cover2 = {
                            CoverMosaic(tracks, Modifier.fillMaxSize(), 8.dp, 2.dp)
                        })
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
            .background(Brush.linearGradient(listOf(Color(0xFFFFB199), Color(0xFFFF0844))))
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
            .padding(horizontal = 20.dp, vertical = 8.dp)
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
