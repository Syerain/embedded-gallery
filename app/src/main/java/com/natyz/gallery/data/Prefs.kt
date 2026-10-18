package com.natyz.gallery.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "natyz_prefs")

/**
 * User-local state: renamed titles, favorites (gallery & item level),
 * custom item orders, home view mode. All persisted via DataStore.
 */
class Prefs(private val context: Context) {

    private object K {
        val TITLES = stringPreferencesKey("titles_json")
        val FAV_GALLERIES = stringSetPreferencesKey("fav_galleries")
        val FAV_ITEMS = stringSetPreferencesKey("fav_items")
        val ORDERS = stringPreferencesKey("orders_json")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val ANIM_SPEED = floatPreferencesKey("anim_speed")
        val SYS_ANIM = booleanPreferencesKey("sys_anim")
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val m = HashMap<String, String>()
        for (k in keys()) m[k] = getString(k)
        return m
    }

    private fun JSONObject.toOrderMap(): Map<String, List<String>> {
        val m = HashMap<String, List<String>>()
        for (k in keys()) {
            val a = getJSONArray(k)
            m[k] = (0 until a.length()).map { a.getString(it) }
        }
        return m
    }

    val titles: Flow<Map<String, String>> = context.dataStore.data.map { p ->
        p[K.TITLES]?.let { runCatching { JSONObject(it).toStringMap() }.getOrNull() } ?: emptyMap()
    }

    val favGalleries: Flow<Set<String>> = context.dataStore.data.map { it[K.FAV_GALLERIES] ?: emptySet() }

    val favItems: Flow<Set<String>> = context.dataStore.data.map { it[K.FAV_ITEMS] ?: emptySet() }

    val orders: Flow<Map<String, List<String>>> = context.dataStore.data.map { p ->
        p[K.ORDERS]?.let { runCatching { JSONObject(it).toOrderMap() }.getOrNull() } ?: emptyMap()
    }

    val viewMode: Flow<String> = context.dataStore.data.map { it[K.VIEW_MODE] ?: "waterfall" }

    val animSpeed: Flow<Float> = context.dataStore.data.map { it[K.ANIM_SPEED] ?: 1f }

    val sysAnim: Flow<Boolean> = context.dataStore.data.map { it[K.SYS_ANIM] ?: true }

    suspend fun setAnimSpeed(v: Float) {
        context.dataStore.edit { it[K.ANIM_SPEED] = v }
    }

    suspend fun setSysAnim(v: Boolean) {
        context.dataStore.edit { it[K.SYS_ANIM] = v }
    }

    suspend fun setTitle(gid: String, title: String) {
        context.dataStore.edit { p ->
            val o = p[K.TITLES]?.let { JSONObject(it) } ?: JSONObject()
            o.put(gid, title)
            p[K.TITLES] = o.toString()
        }
    }

    suspend fun toggleGalleryFav(gid: String) {
        context.dataStore.edit { p ->
            val s = (p[K.FAV_GALLERIES] ?: emptySet()).toMutableSet()
            if (!s.add(gid)) s.remove(gid)
            p[K.FAV_GALLERIES] = s
        }
    }

    suspend fun toggleItemFav(gid: String, itemId: String) {
        context.dataStore.edit { p ->
            val key = "$gid/$itemId"
            val s = (p[K.FAV_ITEMS] ?: emptySet()).toMutableSet()
            if (!s.add(key)) s.remove(key)
            p[K.FAV_ITEMS] = s
        }
    }

    suspend fun setOrder(gid: String, order: List<String>) {
        context.dataStore.edit { p ->
            val o = p[K.ORDERS]?.let { JSONObject(it) } ?: JSONObject()
            o.put(gid, JSONArray(order))
            p[K.ORDERS] = o.toString()
        }
    }

    suspend fun resetOrder(gid: String) {
        context.dataStore.edit { p ->
            val o = p[K.ORDERS]?.let { JSONObject(it) } ?: JSONObject()
            o.remove(gid)
            p[K.ORDERS] = o.toString()
        }
    }

    suspend fun setViewMode(mode: String) {
        context.dataStore.edit { it[K.VIEW_MODE] = mode }
    }

    /** Export everything user-modifiable as one JSON document. */
    suspend fun exportJson(): String {
        val p = context.dataStore.data.first()
        val root = JSONObject()
        root.put("version", 1)
        root.put("titles", p[K.TITLES] ?: "{}")
        root.put("favGalleries", JSONArray((p[K.FAV_GALLERIES] ?: emptySet()).toList()))
        root.put("favItems", JSONArray((p[K.FAV_ITEMS] ?: emptySet()).toList()))
        root.put("orders", p[K.ORDERS] ?: "{}")
        return root.toString(2)
    }

    suspend fun importJson(text: String): Boolean {
        return try {
            val root = JSONObject(text)
            context.dataStore.edit { p ->
                if (root.has("titles")) p[K.TITLES] = root.getJSONObject("titles").toString()
                if (root.has("favGalleries")) {
                    val a = root.getJSONArray("favGalleries")
                    p[K.FAV_GALLERIES] = (0 until a.length()).map { a.getString(it) }.toSet()
                }
                if (root.has("favItems")) {
                    val a = root.getJSONArray("favItems")
                    p[K.FAV_ITEMS] = (0 until a.length()).map { a.getString(it) }.toSet()
                }
                if (root.has("orders")) p[K.ORDERS] = root.getJSONObject("orders").toString()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
