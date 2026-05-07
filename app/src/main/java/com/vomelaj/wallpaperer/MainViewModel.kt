package com.vomelaj.wallpaperer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WallpaperRepository(application.applicationContext)
    private val context = application.applicationContext

    var folders by mutableStateOf<List<FolderInfo>>(emptyList())
        private set

    var activeFolderUri by mutableStateOf<String?>(null)
        private set

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedFolders = repository.loadFolders()
            val activeUri = repository.getActiveFolderUri()
            withContext(Dispatchers.Main) {
                folders = loadedFolders
                activeFolderUri = activeUri
            }
        }
    }

    fun setActiveFolder(uri: String?) {
        activeFolderUri = uri
        viewModelScope.launch(Dispatchers.IO) {
            repository.setActiveFolderUri(uri)
        }
    }

    fun createAlbum(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(context.filesDir, "albums/${UUID.randomUUID()}")
            if (dir.mkdirs()) {
                val newFolder = FolderInfo(Uri.fromFile(dir).toString(), name)
                val newList = folders + newFolder
                repository.saveFolders(newList)
                withContext(Dispatchers.Main) {
                    folders = newList
                    if (folders.size == 1) setActiveFolder(newFolder.uri)
                }
            }
        }
    }

    fun deleteAlbum(folder: FolderInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            try { File(Uri.parse(folder.uri).path ?: "").deleteRecursively() } catch (e: Exception) {}
            val newList = folders.filter { it.uri != folder.uri }
            repository.saveFolders(newList)
            withContext(Dispatchers.Main) {
                folders = newList
                if (activeFolderUri == folder.uri) setActiveFolder(null)
            }
        }
    }

    fun updateFolder(updatedFolder: FolderInfo) {
        val newList = folders.map { if (it.uri == updatedFolder.uri) updatedFolder else it }
        folders = newList
        viewModelScope.launch(Dispatchers.IO) { repository.saveFolders(newList) }
    }

    suspend fun addPhotosToAlbum(uris: List<Uri>, folderUri: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        val dir = File(Uri.parse(folderUri).path ?: return@withContext 0)
        for (src in uris) {
            try {
                val dest = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
                context.contentResolver.openInputStream(src)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output); count++ }
                }
            } catch (e: Exception) {}
        }
        count
    }

    fun getPhotos(folderUri: String): List<Uri> = repository.getPhotos(folderUri)
}
