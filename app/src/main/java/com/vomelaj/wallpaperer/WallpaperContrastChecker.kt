package com.vomelaj.wallpaperer

import android.graphics.Bitmap

/**
 * Ultra-fast contrast checker for wallpapers.
 * Analyzes the brightness of the area where the system clock usually resides.
 * Optimized for speed using bulk pixel reading.
 */
object WallpaperContrastChecker {

    /**
     * Checks if the clock area (top-middle) is too bright for a white clock.
     * Uses getPixels for bulk processing to minimize JNI overhead.
     */
    fun isClockAreaTooBright(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false

        // Target area: Top 30%, Middle 50%
        val startX = (bitmap.width * 0.25).toInt()
        val startY = 0
        val targetWidth = (bitmap.width * 0.50).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * 0.30).toInt().coerceAtLeast(1)

        // Sample a grid of 20x20 pixels
        val samplesCount = 20
        val pixels = IntArray(samplesCount * samplesCount)
        
        // We use a small scaled bitmap to sample the area efficiently
        // This is often faster than manual stepping with getPixels for large images
        return try {
            val region = Bitmap.createBitmap(bitmap, startX, startY, targetWidth, targetHeight)
            val scaled = Bitmap.createScaledBitmap(region, samplesCount, samplesCount, false)
            scaled.getPixels(pixels, 0, samplesCount, 0, 0, samplesCount, samplesCount)
            
            if (region != bitmap) region.recycle()
            scaled.recycle()

            var totalLuminance = 0.0
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Weighted luminance formula (0.2126*R + 0.7152*G + 0.0722*B)
                totalLuminance += (0.2126 * r + 0.7152 * g + 0.0722 * b)
            }

            (totalLuminance / (samplesCount * samplesCount)) > 175
        } catch (e: Exception) {
            false
        }
    }
}
