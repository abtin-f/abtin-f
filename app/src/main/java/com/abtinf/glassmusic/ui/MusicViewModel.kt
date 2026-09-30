package com.abtinf.glassmusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abtinf.glassmusic.App
import com.abtinf.glassmusic.data.Album
import com.abtinf.glassmusic.data.Artist
import com.abtinf.glassmusic.data.Library
import com.abtinf.glassmusic.data.LrcParser
import com.abtinf.glassmusic.data.LyricLine
import com.abtinf.glassmusic.data.Playlist
import com.abtinf.glassmusic.data.PlaylistGenerator
import com.abtinf.glassmusic.data.Track
import com.abtinf.glassmusic.playback.PlayerState
import com.abtinf.glassmusic.ui.components.HomePick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class HomeState(
    val picks: List<HomePick> = emptyList(),
    val hotArtists: List<Artist> = emptyList(),
    val hotSongs: List<Track> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val recentlyAdded: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

enum class SearchFilter(val label: String) { All("All"), Songs("Songs"), Artists("Artists"), Albums("Albums"), Playlists("Playlists") }

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) { val isEmpty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() }

data class EditorState(
    val playlistId: String?,
    val name: String,
    val description: String,
    val tracks: List<Track>,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) { val subtitle: String get() = com.abtinf.glassmusic.data.formatTotal(tracks) }

/** Track context menu target; [playlistId] is set when opened from inside a playlist. */
data class TrackMenu(val track: Track, val playlistId: String? = null)

class MusicViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as App).container
    private val repo = container.repository
    private val store = container.userStore
    private val controller = container.player

    val library: StateFlow<Library> = repo.library
    val playerState: StateFlow<PlayerState> = controller.state

    /** Player state without the ticking position, so list screens don't recompose four times a second. */
    val playback: StateFlow<PlayerState> = controller.state
        .map { it.copy(positionMs = 0L) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState())
    val positionMs: StateFlow<Long> = controller.state
        .map { it.positionMs }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val favorites = store.favorites
    val playlists = store.playlists

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentLyrics: StateFlow<List<LyricLine>> = combine(
        controller.state.map { it.current }.distinctUntilChanged(),
        store.lyrics,
    ) { track, overrides -> track to overrides }.mapLatest { (track, overrides) ->
        when {
            track == null -> emptyList()
            track.lyrics.isNotEmpty() -> track.lyrics
            else -> overrides[track.id]?.let { LrcParser.parse(it, track.durationMs) }
                ?: com.abtinf.glassmusic.data.LyricsLoader.load(getApplication<Application>(), track)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val home: StateFlow<HomeState> = combine(library, store.recents, store.plays, favorites, playlists) { lib, recents, plays, favs, pls ->
        buildHome(lib, recents, plays, favs, pls)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---- search ---------------------------------------------------------------------------
    val query = MutableStateFlow("")
    val searchFilter = MutableStateFlow(SearchFilter.All)
    val searchResults: StateFlow<SearchResults> = combine(query, library, playlists) { q, lib, pls ->
        val s = q.trim().lowercase()
        if (s.isEmpty()) SearchResults() else SearchResults(
            songs = lib.tracks.filter { t ->
                t.title.lowercase().contains(s) || t.artist.lowercase().contains(s) ||
                    t.album.lowercase().contains(s) || t.lyrics.any { it.text.lowercase().contains(s) }
            },
            artists = lib.artists.filter { it.name.lowercase().contains(s) },
            albums = lib.albums.filter { it.title.lowercase().contains(s) || it.artist.lowercase().contains(s) },
            playlists = pls.filter { it.name.lowercase().contains(s) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    // ---- overlays -------------------------------------------------------------------------
    private val _menu = MutableStateFlow<TrackMenu?>(null)
    val menu = _menu.asStateFlow()
    private val _pickerTracks = MutableStateFlow<List<Track>?>(null)
    val pickerTracks = _pickerTracks.asStateFlow()
    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    // ---- playlist editor ------------------------------------------------------------------
    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor = _editor.asStateFlow()
    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()

    init {
        viewModelScope.launch {
            library.collect { lib ->
                if (!lib.loaded || lib.tracks.isEmpty()) return@collect
                val st = controller.state.value
                val stale = st.current?.let { cur -> lib.trackById[cur.id] == null } ?: true
                if (st.queue.isEmpty() || stale) {
                    val recent = store.recents.value.firstNotNullOfOrNull { lib.trackById[it] }
                    val start = recent?.let { r -> lib.tracks.indexOfFirst { it.id == r.id } }?.takeIf { it >= 0 } ?: 0
                    controller.setInitialQueue(lib.tracks, start)
                }
            }
        }
        viewModelScope.launch { repo.refresh() }
    }

    fun refreshLibrary() = viewModelScope.launch { repo.refresh() }

    private fun buildHome(lib: Library, recents: List<Long>, plays: Map<Long, Int>, favs: Set<Long>, pls: List<Playlist>): HomeState {
        if (lib.tracks.isEmpty()) return HomeState()
        val recent = recents.mapNotNull { lib.trackById[it] }.ifEmpty { lib.recentlyAddedTracks }.take(14)
        val top = lib.tracks.sortedByDescending { plays[it.id] ?: 0 }
        val topArtists = top.map { it.artist }.distinct().take(5)
        val picks = mutableListOf<HomePick>()
        picks += HomePick.Replay(top.take(50), topArtists)
        recent.map { it.albumId }.distinct().take(3).forEach { id -> lib.albumById[id]?.let { picks += HomePick.AlbumPick(it) } }
        topArtists.take(3).forEach { name ->
            lib.artists.firstOrNull { it.name == name }?.let { picks += HomePick.ArtistMix(it) }
        }
        val favTracks = favs.mapNotNull { lib.trackById[it] }
        if (favTracks.isNotEmpty()) picks += HomePick.Favorites(favTracks)
        pls.forEach { p -> picks += HomePick.UserPlaylist(p, p.trackIds.mapNotNull { lib.trackById[it] }) }
        val artistScore = lib.artists.associateWith { a -> a.tracks.sumOf { (plays[it.id] ?: 0) } }
        val hotArtists = lib.artists.sortedWith(compareByDescending<Artist> { artistScore[it] ?: 0 }.thenByDescending { it.tracks.size })
        return HomeState(picks, hotArtists.take(12), top.take(12), recent, lib.recentlyAddedAlbums.take(10), pls)
    }

    // ---- playback -------------------------------------------------------------------------
    fun play(tracks: List<Track>, index: Int) = controller.playQueue(tracks, index)
    fun shuffle(tracks: List<Track>) { if (tracks.isNotEmpty()) controller.playQueue(tracks, tracks.indices.random(), shuffle = true) }
    fun togglePlay() = controller.toggle()
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun seekFraction(f: Float) = controller.seekTo((f * controller.state.value.durationMs).toLong())
    fun seekMs(ms: Long) = controller.seekTo(ms)
    fun skipTo(i: Int) = controller.skipTo(i)
    fun removeFromQueue(i: Int) = controller.removeFromQueue(i)
    fun playNext(t: Track) = controller.playNext(t)
    fun addToQueue(t: Track) = controller.addToQueue(t)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()
    fun toggleSing() = controller.toggleSingMode()
    val outputs = controller.outputs
    val selectedOutput = controller.selectedOutput
    fun selectOutput(id: Int) = controller.selectOutput(id)
    fun toggleEndless() = controller.toggleEndless()
    fun setSleepTimer(minutes: Int) = controller.setSleepTimer(minutes)
    fun sleepAtEndOfTrack() = controller.sleepAtEndOfTrack()
    fun setAutoMix(on: Boolean) { controller.setAutoMix(on); store.setAutoMix(on) }
    fun toggleFavorite(t: Track) = store.toggleFavorite(t.id)

    fun importLyrics(track: Track, text: String) = store.setLyrics(track.id, text)

    // ---- playlists ------------------------------------------------------------------------
    fun createPlaylist(name: String, tracks: List<Track>): String {
        val id = UUID.randomUUID().toString()
        store.savePlaylist(Playlist(id, name.ifBlank { "New Playlist" }, tracks.map { it.id }))
        return id
    }
    fun addToPlaylist(playlistId: String, tracks: List<Track>) = store.addToPlaylist(playlistId, tracks.map { it.id })
    fun removeFromPlaylist(playlistId: String, track: Track) = store.removeFromPlaylist(playlistId, track.id)
    fun deletePlaylist(id: String) = store.deletePlaylist(id)

    fun resolve(p: Playlist): List<Track> = library.value.let { lib -> p.trackIds.mapNotNull { lib.trackById[it] } }

    // ---- overlays -------------------------------------------------------------------------
    fun showTrackMenu(t: Track, playlistId: String? = null) { _menu.value = TrackMenu(t, playlistId) }
    fun dismissMenu() { _menu.value = null }
    fun showPlaylistPicker(tracks: List<Track>) { _pickerTracks.value = tracks }
    fun dismissPicker() { _pickerTracks.value = null }
    fun setShowSettings(v: Boolean) { _showSettings.value = v }

    // ---- editor ---------------------------------------------------------------------------
    fun openEditor(existing: Playlist?) {
        undoStack.clear(); redoStack.clear()
        val lib = library.value
        _editor.value = if (existing != null) {
            EditorState(existing.id, existing.name, "", resolve(existing))
        } else {
            val g = PlaylistGenerator.generate(lib, PlaylistGenerator.defaultPrompt(lib))
            EditorState(null, g.name, g.description, g.tracks)
        }
    }

    fun closeEditor() { _editor.value = null; undoStack.clear(); redoStack.clear() }

    fun saveEditor(): Boolean {
        val e = _editor.value ?: return false
        store.savePlaylist(Playlist(e.playlistId ?: UUID.randomUUID().toString(), e.name.ifBlank { "New Playlist" }, e.tracks.map { it.id }))
        closeEditor()
        return true
    }

    private fun mutateEditor(snapshot: Boolean = true, block: (EditorState) -> EditorState) {
        val cur = _editor.value ?: return
        if (snapshot) { undoStack.addLast(cur); redoStack.clear() }
        _editor.value = block(cur).let { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    fun editorApplyPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val g = PlaylistGenerator.generate(library.value, prompt)
        mutateEditor { it.copy(name = g.name, description = g.description, tracks = g.tracks) }
    }

    fun editorBeginDrag() { _editor.value?.let { undoStack.addLast(it); redoStack.clear() } }

    fun editorMove(from: Int, to: Int) {
        mutateEditor(snapshot = false) { e ->
            if (from !in e.tracks.indices || to !in e.tracks.indices) e
            else e.copy(tracks = e.tracks.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun editorRemove(track: Track) = mutateEditor { e -> e.copy(tracks = e.tracks.filter { it.id != track.id }) }
    fun editorAdd(tracks: List<Track>) = mutateEditor { e -> e.copy(tracks = e.tracks + tracks.filter { t -> e.tracks.none { it.id == t.id } }) }
    fun editorRename(name: String) = mutateEditor { it.copy(name = name) }

    fun editorUndo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _editor.value?.let { redoStack.addLast(it) }
        _editor.value = prev.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
    }

    fun editorRedo() {
        val next = redoStack.removeLastOrNull() ?: return
        _editor.value?.let { undoStack.addLast(it) }
        _editor.value = next.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
    }
}
