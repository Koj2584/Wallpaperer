package com.vomelaj.wallpaperer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Aplikuje stín pro hodiny přímo do existující bitmapy bez vytváření kopií.
 * Vyžaduje, aby byla bitmapa načtena jako 'mutable'.
 */
fun Bitmap.applyRadialClockScrim(): Bitmap {
    if (!this.isMutable) return this // Pojistka
    
    val canvas = Canvas(this)
    val width = this.width.toFloat()
    val height = this.height.toFloat()

    val centerX = width * 0.5f
    val centerY = height * 0.2f
    val radius = width * 0.45f
    
    // Použijeme barvu s alfa kanálem - Android ji při vykreslení do RGB_565 
    // automaticky smíchá (blend) s podkladem.
    val centerColor = Color.parseColor("#A6000000") 
    val edgeColor = Color.TRANSPARENT

    val paint = Paint().apply {
        shader = RadialGradient(
            centerX, centerY, radius,
            centerColor, edgeColor,
            Shader.TileMode.CLAMP
        )
        isDither = true
    }

    canvas.drawRect(0f, 0f, width, height * 0.5f, paint)
    return this
}
