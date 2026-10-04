package com.abtinf.glassmusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.data.AudioArtFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val autoMix: Boolean = true,
    val isMixing: Boolean = false,
    val singMode: Boolean = false,
    /** When the queue runs out, keep playing shuffled songs from the library. */
    val endless: Boolean = false,
    /** SystemClock.elapsedRealtime() at which playback pauses (sleep timer); 0 = off. */
    val sleepEndsAt: Long = 0L,
    val sleepAtTrackEnd: Boolean = false,
    /** True once the user has started playback; the mini-player stays hidden until then. */
    val started: Boolean = false,
) {
    val current: Track? get() = queue.getOrNull(index)
    val durationMs: Long get() = current?.durationMs ?: 0L
}

/**
 * Playback layer. Queue logic (shuffle / repeat / AutoMix) lives here; Media3 ExoPlayer plays one
 * item at a time. Demo tracks without a file are played by a simulated clock.
 * Must be used from the main thread.
 */
class PlayerController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTrackStarted: (Track) -> Unit,
    initialAutoMix: Boolean,
) {
    private val _state = MutableStateFlow(PlayerState(autoMix = initialAutoMix))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var exoInitialized = false

    // ---- audio output routing (headphones / bluetooth / phone speaker) -----------------------
    data class OutputDevice(val id: Int, val name: String, val isPhone: Boolean)

    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    private val _outputs = MutableStateFlow<List<OutputDevice>>(emptyList())
    val outputs: StateFlow<List<OutputDevice>> = _outputs.asStateFlow()
    private val _selectedOutput = MutableStateFlow<Int?>(null)
    val selectedOutput: StateFlow<Int?> = _selectedOutput.asStateFlow()
    private var preferredDevice: android.media.AudioDeviceInfo? = null
    private var userPickedPhone = false

    private fun isOutputType(t: Int) = when (t) {
        android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET, android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        android.media.AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> false
    }

    private fun applyPreferred(d: android.media.AudioDeviceInfo?) {
        preferredDevice = d
        _selectedOutput.value = d?.id
        if (exoInitialized) exo.setPreferredAudioDevice(d)
    }

    private fun refreshOutputs(added: Array<out android.media.AudioDeviceInfo>? = null) {
        val devs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).filter { isOutputType(it.type) }
        _outputs.value = devs.map {
            val phone = it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            OutputDevice(it.id, if (phone) "This phone" else it.productName?.toString()?.ifBlank { null } ?: "Headphones", phone)
        }.sortedByDescending { it.isPhone }
        val pref = preferredDevice
        if (pref != null && devs.none { it.id == pref.id }) applyPreferred(null) // the device went away: back to default routing
        // A newly connected headset/bluetooth device takes over, like the system's own player would.
        val fresh = added?.firstOrNull { isOutputType(it.type) && it.type != android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (fresh != null && !userPickedPhone) applyPreferred(devs.firstOrNull { it.id == fresh.id })
        if (_selectedOutput.value == null) _selectedOutput.value = devs.firstOrNull { it.type != android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id
            ?: devs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id
    }

    fun selectOutput(id: Int) {
        val dev = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id } ?: return
        userPickedPhone = dev.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        applyPreferred(dev)
    }

    private val exo: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { p ->
                exoInitialized = true
                p.setPreferredAudioDevice(preferredDevice)
                p.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (currentIsReal() && p.playbackState != Player.STATE_ENDED) {
                            _state.update { it.copy(isPlaying = playWhenReady) }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) errorStreak = 0
                        if (playbackState == Player.STATE_ENDED && currentIsReal()) onEnded()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (!currentIsReal()) return
                        // A broken file skips to the next song, but a whole queue of broken files must not spin forever.
                        errorStreak++
                        if (errorStreak >= 4) { errorStreak = 0; pause() } else next()
                    }
                })
            }
    }

    /** Player handed to the MediaSession: exposes next/previous so system controls route to the queue. */
    val sessionPlayer: Player by lazy {
        object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .addAll(
                        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    ).build()

            override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true
            override fun seekToNext() { android.util.Log.i("GlassPlayer", "session seekToNext"); next() }
            override fun seekToPrevious() { android.util.Log.i("GlassPlayer", "session seekToPrevious"); previous() }
            override fun seekToNextMediaItem() { android.util.Log.i("GlassPlayer", "session seekToNextMediaItem"); next() }
            override fun seekToPreviousMediaItem() { android.util.Log.i("GlassPlayer", "session seekToPreviousMediaItem"); previous() }
        }
    }

    private var originalQueue: List<Track> = emptyList()
    private var simPos = 0L
    private var lastTick = SystemClock.elapsedRealtime()
    private var mixedIn = false
    private var lastRecorded: Long? = null
    private var controllerFuture: Any? = null

    /** The current real track has not been handed to ExoPlayer yet (nothing played since the app started). */
    private var needsLoad = false
    private var errorStreak = 0
    private val artCache = LruCache<Long, ByteArray>(8)
    private val noArt = java.util.Collections.synchronizedSet(HashSet<Long>())

    init {
        runCatching {
            refreshOutputs()
            audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>) { refreshOutputs(addedDevices) }
                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>) {
                    if (removedDevices.any { it.id == preferredDevice?.id }) userPickedPhone = false
                    refreshOutputs()
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        scope.launch {
            while (true) {
                delay(250)
                tick()
            }
        }
    }

    // ---- public API -------------------------------------------------------------------------

    fun setInitialQueue(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        originalQueue = tracks
        _state.update { it.copy(queue = tracks, index = start.coerceIn(0, tracks.lastIndex), shuffle = false, isPlaying = false, positionMs = 0) }
        loadCurrent(autoPlay = false)
    }

    fun playQueue(tracks: List<Track>, start: Int, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val safeStart = start.coerceIn(0, tracks.lastIndex)
        val doShuffle = shuffle ?: _state.value.shuffle
        originalQueue = tracks
        val queue = if (doShuffle) {
            listOf(tracks[safeStart]) + tracks.filterIndexed { i, _ -> i != safeStart }.shuffled()
        } else tracks
        _state.update { it.copy(queue = queue, index = if (doShuffle) 0 else safeStart, shuffle = doShuffle) }
        goTo(_state.value.index, autoPlay = true)
    }

    fun toggle() = if (_state.value.isPlaying) pause() else play()

    fun play() {
        val t = _state.value.current ?: return
        ensureService()
        if (t.uri != null) {
            ensureLoaded()
            if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
            if (exo.playbackState == Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
        } else {
            if (simPos >= t.durationMs) simPos = 0
            lastTick = SystemClock.elapsedRealtime()
        }
        _state.update { it.copy(isPlaying = true, started = true) }
    }

    fun pause() {
        val t = _state.value.current ?: return
        if (t.uri != null && exoInitialized) exo.pause()
        _state.update { it.copy(isPlaying = false, isMixing = false) }
    }

    fun next() {
        android.util.Log.i("GlassPlayer", "next() queue=${_state.value.queue.size} index=${_state.value.index}")
        val s = _state.value
        if (s.queue.isEmpty()) return
        goTo(nextIndex(), autoPlay = true)
    }

    fun previous() {
        android.util.Log.i("GlassPlayer", "previous() queue=${_state.value.queue.size} index=${_state.value.index}")
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (s.positionMs > 3_000) {
            seekTo(0)
        } else {
            val prev = when {
                s.index > 0 -> s.index - 1
                s.repeat == RepeatMode.ALL -> s.queue.lastIndex
                else -> 0
            }
            goTo(prev, autoPlay = s.isPlaying)
        }
    }

    fun skipTo(index: Int) {
        if (index in _state.value.queue.indices) goTo(index, autoPlay = true)
    }

    fun seekTo(ms: Long) {
        val t = _state.value.current ?: return
        val pos = ms.coerceIn(0, t.durationMs)
        if (t.uri != null) { ensureLoaded(); exo.seekTo(pos) }
        simPos = pos
        lastTick = SystemClock.elapsedRealtime()
        _state.update { it.copy(positionMs = pos) }
    }

    fun playNext(track: Track) {
        _state.update {
            if (it.queue.isEmpty()) it.copy(queue = listOf(track), index = 0)
            else it.copy(queue = it.queue.toMutableList().apply { add(it.index + 1, track) })
        }
        if (_state.value.queue.size == 1) loadCurrent(false)
    }

    fun addToQueue(track: Track) {
        _state.update {
            if (it.queue.isEmpty()) it.copy(queue = listOf(track), index = 0) else it.copy(queue = it.queue + track)
        }
        if (_state.value.queue.size == 1) loadCurrent(false)
    }

    fun removeFromQueue(i: Int) {
        val s = _state.value
        if (s.queue.size <= 1 || i !in s.queue.indices) return
        val newQueue = s.queue.toMutableList().apply { removeAt(i) }
        when {
            i < s.index -> _state.update { it.copy(queue = newQueue, index = it.index - 1) }
            i > s.index -> _state.update { it.copy(queue = newQueue) }
            else -> {
                _state.update { it.copy(queue = newQueue, index = i.coerceAtMost(newQueue.lastIndex)) }
                loadCurrent(s.isPlaying)
            }
        }
    }

    fun toggleShuffle() {
        val s = _state.value
        val cur = s.current ?: run { _state.update { it.copy(shuffle = !it.shuffle) }; return }
        if (!s.shuffle) {
            val rest = s.queue.filterIndexed { i, _ -> i != s.index }.shuffled()
            _state.update { it.copy(queue = listOf(cur) + rest, index = 0, shuffle = true) }
        } else {
            val base = originalQueue.ifEmpty { s.queue }
            val idx = base.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
            _state.update { it.copy(queue = base, index = idx, shuffle = false) }
        }
    }

    fun cycleRepeat() = _state.update {
        it.copy(repeat = when (it.repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        })
    }

    fun setAutoMix(enabled: Boolean) = _state.update { it.copy(autoMix = enabled, isMixing = if (enabled) it.isMixing else false) }

    fun toggleEndless() = _state.update { it.copy(endless = !it.endless) }

    /** [minutes] <= 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) = _state.update {
        it.copy(sleepEndsAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else 0L, sleepAtTrackEnd = false)
    }

    fun sleepAtEndOfTrack() = _state.update { it.copy(sleepAtTrackEnd = true, sleepEndsAt = 0L) }

    fun toggleSingMode() = _state.update { it.copy(singMode = !it.singMode) }

    // ---- internals --------------------------------------------------------------------------

    private fun currentIsReal() = _state.value.current?.uri != null

    private fun nextIndex(): Int {
        var s = _state.value
        if (s.index >= s.queue.lastIndex && s.endless && originalQueue.isNotEmpty()) {
            val more = originalQueue.shuffled().filter { it.id != s.current?.id }
            _state.update { it.copy(queue = it.queue + more) }
            s = _state.value
        }
        return if (s.index < s.queue.lastIndex) s.index + 1 else 0
    }

    private fun hasNext(): Boolean {
        val s = _state.value
        return s.queue.size > 1 && (s.index < s.queue.lastIndex || s.repeat == RepeatMode.ALL || s.endless)
    }

    private fun goTo(index: Int, autoPlay: Boolean, mixed: Boolean = false) {
        mixedIn = mixed
        _state.update { it.copy(index = index, positionMs = 0, isMixing = mixed) }
        loadCurrent(autoPlay)
    }

    private fun loadCurrent(autoPlay: Boolean) {
        val t = _state.value.current ?: return
        simPos = 0
        lastTick = SystemClock.elapsedRealtime()
        if (t.uri != null) {
            if (!autoPlay && !exoInitialized) {
                needsLoad = true // nothing is playing yet: don't spin up ExoPlayer (and decode art) during app start
            } else {
                needsLoad = false
                exo.setMediaItem(mediaItemFor(t))
                exo.prepare()
                exo.playWhenReady = autoPlay
            }
        } else if (exoInitialized) {
            needsLoad = false
            exo.stop()
            exo.clearMediaItems()
        }
        _state.update { it.copy(isPlaying = autoPlay, positionMs = 0, started = it.started || autoPlay) }
        if (autoPlay) ensureService()
        prefetchArt()
    }

    /** Hands a deferred first track to ExoPlayer (paused). */
    private fun ensureLoaded() {
        if (!needsLoad) return
        val t = _state.value.current ?: return
        needsLoad = false
        if (t.uri == null) return
        exo.setMediaItem(mediaItemFor(t))
        exo.prepare()
        exo.playWhenReady = false
    }

    private fun mediaItemFor(t: Track): MediaItem {
        val meta = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).apply {
            artBytes(t)?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        }.build()
        return MediaItem.Builder().setMediaId(t.id.toString()).setUri(t.uri).setMediaMetadata(meta).build()
    }

    /** Small cover JPEG for the system notification / lock screen; cached because decoding it is not free. */
    private fun artBytes(t: Track): ByteArray? {
        val u = t.artUri ?: return null
        artCache.get(t.id)?.let { return it }
        if (t.id in noArt) return null
        val bytes = AudioArtFetcher.notificationArt(context, Uri.parse(u))
        if (bytes != null) artCache.put(t.id, bytes) else noArt.add(t.id)
        return bytes
    }

    /** Decode the covers of the neighbouring songs off the main thread so Next / Previous never stall on it. */
    private fun prefetchArt() {
        val s = _state.value
        val targets = listOfNotNull(s.queue.getOrNull(s.index + 1), s.queue.getOrNull(s.index - 1))
            .filter { it.uri != null && artCache.get(it.id) == null && it.id !in noArt }
        if (targets.isEmpty()) return
        scope.launch(Dispatchers.IO) { targets.forEach { artBytes(it) } }
    }

    private fun record(t: Track) {
        if (lastRecorded != t.id) {
            lastRecorded = t.id
            onTrackStarted(t)
        }
    }

    private fun onEnded() {
        val s = _state.value
        if (s.sleepAtTrackEnd && s.repeat != RepeatMode.ONE) {
            _state.update { it.copy(sleepAtTrackEnd = false) }
            if (hasNext()) goTo(nextIndex(), autoPlay = false) else goTo(0, autoPlay = false)
            return
        }
        when {
            s.repeat == RepeatMode.ONE -> { lastRecorded = null; seekTo(0); play() }
            hasNext() -> goTo(nextIndex(), autoPlay = true, mixed = mixedIn && s.autoMix)
            else -> {
                goTo(0, autoPlay = false)
                lastRecorded = null
            }
        }
    }

    private fun tick() {
        val s = _state.value
        if (s.sleepEndsAt != 0L && SystemClock.elapsedRealtime() >= s.sleepEndsAt) {
            _state.update { it.copy(sleepEndsAt = 0L) }
            pause()
            return
        }
        val t = s.current ?: return
        val now = SystemClock.elapsedRealtime()
        val real = t.uri != null
        val pos = if (real) {
            if (exoInitialized && !needsLoad) exo.currentPosition else s.positionMs
        } else {
            if (s.isPlaying) simPos += now - lastTick
            simPos
        }
        lastTick = now
        if (!real && pos >= t.durationMs && s.isPlaying) { onEnded(); return }

        val remaining = t.durationMs - pos
        val fadeOut = s.autoMix && !s.sleepAtTrackEnd && s.isPlaying && hasNext() && t.durationMs > 20_000 && remaining <= MIX_MS
        val fadeIn = s.autoMix && s.isPlaying && mixedIn && pos < FADE_IN_MS
        val factor = when {
            fadeOut -> 0.3f + 0.7f * (remaining / MIX_MS.toFloat()).coerceIn(0f, 1f)
            fadeIn -> 0.3f + 0.7f * (pos / FADE_IN_MS.toFloat()).coerceIn(0f, 1f)
            else -> 1f
        }
        if (real && exoInitialized) exo.volume = factor * (if (s.singMode) 0.4f else 1f)
        // A play only counts once the song has really been listened to (not when it is skipped after a second).
        if (s.isPlaying && lastRecorded != t.id && pos >= minOf(10_000L, t.durationMs / 2)) record(t)
        if (fadeOut && remaining <= HANDOFF_MS) { goTo(nextIndex(), autoPlay = true, mixed = true); return }
        if (!s.isPlaying && pos == s.positionMs) return
        _state.update { it.copy(positionMs = pos.coerceAtMost(t.durationMs), isMixing = fadeOut || fadeIn) }
    }

    private fun ensureService() {
        if (controllerFuture != null) return
        runCatching {
            controllerFuture = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, PlaybackService::class.java)),
            ).buildAsync()
        }
    }

    private companion object {
        const val MIX_MS = 6_000L
        const val FADE_IN_MS = 4_000L
        const val HANDOFF_MS = 1_200L
    }
}
