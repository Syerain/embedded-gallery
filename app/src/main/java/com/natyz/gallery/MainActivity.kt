package com.natyz.gallery

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.natyz.gallery.ui.AppRoot
import com.natyz.gallery.ui.NatyzTheme


class MainActivity : ComponentActivity(), ImageLoaderFactory {

    override fun onCreate(savedInstanceState: Bundle?) {
        // start-window keeps the red splash theme; switch to the normal theme
        // right away so no red flashes through later transitions
        setTheme(R.style.Theme_GalleryNatyz)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferHighRefreshRate()
        setContent {
            NatyzTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // re-assert on every resume; some OEM shells reset the preferred mode
        preferHighRefreshRate()
    }

    /** High-refresh displays (e.g. 120Hz originOS): prefer the fastest mode. */
    private fun preferHighRefreshRate() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                val display = if (Build.VERSION.SDK_INT >= 34) display
                else @Suppress("DEPRECATION") (windowManager.defaultDisplay)
                val modes = display?.supportedModes ?: return
                // keep the current resolution, take the highest refresh rate
                val current = display.mode
                val best = modes
                    .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                    .maxByOrNull { it.refreshRate } ?: return
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                    preferredRefreshRate = best.refreshRate
                }
            }
        }
    }

    /** Shared Coil loader with generous caching for smooth list scrolling. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .build()
            }
            .crossfade(false)
            .build()
}
