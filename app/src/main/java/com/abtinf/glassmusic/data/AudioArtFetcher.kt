package com.abtinf.glassmusic.data

import android.content.Context
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
import coil.size.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import java.util.concurrent.Executors

/**
 * Coil model for the artwork embedded in a local audio file. [key] identifies the picture (the album, so every
 * song of an album shares one decoded bitmap); [uri] is the audio file it is read from.
 */
data class AudioArt(val uri: String, val key: String = uri)

/** Requested edge in px, rounded up to a bucket so a small list thumbnail is never reused for the big player art. */
internal fun bucket(options: Options): Int {
    val px = (options.size.width as? Dimension.Pixels)?.px ?: 640
    return when {
        px <= 160 -> 160
        px <= 320 -> 320
        px <= 640 -> 640
        else -> 1024
    }
}

class AudioArtKeyer : Keyer<AudioArt> {
    override fun key(data: AudioArt, options: Options): String = "audioart:${data.key}:${bucket(options)}"
}

class AudioArtFetcher(private val data: AudioArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val ctx = options.context
        val px = bucket(options)
        val uri = Uri.parse(data.uri)
        val bmp = ArtLoader.load(flightKey = "${data.key}:$px", missKey = "${data.uri}:$px") { decode(ctx, uri, px) } ?: return null
        // Hardware bitmaps are uploaded to the GPU here, off the UI thread (no hitch when a row first draws them);
        // requests that need pixel access (palette extraction) get a software copy instead.
        return DrawableResult(BitmapDrawable(ctx.resources, bmp.forRequest(options.config == Bitmap.Config.HARDWARE)), false, DataSource.DISK)
    }

    private fun Bitmap.forRequest(allowHardware: Boolean): Bitmap = when {
        allowHardware && config != Bitmap.Config.HARDWARE -> copy(Bitmap.Config.HARDWARE, false) ?: this
        !allowHardware && config == Bitmap.Config.HARDWARE -> copy(Bitmap.Config.ARGB_8888, false) ?: this
        else -> this
    }

    class Factory : Fetcher.Factory<AudioArt> {
        override fun create(data: AudioArt, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(data, options)
    }

    companion object {
        /** Small thumbnails come from the system's (fast) thumbnailer; big ones decode the embedded picture in full quality. */
        private fun decode(ctx: Context, uri: Uri, px: Int): Bitmap? =
            if (px <= 320) thumbnail(ctx, uri, px) ?: embedded(ctx, uri, px)
            else embedded(ctx, uri, px) ?: thumbnail(ctx, uri, px)

        private fun thumbnail(ctx: Context, uri: Uri, px: Int): Bitmap? =
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) }.getOrNull()

        private fun embedded(ctx: Context, uri: Uri, target: Int): Bitmap? = runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(ctx, uri)
                val bytes = mmr.embeddedPicture ?: return@runCatching null
                decodeSampled(bytes, target)
            } finally {
                mmr.release()
            }
        }.getOrNull()

        private fun decodeSampled(bytes: ByteArray, target: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        /** Small JPEG of the embedded cover, for the system media notification / lock screen. */
        fun notificationArt(ctx: Context, uri: Uri): ByteArray? {
            val bmp = embedded(ctx, uri, 768) ?: return null
            return java.io.ByteArrayOutputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
        }
    }
}

/**
 * Runs cover decoding on a small dedicated pool (scrolling a long list must not start dozens of file reads at
 * once), shares one decode between simultaneous requests for the same picture, and remembers files that have no
 * picture so they are not looked up again every time a row scrolls into view.
 */
private object ArtLoader {
    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newFixedThreadPool(3) { r ->
            Thread(r, "art-loader").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
        }.asCoroutineDispatcher(),
    )
    private val inFlight = HashMap<String, Deferred<Bitmap?>>()
    private val missing = HashSet<String>()

    /**
     * [flightKey] names the picture (every song of an album shares it, so one decode serves them all); [missKey] names
     * the file, so a song without a picture is not asked again - without hiding the picture of a sibling that has one.
     */
    suspend fun load(flightKey: String, missKey: String, block: () -> Bitmap?): Bitmap? {
        for (attempt in 0 until 3) {
            var owner = false
            val job = synchronized(inFlight) {
                if (missKey in missing) return null
                inFlight[flightKey] ?: run {
                    owner = true
                    scope.async(start = CoroutineStart.LAZY) {
                        try {
                            block()
                        } finally {
                            synchronized(inFlight) { inFlight.remove(flightKey) }
                        }
                    }.also { inFlight[flightKey] = it }
                }
            }
            job.start()
            val bmp = job.await()
            if (bmp != null) return bmp
            if (owner) break
            // Joined a decode that was started for another song of the album and found nothing: try this song's own file.
        }
        synchronized(inFlight) { missing.add(missKey) }
        return null
    }
}
