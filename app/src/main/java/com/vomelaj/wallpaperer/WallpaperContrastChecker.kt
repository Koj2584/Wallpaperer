package com.vomelaj.wallpaperer

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Ultra-fast contrast checker for wallpapers.
 * Analyzes the brightness of the area where the system clock usually resides.
 * Optimized for speed (zero allocations) and lightness.
 */
object WallpaperContrastChecker {

    /**
     * Checks if the clock area (top-middle) is too bright for a white clock.
     * Uses a fast sampling approach to avoid heavy pixel processing and allocations.
     */
    fun isClockAreaTooBright(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return false

        // Target area: Top 30%, Middle 50%
        val startX = (bitmap.width * 0.25).toInt()
        val startY = 0
        val targetWidth = (bitmap.width * 0.50).toInt()
        val targetHeight = (bitmap.height * 0.30).toInt()

        if (targetWidth <= 0 || targetHeight <= 0) return false

        // Sampling step: analyze 20x20 grid points within the target area.
        // This avoids creating any helper bitmaps or large arrays.
        val samplesCount = 20
        val stepX = targetWidth / samplesCount
        val stepY = targetHeight / samplesCount
        
        if (stepX <= 0 || stepY <= 0) return false

        var totalLuminance = 0.0
        var count = 0

        try {
            for (i in 0 until samplesCount) {
                for (j in 0 until samplesCount) {
                    val x = startX + i * stepX
                    val y = startY + j * stepY
                    
                    if (x < bitmap.width && y < bitmap.height) {
                        val pixel = bitmap.getPixel(x, y)
                        
                        // Weighted luminance formula (0.2126*R + 0.7152*G + 0.0722*B)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        
                        totalLuminance += (0.2126 * r + 0.7152 * g + 0.0722 * b)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            return false
        }

        if (count == 0) return false
        
        // Threshold: 175/255. If average brightness is above this, we should darken the area.
        return (totalLuminance / count) > 175
    }
}
