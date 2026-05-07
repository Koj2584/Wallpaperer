package com.vomelaj.wallpaperer

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast

/**
 * Invisible Activity to handle Deep Links and App Actions without opening the main UI.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return

        val data = intent.data ?: return
        val scheme = data.scheme
        val host = data.host
        val path = data.path

        val isAppScheme = scheme == "wallpaperapp" && host == "open"
        val isWebScheme = scheme == "https" && (host == "mojetapeta.local" || host == "wp.local") && (path?.startsWith("/open") == true)

        if (isAppScheme || isWebScheme) {
            val folderNameQuery = data.getQueryParameter("folder")
            if (!folderNameQuery.isNullOrEmpty()) {
                val repository = WallpaperRepository(applicationContext)
                val currentFolders = repository.loadFolders()
                val matchedFolder = currentFolders.find { it.name.contains(folderNameQuery, ignoreCase = true) }

                if (matchedFolder != null) {
                    repository.setActiveFolderUri(matchedFolder.uri)
                    
                    val dismissIntent = Intent(NotificationCleanerService.ACTION_DISMISS_SMARTTHINGS).apply {
                        setPackage(packageName)
                        val keyword = matchedFolder.notificationKeyword.takeUnless { it.isNullOrEmpty() } ?: matchedFolder.name
                        putExtra(NotificationCleanerService.EXTRA_FOLDER_NAME, keyword)
                    }
                    sendBroadcast(dismissIntent)
                    
                    val keywordToDisplay = matchedFolder.notificationKeyword.takeUnless { it.isNullOrEmpty() } ?: matchedFolder.name
                    Toast.makeText(applicationContext, "Activated $keywordToDisplay", Toast.LENGTH_SHORT).show()
                    
                    if (isAccessibilityServiceEnabled(applicationContext)) {
                        AutoBackService.triggerBack(applicationContext)
                    }
                } else {
                    Toast.makeText(applicationContext, "Folder '$folderNameQuery' not found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AutoBackService::class.java)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return setting.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
