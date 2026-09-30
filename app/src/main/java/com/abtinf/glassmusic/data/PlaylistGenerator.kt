package com.abtinf.glassmusic.data

import java.util.Locale
import kotlin.random.Random

data class GeneratedPlaylist(val name: String, val description: String, val tracks: List<Track>)

/** Offline "Playlist Playground": builds a playlist from a free-text idea using the local library. */
object PlaylistGenerator {
    private const val SIZE = 25

    fun generate(lib: Library, prompt: String): GeneratedPlaylist {
        val q = prompt.lowercase(Locale.ROOT).trim()
        val rnd = Random(q.hashCode())
        val artists = lib.artists.filter { q.contains(it.name.lowercase(Locale.ROOT)) }

        if (artists.isNotEmpty()) {
            val ids = artists.map { it.id }.toSet()
            val own = artists.flatMap { it.tracks }
            val others = lib.tracks.filter { it.artistId !in ids }.shuffled(rnd)
            val picks = (own + others).take(SIZE)
            val name = if (artists.size == 1) "Dive into ${artists[0].name}'s Sonic Universe"
            else artists.take(2).joinToString(" & ") { it.name } + " Mix"
            val who = artists.take(2).joinToString(" and ") { it.name }
            return GeneratedPlaylist(name, "These are tracks by $who and similar artists${including(picks, own.size)}", picks)
        }

        val words = q.split(Regex("[^\\p{L}\\p{N}']+")).filter { it.length > 2 }
        val matched = if (words.isEmpty()) emptyList() else lib.tracks.filter { t ->
            val hay = (t.title + " " + t.album + " " + t.artist).lowercase(Locale.ROOT)
            words.any { hay.contains(it) }
        }
        val rest = lib.tracks.filter { it !in matched }.shuffled(rnd)
        val picks = (matched + rest).take(SIZE)
        val name = if (q.isBlank()) "New Playlist"
        else q.split(" ").filter { it.isNotBlank() }.take(4)
            .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        return GeneratedPlaylist(name, "A mix from your library${including(picks, matched.size)}", picks)
    }

    private fun including(picks: List<Track>, lead: Int): String {
        if (picks.isEmpty()) return "."
        val a = picks.first()
        val b = picks.getOrNull((lead / 2).coerceIn(1, picks.lastIndex.coerceAtLeast(1)))
        return if (b == null || b == a) ", including \"${a.title}\" by ${a.artist}."
        else ", including \"${a.title}\" by ${a.artist} and \"${b.title}\" by ${b.artist}."
    }

    /** The artist with the most tracks, used to seed the first playground playlist. */
    fun defaultPrompt(lib: Library): String = lib.artists.maxByOrNull { it.tracks.size }?.name ?: ""
}
