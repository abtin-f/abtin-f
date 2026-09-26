package com.abtin.iphonekeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

/** iOS-like emoji keyboard: scrolling grid, category bar, ABC and delete keys. */
@SuppressLint("ViewConstructor")
class EmojiView(context: Context, private val prefs: Prefs, private val listener: Listener) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onEmojiBack()
        fun onEmojiDelete()
        fun onEmojiFeedback(type: KeyType)
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = (v * density).toInt()

    private val title = TextView(context)
    private val grid = GridView(context)
    private val bar = LinearLayout(context)
    private val abc = TextView(context)
    private val delete = TextView(context)
    private val tabs = ArrayList<TextView>()
    private var current = 1
    private var items: List<String> = emptyList()
    private var theme = KeyboardTheme.LIGHT
    private val handler = Handler(Looper.getMainLooper())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 29f)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f))
                setTextColor(Color.BLACK)
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR

        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.setPadding(dp(12f), dp(8f), dp(12f), dp(2f))
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = dp(44f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        grid.setOnItemClickListener { _, _, pos, _ ->
            val e = items[pos]
            listener.onEmojiFeedback(KeyType.CHAR)
            listener.onEmojiPicked(e)
            remember(e)
        }
        grid.setPadding(dp(4f), 0, dp(4f), 0)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(4f), 0, dp(4f), 0)

        abc.gravity = Gravity.CENTER
        abc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        abc.setOnClickListener { listener.onEmojiFeedback(KeyType.MODE_ALPHA); listener.onEmojiBack() }
        bar.addView(abc, LayoutParams(dp(52f), LayoutParams.MATCH_PARENT))

        EmojiData.CATEGORIES.forEachIndexed { i, cat ->
            val tab = TextView(context).apply {
                text = cat.icon
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setOnClickListener { listener.onEmojiFeedback(KeyType.MODE_ALPHA); select(i) }
            }
            tabs.add(tab)
            bar.addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }

        delete.text = "⌫"
        delete.gravity = Gravity.CENTER
        delete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        delete.setOnTouchListener(DeleteRepeater())
        bar.addView(delete, LayoutParams(dp(52f), LayoutParams.MATCH_PARENT))

        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, dp(42f)))
    }

    fun show(theme: KeyboardTheme, lang: Lang) {
        this.theme = theme
        setBackgroundColor(theme.background)
        title.setTextColor(if (theme.dark) Color.rgb(160, 160, 165) else Color.rgb(110, 112, 118))
        abc.text = if (lang == Lang.FA) "الفبا" else "ABC"
        abc.setTextColor(theme.text)
        delete.setTextColor(theme.text)
        select(if (prefs.recentEmoji.isEmpty()) 1 else current)
    }

    private fun select(i: Int) {
        current = i
        val cat = EmojiData.CATEGORIES[i]
        title.text = cat.title
        items = if (i == 0) prefs.recentEmoji else cat.emoji
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        tabs.forEachIndexed { j, t ->
            t.alpha = if (j == i) 1f else 0.45f
            t.setTextColor(theme.text)
        }
    }

    private fun remember(e: String) {
        val list = listOf(e) + prefs.recentEmoji.filter { it != e }
        prefs.recentEmoji = list.take(32)
    }

    /** Hold-to-repeat delete key. */
    private inner class DeleteRepeater : OnTouchListener {
        private val repeat = object : Runnable {
            override fun run() {
                listener.onEmojiFeedback(KeyType.DELETE)
                listener.onEmojiDelete()
                handler.postDelayed(this, 90)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.5f
                    listener.onEmojiFeedback(KeyType.DELETE)
                    listener.onEmojiDelete()
                    handler.postDelayed(repeat, 450)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    handler.removeCallbacks(repeat)
                }
            }
            return true
        }
    }
}
