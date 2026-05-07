package com.vomelaj.wallpaperer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.service.wallpaper.WallpaperService as BaseWallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

class WallpaperService : BaseWallpaperService() {

    companion object {
        var activeEngineCount by mutableIntStateOf(0)
    }

    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {
        private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private lateinit var repository: WallpaperRepository
        
        private val bitmapBuffer = ConcurrentLinkedQueue<Pair<Bitmap, android.graphics.Matrix>>()
        private val reusableBitmaps = Collections.synchronizedSet(mutableSetOf<Bitmap>())
        
        private var currentBitmap: Bitmap? = null
        private var currentMatrix: android.graphics.Matrix? = null
        
        private val unshownImages = mutableListOf<Uri>()
        
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        
        private val drawLock = Any()

        private val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = false
            isDither = true
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            repository = WallpaperRepository(applicationContext)
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)
            if (!isPreview) activeEngineCount++

            engineScope.launch {
                fillBuffer()
                performSwap()
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
            engineScope.launch {
                if (visible) {
                    draw()
                } else {
                    performSwap()
                    fillBuffer()
                }
            }
        }

        private fun performSwap() {
            val next = bitmapBuffer.poll() ?: return
            
            val old = synchronized(drawLock) {
                val previous = currentBitmap
                currentBitmap = next.first
                currentMatrix = next.second
                previous
            }
            
            draw()
            
            if (old != null && old.isMutable) {
                reusableBitmaps.add(old)
            } else {
                old?.recycle()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            synchronized(drawLock) {
                currentBitmap?.let { currentMatrix = calculateMatrix(it, width, height) }
            }
            draw()
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            
            var bitmapToDraw: Bitmap? = null
            var matrixToDraw: android.graphics.Matrix? = null
            
            synchronized(drawLock) {
                bitmapToDraw = currentBitmap
                matrixToDraw = currentMatrix
            }
            
            val bitmap = bitmapToDraw ?: return
            
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                holder.lockCanvas()
            } ?: return

            try {
                val matrix = matrixToDraw ?: calculateMatrix(bitmap, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, matrix, paint)
            } finally {
                try { 
                    holder.unlockCanvasAndPost(canvas) 
                } catch (e: Exception) {
                    Log.e("WallpaperService", "Error unlocking canvas", e)
                }
            }
        }

        private suspend fun fillBuffer() {
            while (bitmapBuffer.size < 2) {
                val pair = prepareSingleBitmap() ?: break
                bitmapBuffer.add(pair)
            }
        }

        private suspend fun prepareSingleBitmap(): Pair<Bitmap, android.graphics.Matrix>? {
            val activeUri = repository.getActiveFolderUri() ?: return null

            if (unshownImages.isEmpty()) {
                refillShuffleBag(activeUri)
                if (unshownImages.isEmpty()) return null
            }

            val nextUri = unshownImages.removeAt(0)
            val reqW = if (surfaceWidth > 0) surfaceWidth else resources.displayMetrics.widthPixels
            val reqH = if (surfaceHeight > 0) surfaceHeight else resources.displayMetrics.heightPixels

            var raw = withContext(Dispatchers.IO) { decodeWithReuse(nextUri, reqW, reqH) } ?: return null

            // Fix EXIF rotation
            val rotation = withContext(Dispatchers.IO) { getExifRotation(nextUri) }
            if (rotation != 0f) {
                val rotMatrix = android.graphics.Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, rotMatrix, true)
                if (rotated !== raw) { reusableBitmaps.add(raw); raw = rotated }
            }

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
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
                    inBitmap = findReusableBitmap(options)
                }

                contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedInputStream(stream, 32 * 1024).use { BitmapFactory.decodeStream(it, null, options) }
                }
            } catch (e: Exception) {
                Log.e("WallpaperService", "Error decoding bitmap", e)
                null
            }
        }

        private fun getExifRotation(uri: Uri): Float {
            return try {
                val orientation = contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }
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
            val byteCount = width * height * 2 // RGB_565
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
                unshownImages.clear()
                unshownImages.addAll(repository.getPhotos(uriStr))
                unshownImages.shuffle()
            } catch (e: Exception) {
                Log.e("WallpaperService", "Error refilling shuffle bag", e)
            }
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

        override fun onSharedPreferenceChanged(p: android.content.SharedPreferences?, k: String?) {
            if (k == "active_folder_uri" || k == "pref_contrast_darkening") {
                engineScope.launch {
                    if (k == "active_folder_uri") unshownImages.clear()
                    bitmapBuffer.forEach { it.first.recycle() }
                    bitmapBuffer.clear()
                    fillBuffer()
                    performSwap()
                }
            }
        }
    }
}
