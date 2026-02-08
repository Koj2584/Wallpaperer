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

        // Close immediately without animation
        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return

        val data = intent.data ?: return
        val scheme = data.scheme
        val host = data.host
        val path = data.path

        // Check supported schemes
        val isAppScheme = scheme == "wallpaperapp" && host == "open"
        val isWebScheme = scheme == "https" && (host == "mojetapeta.local" || host == "wp.local") && (path?.startsWith("/open") == true)

        if (isAppScheme || isWebScheme) {
            val folderNameQuery = data.getQueryParameter("folder")
            if (!folderNameQuery.isNullOrEmpty()) {
                // We access the top-level functions from MainActivity.kt (same package)
                val currentFolders = loadFolders(applicationContext)
                val matchedFolder = currentFolders.find { it.name.contains(folderNameQuery, ignoreCase = true) }

                if (matchedFolder != null) {
                    saveActiveFolderUri(applicationContext, matchedFolder.uri)
                    
                    // Trigger SmartThings notification dismissal
                    val dismissIntent = Intent(NotificationCleanerService.ACTION_DISMISS_SMARTTHINGS)
                    dismissIntent.setPackage(packageName)
                    
                    // USE NICKNAME IF SET, OTHERWISE FOLDER NAME
                    val keywordToDismiss = matchedFolder.notificationKeyword.takeUnless { it.isNullOrEmpty() } ?: matchedFolder.name
                    
                    dismissIntent.putExtra(NotificationCleanerService.EXTRA_FOLDER_NAME, keywordToDismiss)
                    sendBroadcast(dismissIntent)
                    
                    // --- CHANGED: Updated Toast Message ---
                    // "Activated [nickname]" (or album name if no nickname)
                    val toastText = "Activated ${keywordToDismiss}"
                    Toast.makeText(applicationContext, toastText, Toast.LENGTH_SHORT).show()
                    
                    // --- NEW LOGIC: Request Global Back Action ---
                    // This was seemingly failing because it was tied to the toast or context?
                    // Actually, if it stopped working, maybe the service is disabled or disconnected?
                    // We'll keep the logic, but ensuring it runs.
                    if (isAccessibilityServiceEnabled(applicationContext)) {
                        AutoBackService.triggerBack(applicationContext)
                    } else {
                         // Warning if not enabled
                         Toast.makeText(applicationContext, "Enable 'Wallpaperer' in Accessibility Settings.", Toast.LENGTH_LONG).show()
                    }
                    
                } else {
                    Toast.makeText(applicationContext, "Folder '$folderNameQuery' not found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Helper to check if our Accessibility Service is enabled
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, AutoBackService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
