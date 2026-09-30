package com.abtinf.glassmusic.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Thin scrubber with a small knob; the track swells while dragging. */
@Composable
fun SeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
    knob: Boolean = true,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val trackH by animateDpAsState(if (dragging) 8.dp else 4.dp, label = "seekH")
    val knobR by animateDpAsState(if (dragging) 7.dp else 3.5.dp, label = "seekKnob")

    Canvas(
        modifier
            .fillMaxWidth()
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
    ) {
        val h = trackH.toPx()
        val cy = size.height / 2f
        val r = CornerRadius(h / 2f)
        drawRoundRect(inactiveColor, Offset(0f, cy - h / 2f), Size(size.width, h), r)
        drawRoundRect(activeColor, Offset(0f, cy - h / 2f), Size((size.width * shown).coerceAtLeast(h), h), r)
        if (knob) drawCircle(activeColor, knobR.toPx(), Offset((size.width * shown).coerceIn(knobR.toPx(), size.width - knobR.toPx()), cy))
    }
}
