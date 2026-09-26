package com.abtin.iphonekeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class ShiftState { OFF, ON, LOCKED }

@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val listener: Listener) : View(context) {

    interface Listener {
        fun onFeedback(type: KeyType)
        fun onChar(key: Key, fromModeSlide: Boolean)
        fun onAlternate(text: String)
        fun onShift()
        fun onDelete(repeat: Int)
        fun onSpace()
        fun onReturn()
        fun onModeKey(type: KeyType)
        fun onGlobe()
        fun onGlobeLongPress()
        fun onEmoji()
        fun onZwnj()
        fun onCursor(dx: Int, dy: Int)
        fun onSuggestion(index: Int)
    }

    // ---- public state ---------------------------------------------------------------------

    var theme: KeyboardTheme = KeyboardTheme.LIGHT
        set(v) { field = v; invalidate() }

    var layout: KeyboardLayout? = null
        set(v) { field = v; buildGeometry(); invalidate() }

    var shiftState = ShiftState.OFF
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Text on the return key, or null to draw the ↵ glyph. */
    var returnLabel: String? = null
        set(v) { field = v; invalidate() }

    /** Blue return key (go / search / send / done), like iOS. */
    var returnIsAction = false
        set(v) { field = v; invalidate() }

    var showPreview = true
    var heightScale = 1f
        set(v) { if (field != v) { field = v; requestLayout() } }

    /** Three predictive-bar slots; empty strings are blank slots. */
    var suggestions: List<String> = listOf("", "", "")
        set(v) { field = v; invalidate() }

    /** Language name shown on the space bar right after switching. */
    var spaceLabelOverride: String? = null
        set(v) { field = v; invalidate() }

    // ---- geometry -------------------------------------------------------------------------

    private class KeyRect(val key: Key, val cell: RectF, val rect: RectF, val row: Int)

    private val keys = ArrayList<KeyRect>()
    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, dm)

    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val barH get() = dp(if (landscape) 38f else 46f)
    private val topPad get() = dp(if (landscape) 4f else 8f)
    private val keyH get() = dp(if (landscape) 34f else 43f) * heightScale
    private val vGap get() = dp(if (landscape) 6f else 11f) * heightScale
    private val bottomPad get() = dp(if (landscape) 3f else 5f)
    private val hGap get() = dp(if (landscape) 5f else 6f)
    private val sidePad get() = dp(3f)
    private val radius get() = dp(5.5f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout?.rows?.size ?: 4
        val h = barH + topPad + rows * keyH + (rows - 1) * vGap + bottomPad
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildGeometry()
    }

    private fun buildGeometry() {
        keys.clear()
        val l = layout ?: return
        if (width == 0) return
        val avail = width - 2 * sidePad
        l.rows.forEachIndexed { r, row ->
            val units = row.keys.sumOf { it.width.toDouble() }.toFloat()
            val unitW = avail / units
            val top = barH + topPad + r * (keyH + vGap)
            var x = sidePad
            row.keys.forEach { k ->
                val w = k.width * unitW
                val cellTop = if (r == 0) barH else top - vGap / 2
                val cellBottom = if (r == l.rows.lastIndex) height.toFloat() else top + keyH + vGap / 2
                val cell = RectF(x, cellTop, x + w, cellBottom)
                val rect = RectF(x + hGap / 2, top, x + w - hGap / 2, top + keyH)
                if (k.type != KeyType.SPACER) keys.add(KeyRect(k, cell, rect, r))
                x += w
            }
            // Stretch the outermost keys' touch area to the screen edges (covers spacers).
            val inRow = keys.filter { it.row == r }
            inRow.firstOrNull()?.cell?.left = 0f
            inRow.lastOrNull()?.cell?.right = width.toFloat()
        }
    }

    private fun keyAt(x: Float, y: Float): KeyRect? {
        if (y < barH) return null
        keys.firstOrNull { it.cell.contains(x, y) }?.let { return it }
        // In gaps left by spacers: pick the nearest key in the same row band.
        val band = keys.filter { y >= it.cell.top && y < it.cell.bottom }
        return band.minByOrNull { min(abs(x - it.cell.left), abs(x - it.cell.right)) }
    }

    // ---- paints ---------------------------------------------------------------------------

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val balloonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val tmp = RectF()

    // ---- touch state ----------------------------------------------------------------------

    private enum class Mode { NORMAL, LONGPRESS, TRACKPAD, BAR, DONE }

    private inner class Pointer(val id: Int) {
        var key: KeyRect? = null
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var lastY = 0f
        var mode = Mode.NORMAL
        var slot = -1
        var altIndex = 0
        var fromModeSlide = false
        var accX = 0f
        var accY = 0f
        var deleteCount = 0
        var pending: Runnable? = null
    }

    private val pointers = LinkedHashMap<Int, Pointer>()
    private val handler = Handler(Looper.getMainLooper())

    private fun schedule(p: Pointer, delay: Long, r: () -> Unit) {
        cancel(p)
        val run = Runnable { p.pending = null; r() }
        p.pending = run
        handler.postDelayed(run, delay)
    }

    private fun cancel(p: Pointer) {
        p.pending?.let { handler.removeCallbacks(it) }
        p.pending = null
    }

    val trackpadActive get() = pointers.values.any { it.mode == Mode.TRACKPAD }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    fun cancelAll() {
        pointers.values.forEach { cancel(it) }
        pointers.clear()
        invalidate()
    }

    private fun down(id: Int, x: Float, y: Float) {
        val p = Pointer(id).apply { downX = x; downY = y; lastX = x; lastY = y }
        pointers[id] = p
        if (y < barH) {
            p.mode = Mode.BAR
            p.slot = slotAt(x)
            invalidate()
            return
        }
        val kr = keyAt(x, y) ?: run { p.mode = Mode.DONE; return }
        // Rolling typing: a new finger commits the letter still held by another finger.
        pointers.values.filter { it !== p && it.mode == Mode.NORMAL && it.key?.key?.type == KeyType.CHAR }
            .forEach { other ->
                cancel(other)
                other.key?.let { listener.onChar(it.key, other.fromModeSlide) }
                other.mode = Mode.DONE
            }
        p.key = kr
        listener.onFeedback(kr.key.type)
        when (kr.key.type) {
            KeyType.SHIFT -> listener.onShift()
            KeyType.DELETE -> {
                listener.onDelete(0)
                schedule(p, 450) { repeatDelete(p) }
            }
            KeyType.MODE_NUM, KeyType.MODE_SYM, KeyType.MODE_ALPHA -> {
                listener.onModeKey(kr.key.type)
                // The layout changed under the finger: iOS lets you slide onto a symbol and
                // release to type it, then jumps back to letters.
                p.key = keyAt(x, y)
                p.fromModeSlide = kr.key.type == KeyType.MODE_NUM || kr.key.type == KeyType.MODE_SYM
            }
            KeyType.CHAR -> scheduleLongPress(p)
            KeyType.SPACE -> schedule(p, 420) { enterTrackpad(p) }
            KeyType.GLOBE -> schedule(p, 500) {
                p.mode = Mode.DONE
                listener.onGlobeLongPress()
                invalidate()
            }
            else -> {}
        }
        invalidate()
    }

    private fun scheduleLongPress(p: Pointer) {
        val k = p.key?.key ?: return
        if (k.alternates.size < 2) { cancel(p); return }
        schedule(p, 380) {
            p.mode = Mode.LONGPRESS
            p.altIndex = 0
            invalidate()
        }
    }

    private fun repeatDelete(p: Pointer) {
        if (pointers[p.id] !== p) return
        p.deleteCount++
        listener.onDelete(p.deleteCount)
        listener.onFeedback(KeyType.DELETE)
        schedule(p, if (p.deleteCount > 12) 130 else 85) { repeatDelete(p) }
    }

    private fun enterTrackpad(p: Pointer) {
        cancel(p)
        p.mode = Mode.TRACKPAD
        p.accX = 0f; p.accY = 0f
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        val dx = x - p.lastX
        val dy = y - p.lastY
        p.lastX = x; p.lastY = y
        when (p.mode) {
            Mode.LONGPRESS -> {
                val n = p.key?.key?.alternates?.size ?: return
                val (left, cw) = altGeometry(p.key!!, n)
                val idx = floor((x - left) / cw).toInt().coerceIn(0, n - 1)
                if (idx != p.altIndex) { p.altIndex = idx; invalidate() }
            }
            Mode.TRACKPAD -> {
                p.accX += dx; p.accY += dy
                val stepX = dp(9f)
                val stepY = dp(24f)
                var mx = 0
                var my = 0
                while (p.accX >= stepX) { mx++; p.accX -= stepX }
                while (p.accX <= -stepX) { mx--; p.accX += stepX }
                while (p.accY >= stepY) { my++; p.accY -= stepY }
                while (p.accY <= -stepY) { my--; p.accY += stepY }
                if (mx != 0 || my != 0) listener.onCursor(mx, my)
            }
            Mode.NORMAL -> {
                val cur = p.key ?: return
                if (cur.key.type == KeyType.SPACE && abs(x - p.downX) > dp(20f)) {
                    enterTrackpad(p)
                    return
                }
                if (cur.key.type == KeyType.CHAR || p.fromModeSlide) {
                    val nk = keyAt(x, y) ?: return
                    if (nk !== cur && (p.fromModeSlide || nk.key.type == KeyType.CHAR)) {
                        p.key = nk
                        if (nk.key.type == KeyType.CHAR) scheduleLongPress(p) else cancel(p)
                        invalidate()
                    }
                }
            }
            Mode.BAR -> {
                val s = slotAt(x)
                if (s != p.slot) { p.slot = -1; invalidate() }
            }
            Mode.DONE -> {}
        }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val p = pointers.remove(id) ?: return
        cancel(p)
        val kr = p.key
        when (p.mode) {
            Mode.BAR -> if (p.slot >= 0 && slotAt(x) == p.slot && suggestions[p.slot].isNotEmpty()) {
                listener.onFeedback(KeyType.CHAR)
                listener.onSuggestion(p.slot)
            }
            Mode.LONGPRESS -> kr?.key?.alternates?.getOrNull(p.altIndex)?.let { listener.onAlternate(it) }
            Mode.NORMAL -> if (kr != null) when (kr.key.type) {
                KeyType.CHAR -> listener.onChar(kr.key, p.fromModeSlide)
                KeyType.SPACE -> listener.onSpace()
                KeyType.RETURN -> listener.onReturn()
                KeyType.EMOJI -> listener.onEmoji()
                KeyType.GLOBE -> listener.onGlobe()
                KeyType.ZWNJ -> listener.onZwnj()
                else -> {}
            }
            else -> {}
        }
        invalidate()
    }

    // ---- predictive bar -------------------------------------------------------------------

    private fun slotAt(x: Float): Int {
        val i = (x / (width / 3f)).toInt().coerceIn(0, 2)
        // Right-to-left: the first slot sits on the right, like the iOS Persian keyboard.
        return if (layout?.rtl == true) 2 - i else i
    }

    private fun slotRect(slot: Int, out: RectF) {
        val w = width / 3f
        val i = if (layout?.rtl == true) 2 - slot else slot
        out.set(i * w, 0f, (i + 1) * w, barH)
    }

    // ---- drawing --------------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        c.drawColor(theme.background)
        drawBar(c)
        val trackpad = trackpadActive
        val pressed = pointers.values.filter { it.mode == Mode.NORMAL || it.mode == Mode.LONGPRESS }
            .mapNotNull { it.key }.toSet()
        keys.forEach { drawKey(c, it, it in pressed, trackpad) }
        if (!trackpad) {
            pointers.values.forEach { p ->
                val kr = p.key ?: return@forEach
                if (p.mode == Mode.LONGPRESS) drawAlternates(c, kr, p.altIndex)
                else if (p.mode == Mode.NORMAL && showPreview && kr.key.type == KeyType.CHAR && kr.key.width <= 1.5f) {
                    drawBalloon(c, kr)
                }
            }
        }
    }

    private fun drawBar(c: Canvas) {
        val barPressed = pointers.values.firstOrNull { it.mode == Mode.BAR }?.slot ?: -1
        text.color = theme.text
        text.typeface = Typeface.DEFAULT
        val nonEmpty = suggestions.count { it.isNotEmpty() }
        for (s in 0..2) {
            slotRect(s, tmp)
            if (s == barPressed && suggestions[s].isNotEmpty()) {
                fill.color = if (theme.dark) theme.key else theme.special
                c.drawRoundRect(tmp.left + dp(4f), tmp.top + dp(5f), tmp.right - dp(4f), tmp.bottom - dp(5f), radius, radius, fill)
            }
            val word = suggestions[s]
            if (word.isEmpty()) continue
            text.textSize = sp(16.5f)
            val label = TextUtils.ellipsize(word, text, tmp.width() - dp(12f), TextUtils.TruncateAt.END).toString()
            c.drawText(label, tmp.centerX(), baseline(tmp.centerY()), text)
        }
        if (nonEmpty > 0) {
            fill.color = theme.divider
            val w = width / 3f
            for (i in 1..2) c.drawRect(i * w - dp(0.5f), barH * 0.28f, i * w + dp(0.5f), barH * 0.72f, fill)
        }
    }

    private fun isSpecial(k: Key) = when (k.type) {
        KeyType.CHAR, KeyType.SPACE -> false
        KeyType.RETURN -> !returnIsAction
        else -> true
    }

    private fun drawKey(c: Canvas, kr: KeyRect, pressed: Boolean, trackpad: Boolean) {
        val k = kr.key
        val r = kr.rect
        val action = k.type == KeyType.RETURN && returnIsAction
        val shiftOn = k.type == KeyType.SHIFT && shiftState != ShiftState.OFF
        val base = when {
            trackpad -> if (theme.dark) theme.special else theme.key
            action -> if (pressed) theme.actionPressed else theme.action
            shiftOn -> if (theme.dark) theme.text else theme.key
            isSpecial(k) -> if (pressed) theme.specialPressed else theme.special
            // Letters don't change colour while the balloon is shown, like iOS.
            k.type == KeyType.CHAR && showPreview && k.width <= 1.5f -> theme.key
            else -> if (pressed) theme.keyPressed else theme.key
        }
        fill.color = theme.shadow
        c.drawRoundRect(r.left, r.top + dp(1.1f), r.right, r.bottom + dp(1.1f), radius, radius, fill)
        fill.color = base
        c.drawRoundRect(r, radius, radius, fill)
        if (trackpad) return

        val fg = when {
            action -> theme.actionText
            shiftOn && theme.dark -> theme.background
            else -> theme.text
        }
        when (k.type) {
            KeyType.SHIFT -> drawShift(c, r, fg)
            KeyType.DELETE -> drawDelete(c, r, fg, pressed)
            KeyType.GLOBE -> drawGlobe(c, r, fg)
            KeyType.EMOJI -> drawEmoji(c, r, fg)
            KeyType.RETURN -> {
                val lbl = returnLabel
                if (lbl == null) drawReturnGlyph(c, r, fg) else drawLabel(c, r, lbl, sp(16f), fg)
            }
            KeyType.SPACE -> drawLabel(c, r, spaceLabelOverride ?: k.label, sp(16f), fg)
            KeyType.CHAR -> {
                val big = k.label.length == 1 || k.label.startsWith("◌")
                val size = when {
                    !big -> sp(16f)
                    layout?.lang == Lang.FA && layout?.page == Page.ALPHA -> sp(22f)
                    else -> sp(24f)
                }
                drawLabel(c, r, display(k), size, fg, light = big)
            }
            else -> drawLabel(c, r, k.label, sp(if (k.type == KeyType.ZWNJ) 13f else 16f), fg)
        }
    }

    fun display(k: Key): String =
        if (k.shiftable && shiftState != ShiftState.OFF) k.label.uppercase() else k.label

    private fun baseline(cy: Float): Float {
        val fm = text.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2
    }

    private fun drawLabel(c: Canvas, r: RectF, label: String, size: Float, color: Int, light: Boolean = false) {
        text.color = color
        text.typeface = if (light) Typeface.create("sans-serif", Typeface.NORMAL) else Typeface.DEFAULT
        text.textSize = size
        val maxW = r.width() - dp(6f)
        val w = text.measureText(label)
        if (w > maxW) text.textSize = size * maxW / w
        // Lowercase latin letters sit slightly high in the key on iOS.
        val nudge = if (light && label.length == 1 && label[0].isLowerCase()) -dp(1.5f) else 0f
        c.drawText(label, r.centerX(), baseline(r.centerY()) + nudge, text)
    }

    // ---- iOS balloon & accent popup -------------------------------------------------------

    private fun balloonPath(kr: RectF, left: Float, right: Float, top: Float, bodyH: Float) {
        val bl = min(left, kr.left)
        val br = max(right, kr.right)
        val r = dp(9f)
        val rk = radius
        val neckTop = top + bodyH
        val neckBottom = kr.top + kr.height() * 0.18f
        val mid = (neckTop + neckBottom) / 2
        path.reset()
        path.moveTo(bl, top + r)
        path.quadTo(bl, top, bl + r, top)
        path.lineTo(br - r, top)
        path.quadTo(br, top, br, top + r)
        path.lineTo(br, neckTop)
        path.cubicTo(br, mid, kr.right, mid, kr.right, neckBottom)
        path.lineTo(kr.right, kr.bottom - rk)
        path.quadTo(kr.right, kr.bottom, kr.right - rk, kr.bottom)
        path.lineTo(kr.left + rk, kr.bottom)
        path.quadTo(kr.left, kr.bottom, kr.left, kr.bottom - rk)
        path.lineTo(kr.left, neckBottom)
        path.cubicTo(kr.left, mid, bl, mid, bl, neckTop)
        path.close()
    }

    private fun popupTop(kr: RectF) = max(dp(1f), kr.top - kr.height() * 1.18f)
    private fun popupBodyH(kr: RectF) = kr.top - kr.height() * 0.2f - popupTop(kr)

    private fun paintBalloon(c: Canvas) {
        balloonPaint.color = theme.key
        balloonPaint.setShadowLayer(dp(3f), 0f, dp(1f), if (theme.dark) 0x99000000.toInt() else 0x55000000)
        c.drawPath(path, balloonPaint)
    }

    private fun drawBalloon(c: Canvas, kr: KeyRect) {
        val r = kr.rect
        val ext = min(r.width() * 0.3f, dp(12f))
        val left = max(dp(1f), r.left - ext)
        val right = min(width - dp(1f), r.right + ext)
        val top = popupTop(r)
        val bodyH = popupBodyH(r)
        balloonPath(r, left, right, top, bodyH)
        paintBalloon(c)
        text.color = theme.text
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        text.textSize = sp(if (layout?.lang == Lang.FA) 32f else 36f)
        val label = display(kr.key)
        val maxW = right - left - dp(4f)
        val w = text.measureText(label)
        if (w > maxW) text.textSize *= maxW / w
        c.drawText(label, (left + right) / 2, baseline(top + bodyH / 2), text)
    }

    /** Returns the x of the first accent cell and the cell width. */
    private fun altGeometry(kr: KeyRect, n: Int): Pair<Float, Float> {
        val cw = max(kr.rect.width(), dp(30f))
        val pad = dp(6f)
        val total = n * cw + 2 * pad
        var left = kr.rect.left - pad
        if (left + total > width - dp(2f)) left = width - dp(2f) - total
        left = max(dp(2f), left)
        return Pair(left + pad, cw)
    }

    private fun drawAlternates(c: Canvas, kr: KeyRect, selected: Int) {
        val alts = kr.key.alternates
        val n = alts.size
        val (first, cw) = altGeometry(kr, n)
        val pad = dp(6f)
        val r = kr.rect
        val top = popupTop(r)
        val bodyH = popupBodyH(r)
        balloonPath(r, first - pad, first + n * cw + pad, top, bodyH)
        paintBalloon(c)
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        alts.forEachIndexed { i, a ->
            val cl = first + i * cw
            val shown = if (kr.key.shiftable && shiftState != ShiftState.OFF) a.uppercase() else a
            if (i == selected) {
                fill.color = theme.action
                c.drawRoundRect(cl + dp(1f), top + dp(5f), cl + cw - dp(1f), top + bodyH - dp(5f), radius, radius, fill)
            }
            text.color = if (i == selected) theme.actionText else theme.text
            text.textSize = sp(if (shown.length > 2) 17f else 26f)
            c.drawText(shown, cl + cw / 2, baseline(top + bodyH / 2), text)
        }
    }

    // ---- icons (drawn as vectors to match the iOS glyphs) ---------------------------------

    private fun drawShift(c: Canvas, r: RectF, color: Int) {
        val s = dp(10f)
        val cx = r.centerX()
        val cy = r.centerY() - if (shiftState == ShiftState.LOCKED) dp(2f) else 0f
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        if (shiftState == ShiftState.OFF) {
            stroke.color = color; stroke.strokeWidth = dp(1.6f)
            c.drawPath(path, stroke)
        } else {
            fill.color = color
            c.drawPath(path, fill)
            if (shiftState == ShiftState.LOCKED) {
                c.drawRect(cx - s * 0.45f, cy + s * 1.15f, cx + s * 0.45f, cy + s * 1.35f, fill)
            }
        }
    }

    private fun drawDelete(c: Canvas, r: RectF, color: Int, pressed: Boolean) {
        val h = dp(16f)
        val w = dp(22f)
        val cx = r.centerX()
        val cy = r.centerY()
        val left = cx - w / 2
        path.reset()
        path.moveTo(left, cy)
        path.lineTo(left + h * 0.45f, cy - h / 2)
        path.lineTo(cx + w / 2, cy - h / 2)
        path.lineTo(cx + w / 2, cy + h / 2)
        path.lineTo(left + h * 0.45f, cy + h / 2)
        path.close()
        stroke.color = color; stroke.strokeWidth = dp(1.6f)
        if (pressed) {
            fill.color = color
            c.drawPath(path, fill)
        } else {
            c.drawPath(path, stroke)
        }
        val xc = cx + dp(2.5f)
        val xs = dp(3.4f)
        stroke.color = if (pressed) (if (theme.dark) theme.special else theme.specialPressed) else color
        c.drawLine(xc - xs, cy - xs, xc + xs, cy + xs, stroke)
        c.drawLine(xc - xs, cy + xs, xc + xs, cy - xs, stroke)
    }

    private fun drawGlobe(c: Canvas, r: RectF, color: Int) {
        val rad = dp(10f)
        val cx = r.centerX()
        val cy = r.centerY()
        stroke.color = color; stroke.strokeWidth = dp(1.4f)
        c.drawCircle(cx, cy, rad, stroke)
        tmp.set(cx - rad * 0.45f, cy - rad, cx + rad * 0.45f, cy + rad)
        c.drawOval(tmp, stroke)
        c.drawLine(cx, cy - rad, cx, cy + rad, stroke)
        c.drawLine(cx - rad, cy, cx + rad, cy, stroke)
        val o = rad * 0.55f
        val hw = rad * 0.83f
        c.drawLine(cx - hw, cy - o, cx + hw, cy - o, stroke)
        c.drawLine(cx - hw, cy + o, cx + hw, cy + o, stroke)
    }

    private fun drawEmoji(c: Canvas, r: RectF, color: Int) {
        val rad = dp(10f)
        val cx = r.centerX()
        val cy = r.centerY()
        stroke.color = color; stroke.strokeWidth = dp(1.4f)
        c.drawCircle(cx, cy, rad, stroke)
        fill.color = color
        c.drawCircle(cx - rad * 0.35f, cy - rad * 0.25f, dp(1.3f), fill)
        c.drawCircle(cx + rad * 0.35f, cy - rad * 0.25f, dp(1.3f), fill)
        tmp.set(cx - rad * 0.5f, cy - rad * 0.2f, cx + rad * 0.5f, cy + rad * 0.55f)
        c.drawArc(tmp, 20f, 140f, false, stroke)
    }

    private fun drawReturnGlyph(c: Canvas, r: RectF, color: Int) {
        val cx = r.centerX()
        val cy = r.centerY()
        val s = dp(9f)
        stroke.color = color; stroke.strokeWidth = dp(1.6f)
        path.reset()
        path.moveTo(cx + s, cy - s * 0.8f)
        path.lineTo(cx + s, cy + s * 0.2f)
        path.lineTo(cx - s, cy + s * 0.2f)
        c.drawPath(path, stroke)
        path.reset()
        path.moveTo(cx - s * 0.45f, cy - s * 0.35f)
        path.lineTo(cx - s, cy + s * 0.2f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.75f)
        c.drawPath(path, stroke)
    }
}
