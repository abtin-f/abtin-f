package com.abtin.iphonekeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmokeTest {

    @Test
    fun settingsActivityOpens() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        controller.get().onWindowFocusChanged(true)
        controller.pause().stop().destroy()
    }

    @Test
    fun keyboardServiceStarts() {
        val service = Robolectric.buildService(KeyboardService::class.java).create().get()
        service.onCreateInputView()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        service.onStartInputView(info, false)
        service.onFinishInputView(true)
        service.onDestroy()
    }

    private val noop = object : KeyboardView.Listener {
        override fun onFeedback(type: KeyType) {}
        override fun onChar(key: Key, fromModeSlide: Boolean) {}
        override fun onAlternate(text: String) {}
        override fun onShift() {}
        override fun onDelete(repeat: Int) {}
        override fun onSpace() {}
        override fun onReturn() {}
        override fun onModeKey(type: KeyType) {}
        override fun onGlobe() {}
        override fun onGlobeLongPress() {}
        override fun onEmoji() {}
        override fun onZwnj() {}
        override fun onCursor(dx: Int, dy: Int) {}
        override fun onSuggestion(index: Int) {}
    }

    private fun render(name: String, press: Pair<Float, Float>? = null, longPress: Boolean = false, setup: (KeyboardView) -> Unit) {
        val ctx = RuntimeEnvironment.getApplication()
        val kv = KeyboardView(ctx, noop)
        setup(kv)
        val w = ctx.resources.displayMetrics.widthPixels
        kv.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
        if (press != null) {
            val t = SystemClock.uptimeMillis()
            kv.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, press.first * kv.width, press.second * kv.height, 0))
            if (longPress) ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        }
        val bmp = Bitmap.createBitmap(kv.width, kv.height, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))
        val dir = File(System.getProperty("screensDir") ?: "build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderScreens() {
        render("1_fa_light", 0.12f to 0.33f) {
            it.layout = Layouts.get(Lang.FA, Page.ALPHA, false, FieldKind.TEXT, true)
            it.suggestions = listOf("“سل”", "سلام", "سلامت")
        }
        render("2_en_light", 0.33f to 0.50f) {
            it.layout = Layouts.get(Lang.EN, Page.ALPHA, false, FieldKind.TEXT, true)
            it.returnLabel = "return"
            it.suggestions = listOf("“hel”", "hello", "help")
        }
        render("3_en_dark_shift") {
            it.theme = KeyboardTheme.DARK
            it.layout = Layouts.get(Lang.EN, Page.ALPHA, false, FieldKind.TEXT, true)
            it.shiftState = ShiftState.ON
            it.returnLabel = "search"; it.returnIsAction = true
        }
        render("4_fa_shift") {
            it.layout = Layouts.get(Lang.FA, Page.ALPHA, true, FieldKind.TEXT, true)
            it.shiftState = ShiftState.ON
        }
        render("5_fa_num_dark") {
            it.theme = KeyboardTheme.DARK
            it.layout = Layouts.get(Lang.FA, Page.NUM, false, FieldKind.TEXT, true)
        }
        render("6_en_longpress", 0.25f to 0.33f, longPress = true) {
            it.layout = Layouts.get(Lang.EN, Page.ALPHA, false, FieldKind.TEXT, true)
            it.returnLabel = "return"
        }
    }
}
