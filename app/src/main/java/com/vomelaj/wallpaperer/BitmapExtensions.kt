package com.vomelaj.wallpaperer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Aplikuje stín pro hodiny přímo do existující bitmapy.
 * Respektuje rotaci EXIF tak, aby byl stín vždy nahoře u hodin.
 */
fun Bitmap.applyRadialClockScrim(rotation: Int = 0): Bitmap {
    if (!this.isMutable) return this 
    
    val canvas = Canvas(this)
    val bw = this.width.toFloat()
    val bh = this.height.toFloat()
    
    // Rozměry tak, jak budou vidět na displeji po rotaci
    val dw = if (rotation % 180 != 0) bh else bw
    val dh = if (rotation % 180 != 0) bw else bh

    canvas.save()
    
    // Transformujeme plátno tak, abychom mohli kreslit v souřadnicích displeje (0,0 = vlevo nahoře)
    // To odpovídá inverzní operaci k té, kterou děláme při vykreslování na plochu.
    canvas.translate(bw / 2f, bh / 2f)
    canvas.rotate(-rotation.toFloat())
    canvas.translate(-dw / 2f, -dh / 2f)

    val centerX = dw * 0.5f
    val centerY = dh * 0.2f
    val radius = dw * 0.45f
    
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

    // Vykreslíme stmívací obdélník (horní polovina displeje)
    canvas.drawRect(0f, 0f, dw, dh * 0.5f, paint)
    
    canvas.restore()
    return this
}
