package com.abtinf.glassmusic.ui.nowplaying

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.formatTime
import com.abtinf.glassmusic.playback.PlayerState
import com.abtinf.glassmusic.playback.RepeatMode
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.DynamicAlbumBackground
import com.abtinf.glassmusic.ui.components.LocalBackdrop
import com.abtinf.glassmusic.ui.components.SeekBar
import com.abtinf.glassmusic.ui.components.glass
import com.abtinf.glassmusic.ui.theme.AmAccent
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class NpMode { Art, Lyrics, Queue }

private val Muted = Color.White.copy(alpha = 0.62f)

/**
 * Full-screen player: big artwork, or (lyrics / queue) a compact header the artwork flies into via a shared
 * element transition. Context menu, output picker, details sheet and sleep timer are overlays.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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
    val ps by vm.playback.collectAsState()
    val lyrics by vm.currentLyrics.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val track = ps.current

    var mode by rememberSaveable { mutableStateOf(NpMode.Art) }
    var menuOpen by remember { mutableStateOf(false) }
    var outputOpen by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }

    // Back closes whichever sheet is open before it collapses the player.
    BackHandler(enabled = menuOpen || outputOpen || detailsOpen) { menuOpen = false; outputOpen = false; detailsOpen = false }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = vm.playerState.value.current
        if (uri != null && t != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (!text.isNullOrBlank()) vm.importLyrics(t, text)
        }
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            DynamicAlbumBackground(track, Modifier.fillMaxSize().layerBackdrop(backdrop))

            if (track != null) {
                val fav = track.id in favorites
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Box(dragModifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SharedTransitionLayout(Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = mode,
                                transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(220)) },
                                label = "npMode",
                            ) { m ->
                                val shared = Shared(this@SharedTransitionLayout, this@AnimatedContent)
                                if (m == NpMode.Art) {
                                    ArtLayout(ps, track, fav, shared, { vm.toggleFavorite(track) }, { menuOpen = true })
                                } else {
                                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                                        CompactHeader(track, fav, shared, { vm.toggleFavorite(track) }, { menuOpen = true })
                                        if (m == NpMode.Lyrics) {
                                            Spacer(Modifier.height(12.dp))
                                            LyricsView(
                                                lines = lyrics, position = vm.positionMs, showTranslation = true,
                                                onSeek = { vm.seekMs(it) }, onImport = { importer.launch(arrayOf("*/*")) },
                                            )
                                        } else {
                                            QueueBody(ps, vm)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Footer(vm, ps, track, mode, onMode = { target -> mode = if (mode == target) NpMode.Art else target }, onOutput = { outputOpen = true })
                }

                PlayerMenu(
                    visible = menuOpen, track = track, fav = fav, ps = ps,
                    onDismiss = { menuOpen = false },
                    onFavorite = { vm.toggleFavorite(track); menuOpen = false },
                    onShare = { menuOpen = false; shareTrack(ctx, track) },
                    onAddToPlaylist = { menuOpen = false; vm.showPlaylistPicker(listOf(track)) },
                    onAlbum = { menuOpen = false; onCollapse(); onOpenAlbum(track.albumId) },
                    onArtist = { menuOpen = false; onCollapse(); onOpenArtist(track.artistId) },
                    onDetails = { menuOpen = false; detailsOpen = true },
                    onSleep = { menuOpen = false; sleepOpen = true },
                )
                OutputSheet(outputOpen, track, vm, onDone = { outputOpen = false }, onConnect = { openOutputSwitcher(ctx) })
                DetailsSheet(detailsOpen, track, onDone = { detailsOpen = false })
            }
        }
    }

    if (sleepOpen) SleepTimerDialog(vm, onDismiss = { sleepOpen = false })
}

@OptIn(ExperimentalSharedTransitionApi::class)
private class Shared(val scope: SharedTransitionScope, val visibility: AnimatedVisibilityScope) {
    @Composable
    fun element(modifier: Modifier, key: String): Modifier = with(scope) {
        modifier.sharedElement(
            rememberSharedContentState(key), visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 320f) },
        )
    }

    @Composable
    fun bounds(modifier: Modifier, key: String): Modifier = with(scope) {
        modifier.sharedBounds(
            rememberSharedContentState(key), visibility,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.82f, stiffness = 320f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@Composable
private fun ArtLayout(ps: PlayerState, track: Track, fav: Boolean, shared: Shared, onFav: () -> Unit, onMore: () -> Unit) {
    val scale by animateFloatAsState(
        if (ps.isPlaying) 1f else 0.86f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow), label = "artScale",
    )
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        val artSize = minOf(maxWidth, maxHeight - 96.dp).coerceAtLeast(120.dp)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(artSize), contentAlignment = Alignment.Center) {
                Crossfade(track, animationSpec = tween(450), label = "npArt") { t ->
                    Artwork(
                        t, shared.element(Modifier.size(artSize).graphicsLayer { scaleX = scale; scaleY = scale }, "art"),
                        corner = 12.dp, elevation = 20.dp,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(track, shared, Modifier.weight(1f), 22.sp, 20.sp)
                PlainIcon(if (fav) AmIcons.StarFilled else AmIcons.Star, "Favorite", shared.element(Modifier, "star"), onFav)
                PlainIcon(AmIcons.More, "More", shared.element(Modifier, "more"), onMore)
            }
        }
    }
}

@Composable
private fun CompactHeader(track: Track, fav: Boolean, shared: Shared, onFav: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, shared.element(Modifier.size(56.dp), "art"), corner = 8.dp, elevation = 6.dp)
        Spacer(Modifier.width(12.dp))
        TitleBlock(track, shared, Modifier.weight(1f), 18.sp, 15.sp)
        PlainIcon(if (fav) AmIcons.StarFilled else AmIcons.Star, "Favorite", shared.element(Modifier, "star"), onFav)
        PlainIcon(AmIcons.More, "More", shared.element(Modifier, "more"), onMore)
    }
}

@Composable
private fun TitleBlock(track: Track, shared: Shared, modifier: Modifier, titleSize: androidx.compose.ui.unit.TextUnit, artistSize: androidx.compose.ui.unit.TextUnit) {
    Column(shared.bounds(modifier.padding(end = 8.dp), "title")) {
        Crossfade(track.id, label = "npTitle") {
            Column {
                Text(track.title, style = AmType.Title.copy(fontSize = titleSize, fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = AmType.Title.copy(fontSize = artistSize, fontWeight = FontWeight.Normal), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlainIcon(icon: ImageVector, desc: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Footer(vm: MusicViewModel, ps: PlayerState, track: Track, mode: NpMode, onMode: (NpMode) -> Unit, onOutput: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        ProgressSection(vm, ps, track)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(AmIcons.Rewind, "Previous", 44.dp) { vm.previous() }
            TransportButton(if (ps.isPlaying) AmIcons.Pause else AmIcons.Play, if (ps.isPlaying) "Pause" else "Play", 56.dp, crossfade = true) { vm.togglePlay() }
            TransportButton(AmIcons.Forward, "Next", 44.dp) { vm.next() }
        }

        VolumeRow(ctx)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton(AmIcons.Lyrics, "Lyrics", mode == NpMode.Lyrics) { onMode(NpMode.Lyrics) }
            ModeButton(AmIcons.Device, "Output", false, onOutput)
            ModeButton(AmIcons.Queue, "Queue", mode == NpMode.Queue) { onMode(NpMode.Queue) }
        }
    }
}

/** Seek bar + elapsed / remaining time. The only part of the player that reads the 4 Hz position. */
@Composable
private fun ProgressSection(vm: MusicViewModel, ps: PlayerState, track: Track) {
    val position by vm.positionMs.collectAsState()
    val dur = ps.durationMs.coerceAtLeast(1L)
    SeekBar(progress = position / dur.toFloat(), onSeek = { vm.seekFraction(it) })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(position), style = AmType.Tiny.copy(fontSize = 12.sp), color = Muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = if (ps.isMixing) "Mixing" else ""
            Crossfade(label, label = "npLabel") { l ->
                if (l.isNotEmpty()) Text(l, style = AmType.Tiny.copy(fontSize = 12.sp), color = Muted)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AmIcons.Waveform, null, tint = Muted, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOfNotNull(track.format, track.bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" }).joinToString("  ·  "),
                        style = AmType.Tiny.copy(fontSize = 10.sp), color = Muted,
                    )
                }
            }
        }
        Text("-" + formatTime(dur - position), style = AmType.Tiny.copy(fontSize = 12.sp), color = Muted)
    }
}

@Composable
private fun ModeButton(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val bg by animateFloatAsState(if (active) 0.22f else 0f, tween(220), label = "modeBg")
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = bg)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun TransportButton(icon: ImageVector, desc: String, iconSize: Dp, crossfade: Boolean = false, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.5f, 500f), label = "press")
    Box(
        Modifier.size(72.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
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
            delay(800)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(AmIcons.VolumeLow, null, tint = Muted, modifier = Modifier.size(16.dp))
        SeekBar(
            progress = volume,
            onSeek = { f -> volume = f; audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0) },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            activeColor = Color.White.copy(alpha = 0.7f), inactiveColor = Color.White.copy(alpha = 0.2f), knob = false,
        )
        Icon(AmIcons.VolumeHigh, null, tint = Muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun QueueBody(ps: PlayerState, vm: MusicViewModel) {
    val upcoming = androidx.compose.runtime.remember(ps.queue, ps.index) { ps.queue.drop(ps.index + 1) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            QueuePill(AmIcons.Mix, ps.autoMix) { vm.setAutoMix(!ps.autoMix) }
            QueuePill(AmIcons.Shuffle, ps.shuffle) { vm.toggleShuffle() }
            QueuePill(if (ps.repeat == RepeatMode.ONE) AmIcons.RepeatOne else AmIcons.Repeat, ps.repeat != RepeatMode.OFF) { vm.cycleRepeat() }
            QueuePill(AmIcons.Infinity, ps.endless) { vm.toggleEndless() }
        }
        Text("Continue Playing", style = AmType.Section.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        if (upcoming.isEmpty()) {
            Text("Queue is empty", style = AmType.Body.copy(fontWeight = FontWeight.Normal), color = Muted)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(upcoming, key = { i, t -> "${t.id}#$i" }) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).clickable { vm.skipTo(ps.index + 1 + i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t, Modifier.size(44.dp), corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AmType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AmType.Caption, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { vm.removeFromQueue(ps.index + 1 + i) }, contentAlignment = Alignment.Center) {
                            Icon(AmIcons.Close, "Remove", tint = Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueuePill(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(width = 76.dp, height = 40.dp).glass(shape, Color.White.copy(alpha = if (active) 0.30f else 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

// ---- overlays -------------------------------------------------------------------------------

@Composable
private fun PlayerMenu(
    visible: Boolean, track: Track, fav: Boolean, ps: PlayerState,
    onDismiss: () -> Unit, onFavorite: () -> Unit, onShare: () -> Unit, onAddToPlaylist: () -> Unit,
    onAlbum: () -> Unit, onArtist: () -> Unit, onDetails: () -> Unit, onSleep: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(120)), exit = fadeOut(tween(120))) {
        Box(Modifier.fillMaxSize().clickable(MutableInteractionSource(), null, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.statusBarsPadding().padding(top = 36.dp).fillMaxWidth(),
        enter = fadeIn(tween(140)) + scaleIn(spring(0.68f, 420f), initialScale = 0.86f, transformOrigin = TransformOrigin(0.85f, 1f)) +
            slideInVertically(spring(0.75f, 400f)) { -it / 10 },
        exit = fadeOut(tween(110)) + scaleOut(tween(130), targetScale = 0.92f, transformOrigin = TransformOrigin(0.85f, 1f)),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth(0.82f).glass(RoundedCornerShape(28.dp), Color(0xFF8A7478).copy(alpha = 0.42f), elevation = 16.dp)
                    .padding(vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(72.dp)) {
                    MenuTop(if (fav) AmIcons.StarFilled else AmIcons.Star, "Favorite", Modifier.weight(1f), onFavorite)
                    MenuTop(AmIcons.Share, "Share", Modifier.weight(1f), onShare)
                }
                MenuDivider()
                MenuItem(AmIcons.PlaylistAdd, "Add to playlist", null, onAddToPlaylist)
                MenuDivider()
                MenuItem(AmIcons.AlbumTile, "Go to Album", track.album, onAlbum)
                MenuItem(AmIcons.Mic, "Go to Artist", track.artist, onArtist)
                MenuItem(AmIcons.Info, "Details", null, onDetails)
                MenuDivider()
                val remaining = if (ps.sleepAtTrackEnd) "End of track" else if (ps.sleepEndsAt != 0L) "On" else null
                MenuItem(AmIcons.Moon, "Sleep timer", remaining, onSleep)
            }
        }
    }
}

@Composable
private fun MenuTop(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxSize().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = AmType.Caption.copy(fontSize = 13.sp), color = Color.White)
    }
}

@Composable
private fun MenuItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = if (subtitle != null) 9.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = AmType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal), color = Color.White)
            if (subtitle != null) Text(subtitle, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuDivider() = HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Color.White.copy(alpha = 0.18f))

@Composable
private fun OutputSheet(visible: Boolean, track: Track, vm: MusicViewModel, onDone: () -> Unit, onConnect: () -> Unit) {
    val ctx = LocalContext.current
    AnimatedVisibility(visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable(MutableInteractionSource(), null, onClick = onDone))
    }
    AnimatedVisibility(
        visible, modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(spring(0.82f, 380f)) { it / 2 } + fadeIn(tween(180)) + scaleIn(spring(0.85f, 400f), initialScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)),
    ) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 84.dp), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp), Color(0xFF1A1214).copy(alpha = 0.68f), elevation = 20.dp)
                    .padding(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Artwork(track, Modifier.size(64.dp), corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF4B6C2)))
                        Spacer(Modifier.height(6.dp))
                        Text(track.title, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = AmType.Caption, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                .clickable(onClick = onConnect).padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(AmIcons.Plus, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Connect device", style = AmType.Body.copy(fontSize = 13.sp), color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                val devices by vm.outputs.collectAsState()
                val selectedId by vm.selectedOutput.collectAsState()
                devices.forEach { d ->
                    val on = d.id == selectedId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.selectOutput(d.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(d.name, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White.copy(alpha = if (on) 1f else 0.7f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (on) Icon(AmIcons.Check, "Selected", tint = Color(0xFFF4B6C2), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                DottedVolume(ctx)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Text(
                        "Done", style = AmType.Body.copy(fontSize = 14.sp), color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFF4B6C2).copy(alpha = 0.55f))
                            .clickable(onClick = onDone).padding(horizontal = 22.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** Volume bar drawn as a pill of dots: pink (filled) then violet (empty) with a white thumb line. */
@Composable
private fun DottedVolume(ctx: Context) {
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(1) }
    fun set(f: Float) { volume = f.coerceIn(0f, 1f); audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * max).roundToInt(), 0) }
    // Follow the hardware volume keys while the sheet is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            if (!dragging) volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }
    }
    Canvas(
        Modifier.fillMaxWidth().height(28.dp).onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { set(it.x / widthPx) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ -> change.consume(); set(change.position.x / widthPx) }
            },
    ) {
        val h = size.height
        val x = size.width * volume
        drawRoundRect(Color.White.copy(alpha = 0.08f), Offset.Zero, size, CornerRadius(h / 2))
        drawRoundRect(Color(0xFFF4B6C2).copy(alpha = 0.85f), Offset.Zero, Size(x.coerceAtLeast(h), h), CornerRadius(h / 2))
        val step = 7.dp.toPx()
        var d = step / 2
        while (d < size.width) {
            drawCircle(if (d < x) Color(0xFF2A1A1E).copy(alpha = 0.55f) else Color(0xFF7C7CF0).copy(alpha = 0.9f), 1.6.dp.toPx(), Offset(d, h / 2))
            d += step
        }
        drawRoundRect(Color.White, Offset(x - 1.5.dp.toPx(), -2.dp.toPx()), Size(3.dp.toPx(), h + 4.dp.toPx()), CornerRadius(1.5.dp.toPx()))
    }
}

@Composable
private fun DetailsSheet(visible: Boolean, track: Track, onDone: () -> Unit) {
    AnimatedVisibility(
        visible, modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(spring(0.9f, 300f)) { it } + fadeIn(tween(120)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp)) {
            Column(
                Modifier.fillMaxSize().glass(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), Color(0xFF151012).copy(alpha = 0.72f))
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Details", style = AmType.Title.copy(fontSize = 17.sp), color = Color.White, modifier = Modifier.weight(1f))
                    Text("Done", style = AmType.Title.copy(fontSize = 17.sp), color = AmAccent, modifier = Modifier.clickable(onClick = onDone))
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(track, Modifier.size(64.dp), corner = 8.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(track.title, style = AmType.Title.copy(fontSize = 20.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, style = AmType.Body.copy(fontWeight = FontWeight.Normal), color = Muted)
                            }
                        }
                    }
                    val rows = listOf(
                        "Title" to track.title, "Artist" to track.artist, "Album" to track.album,
                        "Album Artist" to track.albumArtist.ifBlank { track.artist },
                        "Year" to (if (track.year > 0) track.year.toString() else "—"),
                        "Track #" to (track.trackNo.takeIf { it > 0 } ?: 1).toString(), "Disc #" to track.discNo.toString(),
                        "Format" to track.format,
                        "Bitrate" to (if (track.bitrateKbps > 0) "${track.bitrateKbps} kbps" else "—"),
                        "Duration" to formatTime(track.durationMs),
                    )
                    itemsIndexed(rows, key = { _, r -> r.first }) { i, (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                            Text(k, style = AmType.Caption.copy(fontSize = 14.sp), color = Muted)
                            Text(v, style = AmType.Body.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal), color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                            if (i < rows.lastIndex) HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(vm: MusicViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                    Text("$m minutes", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(m); onDismiss() }.padding(vertical = 12.dp))
                }
                Text("End of track", Modifier.fillMaxWidth().clickable { vm.sleepAtEndOfTrack(); onDismiss() }.padding(vertical = 12.dp))
                Text("Off", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(0); onDismiss() }.padding(vertical = 12.dp), color = AmAccent)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun shareTrack(ctx: Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND)
    if (track.uri != null) {
        send.setType("audio/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else {
        send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${track.title} — ${track.artist}")
    }
    runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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
