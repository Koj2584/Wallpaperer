package com.vomelaj.wallpaperer

/**
 * Data class representing an album folder.
 */
data class FolderInfo(
    val uri: String,
    val name: String,
    val notificationKeyword: String? = null
)
