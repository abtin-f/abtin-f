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
import com.abtinf.glassmusic.ui.components.glass
import com.abtinf.glassmusic.ui.theme.AmIcons
import com.abtinf.glassmusic.ui.theme.AmType
import androidx.compose.animation.animateContentSize
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
    var adding by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
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

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0A1B4D), Color(0xFF0B3B6B), Color(0xFF0D5C5A), Color(0xFF2E6B2E))),
        ),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 88.dp, bottom = 160.dp),
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
            items(e.tracks, key = { it.id }) { track ->
                val dragging = draggingId == track.id
                val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                    if (v == SwipeToDismissBoxValue.EndToStart) { vm.editorRemove(track); true } else false
                })
                SwipeToDismissBox(
                    state = dismiss,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = !dragging,
                    modifier = Modifier
                        .then(if (dragging) Modifier else Modifier.animateItem())
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f; scaleX = if (dragging) 1.02f else 1f; scaleY = if (dragging) 1.02f else 1f }
                        .then(if (dragging) Modifier.shadow(12.dp, RoundedCornerShape(12.dp)) else Modifier),
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().background(Color(0xFFFF3B30)).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(AmIcons.Trash, "Remove", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    },
                ) {
                    EditorRow(
                        track = track,
                        dragging = dragging,
                        onAdd = { vm.showPlaylistPicker(listOf(track)) },
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
                    DropdownMenuItem(text = { Text("Add Songs…") }, onClick = { menuOpen = false; adding = true })
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

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            val candidates = library.tracks.filter { t -> e.tracks.none { it.id == t.id } }
            Text("Add Songs", style = AmType.Section, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
                items(candidates, key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().height(RowHeight).clickable { vm.editorAdd(listOf(t)) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(t, Modifier.size(40.dp), corner = 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = AmType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = AmType.Caption, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(AmIcons.Plus, "Add", modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorRow(track: Track, dragging: Boolean, onAdd: () -> Unit, dragModifier: Modifier) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(if (dragging) Color(0xFF0E3F66) else Color(0xFF0B2F55).copy(alpha = 0f))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track, Modifier.size(40.dp), corner = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = AmType.Body.copy(fontSize = 15.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = AmType.Caption.copy(fontSize = 12.sp), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
            Icon(AmIcons.Plus, "Add to playlist", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
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
