package com.faisal.tv.data.local

import android.content.Context
import android.content.SharedPreferences

class PrefsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFavorite(channelId: Int): Boolean {
        val favorites = getFavoriteIds()
        return favorites.contains(channelId.toString())
    }

    fun toggleFavorite(channelId: Int): Boolean {
        val idStr = channelId.toString()
        val current = getFavoriteIds().toMutableSet()
        val isNowFav = if (current.contains(idStr)) {
            current.remove(idStr)
            false
        } else {
            current.add(idStr)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return isNowFav
    }

    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun addRecent(channelId: Int) {
        val idStr = channelId.toString()
        val recents = getRecentIds().toMutableList()
        recents.remove(idStr)
        recents.add(0, idStr)
        val trimmed = if (recents.size > MAX_RECENTS) recents.subList(0, MAX_RECENTS) else recents
        prefs.edit().putString(KEY_RECENTS, trimmed.joinToString(",")).apply()
    }

    fun getRecentIds(): List<String> {
        val raw = prefs.getString(KEY_RECENTS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    companion object {
        private const val PREFS_NAME = "streamhome_tv_prefs"
        private const val KEY_FAVORITES = "key_favorite_channel_ids"
        private const val KEY_RECENTS = "key_recent_channel_ids"
        const val MAX_RECENTS = 10
    }
}
