package com.abtin.iphonekeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Prefix-based word suggestions for the predictive bar. A small frequency-ordered built-in
 * word list per language is combined with words the user has typed (learned over time).
 */
class Suggest(context: Context) {
    private val app = context.applicationContext
    private val builtIn = HashMap<Lang, List<String>>()
    private val learnedSp = app.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
    private val learned = HashMap<String, Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { save() }

    init {
        learnedSp.all.forEach { (k, v) -> if (v is Int) learned[k] = v }
    }

    private fun words(lang: Lang): List<String> = builtIn.getOrPut(lang) {
        val file = if (lang == Lang.EN) "dict_en.txt" else "dict_fa.txt"
        runCatching {
            app.assets.open(file).bufferedReader().useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
            }
        }.getOrDefault(emptyList())
    }

    fun suggest(prefix: String, lang: Lang, max: Int = 2): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val lower = prefix.lowercase()
        val scores = HashMap<String, Double>()
        val list = words(lang)
        list.forEachIndexed { rank, w ->
            if (w.length > lower.length && w.startsWith(lower)) {
                scores[w] = 1000.0 / (rank + 10)
            }
        }
        learned.forEach { (w, count) ->
            if (w.length > lower.length && w.startsWith(lower) && isLang(w, lang)) {
                scores[w] = (scores[w] ?: 0.0) + count * 25.0
            }
        }
        val capitalize = prefix.first().isUpperCase()
        return scores.entries.sortedByDescending { it.value }.take(max).map {
            if (capitalize) it.key.replaceFirstChar { c -> c.uppercaseChar() } else it.key
        }
    }

    fun isKnown(word: String, lang: Lang): Boolean {
        val lower = word.lowercase()
        return learned.containsKey(lower) || words(lang).contains(lower)
    }

    fun learn(word: String) {
        if (word.length < 2 || word.any { it.isDigit() }) return
        val w = word.lowercase()
        learned[w] = (learned[w] ?: 0) + 1
        if (learned.size > 3000) {
            learned.entries.sortedBy { it.value }.take(500).forEach { learned.remove(it.key) }
        }
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, 3000)
    }

    private fun save() {
        val e = learnedSp.edit().clear()
        learned.forEach { (k, v) -> e.putInt(k, v) }
        e.apply()
    }

    private fun isLang(w: String, lang: Lang): Boolean {
        val c = w.first()
        val persian = c in '\u0600'..'\u06FF'
        return if (lang == Lang.FA) persian else !persian
    }

    companion object {
        fun isWordChar(c: Char): Boolean =
            c.isLetter() || c == '\'' || c == '\u200C' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
    }
}
