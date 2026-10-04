package com.abtinf.glassmusic.ui.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.LyricLine
import com.abtinf.glassmusic.ui.theme.AmType

/** Large centred lyrics: the current line is bright, its neighbours are faded. */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    position: kotlinx.coroutines.flow.StateFlow<Long>,
    showTranslation: Boolean,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text("No lyrics for this song", style = AmType.Title, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            Text("Lyrics are read from the song's tags or a .lrc file next to it.", style = AmType.Caption, color = Color.White.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Allow access to .lrc files", style = AmType.Body, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)).clickable {
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Import lyrics", style = AmType.Body, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.18f)).clickable(onClick = onImport).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        return
    }

    // Plain (unsynced) lyrics carry no timestamps: show them as ordinary text without a moving highlight.
    val synced = lines.first().timeMs >= 0
    val positionMs by position.collectAsState()
    // Only changes when the sung line changes, so the list does not recompose four times a second.
    val current by remember(lines) { derivedStateOf { if (synced) lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0) else -1 } }
    val listState = rememberLazyListState()
    var viewportH by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(current, viewportH, lines) {
        if (viewportH > 0 && current >= 0) listState.animateScrollToItem(current, scrollOffset = -(viewportH * 0.22f).toInt())
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportH = it.height }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = PaddingValues(top = with(density) { (viewportH * 0.22f).toDp() }, bottom = with(density) { (viewportH * 0.7f).toDp() }),
    ) {
        itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, line ->
            val isCurrent = !synced || i == current
            val a by animateFloatAsState(if (isCurrent) 1f else 0.55f, tween(400), label = "lyricAlpha")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = synced) { onSeek(line.timeMs) }
                    .padding(vertical = 7.dp)
                    .alpha(a),
            ) {
                Text(line.text, style = AmType.Lyric.copy(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                if (line.translation != null && showTranslation) {
                    Spacer(Modifier.height(4.dp))
                    Text(line.translation, style = AmType.Body.copy(fontSize = 14.sp), color = Color.White.copy(alpha = 0.7f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
