package com.vomelaj.wallpaperer

import android.graphics.Bitmap

object WallpaperContrastChecker {

    /**
     * Analyzes the specific clock area (center top) to determine if it is too bright.
     * Horizontal: 25% to 75%
     * Vertical: 0% to 30%
     */
    fun isClockAreaTooBright(bitmap: Bitmap): Boolean {
        if (bitmap.width == 0 || bitmap.height == 0) return false

        // 1. Crucial Change - Targeted Crop
        val startX = (bitmap.width * 0.25).toInt()
        val startY = 0
        val targetWidth = (bitmap.width * 0.50).toInt().coerceAtLeast(1) // Middle 50%
        val targetHeight = (bitmap.height * 0.30).toInt().coerceAtLeast(1) // Top 30%
        
        // Safety check
        if (startX + targetWidth > bitmap.width) return false
        
        val croppedBitmap = Bitmap.createBitmap(bitmap, startX, startY, targetWidth, targetHeight)
        
        // 2. Scale Down to 50x30 for performance
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 50, 30, true)
        
        // Cleanup intermediate if necessary
        if (croppedBitmap != bitmap && croppedBitmap != scaledBitmap) {
            croppedBitmap.recycle()
        }

        // 3. Calculate Luminance
        var totalLuminance = 0.0
        val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
        scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Standard luminance formula
            val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
            totalLuminance += luminance
        }
        
        // Cleanup scaled bitmap
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val averageLuminance = totalLuminance / pixels.size

        // 4. Threshold: > 170 is considered too bright for white clock text
        return averageLuminance > 170
    }
}
