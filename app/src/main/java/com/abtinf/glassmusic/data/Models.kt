package com.abtinf.glassmusic.data

data class LyricLine(val timeMs: Long, val text: String, val translation: String? = null)

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Playable content Uri; null for built-in demo tracks (simulated playback). */
    val uri: String?,
    /** Content Uri whose embedded artwork is loaded through [AudioArt]. */
    val artUri: String?,
    val dateAdded: Long,
    val lyrics: List<LyricLine> = emptyList(),
    val format: String = "MP3",
    val bitrateKbps: Int = 0,
    val sizeBytes: Long = 0,
    val year: Int = 0,
    val trackNo: Int = 0,
    val discNo: Int = 1,
    val albumArtist: String = "",
) {
    val albumId: Long get() = albumIdOf(album, artist)
    val artistId: Long get() = artistIdOf(artist)
    val seed: Int get() = album.hashCode() xor (artist.hashCode() * 31)
}

fun albumIdOf(album: String, artist: String): Long = (album.lowercase() + "|" + artist.lowercase()).hashCode().toLong()
fun artistIdOf(artist: String): Long = artist.lowercase().hashCode().toLong()

data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>) {
    val cover: Track get() = tracks.first()
    val dateAdded: Long get() = tracks.maxOf { it.dateAdded }
}

data class Artist(val id: Long, val name: String, val tracks: List<Track>) {
    val cover: Track get() = tracks.first()
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Library(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val isDemo: Boolean = false,
    val hasPermission: Boolean = false,
    val loaded: Boolean = false,
) {
    val trackById: Map<Long, Track> by lazy { tracks.associateBy { it.id } }
    val albumById: Map<Long, Album> by lazy { albums.associateBy { it.id } }
    val artistById: Map<Long, Artist> by lazy { artists.associateBy { it.id } }
    val recentlyAddedTracks: List<Track> by lazy { tracks.sortedByDescending { it.dateAdded } }
    val recentlyAddedAlbums: List<Album> by lazy { albums.sortedByDescending { it.dateAdded } }

    companion object {
        fun build(tracks: List<Track>, isDemo: Boolean, hasPermission: Boolean): Library {
            val albums = tracks.groupBy { it.albumId }.map { (id, list) ->
                Album(id, list.first().album, list.first().artist, list.sortedBy { it.title })
            }.sortedBy { it.title.lowercase() }
            val artists = tracks.groupBy { it.artistId }.map { (id, list) ->
                Artist(id, list.first().artist, list.sortedBy { it.title })
            }.sortedBy { it.name.lowercase() }
            return Library(tracks, albums, artists, isDemo, hasPermission, loaded = true)
        }
    }
}
