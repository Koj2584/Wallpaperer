package com.vomelaj.wallpaperer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vomelaj.wallpaperer.isAccessibilityServiceEnabled
import com.vomelaj.wallpaperer.ui.NeonSettingsToggle
import com.vomelaj.wallpaperer.ui.PermissionRow
import com.vomelaj.wallpaperer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var contrastEnabled by remember { mutableStateOf(prefs.getBoolean("pref_contrast_darkening", true)) }
    var notifCleanEnabled by remember { mutableStateOf(prefs.getBoolean("pref_notification_cleaning", true)) }

    // Permission states
    var notifAccessGranted by remember { mutableStateOf(false) }
    var accessibilityGranted by remember { mutableStateOf(false) }

    // Refresh permissions on every resume (when user comes back from system settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifAccessGranted = NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
                accessibilityGranted = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ── Preferences ──
        Text("PREFERENCES", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        NeonSettingsToggle(
            title = "Contrast Darkening",
            description = "Darken the top area if too bright for the clock.",
            checked = contrastEnabled,
            onCheckedChange = { contrastEnabled = it; prefs.edit { putBoolean("pref_contrast_darkening", it) } }
        )
        Spacer(modifier = Modifier.height(10.dp))

        NeonSettingsToggle(
            title = "Notification Cleaning",
            description = "Auto-dismiss SmartThings notifications for active albums.",
            checked = notifCleanEnabled,
            onCheckedChange = { notifCleanEnabled = it; prefs.edit { putBoolean("pref_notification_cleaning", it) } }
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Permissions ──
        Text("PERMISSIONS", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Tap to open system settings. Status updates on return.", color = TextGray, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(12.dp))

        PermissionRow(
            name = "Notification Access",
            isGranted = notifAccessGranted,
            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionRow(
            name = "Accessibility Service",
            isGranted = accessibilityGranted,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionRow(
            name = "Battery Optimization",
            isGranted = true,
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
