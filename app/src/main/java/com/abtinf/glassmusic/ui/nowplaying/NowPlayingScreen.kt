package com.abtinf.glassmusic.ui.nowplaying

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.formatTime
import com.abtinf.glassmusic.playback.PlayerState
import com.abtinf.glassmusic.playback.RepeatMode
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.DynamicAlbumBackground
import com.abtinf.glassmusic.ui.components.Equalizer
import com.abtinf.glassmusic.ui.components.SeekBar
import com.abtinf.glassmusic.ui.components.glass
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-screen player. Two layouts share the same transport: artwork mode (big cover) and lyrics mode
 * (compact header + large lyrics). The background is a blurred, palette-tinted copy of the cover.
 */
@Composable
fun NowPlayingScreen(
    vm: MusicViewModel,
    dragModifier: Modifier,
    onCollapse: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val ps by vm.playerState.collectAsState()
    val lyrics by vm.currentLyrics.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val track = ps.current

    var lyricsMode by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var translate by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = vm.playerState.value.current
        if (uri != null && t != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (!text.isNullOrBlank()) vm.importLyrics(t, text)
        }
    }

    Box(modifier.fillMaxSize()) {
        DynamicAlbumBackground(track)

        if (track != null) BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val artSize = minOf(maxWidth - 48.dp, maxHeight * 0.42f)
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Box(dragModifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                }

                val more: @Composable () -> Unit = {
                    Box {
                        GlassButton(AmIcons.More, "More", 36.dp, 20.dp) { menuOpen = true }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Add to Playlist…") }, onClick = { menuOpen = false; vm.showPlaylistPicker(listOf(track)) })
                            DropdownMenuItem(text = { Text("Import lyrics (.lrc)") }, onClick = { menuOpen = false; importer.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text("Go to Album") }, onClick = { menuOpen = false; onCollapse(); onOpenAlbum(track.albumId) })
                            DropdownMenuItem(text = { Text("Go to Artist") }, onClick = { menuOpen = false; onCollapse(); onOpenArtist(track.artistId) })
                        }
                    }
                }
                val fav = track.id in favorites
                val star: @Composable () -> Unit = {
                    GlassButton(if (fav) AmIcons.StarFilled else AmIcons.Star, "Favorite", 36.dp, 20.dp) { vm.toggleFavorite(track) }
                }

                AnimatedContent(
                    targetState = lyricsMode,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) using SizeTransform(clip = false) },
                    label = "npMode",
                ) { lm ->
                    if (lm) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(track, Modifier.size(56.dp), corner = 8.dp, elevation = 6.dp)
                            Spacer(Modifier.width(12.dp))
                            TitleBlock(track, Modifier.weight(1f), titleSize = 17.sp, artistSize = 15.sp)
                            star(); Spacer(Modifier.width(8.dp)); more()
                        }
                    } else {
                        Column {
                            val scale by animateFloatAsState(
                                if (ps.isPlaying) 1f else 0.86f,
                                spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow), label = "artScale",
                            )
                            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                                Crossfade(track, animationSpec = tween(500), label = "npArt") { t ->
                                    Artwork(
                                        t, Modifier.size(artSize).graphicsLayer { scaleX = scale; scaleY = scale },
                                        corner = 14.dp, elevation = 24.dp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TitleBlock(track, Modifier.weight(1f), titleSize = 20.sp, artistSize = 18.sp)
                                star(); Spacer(Modifier.width(8.dp)); more()
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (lyricsMode) {
                        LyricsView(
                            lines = lyrics, positionMs = ps.positionMs, showTranslation = translate,
                            onSeek = { vm.seekMs(it) }, onImport = { importer.launch(arrayOf("*/*")) },
                        )
                        if (lyrics.isNotEmpty()) Row(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (lyrics.any { it.translation != null }) {
                                GlassButton(AmIcons.Translate, "Translation", 44.dp, 22.dp, active = translate) { translate = !translate }
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.size(width = 44.dp, height = 64.dp)
                                    .glass(RoundedCornerShape(22.dp), Color.White.copy(alpha = if (ps.singMode) 0.34f else 0.16f))
                                    .clickable { vm.toggleSing() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(AmIcons.Mic, "Sing", tint = Color.White, modifier = Modifier.size(22.dp)) }
                        }
                    }
                }

                // progress
                val dur = ps.durationMs.coerceAtLeast(1L)
                SeekBar(progress = ps.positionMs / dur.toFloat(), onSeek = { vm.seekFraction(it) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(ps.positionMs), style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val label = when {
                            ps.isMixing -> "Mixing"
                            ps.singMode && lyricsMode -> "Sing"
                            else -> ""
                        }
                        Crossfade(label, label = "npLabel") { Text(it, style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f)) }
                    }
                    Text("-" + formatTime(dur - ps.positionMs), style = AmType.Tiny.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f))
                }

                // transport
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TransportButton(AmIcons.Rewind, "Previous", 44.dp) { vm.previous() }
                    TransportButton(if (ps.isPlaying) AmIcons.Pause else AmIcons.Play, if (ps.isPlaying) "Pause" else "Play", 56.dp, crossfade = true) { vm.togglePlay() }
                    TransportButton(AmIcons.Forward, "Next", 44.dp) { vm.next() }
                }

                VolumeRow(ctx)

                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(AmIcons.Lyrics, "Lyrics", 48.dp, 24.dp, active = lyricsMode) { lyricsMode = !lyricsMode }
                    GlassButton(AmIcons.AirPlay, "Output", 48.dp, 24.dp) { openOutputSwitcher(ctx) }
                    GlassButton(AmIcons.Queue, "Queue", 48.dp, 24.dp, active = showQueue) { showQueue = !showQueue }
                }
            }
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            QueuePanel(
                ps = ps,
                onClose = { showQueue = false },
                onSkip = { vm.skipTo(it) },
                onRemove = { vm.removeFromQueue(it) },
                onShuffle = { vm.toggleShuffle() },
                onRepeat = { vm.cycleRepeat() },
                onAutoMix = { vm.setAutoMix(!ps.autoMix) },
            )
        }
    }
}

@Composable
private fun TitleBlock(track: Track, modifier: Modifier, titleSize: androidx.compose.ui.unit.TextUnit, artistSize: androidx.compose.ui.unit.TextUnit) {
    Column(modifier.padding(end = 12.dp)) {
        Crossfade(track.id, label = "npTitle") {
            Column {
                Text(track.title, style = AmType.Title.copy(fontSize = titleSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = AmType.Title.copy(fontSize = artistSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GlassButton(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glass(CircleShape, Color.White.copy(alpha = if (active) 0.32f else 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
private fun TransportButton(icon: ImageVector, desc: String, iconSize: androidx.compose.ui.unit.Dp, crossfade: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (crossfade) {
            Crossfade(icon, animationSpec = tween(180), label = "transport") { Icon(it, desc, tint = Color.White, modifier = Modifier.size(iconSize)) }
        } else {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun VolumeRow(ctx: Context) {
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(AmIcons.VolumeLow, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        SeekBar(
            progress = volume,
            onSeek = { f ->
                volume = f
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0)
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            activeColor = Color.White.copy(alpha = 0.7f), inactiveColor = Color.White.copy(alpha = 0.2f),
        )
        Icon(AmIcons.VolumeHigh, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
    }
}

private fun openOutputSwitcher(ctx: Context) {
    val intents = listOf(
        Intent("com.android.settings.panel.action.MEDIA_OUTPUT"),
        Intent(Settings.Panel.ACTION_VOLUME),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )
    for (i in intents) {
        if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}

@Composable
private fun QueuePanel(
    ps: PlayerState,
    onClose: () -> Unit,
    onSkip: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoMix: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ps.index.coerceAtLeast(0))
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color(0xF0121214))
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playing Next", style = AmType.Section, color = Color.White, modifier = Modifier.weight(1f))
            GlassButton(AmIcons.ChevronDown, "Close", 40.dp, 22.dp, onClick = onClose)
        }
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(AmIcons.Shuffle, "Shuffle", 44.dp, 22.dp, active = ps.shuffle, onClick = onShuffle)
            GlassButton(
                if (ps.repeat == RepeatMode.ONE) AmIcons.RepeatOne else AmIcons.Repeat, "Repeat", 44.dp, 22.dp,
                active = ps.repeat != RepeatMode.OFF, onClick = onRepeat,
            )
            Row(
                Modifier.height(44.dp)
                    .glass(RoundedCornerShape(22.dp), if (ps.autoMix) AmAccent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onAutoMix).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AmIcons.Sparkle, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("AutoMix", style = AmType.Body, color = Color.White)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(ps.queue, key = { i, t -> "${t.id}#$i" }) { i, t ->
                val current = i == ps.index
                Row(
                    Modifier.fillMaxWidth().height(64.dp).clickable { onSkip(i) }.padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Artwork(t, Modifier.size(44.dp), corner = 6.dp)
                        if (current) Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            Equalizer(ps.isPlaying, Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = AmType.Body.copy(fontSize = 16.sp), color = if (current) Color.White else Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = AmType.Caption, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!current) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { onRemove(i) }, contentAlignment = Alignment.Center) {
                            Icon(AmIcons.Close, "Remove", tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
