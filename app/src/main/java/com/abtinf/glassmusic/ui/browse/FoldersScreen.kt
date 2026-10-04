package com.abtinf.glassmusic.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.abtinf.glassmusic.ui.theme.LocalAm

/** Every folder on the device that contains music; tap one to see and play the songs inside. */
@Composable
fun FoldersScreen(vm: MusicViewModel, bottomPad: Dp, onOpenFolder: (Long) -> Unit) {
    val am = LocalAm.current
    val library by vm.library.collectAsState()

    LazyColumn(Modifier.fillMaxSize().background(am.background), contentPadding = PaddingValues(bottom = bottomPad)) {
        item {
            Text(
                "Folders", style = AmType.LargeTitle, color = am.text,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, top = 64.dp, bottom = 12.dp),
            )
        }
        items(library.folders, key = { it.path }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenFolder(f.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(am.surface), contentAlignment = Alignment.Center) {
                    Artwork(f.tracks.firstOrNull(), Modifier.fillMaxSize(), corner = 8.dp)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        Icon(AmIcons.Folder, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = AmType.Body, color = am.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${f.tracks.size} ${if (f.tracks.size == 1) "song" else "songs"}  •  ${f.path.trimEnd('/')}",
                        style = AmType.Caption, color = am.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(AmIcons.ChevronRight, null, tint = am.secondary, modifier = Modifier.size(18.dp))
            }
        }
        if (library.folders.isEmpty()) {
            item { Text("No music folders found.", style = AmType.Caption, color = am.secondary, modifier = Modifier.padding(16.dp)) }
        }
    }
}
