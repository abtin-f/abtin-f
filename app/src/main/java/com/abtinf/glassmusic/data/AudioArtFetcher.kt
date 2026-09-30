package com.abtinf.glassmusic.data

import android.graphics.drawable.BitmapDrawable
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

class AudioArtKeyer : Keyer<AudioArt> {
    override fun key(data: AudioArt, options: Options): String = "audioart:${data.uri}"
}

class AudioArtFetcher(private val data: AudioArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val ctx = options.context
        val px = (options.size.width as? coil.size.Dimension.Pixels)?.px?.coerceIn(96, 800) ?: 512
        val bmp = runCatching {
            ctx.contentResolver.loadThumbnail(Uri.parse(data.uri), Size(px, px), null)
        }.getOrNull() ?: return@withContext null
        DrawableResult(BitmapDrawable(ctx.resources, bmp), false, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<AudioArt> {
        override fun create(data: AudioArt, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, options)
    }
}
