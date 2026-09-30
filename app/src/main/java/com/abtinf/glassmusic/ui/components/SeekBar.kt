package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Thin Apple-style scrubber: 5dp track that swells to 10dp while dragging. */
@Composable
fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White.copy(alpha = 0.9f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val h by animateDpAsState(if (dragging) 10.dp else 5.dp, label = "seekH")

    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / widthPx).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragValue = (o.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = { onSeek(dragValue); dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragValue = (dragValue + dx / widthPx).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(inactiveColor)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(activeColor))
        }
    }
}
