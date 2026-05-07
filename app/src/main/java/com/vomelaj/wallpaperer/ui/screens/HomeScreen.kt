package com.vomelaj.wallpaperer.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vomelaj.wallpaperer.*
import com.vomelaj.wallpaperer.ui.*
import com.vomelaj.wallpaperer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenAlbum: (FolderInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders = viewModel.folders
    val activeFolderUri = viewModel.activeFolderUri
    val isWallpaperActive = WallpaperService.activeEngineCount > 0

    // Load preview image from active album
    val activeFolder = folders.find { it.uri == activeFolderUri }
    var previewUri by remember(activeFolderUri) { mutableStateOf<Uri?>(null) }
    LaunchedEffect(activeFolderUri) {
        if (activeFolderUri != null) {
            val photos = withContext(Dispatchers.IO) { viewModel.getPhotos(activeFolderUri) }
            previewUri = photos.firstOrNull()
        } else {
            previewUri = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        // Wallpaper preview
        WallpaperPreviewCard(imageUri = previewUri, albumName = activeFolder?.name)

        Spacer(modifier = Modifier.height(16.dp))

        // Wake Spark Toggle
        WakeSparkToggle(
            isChecked = isWallpaperActive,
            onCheckedChange = { checked ->
                if (!checked) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val wm = WallpaperManager.getInstance(context)
                            val info = wm.wallpaperInfo
                            val isHome = info != null && info.packageName == context.packageName && info.serviceName == WallpaperService::class.java.name
                            if (isHome) wm.clear()
                            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wm.clear(WallpaperManager.FLAG_LOCK)
                            else wm.clear()
                        } catch (e: Exception) { Log.e(TAG, "Error deactivating", e) }
                    }
                } else {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, WallpaperService::class.java))
                    }
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Recent collections
        Text(
            "RECENT COLLECTIONS",
            style = MaterialTheme.typography.labelLarge,
            color = NeonGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val recentFolders = folders.take(5)
            items(recentFolders, key = { it.uri }) { folder ->
                var coverUri by remember(folder.uri) { mutableStateOf<Uri?>(null) }
                LaunchedEffect(folder.uri) {
                    coverUri = withContext(Dispatchers.IO) { viewModel.getPhotos(folder.uri) }.firstOrNull()
                }
                NeonAlbumCard(
                    title = folder.name,
                    imageUri = coverUri,
                    isActive = folder.uri == activeFolderUri,
                    onSelect = {
                        viewModel.setActiveFolder(folder.uri)
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("pref_notification_cleaning", true)) {
                            val intent = Intent(NotificationCleanerService.ACTION_DISMISS_SMARTTHINGS).apply {
                                setPackage(context.packageName)
                                putExtra(NotificationCleanerService.EXTRA_FOLDER_NAME, folder.notificationKeyword.takeUnless { it.isNullOrEmpty() } ?: folder.name)
                            }
                            context.sendBroadcast(intent)
                        }
                    }
                )
            }
        }
    }
}
