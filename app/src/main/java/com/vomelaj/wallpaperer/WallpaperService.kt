package com.vomelaj.wallpaperer

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.service.wallpaper.WallpaperService as BaseWallpaperService
import android.view.SurfaceHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream

class WallpaperService : BaseWallpaperService() {

    companion object {
        private const val TAG = "WallpaperService"
        var activeEngineCount by mutableStateOf(0)
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit {
            putInt("active_engines", 0)
            putBoolean("wallpaper_active", false)
        }
    }

    override fun onCreateEngine(): Engine {
        return WallpaperEngine()
    }

    inner class WallpaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var currentBitmap: Bitmap? = null
        private var lastShownUri: Uri? = null
        private var isVisible = false
        private val unshownImages = mutableListOf<Uri>()

        private val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            isDither = true
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(this)

            if (!isPreview) {
                WallpaperService.activeEngineCount++
                val currentCount = prefs.getInt("active_engines", 0)
                prefs.edit {
                    putInt("active_engines", currentCount + 1)
                    putBoolean("wallpaper_active", true)
                }
            }

            engineScope.launch {
                reloadImageDeck()
                prepareAndDrawWallpaper()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.unregisterOnSharedPreferenceChangeListener(this)

            if (!isPreview) {
                if (WallpaperService.activeEngineCount > 0) {
                     WallpaperService.activeEngineCount--
                }
                
                val currentCount = prefs.getInt("active_engines", 0)
                val newCount = (currentCount - 1).coerceAtLeast(0)
                prefs.edit {
                    putInt("active_engines", newCount)
                    if (newCount == 0) {
                        putBoolean("wallpaper_active", false)
                    }
                }
            }
            engineScope.cancel()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                draw()
            } else {
                engineScope.launch {
                    prepareAndDrawWallpaper()
                }
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "active_folder_uri") {
                engineScope.launch {
                    reloadImageDeck()
                    prepareAndDrawWallpaper()
                }
            }
        }

        private fun reloadImageDeck() {
            try {
                unshownImages.clear()
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val activeFolderUriStr = prefs.getString("active_folder_uri", null) ?: return
                
                refillShuffleBag(activeFolderUriStr)
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading image deck", e)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    if (currentBitmap != null) {
                        val bitmap = currentBitmap!!
                        val scale: Float
                        var dx = 0f
                        var dy = 0f

                        if (bitmap.width * canvas.height > canvas.width * bitmap.height) {
                            scale = canvas.height.toFloat() / bitmap.height.toFloat()
                            dx = (canvas.width - bitmap.width * scale) * 0.5f
                        } else {
                            scale = canvas.width.toFloat() / bitmap.width.toFloat()
                            dy = (canvas.height - bitmap.height * scale) * 0.5f
                        }

                        val matrix = android.graphics.Matrix()
                        matrix.setScale(scale, scale)
                        matrix.postTranslate(dx, dy)

                        canvas.drawColor(Color.BLACK)
                        canvas.drawBitmap(bitmap, matrix, paint)
                    } else {
                        canvas.drawColor(Color.BLACK)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error drawing wallpaper", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error unlocking canvas", e)
                    }
                }
            }
        }

        private suspend fun prepareAndDrawWallpaper() {
            try {
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val activeFolderUriStr = prefs.getString("active_folder_uri", null) ?: return

                if (unshownImages.isEmpty()) {
                    refillShuffleBag(activeFolderUriStr)

                    if (unshownImages.isNotEmpty() && unshownImages.first() == lastShownUri && unshownImages.size > 1) {
                        val swapIndex = (1 until unshownImages.size).random()
                        val temp = unshownImages[0]
                        unshownImages[0] = unshownImages[swapIndex]
                        unshownImages[swapIndex] = temp
                    }
                }

                if (unshownImages.isNotEmpty()) {
                    val nextUri = unshownImages.removeAt(0)
                    lastShownUri = nextUri

                    val metrics = resources.displayMetrics
                    val reqWidth = metrics.widthPixels
                    val reqHeight = metrics.heightPixels

                    val bitmap = decodeSampledBitmapFromUri(nextUri, reqWidth, reqHeight)

                    if (bitmap != null) {
                        // --- INTEGRATION: CHECK CONTRAST & APPLY GRADIENT ---
                        // Use the new targeted check for the clock area
                        val finalBitmap = if (WallpaperContrastChecker.isClockAreaTooBright(bitmap)) {
                            // Apply the new radial scrim if the clock area is too bright
                            bitmap.applyRadialClockScrim()
                        } else {
                            // Use the original bitmap
                            bitmap
                        }

                        // Recycle original bitmap if a new one was created
                        if (finalBitmap != bitmap) {
                            bitmap.recycle()
                        }

                        currentBitmap = finalBitmap
                        // --- END INTEGRATION ---

                        if (!isVisible) {
                            draw()
                        } else {
                            draw()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing wallpaper", e)
            }
        }

        private fun refillShuffleBag(folderUriStr: String) {
            unshownImages.clear()
            try {
                val uri = Uri.parse(folderUriStr)
                val path = uri.path ?: return
                val folder = File(path)

                if (folder.exists() && folder.isDirectory) {
                    val files = folder.listFiles()
                    if (files != null) {
                        for (file in files) {
                            if (file.isFile && isImageFile(file.name)) {
                                unshownImages.add(Uri.fromFile(file))
                            }
                        }
                    }
                }
                unshownImages.shuffle()
            } catch (e: Exception) {
                Log.e(TAG, "Error refilling shuffle bag", e)
            }
        }
        
        private fun isImageFile(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
        }

        private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
            return try {
                var inputStream: InputStream? = contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                options.inJustDecodeBounds = false
                inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding bitmap", e)
                null
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
}
