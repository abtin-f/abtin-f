package com.abtinf.glassmusic.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coil model for the artwork embedded in a local audio file. */
data class AudioArt(val uri: String)

/** Requested edge in px, rounded up to a bucket so a small list thumbnail is never reused for the big player art. */
internal fun bucket(options: Options): Int {
    val px = (options.size.width as? coil.size.Dimension.Pixels)?.px ?: 1024
    return when {
        px <= 160 -> 160
        px <= 320 -> 320
        px <= 640 -> 640
        px <= 1024 -> 1024
        else -> 1600
    }
}

class AudioArtKeyer : Keyer<AudioArt> {
    override fun key(data: AudioArt, options: Options): String = "audioart:${data.uri}:${bucket(options)}"
}

class AudioArtFetcher(private val data: AudioArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val ctx = options.context
        val px = bucket(options)
        val uri = Uri.parse(data.uri)
        val bmp = embedded(ctx, uri, px) ?: runCatching {
            ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
        }.getOrNull() ?: return@withContext null
        DrawableResult(BitmapDrawable(ctx.resources, bmp), false, DataSource.DISK)
    }

    /** Decodes the picture embedded in the file at full quality (MediaStore thumbnails are heavily downscaled). */
    private fun embedded(ctx: android.content.Context, uri: Uri, target: Int): Bitmap? = runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, uri)
            val bytes = mmr.embeddedPicture ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            mmr.release()
        }
    }.getOrNull()

    companion object {
        /** Small JPEG of the embedded cover, for the system media notification / lock screen. */
        fun notificationArt(ctx: android.content.Context, uri: Uri): ByteArray? {
            val bmp = runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(ctx, uri)
                    val bytes = mmr.embeddedPicture ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 768 && bounds.outHeight / (sample * 2) >= 768) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                } finally { mmr.release() }
            }.getOrNull() ?: return null
            return java.io.ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
        }
    }

    class Factory : Fetcher.Factory<AudioArt> {
        override fun create(data: AudioArt, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, options)
    }
}
