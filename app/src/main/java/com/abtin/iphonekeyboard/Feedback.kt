package com.abtin.iphonekeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class ClickSound { CHAR, DELETE, MODIFIER }

/**
 * Plays iOS-style key clicks. The three click samples (letter, delete, modifier — the same
 * split iOS uses) are synthesised once into WAV files and played through a SoundPool, so
 * there is no latency and the volume can be scaled freely from 0 to 100%.
 */
class Feedback(context: Context, private val prefs: Prefs) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Suppress("DEPRECATION")
    private val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = IntArray(3)
    private val loaded = BooleanArray(3)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            val i = ids.indexOf(sampleId)
            if (i >= 0 && status == 0) loaded[i] = true
        }
        // Sound is a nicety: never let it take the keyboard down.
        ClickSound.values().forEach { s ->
            runCatching {
                val f = File(app.cacheDir, "click_${s.name.lowercase()}_v2.wav")
                if (!f.exists() || f.length() <= 44L) f.writeBytes(wav(synth(s)))
                ids[s.ordinal] = pool.load(f.absolutePath, 1)
            }
        }
    }

    fun click(sound: ClickSound, volumePercent: Int = prefs.soundVolume, force: Boolean = false) {
        if (!force && !prefs.soundEnabled) return
        if (!force && prefs.respectSilent && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (!loaded[sound.ordinal] || volumePercent <= 0) return
        // Perceptual curve: the slider feels linear to the ear.
        val v = (volumePercent / 100f).pow(1.8f)
        pool.play(ids[sound.ordinal], v, v, 1, 0, 1f)
    }

    fun vibrate(strength: Int = prefs.vibrateStrength, force: Boolean = false) {
        if (!force && !prefs.vibrate) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator() || strength <= 0) return
        val duration = (6 + strength / 6).toLong()
        val amplitude = if (vib.hasAmplitudeControl()) max(1, (strength * 2.55f).toInt()).coerceAtMost(255)
        else VibrationEffect.DEFAULT_AMPLITUDE
        vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }

    fun onKeyDown(type: KeyType) {
        click(
            when (type) {
                KeyType.DELETE -> ClickSound.DELETE
                KeyType.CHAR, KeyType.ZWNJ -> ClickSound.CHAR
                else -> ClickSound.MODIFIER
            }
        )
        vibrate()
    }

    fun release() = pool.release()

    // ---- synthesis -------------------------------------------------------------------------

    private fun synth(s: ClickSound): ShortArray {
        // (tone Hz, body Hz, noise amount, length ms)
        val (tone, body, noiseAmt, lenMs) = when (s) {
            ClickSound.CHAR -> Quad(1750.0, 480.0, 0.55, 45)
            ClickSound.DELETE -> Quad(1350.0, 380.0, 0.50, 55)
            ClickSound.MODIFIER -> Quad(1150.0, 330.0, 0.40, 55)
        }
        val n = rate * lenMs / 1000
        val out = DoubleArray(n)
        val rnd = Random(42 + s.ordinal)
        var hp = 0.0
        var prev = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val attack = (t / 0.0004).coerceAtMost(1.0)
            // band-limited noise burst: the "tick" transient
            val white = rnd.nextDouble() * 2 - 1
            hp = 0.85 * (hp + white - prev); prev = white
            lp += 0.55 * (hp - lp)
            val noise = lp * exp(-t / 0.0022) * noiseAmt
            // the woody "tock" of the key
            val ring = 0.45 * sin(2 * PI * tone * t) * exp(-t / 0.0055)
            val thump = 0.35 * sin(2 * PI * body * t) * exp(-t / 0.0095)
            out[i] = attack * (noise + ring + thump)
        }
        val peak = out.maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
        return ShortArray(n) { (out[it] / peak * 0.9 * Short.MAX_VALUE).toInt().toShort() }
    }

    private companion object {
        const val rate = 44100
    }

    private data class Quad(val tone: Double, val body: Double, val noise: Double, val ms: Int)

    private fun wav(pcm: ShortArray): ByteArray {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }
        return ByteArrayOutputStream().apply { write(header.array()); write(data.array()) }.toByteArray()
    }
}
