package com.natyz.gallery.data

import android.content.Context
import org.json.JSONObject

enum class MediaType { IMAGE, VIDEO }

data class GalleryItem(
    val id: String,
    val type: MediaType,
    val path: String,
    val width: Int,
    val height: Int,
    val title: String? = null,
    val thumb: String? = null
) {
    val aspect: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0.75f

    /** asset path used by list/grid UI (thumbnail when available) */
    val listPath: String get() = thumb ?: path
}

data class Gallery(
    val id: String,
    val title: String,
    val titleZh: String,
    val cover: String,
    val coverW: Int,
    val coverH: Int,
    val source: String,
    val items: List<GalleryItem>
)

object ManifestLoader {

    /** asset-relative path of the embedded manifest */
    private const val MANIFEST_PATH = "gallery/manifest.json"

    fun load(context: Context): List<Gallery> {
        val txt = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
        val root = JSONObject(txt)
        val arr = root.getJSONArray("galleries")
        val list = ArrayList<Gallery>(arr.length())
        for (i in 0 until arr.length()) {
            val g = arr.getJSONObject(i)
            val itemsArr = g.getJSONArray("items")
            val items = ArrayList<GalleryItem>(itemsArr.length())
            for (j in 0 until itemsArr.length()) {
                val o = itemsArr.getJSONObject(j)
                items.add(
                    GalleryItem(
                        id = o.getString("id"),
                        type = if (o.getString("type") == "video") MediaType.VIDEO else MediaType.IMAGE,
                        path = o.getString("path"),
                        width = o.optInt("w", 0),
                        height = o.optInt("h", 0),
                        title = if (o.has("title") && !o.isNull("title")) o.getString("title") else null,
                        thumb = if (o.has("thumb") && !o.isNull("thumb")) o.getString("thumb") else null
                    )
                )
            }
            list.add(
                Gallery(
                    id = g.getString("id"),
                    title = g.getString("title"),
                    titleZh = g.optString("titleZh", ""),
                    cover = g.getString("cover"),
                    coverW = g.optInt("coverW", 3),
                    coverH = g.optInt("coverH", 4),
                    source = g.optString("source", ""),
                    items = items
                )
            )
        }
        return list
    }
}
