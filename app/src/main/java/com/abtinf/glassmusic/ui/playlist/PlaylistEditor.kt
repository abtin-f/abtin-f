package com.abtinf.glassmusic.ui.playlist

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.ui.MusicViewModel
import com.abtinf.glassmusic.ui.components.Artwork
import com.abtinf.glassmusic.ui.components.LocalBackdrop
import com.abtinf.glassmusic.ui.components.glass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

private val RowHeight = 56.dp

/** Immersive Playlist Playground editor: undo/redo, drag to reorder, swipe to remove, prompt bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditor(vm: MusicViewModel, onClose: () -> Unit) {
    val state by vm.editor.collectAsState()
    val library by vm.library.collectAsState()
    val e = state ?: return
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val visibleAll = remember(library.tracks, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) library.tracks else library.tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
    }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { RowHeight.toPx() }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                prompt = it
                vm.editorApplyPrompt(it)
            }
        }
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).background(
                Brush.verticalGradient(listOf(Color(0xFF0A1B4D), Color(0xFF0B3B6B), Color(0xFF0D5C5A), Color(0xFF2E6B2E))),
            ),
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp,
                bottom = 160.dp,
            ),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (e.description.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(AmIcons.Sparkle, null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(e.description, style = AmType.Caption.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.75f))
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        e.name, style = AmType.LargeTitle.copy(fontSize = 30.sp, lineHeight = 36.sp), color = Color.White,
                        modifier = Modifier.clickable { renaming = true },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(e.subtitle, style = AmType.Caption, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(16.dp))
                }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().height(40.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.14f)).padding(3.dp),
                ) {
                    listOf(false to "Playlist (${e.tracks.size})", true to "All Songs (${library.tracks.size})").forEach { (all, label) ->
                        Box(
                            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(17.dp))
                                .background(if (showAll == all) Color.White else Color.Transparent)
                                .clickable { showAll = all },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = AmType.Body.copy(fontSize = 14.sp), color = if (showAll == all) Color(0xFF0A1B4D) else Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (showAll) {
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().height(44.dp)
                            .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(AmIcons.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (filter.isEmpty()) Text("Filter songs", style = AmType.Body.copy(fontSize = 15.sp), color = Color.White.copy(alpha = 0.55f))
                            BasicTextField(
                                value = filter, onValueChange = { filter = it }, singleLine = true,
                                textStyle = AmType.Body.copy(fontSize = 15.sp, color = Color.White),
                                cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            "Add all", style = AmType.Body.copy(fontSize = 14.sp), color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { vm.editorAdd(visibleAll) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                items(visibleAll, key = { "all${it.id}" }) { t ->
                    val inList = e.tracks.any { it.id == t.id }
                    Row(
                        Modifier.fillMaxWidth().height(RowHeight)
                            .clickable { if (inList) vm.editorRemove(t) else vm.editorAdd(listOf(t)) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(t, Modifier.size(40.dp), corner = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (inList) Color.White else Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (inList) AmIcons.Check else AmIcons.Plus, if (inList) "In playlist" else "Add", tint = if (inList) Color(0xFF0A1B4D) else Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                if (e.tracks.isEmpty()) {
                    item {
                        Text(
                            "No songs yet. Open “All Songs” to pick from your library.", style = AmType.Body, color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                items(e.tracks, key = { it.id }) { track ->
                    val dragging = draggingId == track.id
                    Box(
                        Modifier
                            .then(if (dragging) Modifier else Modifier.animateItem())
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = if (dragging) 1.02f else 1f; scaleY = if (dragging) 1.02f else 1f }
                            .then(if (dragging) Modifier.shadow(12.dp, RoundedCornerShape(12.dp)) else Modifier),
                    ) {
                        EditorRow(
                            track = track,
                            dragging = dragging,
                            onRemove = { vm.editorRemove(track) },
                            dragModifier = Modifier.pointerInput(track.id) {
                                detectDragGestures(
                                    onDragStart = { draggingId = track.id; dragOffset = 0f; vm.editorBeginDrag() },
                                    onDragEnd = { draggingId = null; dragOffset = 0f },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = vm.editor.value?.tracks ?: return@detectDragGestures
                                        val idx = list.indexOfFirst { it.id == track.id }
                                        if (dragOffset > rowPx / 2 && idx < list.lastIndex) {
                                            vm.editorMove(idx, idx + 1); dragOffset -= rowPx
                                        } else if (dragOffset < -rowPx / 2 && idx > 0) {
                                            vm.editorMove(idx, idx - 1); dragOffset += rowPx
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        }

        // top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircle(AmIcons.Close, "Close", onClick = onClose)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.height(44.dp).glass(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.14f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(AmIcons.Undo, "Undo", e.canUndo) { vm.editorUndo() }
                BarIcon(AmIcons.Redo, "Redo", e.canRedo) { vm.editorRedo() }
            }
            Spacer(Modifier.width(8.dp))
            Box {
                GlassCircle(AmIcons.More, "More") { menuOpen = true }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renaming = true })
                    DropdownMenuItem(text = { Text("Add Songs…") }, onClick = { menuOpen = false; showAll = true })
                    DropdownMenuItem(text = { Text("Regenerate") }, onClick = {
                        menuOpen = false
                        vm.editorApplyPrompt(prompt.ifBlank { e.name + " " + System.nanoTime() })
                    })
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable { if (vm.saveEditor()) onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(AmIcons.Check, "Save", tint = Color(0xFF0A1B4D), modifier = Modifier.size(24.dp)) }
        }

        // prompt bar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .glass(RoundedCornerShape(28.dp), Color.White.copy(alpha = 0.16f), elevation = 8.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AmIcons.Sparkle, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (prompt.isEmpty()) Text("Customize playlist?", style = AmType.Body.copy(fontSize = 16.sp), color = Color.White.copy(alpha = 0.6f))
                BasicTextField(
                    value = prompt, onValueChange = { prompt = it }, singleLine = true,
                    textStyle = AmType.Body.copy(fontSize = 16.sp, color = Color.White),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.editorApplyPrompt(prompt) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { speech.launch(intent) }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(AmIcons.Mic, "Voice", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)) }
        }
    }
    }

    if (renaming) {
        var text by remember { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Playlist name") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.editorRename(text); renaming = false }) { Text("Done") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorRow(track: Track, dragging: Boolean, onRemove: () -> Unit, dragModifier: Modifier) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(if (dragging) Color(0xFF0E3F66) else Color(0xFF0B2F55).copy(alpha = 0f))
            .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFF3B30)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White))
            }
        }
        Spacer(Modifier.width(4.dp))
        Artwork(track, Modifier.size(40.dp), corner = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).then(dragModifier), contentAlignment = Alignment.Center) {
            Icon(AmIcons.Handle, "Reorder", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun GlassCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).glass(CircleShape, Color.White.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 48.dp, height = 44.dp).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(22.dp)) }
}
