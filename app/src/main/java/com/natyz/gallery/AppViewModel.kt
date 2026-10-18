package com.natyz.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.natyz.gallery.data.Gallery
import com.natyz.gallery.data.GalleryItem
import com.natyz.gallery.data.ManifestLoader
import com.natyz.gallery.data.Prefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val galleries: List<Gallery> = ManifestLoader.load(app)
    val prefs = Prefs(app)

    data class UiState(
        val titles: Map<String, String> = emptyMap(),
        val favGalleries: Set<String> = emptySet(),
        val favItems: Set<String> = emptySet(),
        val orders: Map<String, List<String>> = emptyMap(),
        val viewMode: String = "waterfall",
        val animSpeed: Float = 1f,
        val sysAnim: Boolean = true
    )

    val ui: StateFlow<UiState> = combine(
        combine(prefs.titles, prefs.favGalleries, prefs.favItems, prefs.orders) { t, fg, fi, o ->
            listOf(t, fg, fi, o)
        },
        combine(prefs.viewMode, prefs.animSpeed, prefs.sysAnim) { v, sp, sa ->
            Triple(v, sp, sa)
        }
    ) { a, b ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            titles = a[0] as Map<String, String>,
            favGalleries = a[1] as Set<String>,
            favItems = a[2] as Set<String>,
            orders = a[3] as Map<String, List<String>>,
            viewMode = b.first,
            animSpeed = b.second,
            sysAnim = b.third
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    fun gallery(gid: String): Gallery? = galleries.firstOrNull { it.id == gid }

    fun titleOf(g: Gallery, s: UiState): String = s.titles[g.id] ?: g.title

    /** Manifest order, overridden by the user's custom order when present. */
    fun effectiveItems(g: Gallery, s: UiState): List<GalleryItem> {
        val ord = s.orders[g.id] ?: return g.items
        val idSet = ord.toHashSet()
        val byId = g.items.associateBy { it.id }
        return ord.mapNotNull { byId[it] } + g.items.filter { it.id !in idSet }
    }

    fun isItemFav(gid: String, itemId: String, s: UiState) = s.favItems.contains("$gid/$itemId")
    fun isGalleryFav(gid: String, s: UiState) = s.favGalleries.contains(gid)

    fun toggleGalleryFav(gid: String) = viewModelScope.launch { prefs.toggleGalleryFav(gid) }
    fun toggleItemFav(gid: String, itemId: String) = viewModelScope.launch { prefs.toggleItemFav(gid, itemId) }
    fun setTitle(gid: String, title: String) = viewModelScope.launch { prefs.setTitle(gid, title) }
    fun setViewMode(mode: String) = viewModelScope.launch { prefs.setViewMode(mode) }
    fun setOrder(gid: String, order: List<String>) = viewModelScope.launch { prefs.setOrder(gid, order) }
    fun resetOrder(gid: String) = viewModelScope.launch { prefs.resetOrder(gid) }
    fun setAnimSpeed(v: Float) = viewModelScope.launch { prefs.setAnimSpeed(v) }
    fun setSysAnim(v: Boolean) = viewModelScope.launch { prefs.setSysAnim(v) }

    suspend fun exportJson(): String = prefs.exportJson()
    suspend fun importJson(text: String): Boolean = prefs.importJson(text)
}
