package com.vomelaj.wallpaperer

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class WallpaperRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadFolders(): List<FolderInfo> {
        val json = prefs.getString("saved_folders_json", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<FolderInfo>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<FolderInfo>) {
        prefs.edit().putString("saved_folders_json", gson.toJson(folders)).apply()
    }

    fun getActiveFolderUri(): String? {
        return prefs.getString("active_folder_uri", null)
    }

    fun setActiveFolderUri(uri: String?) {
        prefs.edit().putString("active_folder_uri", uri).apply()
    }

    fun getPhotos(folderUri: String): List<Uri> {
        val folder = File(Uri.parse(folderUri).path ?: return emptyList())
        return folder.listFiles()?.filter { it.isFile && isImageFile(it.name) }?.map { Uri.fromFile(it) } ?: emptyList()
    }

    private fun isImageFile(name: String) = name.lowercase().let { 
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") 
    }
}
