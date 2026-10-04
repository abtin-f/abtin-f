package com.abtinf.glassmusic.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MediaStoreSource {
    fun load(context: Context): List<Track> {
        val out = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.BITRATE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA,
        )
        // Not IS_MUSIC: downloaded FLAC/M4A files are often not flagged as music by the scanner.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND ${MediaStore.Audio.Media.IS_RECORDING} = 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 30000"
        runCatching {
            context.contentResolver.query(
                collection, projection, selection, null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iBitrate = c.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iAlbumArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                val iRel = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist"
                    val album = c.getString(iAlbum)?.takeUnless { it.isBlank() } ?: "Unknown Album"
                    val data = if (iData >= 0) c.getString(iData) else null
                    val folder = (if (iRel >= 0) c.getString(iRel) else null)
                        ?: data?.substringBeforeLast('/', "")?.removePrefix("/storage/emulated/0/")?.plus("/")
                        ?: "Music/"
                    out += Track(
                        id = id,
                        title = c.getString(iTitle) ?: "Untitled",
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(iDur),
                        uri = uri,
                        artUri = uri,
                        dateAdded = c.getLong(iAdded),
                        format = formatOf(c.getString(iMime), c.getString(iName)),
                        bitrateKbps = c.getInt(iBitrate) / 1000,
                        sizeBytes = c.getLong(iSize),
                        year = c.getInt(iYear),
                        trackNo = c.getInt(iTrack) % 1000,
                        discNo = (c.getInt(iTrack) / 1000).coerceAtLeast(1),
                        albumArtist = c.getString(iAlbumArtist)?.takeUnless { it == "<unknown>" } ?: artist,
                        folder = folder,
                        path = data,
                    )
                }
            }
        }
        return out
    }

    private fun formatOf(mime: String?, name: String?): String = when (mime?.lowercase()) {
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac", "audio/aacp" -> "M4A"
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/ogg", "application/ogg" -> "OGG"
        "audio/opus" -> "OPUS"
        "audio/x-wav", "audio/wav" -> "WAV"
        else -> name?.substringAfterLast('.', "")?.uppercase()?.ifBlank { null } ?: "AUDIO"
    }
}
