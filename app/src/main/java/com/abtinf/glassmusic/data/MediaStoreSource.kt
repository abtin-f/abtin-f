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
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
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
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist"
                    val album = c.getString(iAlbum)?.takeUnless { it.isBlank() } ?: "Unknown Album"
                    out += Track(
                        id = id,
                        title = c.getString(iTitle) ?: "Untitled",
                        artist = artist,
                        album = album,
                        durationMs = c.getLong(iDur),
                        uri = uri,
                        artUri = uri,
                        dateAdded = c.getLong(iAdded),
                    )
                }
            }
        }
        return out
    }
}
