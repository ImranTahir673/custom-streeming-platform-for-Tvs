package com.faisal.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrefsStoreTest {

    private class MockSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = MockEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class MockEditor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { temp[key!!] = values?.toSet(); return this }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { temp[key!!] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { temp.remove(key); target.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { temp.clear(); target.clear(); return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { target.putAll(temp) }
        }
    }

    private lateinit var mockPrefs: MockSharedPreferences
    private lateinit var prefsStore: PrefsStore

    @Before
    fun setUp() {
        mockPrefs = MockSharedPreferences()
        val mockContext = object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = mockPrefs
        }
        prefsStore = PrefsStore(mockContext)
    }

    @Test
    fun testFavoriteToggle() {
        assertFalse("Channel 10 should not be favorite initially", prefsStore.isFavorite(10))

        val isFav = prefsStore.toggleFavorite(10)
        assertTrue("Channel 10 should now be favorite", isFav)
        assertTrue(prefsStore.isFavorite(10))

        val isFavAfterSecondToggle = prefsStore.toggleFavorite(10)
        assertFalse("Channel 10 should be unfavorited", isFavAfterSecondToggle)
        assertFalse(prefsStore.isFavorite(10))
    }

    @Test
    fun testRecentsListMax10AndOrder() {
        // Add 12 channels in sequence
        for (i in 1..12) {
            prefsStore.addRecent(i)
        }

        val recents = prefsStore.getRecentIds()
        assertEquals("Recents should be capped at 10", 10, recents.size)
        // Most recent should be at index 0 (12, 11, 10, ... 3)
        assertEquals("12", recents[0])
        assertEquals("11", recents[1])
        assertEquals("3", recents[9])
        assertFalse("Oldest items should be pushed out", recents.contains("1"))
        assertFalse(recents.contains("2"))
    }

    @Test
    fun testAddRecentDeduplication() {
        prefsStore.addRecent(5)
        prefsStore.addRecent(6)
        prefsStore.addRecent(5)

        val recents = prefsStore.getRecentIds()
        assertEquals("Should not contain duplicates", listOf("5", "6"), recents)
    }
}
