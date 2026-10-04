package com.abtinf.glassmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Small SharedPreferences-backed store for everything the user owns: favorites, playlists, history. */
class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences("user_store", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(readLongs("fav").toSet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(readLongs("recent"))
    val recents: StateFlow<List<Long>> = _recents.asStateFlow()

    private val _plays = MutableStateFlow(readPlays())
    val plays: StateFlow<Map<Long, Int>> = _plays.asStateFlow()

    private val _playlists = MutableStateFlow(readPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _lyrics = MutableStateFlow(readLyrics())
    val lyrics: StateFlow<Map<Long, String>> = _lyrics.asStateFlow()

    private val _autoMix = MutableStateFlow(prefs.getBoolean("automix", true))
    val autoMix: StateFlow<Boolean> = _autoMix.asStateFlow()

    fun toggleFavorite(id: Long) {
        _favorites.update { if (id in it) it - id else it + id }
        prefs.edit().putString("fav", _favorites.value.joinToString(",")).apply()
    }

    fun recordPlay(id: Long) {
        _recents.update { (listOf(id) + it.filter { x -> x != id }).take(40) }
        _plays.update { it + (id to ((it[id] ?: 0) + 1)) }
        prefs.edit()
            .putString("recent", _recents.value.joinToString(","))
            .putString("plays", _plays.value.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    fun savePlaylist(playlist: Playlist) {
        val p = playlist.copy(trackIds = playlist.trackIds.distinct()) // lists key their rows by song id
        _playlists.update { list ->
            if (list.any { it.id == p.id }) list.map { if (it.id == p.id) p else it } else list + p
        }
        persistPlaylists()
    }

    fun deletePlaylist(id: String) {
        _playlists.update { l -> l.filter { it.id != id } }
        persistPlaylists()
    }

    fun addToPlaylist(playlistId: String, trackIds: List<Long>) {
        _playlists.update { list ->
            list.map { p ->
                if (p.id == playlistId) p.copy(trackIds = p.trackIds + trackIds.filter { it !in p.trackIds }) else p
            }
        }
        persistPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, trackId: Long) {
        _playlists.update { list ->
            list.map { p -> if (p.id == playlistId) p.copy(trackIds = p.trackIds - trackId) else p }
        }
        persistPlaylists()
    }

    fun setLyrics(trackId: Long, text: String) {
        _lyrics.update { it + (trackId to text) }
        val obj = JSONObject()
        _lyrics.value.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString("lyrics", obj.toString()).apply()
    }

    fun setAutoMix(enabled: Boolean) {
        _autoMix.value = enabled
        prefs.edit().putBoolean("automix", enabled).apply()
    }

    private fun persistPlaylists() {
        val arr = JSONArray()
        _playlists.value.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("created", p.createdAt)
                    .put("ids", JSONArray(p.trackIds)),
            )
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun readLongs(key: String): List<Long> =
        prefs.getString(key, "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }

    private fun readPlays(): Map<Long, Int> =
        prefs.getString("plays", "").orEmpty().split(",").mapNotNull {
            val p = it.split(":")
            val k = p.getOrNull(0)?.toLongOrNull()
            val v = p.getOrNull(1)?.toIntOrNull()
            if (k != null && v != null) k to v else null
        }.toMap()

    private fun readPlaylists(): List<Playlist> = runCatching {
        val arr = JSONArray(prefs.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) }.distinct(), o.optLong("created"))
        }
    }.getOrDefault(emptyList())

    private fun readLyrics(): Map<Long, String> = runCatching {
        val o = JSONObject(prefs.getString("lyrics", "{}"))
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.getString(k) } }.toMap()
    }.getOrDefault(emptyMap())
}
