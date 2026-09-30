package com.abtinf.glassmusic.ui.search

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.SearchFilter
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.CoverMosaic
import com.abtinf.glassmusic.ui.components.SectionHeader
import com.abtinf.glassmusic.ui.components.SongRow
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

@Composable
fun SearchScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val am = LocalAm.current
    val query by vm.query.collectAsState()
    val filter by vm.searchFilter.collectAsState()
    val results by vm.searchResults.collectAsState()
    val player by vm.playerState.collectAsState()
    val focus = LocalFocusManager.current

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Text(
                "Search", style = AmType.LargeTitle, color = am.text,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp)).background(am.surface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AmIcons.Search, null, tint = am.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Songs, Artists, Albums, Lyrics", style = AmType.Body.copy(fontSize = 16.sp), color = am.secondary)
                    BasicTextField(
                        value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                        textStyle = AmType.Body.copy(fontSize = 16.sp, color = am.text),
                        cursorBrush = SolidColor(AmAccent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(AmIcons.Close, "Clear", tint = am.secondary, modifier = Modifier.size(22.dp).clickable { vm.query.value = "" })
                }
            }
        }
        if (query.isBlank()) {
            item { SectionHeader("Browse Your Library", Modifier.padding(top = 16.dp)) }
            val tiles = listOf(
                Triple("songs", "Songs", Color(0xFFFA2D48)), Triple("artists", "Artists", Color(0xFF7B61FF)),
                Triple("albums", "Albums", Color(0xFF0FA3B1)), Triple("playlists", "Playlists", Color(0xFFF2994A)),
            )
            items(tiles.chunked(2), key = { it.first().first }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (kind, label, color) ->
                        Box(
                            Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
                                .clickable { onOpenList(kind) }.padding(12.dp),
                        ) {
                            Text(label, style = AmType.Title, color = Color.White, modifier = Modifier.align(Alignment.TopStart))
                        }
                    }
                }
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchFilter.entries.toList(), key = { it.name }) { f ->
                        val on = f == filter
                        Text(
                            f.label, style = AmType.Body.copy(fontSize = 14.sp), color = if (on) Color.White else am.text,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) AmAccent else am.surface)
                                .clickable { vm.searchFilter.value = f }.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            if (results.isEmpty) {
                item { Text("No results for “$query”.", style = AmType.Body, color = am.secondary, modifier = Modifier.padding(20.dp)) }
            }
            val all = filter == SearchFilter.All
            if ((all || filter == SearchFilter.Artists) && results.artists.isNotEmpty()) {
                item { SectionHeader("Artists", Modifier.padding(top = 16.dp)) }
                items(results.artists.take(if (all) 3 else 100), key = { "ar${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth().height(64.dp).clickable { onOpenArtist(a.id) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(a.cover, Modifier.size(48.dp), corner = 24.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(a.name, style = AmType.Body.copy(fontSize = 16.sp), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if ((all || filter == SearchFilter.Albums) && results.albums.isNotEmpty()) {
                item { SectionHeader("Albums", Modifier.padding(top = 16.dp)) }
                items(results.albums.take(if (all) 3 else 100), key = { "al${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth().height(64.dp).clickable { onOpenAlbum(a.id) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(a.cover, Modifier.size(48.dp), corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.title, style = AmType.Body.copy(fontSize = 16.sp), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Album · ${a.artist}", style = AmType.Caption.copy(fontSize = 14.sp), color = am.secondary, maxLines = 1)
                        }
                    }
                }
            }
            if ((all || filter == SearchFilter.Playlists) && results.playlists.isNotEmpty()) {
                item { SectionHeader("Playlists", Modifier.padding(top = 16.dp)) }
                items(results.playlists.take(if (all) 3 else 100), key = { "pl${it.id}" }) { p ->
                    val tracks = vm.resolve(p)
                    Row(Modifier.fillMaxWidth().height(64.dp).clickable { onOpenPlaylist(p.id) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverMosaic(tracks, Modifier.size(48.dp), corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = AmType.Body.copy(fontSize = 16.sp), color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Playlist · ${tracks.size} songs", style = AmType.Caption.copy(fontSize = 14.sp), color = am.secondary, maxLines = 1)
                        }
                    }
                }
            }
            if ((all || filter == SearchFilter.Songs) && results.songs.isNotEmpty()) {
                item { SectionHeader("Songs", Modifier.padding(top = 16.dp)) }
                val songs = results.songs
                items(songs.take(if (all) 8 else 200), key = { "s${it.id}" }) { t ->
                    SongRow(
                        t, onClick = { vm.play(songs, songs.indexOf(t)) },
                        isCurrent = player.current?.id == t.id, isPlaying = player.isPlaying, onMore = { vm.showTrackMenu(it) },
                    )
                }
            }
        }
    }
}
