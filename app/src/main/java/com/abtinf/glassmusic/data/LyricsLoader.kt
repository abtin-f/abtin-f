package com.abtinf.glassmusic.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * Finds lyrics for a local song, in this order: sidecar `.lrc`/`.txt` next to the file, then the lyrics
 * embedded in the tags (ID3 USLT/SYLT for MP3, LYRICS/UNSYNCEDLYRICS Vorbis comment for FLAC, ©lyr for M4A).
 */
object LyricsLoader {
    private val cache = HashMap<Long, List<LyricLine>>()

    suspend fun load(context: Context, track: Track): List<LyricLine> {
        if (track.uri == null) return emptyList()
        cache[track.id]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            runCatching { sidecar(track) }.getOrNull()
                ?: runCatching { embedded(context, track) }.getOrNull()
                ?: emptyList()
        }
        if (result.isNotEmpty()) cache[track.id] = result
        return result
    }

    fun forget(trackId: Long) { cache.remove(trackId) }

    private fun sidecar(track: Track): List<LyricLine>? {
        val path = track.path ?: return null
        val base = path.substringBeforeLast('.')
        for (ext in listOf("lrc", "LRC", "txt")) {
            val f = File("$base.$ext")
            if (f.isFile && f.canRead()) {
                val text = readDecoded(f.readBytes())
                val parsed = LrcParser.parse(text, track.durationMs)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return null
    }

    private fun readDecoded(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('�' in utf8) String(bytes, Charset.forName("windows-1256")) else utf8
    }

    private fun embedded(context: Context, track: Track): List<LyricLine>? {
        val pfd = context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r") ?: return null
        pfd.use {
            java.io.FileInputStream(it.fileDescriptor).channel.use { ch ->
                val head = ByteBuffer.allocate(10)
                ch.read(head, 0)
                val h = head.array()
                val text: String? = when {
                    h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte() -> id3(ch)
                    h[0] == 'f'.code.toByte() && h[1] == 'L'.code.toByte() && h[2] == 'a'.code.toByte() -> flac(ch)
                    h[4] == 'f'.code.toByte() && h[5] == 't'.code.toByte() && h[6] == 'y'.code.toByte() -> m4a(ch)
                    else -> null
                }
                if (text.isNullOrBlank()) return null
                val parsed = LrcParser.parse(text, track.durationMs)
                return parsed.ifEmpty { null }
            }
        }
    }

    private fun readAt(ch: FileChannel, pos: Long, len: Int): ByteArray {
        val b = ByteBuffer.allocate(len)
        var p = pos
        while (b.hasRemaining()) { val n = ch.read(b, p); if (n <= 0) break; p += n }
        return b.array().copyOf(b.position())
    }

    // ---- ID3v2 -----------------------------------------------------------------------------
    private fun synchsafe(b: ByteArray, o: Int) = (b[o].toInt() shl 21) or (b[o + 1].toInt() shl 14) or (b[o + 2].toInt() shl 7) or b[o + 3].toInt()
    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)

    private fun id3(ch: FileChannel): String? {
        val hdr = readAt(ch, 0, 10)
        val ver = hdr[3].toInt()
        val size = synchsafe(hdr, 6).coerceIn(0, 24_000_000)
        val tag = readAt(ch, 10, size)
        var o = 0
        if (hdr[5].toInt() and 0x40 != 0 && tag.size >= 4) { // extended header
            o = if (ver == 4) synchsafe(tag, 0) else be32(tag, 0) + 4
        }
        var uslt: String? = null
        var sylt: String? = null
        val idLen = if (ver == 2) 3 else 4
        val hdrLen = if (ver == 2) 6 else 10
        while (o + hdrLen <= tag.size) {
            if (tag[o].toInt() == 0) break
            val id = String(tag, o, idLen, Charsets.ISO_8859_1)
            val fsize = when (ver) {
                2 -> ((tag[o + 3].toInt() and 255) shl 16) or ((tag[o + 4].toInt() and 255) shl 8) or (tag[o + 5].toInt() and 255)
                4 -> synchsafe(tag, o + 4)
                else -> be32(tag, o + 4)
            }
            val start = o + hdrLen
            if (fsize <= 0 || start + fsize > tag.size) break
            val body = tag.copyOfRange(start, start + fsize)
            if ((id == "USLT" || id == "ULT") && uslt == null) uslt = usltText(body)
            if (id == "SYLT" && sylt == null) sylt = syltText(body)
            o = start + fsize
        }
        return sylt ?: uslt
    }

    private fun charsetOf(enc: Int): Charset = when (enc) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    /** Index just past the string terminator at/after [from] for the given encoding. */
    private fun terminatorEnd(b: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < b.size) { if (b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i + 2; i += 2 }
            return b.size
        }
        var i = from
        while (i < b.size) { if (b[i].toInt() == 0) return i + 1; i++ }
        return b.size
    }

    private fun decode(b: ByteArray, from: Int, to: Int, enc: Int): String {
        val len = (to - from).coerceAtLeast(0)
        if (enc == 0 && len > 0) {
            var high = 0
            for (i in from until from + len) if (b[i].toInt() < 0) high++
            if (high > 0) {
                // Many taggers write UTF-8 under the ISO-8859-1 flag; real Latin-1 with accents is almost never valid UTF-8.
                val utf8 = String(b, from, len, Charsets.UTF_8)
                if ('\uFFFD' !in utf8) return utf8.trimEnd('\u0000')
                // Persian/Arabic tags are often Windows-1256 under the same flag: mostly "high" bytes cannot be Western text.
                if (high * 10 > len * 4) return String(b, from, len, Charset.forName("windows-1256")).trimEnd('\u0000')
            }
        }
        return String(b, from, len, charsetOf(enc)).trimEnd('\u0000')
    }

    private fun usltText(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        val textStart = terminatorEnd(b, 4, enc)
        return decode(b, textStart, b.size, enc).replace("\r\n", "\n").replace('\r', '\n').ifBlank { null }
    }

    private fun syltText(b: ByteArray): String? {
        if (b.size < 7) return null
        val enc = b[0].toInt()
        val format = b[4].toInt()
        if (format != 2) return null // only millisecond timestamps
        var o = terminatorEnd(b, 6, enc)
        val sb = StringBuilder()
        while (o < b.size) {
            val end = terminatorEnd(b, o, enc)
            val textEnd = end - (if (enc == 1 || enc == 2) 2 else 1)
            if (end + 4 > b.size) break
            val t = be32(b, end).toLong() and 0xFFFFFFFFL
            val line = decode(b, o, textEnd.coerceAtLeast(o), enc).trim('\n', '\r')
            if (line.isNotBlank()) sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d.%02d]%s\n", t / 60000, (t / 1000) % 60, (t % 1000) / 10, line))
            o = end + 4
        }
        return sb.toString().ifBlank { null }
    }

    // ---- FLAC ------------------------------------------------------------------------------
    private fun flac(ch: FileChannel): String? {
        var pos = 4L
        while (true) {
            val h = readAt(ch, pos, 4)
            if (h.size < 4) return null
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toInt() and 255) shl 16) or ((h[2].toInt() and 255) shl 8) or (h[3].toInt() and 255)
            if (type == 4) {
                val b = readAt(ch, pos + 4, len)
                fun le32(o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8) or ((b[o + 2].toInt() and 255) shl 16) or ((b[o + 3].toInt() and 255) shl 24)
                var o = 4 + le32(0)
                val n = le32(o); o += 4
                repeat(n) {
                    if (o + 4 > b.size) return null
                    val l = le32(o); o += 4
                    if (l < 0 || o + l > b.size) return null
                    val kv = String(b, o, l, Charsets.UTF_8); o += l
                    val key = kv.substringBefore('=').uppercase()
                    if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") return kv.substringAfter('=')
                }
                return null
            }
            pos += 4 + len
            if (last) return null
        }
    }

    // ---- MP4 / M4A -------------------------------------------------------------------------
    private fun m4a(ch: FileChannel): String? = walkAtoms(ch, 0, ch.size(), 0)

    private fun walkAtoms(ch: FileChannel, start: Long, end: Long, depth: Int): String? {
        var pos = start
        while (pos + 8 <= end) {
            val h = readAt(ch, pos, 8)
            if (h.size < 8) return null
            var size = be32(h, 0).toLong() and 0xFFFFFFFFL
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var header = 8L
            if (size == 1L) { val e = readAt(ch, pos + 8, 8); size = java.nio.ByteBuffer.wrap(e).long; header = 16 }
            if (size == 0L) size = end - pos
            if (size < header) return null
            val bodyStart = pos + header
            val bodyEnd = (pos + size).coerceAtMost(end)
            when (type) {
                "moov", "udta", "ilst" -> walkAtoms(ch, bodyStart, bodyEnd, depth + 1)?.let { return it }
                "meta" -> walkAtoms(ch, bodyStart + 4, bodyEnd, depth + 1)?.let { return it }
                "©lyr" -> {
                    val inner = readAt(ch, bodyStart, (bodyEnd - bodyStart).toInt().coerceAtMost(2_000_000))
                    if (inner.size > 16) return String(inner, 16, inner.size - 16, Charsets.UTF_8)
                }
            }
            pos += size
        }
        return null
    }
}
