package com.abtinf.glassmusic.data

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

fun formatTotal(tracks: List<Track>): String {
    val minutes = (tracks.sumOf { it.durationMs } / 60_000).toInt()
    val songs = if (tracks.size == 1) "1 song" else "${tracks.size} songs"
    val h = minutes / 60
    val m = minutes % 60
    val dur = when {
        h == 0 -> if (m == 1) "1 minute" else "$m minutes"
        m == 0 -> if (h == 1) "1 hour" else "$h hours"
        else -> "${if (h == 1) "1 hour" else "$h hours"} ${if (m == 1) "1 minute" else "$m minutes"}"
    }
    return "$songs, $dur"
}
