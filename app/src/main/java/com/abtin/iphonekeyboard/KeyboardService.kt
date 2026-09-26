package com.abtin.iphonekeyboard

import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout

class KeyboardService : InputMethodService(), KeyboardView.Listener, EmojiView.Listener {

    private lateinit var prefs: Prefs
    private lateinit var feedback: Feedback
    private lateinit var suggest: Suggest
    private var keyboard: KeyboardView? = null
    private var emoji: EmojiView? = null

    private var lang = Lang.FA
    private var page = Page.ALPHA
    private var field = FieldKind.TEXT
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var autoCapAllowed = true
    private var suggestionsAllowed = true
    private var numericField = false

    /** Set right after a suggestion inserted "word " so punctuation can swallow that space. */
    private var autoSpace = false
    private var lastWasSpace = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        feedback = Feedback(this, prefs)
        suggest = Suggest(this)
        lang = prefs.lang
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this)
        val kv = KeyboardView(this, this)
        val ev = EmojiView(this, prefs, this)
        ev.visibility = View.GONE
        root.addView(kv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(ev, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        keyboard = kv
        emoji = ev
        applyLayout()
        return root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val kv = keyboard ?: return
        kv.cancelAll()
        hideEmoji()

        kv.theme = KeyboardTheme.resolve(this, prefs)
        kv.showPreview = prefs.keyPreview
        kv.heightScale = prefs.heightPercent / 100f

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        numericField = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        val password = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) || cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        field = when {
            cls != InputType.TYPE_CLASS_TEXT -> FieldKind.TEXT
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
            variation == InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
            else -> FieldKind.TEXT
        }
        autoCapAllowed = cls == InputType.TYPE_CLASS_TEXT && !password && field == FieldKind.TEXT &&
            variation != InputType.TYPE_TEXT_VARIATION_FILTER
        suggestionsAllowed = autoCapAllowed &&
            info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0

        // Latin-only fields always open in English; everything else remembers the last language.
        lang = if (password || field != FieldKind.TEXT) Lang.EN else prefs.lang
        page = if (numericField) Page.NUM else Page.ALPHA
        shift = ShiftState.OFF
        autoSpace = false
        lastWasSpace = false
        kv.spaceLabelOverride = null
        configureReturn(info)
        applyLayout()
        updateShift()
        updateSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboard?.cancelAll()
        hideEmoji()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShift()
        updateSuggestions()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ---- layout helpers -------------------------------------------------------------------

    private fun applyLayout() {
        val kv = keyboard ?: return
        val shiftedPersian = lang == Lang.FA && page == Page.ALPHA && shift != ShiftState.OFF
        kv.layout = Layouts.get(lang, page, shiftedPersian, field, prefs.persianDigits)
        kv.shiftState = shift
        kv.requestLayout()
    }

    private fun configureReturn(info: EditorInfo) {
        val kv = keyboard ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val fa = lang == Lang.FA
        if (noEnterAction || multiLine) {
            kv.returnLabel = if (fa) null else "return"
            kv.returnIsAction = false
            return
        }
        val (en, faLabel, blue) = when (action) {
            EditorInfo.IME_ACTION_GO -> Triple("go", "برو", true)
            EditorInfo.IME_ACTION_SEARCH -> Triple("search", "جستجو", true)
            EditorInfo.IME_ACTION_SEND -> Triple("send", "ارسال", true)
            EditorInfo.IME_ACTION_NEXT -> Triple("next", "بعدی", false)
            EditorInfo.IME_ACTION_DONE -> Triple("done", "تمام", true)
            EditorInfo.IME_ACTION_PREVIOUS -> Triple("previous", "قبلی", false)
            else -> Triple("return", null, false)
        }
        kv.returnLabel = if (fa) faLabel else en
        kv.returnIsAction = blue
    }

    private fun setShift(s: ShiftState) {
        if (shift == s) return
        val wasPersianShift = lang == Lang.FA && shift != ShiftState.OFF
        shift = s
        val isPersianShift = lang == Lang.FA && shift != ShiftState.OFF
        if (wasPersianShift != isPersianShift && page == Page.ALPHA) applyLayout()
        keyboard?.shiftState = s
    }

    /** iOS auto-capitalisation at the start of a sentence (English letters only). */
    private fun updateShift() {
        if (lang != Lang.EN || page != Page.ALPHA || shift == ShiftState.LOCKED) return
        val ic = currentInputConnection ?: return
        if (!prefs.autoCap || !autoCapAllowed) {
            if (shift == ShiftState.ON && !manualShift) setShift(ShiftState.OFF)
            return
        }
        if (manualShift) return
        val caps = ic.getCursorCapsMode(TextUtils.CAP_MODE_SENTENCES) != 0
        setShift(if (caps) ShiftState.ON else ShiftState.OFF)
    }

    /** True while the user pressed shift themselves and hasn't typed a letter yet. */
    private var manualShift = false

    // ---- suggestions ----------------------------------------------------------------------

    private fun currentWord(): String {
        val ic = currentInputConnection ?: return ""
        val before = ic.getTextBeforeCursor(48, 0)?.toString() ?: return ""
        var i = before.length
        while (i > 0 && Suggest.isWordChar(before[i - 1])) i--
        return before.substring(i)
    }

    private fun updateSuggestions() {
        val kv = keyboard ?: return
        if (!prefs.suggestions || !suggestionsAllowed || page != Page.ALPHA) {
            kv.suggestions = listOf("", "", "")
            return
        }
        val word = currentWord()
        if (word.isEmpty()) {
            kv.suggestions = listOf("", "", "")
            return
        }
        val s = suggest.suggest(word, lang, 2)
        kv.suggestions = listOf("“$word”", s.getOrElse(0) { "" }, s.getOrElse(1) { "" })
    }

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        val word = currentWord()
        val chosen = if (index == 0) word else keyboard?.suggestions?.getOrNull(index).orEmpty()
        if (chosen.isEmpty()) return
        ic.beginBatchEdit()
        if (word.isNotEmpty()) ic.deleteSurroundingText(word.length, 0)
        ic.commitText("$chosen ", 1)
        ic.endBatchEdit()
        suggest.learn(chosen)
        autoSpace = true
        lastWasSpace = false
        manualShift = false
        if (shift == ShiftState.ON) setShift(ShiftState.OFF)
    }

    // ---- KeyboardView.Listener ------------------------------------------------------------

    override fun onFeedback(type: KeyType) {
        feedback.onKeyDown(type)
        keyboard?.spaceLabelOverride = null
    }

    override fun onChar(key: Key, fromModeSlide: Boolean) {
        val text = if (key.shiftable && shift != ShiftState.OFF) key.output.uppercase() else key.output
        commitTyped(text)
        if (fromModeSlide && page != Page.ALPHA) {
            page = Page.ALPHA
            applyLayout()
            updateShift()
            updateSuggestions()
        } else if (page != Page.ALPHA && key.output == "'") {
            // iOS: typing an apostrophe on the number page jumps back to letters.
            page = Page.ALPHA
            applyLayout()
            updateShift()
        }
    }

    override fun onAlternate(text: String) {
        val t = if (shift != ShiftState.OFF && lang == Lang.EN && page == Page.ALPHA) text.uppercase() else text
        commitTyped(t)
    }

    private fun isPunctuation(s: String) = s.length == 1 && s[0] in ".,?!;:،؛؟)»"

    private fun commitTyped(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (autoSpace && isPunctuation(text)) {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before != null && before.toString() == " ") ic.deleteSurroundingText(1, 0)
            ic.commitText(text + " ", 1)
        } else {
            if (isPunctuation(text) || text == " ") learnCurrentWord()
            ic.commitText(text, 1)
        }
        ic.endBatchEdit()
        autoSpace = false
        lastWasSpace = false
        // One-shot shift releases after a single character.
        if (shift == ShiftState.ON) {
            manualShift = false
            setShift(ShiftState.OFF)
        }
        manualShift = false
    }

    private fun learnCurrentWord() {
        if (!suggestionsAllowed) return
        val w = currentWord()
        if (w.length >= 2) suggest.learn(w)
    }

    override fun onShift() {
        val now = SystemClock.uptimeMillis()
        val doubleTap = now - lastShiftTap < 320
        lastShiftTap = now
        when {
            doubleTap && shift != ShiftState.LOCKED -> setShift(ShiftState.LOCKED)
            shift == ShiftState.OFF -> { manualShift = true; setShift(ShiftState.ON) }
            else -> { manualShift = true; setShift(ShiftState.OFF) }
        }
    }

    override fun onDelete(repeat: Int) {
        val ic = currentInputConnection ?: return
        autoSpace = false
        lastWasSpace = false
        val selected = ic.getSelectedText(0)
        when {
            !selected.isNullOrEmpty() -> ic.commitText("", 1)
            repeat > 12 -> {
                // Held long enough: delete whole words, like iOS.
                val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                var i = before.length
                while (i > 0 && before[i - 1].isWhitespace()) i--
                while (i > 0 && !before[i - 1].isWhitespace()) i--
                val n = before.length - i
                if (n > 0) ic.deleteSurroundingText(n, 0) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
            else -> {
                val before = ic.getTextBeforeCursor(16, 0)?.toString()
                if (before.isNullOrEmpty()) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                } else {
                    // Delete one whole grapheme so emoji (incl. ZWJ sequences) aren't split.
                    val bi = BreakIterator.getCharacterInstance()
                    bi.setText(before)
                    val end = bi.last()
                    val start = bi.previous().takeIf { it != BreakIterator.DONE } ?: (end - 1)
                    ic.deleteSurroundingText((end - start).coerceAtLeast(1), 0)
                }
            }
        }
        if (lang == Lang.FA && shift == ShiftState.ON) setShift(ShiftState.OFF)
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        if (prefs.doubleSpacePeriod && lastWasSpace && page == Page.ALPHA) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.length == 2 && before[1] == ' ' && (before[0].isLetterOrDigit() || before[0] in ")\"'»”")) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                ic.endBatchEdit()
                lastWasSpace = false
                autoSpace = false
                return
            }
        }
        learnCurrentWord()
        ic.commitText(" ", 1)
        autoSpace = false
        lastWasSpace = true
        if (page != Page.ALPHA && !numericField) {
            page = Page.ALPHA
            applyLayout()
            updateShift()
        }
    }

    override fun onReturn() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        learnCurrentWord()
        autoSpace = false
        lastWasSpace = false
        if (!noEnterAction && !multiLine && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    override fun onModeKey(type: KeyType) {
        page = when (type) {
            KeyType.MODE_NUM -> Page.NUM
            KeyType.MODE_SYM -> Page.SYM
            else -> Page.ALPHA
        }
        if (shift != ShiftState.OFF) {
            shift = ShiftState.OFF
            manualShift = false
        }
        applyLayout()
        updateShift()
        updateSuggestions()
    }

    override fun onGlobe() {
        lang = if (lang == Lang.EN) Lang.FA else Lang.EN
        if (field == FieldKind.TEXT) prefs.lang = lang
        shift = ShiftState.OFF
        manualShift = false
        currentInputEditorInfo?.let { configureReturn(it) }
        applyLayout()
        keyboard?.spaceLabelOverride = if (lang == Lang.EN) "English" else "فارسی"
        updateShift()
        updateSuggestions()
    }

    override fun onGlobeLongPress() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onEmoji() {
        val kv = keyboard ?: return
        val ev = emoji ?: return
        ev.layoutParams = ev.layoutParams.apply { height = kv.height }
        ev.show(kv.theme, lang)
        ev.visibility = View.VISIBLE
        kv.visibility = View.INVISIBLE
    }

    private fun hideEmoji() {
        emoji?.visibility = View.GONE
        keyboard?.visibility = View.VISIBLE
    }

    override fun onZwnj() {
        commitTyped("\u200C")
    }

    override fun onCursor(dx: Int, dy: Int) {
        // Visual movement: in right-to-left text the arrow keys already move visually.
        repeat(kotlin.math.abs(dx)) {
            sendDownUpKeyEvents(if (dx > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        }
        repeat(kotlin.math.abs(dy)) {
            sendDownUpKeyEvents(if (dy > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
        }
        if (dx != 0 || dy != 0) feedback.vibrate(prefs.vibrateStrength / 2)
    }

    // ---- EmojiView.Listener ---------------------------------------------------------------

    override fun onEmojiPicked(emoji: String) {
        currentInputConnection?.commitText(emoji, 1)
        autoSpace = false
        lastWasSpace = false
    }

    override fun onEmojiBack() = hideEmoji()

    override fun onEmojiDelete() = onDelete(0)

    override fun onEmojiFeedback(type: KeyType) = feedback.onKeyDown(type)
}
