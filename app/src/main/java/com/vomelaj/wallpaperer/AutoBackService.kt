package com.vomelaj.wallpaperer

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

class AutoBackService : AccessibilityService() {

    companion object {
        const val ACTION_TRIGGER_BACK = "com.vomelaj.wallpaperer.ACTION_TRIGGER_BACK"

        fun triggerBack(context: Context) {
            val intent = Intent(ACTION_TRIGGER_BACK)
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        }
    }

    private val backReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TRIGGER_BACK) {
                Log.d("AutoBackService", "Triggering Global Back Action")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter(ACTION_TRIGGER_BACK)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(backReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(backReceiver, filter)
        }
        Log.d("AutoBackService", "Service Connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(backReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
