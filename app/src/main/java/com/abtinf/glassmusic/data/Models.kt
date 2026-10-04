package com.abtinf.glassmusic.data

data class LyricLine(val timeMs: Long, val text: String, val translation: String? = null)

/** Shown for songs whose file has no album tag. */
const val UNKNOWN_ALBUM = "Unknown Album"

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
    /** Folder relative to storage root, e.g. "Music/Persian/". */
    val folder: String = "",
    /** Absolute file path when known (used to find sidecar .lrc files). */
    val path: String? = null,
) {
    // Computed once per track: grouping and sorting read these thousands of times.
    // Albums are identified by title + album artist, so a compilation ("Various Artists") stays one album.
    val albumId: Long = albumIdOf(album, albumArtist.ifBlank { artist })
    val artistId: Long = artistIdOf(artist)
    /** Drives the generated placeholder cover; songs without an album tag get their own picture instead of all looking alike. */
    val seed: Int = (if (album == UNKNOWN_ALBUM) title else album).hashCode() xor (artist.hashCode() * 31)
    /** Lower-cased "title artist album" so searching never allocates per keystroke. */
    val searchKey: String = (title + "\u0001" + artist + "\u0001" + album).lowercase()
    /** Cover cache key: the songs of an album share one decoded picture, but "Unknown Album" is not one album. */
    val artKey: String = if (album == UNKNOWN_ALBUM) "track:$id" else "album:$albumId"
}

/** 64-bit FNV-1a, so two different albums/artists practically never share an id. */
fun stableHash(s: String): Long {
    var h = -0x340d631b7bdddcdbL // FNV offset basis
    for (c in s) {
        h = (h xor c.code.toLong()) * 0x100000001b3L
    }
    return h
}

fun albumIdOf(album: String, artist: String): Long = stableHash(album.lowercase() + "|" + artist.lowercase())
fun artistIdOf(artist: String): Long = stableHash(artist.lowercase())

data class Album(val id: Long, val title: String, val artist: String, val tracks: List<Track>) {
    val cover: Track get() = tracks.first()
    val dateAdded: Long get() = tracks.maxOf { it.dateAdded }
}

data class Artist(val id: Long, val name: String, val tracks: List<Track>) {
    val cover: Track get() = tracks.first()
}

data class Folder(val path: String, val tracks: List<Track>) {
    val id: Long = stableHash(path.lowercase())
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { "Storage" }
    val parent: String get() = path.trimEnd('/').substringBeforeLast('/', "")
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
    val folders: List<Folder> = emptyList(),
    val isDemo: Boolean = false,
    val hasPermission: Boolean = false,
    val loaded: Boolean = false,
) {
    val trackById: Map<Long, Track> by lazy { tracks.associateBy { it.id } }
    val albumById: Map<Long, Album> by lazy { albums.associateBy { it.id } }
    val artistById: Map<Long, Artist> by lazy { artists.associateBy { it.id } }
    val folderById: Map<Long, Folder> by lazy { folders.associateBy { it.id } }
    val recentlyAddedTracks: List<Track> by lazy { tracks.sortedByDescending { it.dateAdded } }
    val recentlyAddedAlbums: List<Album> by lazy { albums.sortedByDescending { it.dateAdded } }

    companion object {
        fun build(tracks: List<Track>, isDemo: Boolean, hasPermission: Boolean): Library {
            val albums = tracks.groupBy { it.albumId }.map { (id, list) ->
                Album(
                    id, list.first().album, list.first().albumArtist.ifBlank { list.first().artist },
                    // Album order: disc, track number (unnumbered last), then title.
                    list.sortedWith(compareBy<Track>({ it.discNo }, { if (it.trackNo > 0) it.trackNo else Int.MAX_VALUE }).thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
                )
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            val artists = tracks.groupBy { it.artistId }.map { (id, list) ->
                Artist(id, list.first().artist, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val folders = tracks.groupBy { it.folder }.map { (path, list) ->
                Folder(path, list.sortedWith(compareBy<Track>({ it.discNo }, { it.trackNo }).thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }))
            }.sortedBy { it.path.lowercase() }
            return Library(tracks, albums, artists, folders, isDemo, hasPermission, loaded = true)
        }
    }
}
