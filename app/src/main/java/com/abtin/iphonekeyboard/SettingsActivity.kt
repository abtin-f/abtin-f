package com.abtin.iphonekeyboard

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** iOS "Settings"-style screen: setup steps, sound volume, vibration, typing and look. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var feedback: Feedback
    private lateinit var status: TextView
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val pageBg get() = if (dark) Color.BLACK else Color.rgb(242, 242, 247)
    private val cardBg get() = if (dark) Color.rgb(28, 28, 30) else Color.WHITE
    private val primary get() = if (dark) Color.WHITE else Color.BLACK
    private val secondary get() = if (dark) Color.rgb(142, 142, 147) else Color.rgb(108, 108, 112)
    private val separator get() = if (dark) Color.rgb(56, 56, 58) else Color.rgb(222, 222, 226)
    private val blue = Color.rgb(0, 122, 255)

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
    private fun fa(n: Int) = n.toString().map { if (it.isDigit()) '۰' + (it - '0') else it }.joinToString("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        feedback = Feedback(this, prefs)

        val scroll = ScrollView(this).apply { setBackgroundColor(pageBg); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16f), dp(24f), dp(16f), dp(40f))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "کیبورد آیفون"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primary)
        })
        root.addView(TextView(this).apply {
            text = "طراحی دقیق کیبورد iPhone برای اندروید · فارسی و English"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(secondary)
            setPadding(0, dp(2f), 0, dp(8f))
        })

        // --- setup
        section(root, "راه‌اندازی")
        val setup = card(root)
        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(16f), dp(14f), dp(16f), dp(6f))
        }
        setup.addView(status)
        setup.addView(actionButton("۱. فعال‌سازی کیبورد در تنظیمات") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setup.addView(actionButton("۲. انتخاب «کیبورد آیفون» به عنوان کیبورد") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        // --- sound
        section(root, "صدای تایپ")
        val sound = card(root)
        switchRow(sound, "صدای کلیدها", "صدای تق‌تق مثل آیفون", prefs.soundEnabled) { prefs.soundEnabled = it }
        divider(sound)
        sliderRow(sound, "بلندی صدا", prefs.soundVolume, 0, 100, { "${fa(it)}٪" }) { v, fromUser ->
            prefs.soundVolume = v
            if (fromUser) feedback.click(ClickSound.CHAR, v, force = true)
        }
        divider(sound)
        switchRow(sound, "بی‌صدا در حالت سکوت", "وقتی گوشی روی سایلنت یا ویبره است صدا پخش نشود", prefs.respectSilent) {
            prefs.respectSilent = it
        }
        footer(root, "بلندی صدای کلیدها مستقل تنظیم می‌شود؛ صدای «سیستم» گوشی هم روی آن اثر دارد.")

        // --- haptics
        section(root, "لرزش (هپتیک)")
        val hap = card(root)
        switchRow(hap, "لرزش هنگام تایپ", null, prefs.vibrate) { prefs.vibrate = it }
        divider(hap)
        sliderRow(hap, "شدت لرزش", prefs.vibrateStrength, 1, 100, { "${fa(it)}٪" }) { v, fromUser ->
            prefs.vibrateStrength = v
            if (fromUser) feedback.vibrate(v, force = true)
        }

        // --- typing
        section(root, "تایپ")
        val typing = card(root)
        switchRow(typing, "پیش‌نمایش کاراکتر", "بزرگ شدن حرف هنگام لمس (بالن آیفون)", prefs.keyPreview) { prefs.keyPreview = it }
        divider(typing)
        switchRow(typing, "پیشنهاد کلمات", "نوار پیشنهاد بالای کیبورد + یادگیری کلمات شما", prefs.suggestions) { prefs.suggestions = it }
        divider(typing)
        switchRow(typing, "حروف بزرگ خودکار", "شروع جمله انگلیسی با حرف بزرگ", prefs.autoCap) { prefs.autoCap = it }
        divider(typing)
        switchRow(typing, "میانبر «.»", "دو بار زدن فاصله یک نقطه و فاصله درج می‌کند", prefs.doubleSpacePeriod) { prefs.doubleSpacePeriod = it }
        divider(typing)
        switchRow(typing, "اعداد فارسی", "در صفحهٔ اعداد کیبورد فارسی ۱۲۳ به جای 123", prefs.persianDigits) { prefs.persianDigits = it }

        // --- look
        section(root, "ظاهر")
        val look = card(root)
        look.addView(label("حالت نمایش", primary, 16f).apply { setPadding(dp(16f), dp(12f), dp(16f), dp(4f)) })
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(dp(12f), 0, dp(12f), dp(8f))
        }
        listOf("auto" to "خودکار", "light" to "روشن", "dark" to "تیره").forEachIndexed { i, (key, title) ->
            group.addView(RadioButton(this).apply {
                id = 1000 + i
                text = title
                setTextColor(primary)
                isChecked = prefs.theme == key
                setOnClickListener { prefs.theme = key }
            }, RadioGroup.LayoutParams(0, RadioGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        look.addView(group)
        divider(look)
        sliderRow(look, "ارتفاع کیبورد", prefs.heightPercent, 80, 125, { "${fa(it)}٪" }) { v, _ -> prefs.heightPercent = v }

        // --- try it
        section(root, "امتحان کنید")
        val tryCard = card(root)
        tryCard.addView(EditText(this).apply {
            hint = "اینجا تایپ کنید… Type here…"
            setTextColor(primary)
            setHintTextColor(secondary)
            background = null
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        })
        footer(root, "راهنما: 🌐 تغییر زبان · نگه داشتن 🌐 انتخاب کیبورد دیگر · نگه داشتن فاصله و کشیدن = جابه‌جایی مکان‌نما · نگه داشتن حروف = حروف ویژه · دو بار زدن ⇧ = Caps Lock")

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    override fun onDestroy() {
        feedback.release()
        super.onDestroy()
    }

    private fun refreshStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val selected = current.startsWith("$packageName/")
        status.text = when {
            selected -> "✅ کیبورد فعال و انتخاب شده است"
            enabled -> "☑️ فعال شده — حالا مرحلهٔ ۲ را بزنید"
            else -> "⚠️ کیبورد هنوز فعال نشده است"
        }
        status.setTextColor(primary)
    }

    // ---- small UI builders ----------------------------------------------------------------

    private fun label(t: String, color: Int, size: Float) = TextView(this).apply {
        text = t
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(label(title, secondary, 13f).apply { setPadding(dp(16f), dp(22f), dp(16f), dp(6f)) })
    }

    private fun footer(root: LinearLayout, t: String) {
        root.addView(label(t, secondary, 12.5f).apply { setPadding(dp(16f), dp(6f), dp(16f), 0) })
    }

    private fun card(root: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(12f).toFloat()
            }
            clipToOutline = true
        }
        root.addView(c)
        return c
    }

    private fun divider(card: LinearLayout) {
        card.addView(View(this).apply { setBackgroundColor(separator) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(0.7f).coerceAtLeast(1)).apply {
                marginStart = dp(16f)
            })
    }

    private fun actionButton(t: String, onClick: () -> Unit) = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
        text = t
        isAllCaps = false
        setTextColor(blue)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding(dp(16f), 0, dp(16f), 0)
        setOnClickListener { onClick() }
    }

    private fun switchRow(card: LinearLayout, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(10f), dp(12f), dp(10f))
            minimumHeight = dp(50f)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, primary, 16f))
        if (subtitle != null) texts.addView(label(subtitle, secondary, 12.5f))
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        card.addView(row)
    }

    private fun sliderRow(
        card: LinearLayout, title: String, value: Int, min: Int, max: Int,
        format: (Int) -> String, onChange: (Int, Boolean) -> Unit,
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(12f), dp(16f), dp(10f))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(label(title, primary, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val valueLabel = label(format(value), secondary, 15f)
        head.addView(valueLabel)
        box.addView(head)
        val bar = SeekBar(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            this.max = max - min
            progress = value - min
            setPadding(dp(8f), dp(10f), dp(8f), dp(4f))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                private var last = 0L
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    val v = p + min
                    valueLabel.text = format(v)
                    // Throttle the audible/tactile preview while dragging.
                    val now = System.currentTimeMillis()
                    val preview = fromUser && now - last > 90
                    if (preview) last = now
                    onChange(v, preview)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) = onChange(s.progress + min, true)
            })
        }
        box.addView(bar)
        card.addView(box)
    }
}
