package com.jump.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Small on-disk image cache for favicons, bookmark icons and homepage art.
 *
 * These were decoded with `BitmapFactory.decodeFile` inside composition. That
 * does file I/O and a full-size decode on the main thread on every cache miss,
 * and because favicon files are rewritten as sites load, the misses were
 * frequent: the homepage and bookmark lists got slower the longer the app ran.
 *
 * This decodes downsampled to the size actually drawn and keeps the result in
 * a bounded LRU, so a tile draw is a cache hit in the common case.
 */
object ImageCache {
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val missing = LruCache<String, Boolean>(512)

    /**
     * Returns a bitmap for [path] scaled so its shortest edge is at least
     * [minEdge], or null when the file is absent or not an image.
     */
    /** Raw-bitboard accessor for the view-hosted screens. */
    fun loadBitmap(path: String?, minEdge: Int = 64): Bitmap? =
        load(path, minEdge)?.asAndroidBitmap()

    fun load(path: String?, minEdge: Int = 64): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val key = "$path@$minEdge"
        cache.get(key)?.let { return it }
        if (missing.get(key) == true) return null

        val file = File(path)
        if (!file.isFile) {
            missing.put(key, true)
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            missing.put(key, true)
            return null
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= minEdge && bounds.outHeight / (sample * 2) >= minEdge) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap: Bitmap = runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
            ?: run {
                missing.put(key, true)
                return null
            }
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }

    /** Drops everything; called when icons are re-fetched or cleared. */
    fun clear() {
        cache.evictAll()
        missing.evictAll()
    }
}
