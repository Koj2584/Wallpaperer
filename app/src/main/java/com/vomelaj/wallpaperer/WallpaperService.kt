package com.vomelaj.wallpaperer

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.service.wallpaper.WallpaperService as BaseWallpaperService
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

class WallpaperService : BaseWallpaperService() {

    companion object {
        var activeEngineCount by mutableIntStateOf(0)
    }

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        
        // Synchronized queues for smooth buffering
        private val bitmapBuffer = ConcurrentLinkedQueue<Pair<Bitmap, android.graphics.Matrix>>()
        private val reusableBitmaps = Collections.synchronizedSet(mutableSetOf<Bitmap>())
        
        private var currentBitmap: Bitmap? = null
        private var currentMatrix: android.graphics.Matrix? = null
        
        private val unshownImages = mutableListOf<Uri>()
        
        private var surfaceWidth = 0
        private var surfaceHeight = 0

        private val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = false // Not needed for wallpapers, saves CPU
            isDither = true      // Necessary for quality with RGB_565
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            if (!isPreview) activeEngineCount++

            engineScope.launch {
                fillBuffer() // Initial buffer fill
                performSwap() // First render
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            getSharedPreferences("app_prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
            if (!isPreview) activeEngineCount = (activeEngineCount - 1).coerceAtLeast(0)
            engineScope.cancel()
            clearAllMemory()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                draw()
            } else {
                // Immediate swap when turning off.
                performSwap()
                // Refill buffer in background
                engineScope.launch {
                    fillBuffer()
                }
            }
        }

        private fun performSwap() {
            val next = bitmapBuffer.poll() ?: return // If buffer is empty, keep current image
            
            val old = currentBitmap
            currentBitmap = next.first
            currentMatrix = next.second
            
            draw() // Render to surface
            
            // Reuse memory instead of recycling if possible
            if (old != null && old.isMutable) {
                reusableBitmaps.add(old)
            } else {
                old?.recycle()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            currentBitmap?.let { currentMatrix = calculateMatrix(it, width, height) }
            draw()
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val bitmap = currentBitmap ?: return
            
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                holder.lockCanvas()
            } ?: return

            try {
                val matrix = currentMatrix ?: calculateMatrix(bitmap, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, matrix, paint)
            } finally {
                try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) {}
            }
        }

        private suspend fun fillBuffer() {
            // Maintain 2 images in buffer
            while (bitmapBuffer.size < 2) {
                val pair = prepareSingleBitmap() ?: break
                bitmapBuffer.add(pair)
            }
        }

        private suspend fun prepareSingleBitmap(): Pair<Bitmap, android.graphics.Matrix>? {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val uriStr = prefs.getString("active_folder_uri", null) ?: return null

            if (unshownImages.isEmpty()) {
                refillShuffleBag(uriStr)
                if (unshownImages.isEmpty()) return null
            }

            val nextUri = unshownImages.removeAt(0)

            val reqW = if (surfaceWidth > 0) surfaceWidth else resources.displayMetrics.widthPixels
            val reqH = if (surfaceHeight > 0) surfaceHeight else resources.displayMetrics.heightPixels

            val raw = withContext(Dispatchers.IO) { decodeWithReuse(nextUri, reqW, reqH) } ?: return null

            // Contrast analysis (clock area) - respects user preference
            val isContrastEnabled = prefs.getBoolean("pref_contrast_darkening", true)
            if (isContrastEnabled && WallpaperContrastChecker.isClockAreaTooBright(raw)) {
                raw.applyRadialClockScrim()
            }

            return Pair(raw, calculateMatrix(raw, reqW, reqH))
        }

        private fun decodeWithReuse(uri: Uri, reqW: Int, reqH: Int): Bitmap? {
            return try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

                options.apply {
                    inSampleSize = calculateInSampleSize(options, reqW, reqH)
                    inJustDecodeBounds = false
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = true
                    inTempStorage = ByteArray(32 * 1024)
                    
                    // Zero-allocation decoding attempt
                    inBitmap = findReusableBitmap(options)
                }

                contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedInputStream(stream).use { BitmapFactory.decodeStream(it, null, options) }
                }
            } catch (e: Exception) { null }
        }

        private fun findReusableBitmap(options: BitmapFactory.Options): Bitmap? {
            val iterator = reusableBitmaps.iterator()
            while (iterator.hasNext()) {
                val item = iterator.next()
                if (item.isRecycled) {
                    iterator.remove()
                    continue
                }
                if (canUseForInBitmap(item, options)) {
                    iterator.remove()
                    return item
                }
            }
            return null
        }

        private fun canUseForInBitmap(candidate: Bitmap, targetOptions: BitmapFactory.Options): Boolean {
            val width = targetOptions.outWidth / targetOptions.inSampleSize
            val height = targetOptions.outHeight / targetOptions.inSampleSize
            val byteCount = width * height * 2 // RGB_565 = 2 bytes per pixel
            return candidate.allocationByteCount >= byteCount
        }

        private fun calculateMatrix(bmp: Bitmap, w: Int, h: Int): android.graphics.Matrix {
            val m = android.graphics.Matrix()
            val scale = (w.toFloat() / bmp.width).coerceAtLeast(h.toFloat() / bmp.height)
            m.setScale(scale, scale)
            m.postTranslate((w - bmp.width * scale) / 2f, (h - bmp.height * scale) / 2f)
            return m
        }

        private fun refillShuffleBag(uriStr: String) {
            try {
                val folder = File(uriStr.toUri().path ?: return)
                folder.listFiles()?.filter { it.isFile && isImageFile(it.name) }?.forEach {
                    unshownImages.add(Uri.fromFile(it))
                }
                unshownImages.shuffle()
            } catch (e: Exception) {}
        }

        private fun isImageFile(n: String) = n.lowercase().let { 
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") 
        }

        private fun calculateInSampleSize(o: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
            val (h, w) = o.outHeight to o.outWidth
            var s = 1
            if (h > reqH || w > reqW) {
                val hh = h / 2; val hw = w / 2
                while (hh / s >= reqH && hw / s >= reqW) s *= 2
            }
            return s
        }

        private fun clearAllMemory() {
            bitmapBuffer.forEach { it.first.recycle() }
            bitmapBuffer.clear()
            reusableBitmaps.forEach { it.recycle() }
            reusableBitmaps.clear()
            currentBitmap?.recycle()
            currentBitmap = null
        }

        override fun onSharedPreferenceChanged(p: SharedPreferences?, k: String?) {
            if (k == "active_folder_uri" || k == "pref_contrast_darkening") {
                engineScope.launch {
                    if (k == "active_folder_uri") unshownImages.clear()
                    
                    // Clear buffer to apply new settings or new folder immediately
                    bitmapBuffer.forEach { it.first.recycle() }
                    bitmapBuffer.clear()

                    fillBuffer()
                    performSwap()
                }
            }
        }
    }
}
