package com.abtinf.glassmusic.data

/**
 * Parses LRC text ("[mm:ss.xx] line"). Consecutive lines that share a timestamp become
 * line + translation. Text without timestamps comes back unsynced (every line has timeMs = -1).
 */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String, durationMs: Long): List<LyricLine> {
        val timed = mutableListOf<Pair<Long, String>>()
        val plain = mutableListOf<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val matches = stamp.findAll(line).toList()
            if (matches.isEmpty()) {
                if (!line.startsWith("[")) plain += line // skip [ar:..] style tags
                return@forEach
            }
            val content = line.substring(matches.last().range.last + 1).trim()
            if (content.isEmpty()) return@forEach
            matches.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                timed += (min * 60_000 + sec * 1000 + ms) to content
            }
        }
        if (timed.isNotEmpty()) {
            val sorted = timed.sortedBy { it.first }
            val out = mutableListOf<LyricLine>()
            sorted.forEach { (t, s) ->
                val last = out.lastOrNull()
                if (last != null && last.timeMs == t && last.translation == null) {
                    out[out.lastIndex] = last.copy(translation = s)
                } else {
                    out += LyricLine(t, s)
                }
            }
            return out
        }
        if (plain.isEmpty()) return emptyList()
        // No timestamps in the text: keep the lines but mark them unsynced (timeMs = -1).
        return plain.map { LyricLine(-1L, it) }
    }
}
