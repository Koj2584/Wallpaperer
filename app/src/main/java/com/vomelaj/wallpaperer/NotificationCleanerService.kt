package com.vomelaj.wallpaperer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationCleanerService : NotificationListenerService() {

    companion object {
        const val ACTION_DISMISS_SMARTTHINGS = "com.vomelaj.wallpaperer.ACTION_DISMISS_SMARTTHINGS"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }

    private val dismissalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_DISMISS_SMARTTHINGS) {
                val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: return
                dismissSmartThingsNotification(folderName)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_DISMISS_SMARTTHINGS)
        // For internal broadcasts within the same UID, RECEIVER_NOT_EXPORTED is recommended on API 33+
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dismissalReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dismissalReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissalReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissSmartThingsNotification(keyword: String) {
        try {
            val notifications = activeNotifications ?: return
            for (sbn in notifications) {
                val packageName = sbn.packageName
                val extras = sbn.notification.extras
                val title = extras.getCharSequence("android.title")?.toString() ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""

                // Check for SmartThings package or "SmartThings" in text/title
                val isSmartThings = packageName == "com.samsung.android.oneconnect" || 
                                    title.contains("SmartThings", ignoreCase = true) ||
                                    text.contains("SmartThings", ignoreCase = true)

                // Check if notification matches the specific folder keyword
                val matchesKeyword = title.contains(keyword, ignoreCase = true) || 
                                     text.contains(keyword, ignoreCase = true)

                if (isSmartThings && matchesKeyword) {
                    cancelNotification(sbn.key)
                    Log.d("NotifCleaner", "Dismissed SmartThings notification for: $keyword")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}