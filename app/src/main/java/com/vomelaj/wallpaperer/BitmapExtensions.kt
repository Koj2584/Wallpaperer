package com.vomelaj.wallpaperer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Extension function to apply a dark gradient to the top of the bitmap.
 * This improves readability of the system clock on bright wallpapers.
 */
fun Bitmap.applyTopGradientFilter(): Bitmap {
    // 1. Create a mutable copy of the original bitmap
    // Bitmap.Config.ARGB_8888 is required for transparency and high quality
    val mutableBitmap = this.copy(Bitmap.Config.ARGB_8888, true)
    
    // 2. Create a Canvas to draw on the new bitmap
    val canvas = Canvas(mutableBitmap)

    // 3. Define the height of the gradient (40% of the image height)
    val gradientHeight = mutableBitmap.height * 0.4f

    // 4. Create Paint with a LinearGradient
    // Direction: Top (0,0) to Bottom (0, gradientHeight)
    // Colors: ~50% Black (#80000000) to Transparent
    val startColor = Color.parseColor("#80000000") 
    val endColor = Color.TRANSPARENT

    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f,             // x0, y0
            0f, gradientHeight, // x1, y1
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
    }

    // 5. Draw the gradient rectangle over the top area
    canvas.drawRect(0f, 0f, mutableBitmap.width.toFloat(), gradientHeight, paint)

    // 6. Return the modified bitmap
    return mutableBitmap
}

/**
 * Applies a smooth, dark radial gradient over the clock area.
 * Designed to darken specifically the center-top part where the clock resides,
 * providing a more natural look than a flat linear gradient.
 */
fun Bitmap.applyRadialClockScrim(): Bitmap {
    // 1. Create a mutable copy
    val mutableBitmap = this.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    val width = mutableBitmap.width.toFloat()
    val height = mutableBitmap.height.toFloat()

    // 2. Gradient Setup
    // Center X: 50% (middle)
    // Center Y: ~20% (approx clock position)
    val centerX = width * 0.5f
    val centerY = height * 0.2f
    
    // Radius: ~45% of width
    val radius = width * 0.45f

    // Colors: Semi-transparent black (~65% opacity) to Transparent
    val centerColor = Color.parseColor("#A6000000") 
    val edgeColor = Color.TRANSPARENT

    val paint = Paint().apply {
        shader = RadialGradient(
            centerX, centerY,
            radius,
            centerColor,
            edgeColor,
            Shader.TileMode.CLAMP
        )
    }

    // 3. Draw a rectangle covering the top half (enough to contain the radial effect)
    // We draw slightly larger than radius to ensure smooth fade out
    canvas.drawRect(0f, 0f, width, height * 0.5f, paint)

    return mutableBitmap
}
