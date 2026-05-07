package com.vomelaj.wallpaperer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vomelaj.wallpaperer.ui.*
import com.vomelaj.wallpaperer.ui.screens.*
import com.vomelaj.wallpaperer.ui.theme.*

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallpapererTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    WallpaperApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    var selectedTab by remember { mutableIntStateOf(0) }
    var currentOpenedFolder by remember { mutableStateOf<FolderInfo?>(null) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    // Reload data when window regains focus
    val rootView = activity?.window?.decorView
    DisposableEffect(rootView) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) viewModel.loadData()
        }
        rootView?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
        onDispose { rootView?.viewTreeObserver?.removeOnWindowFocusChangeListener(listener) }
    }

    // Back from album detail
    BackHandler(enabled = currentOpenedFolder != null) { currentOpenedFolder = null }

    // ── Album Detail (overlay) ──
    if (currentOpenedFolder != null) {
        AlbumDetailScreen(
            folder = currentOpenedFolder!!,
            onBack = { currentOpenedFolder = null },
            viewModel = viewModel
        )
        return
    }

    // ── Main Scaffold with Tabs ──
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "WALLPAPERER"
                            1 -> "ALBUMS"
                            2 -> "SEARCH"
                            3 -> "SETTINGS"
                            else -> "WALLPAPERER"
                        },
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            NeonBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeScreen(
                    viewModel = viewModel,
                    onOpenAlbum = { currentOpenedFolder = it }
                )
                1 -> AlbumsScreen(
                    viewModel = viewModel,
                    onOpenAlbum = { currentOpenedFolder = it },
                    onCreateAlbum = { showCreateAlbumDialog = true },
                    onActivateAlbum = { folder ->
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
                2 -> SearchScreen(
                    viewModel = viewModel,
                    onOpenAlbum = { currentOpenedFolder = it },
                    onActivateAlbum = { folder ->
                        viewModel.setActiveFolder(folder.uri)
                    }
                )
                3 -> SettingsScreen()
            }
        }
    }

    // ── Create Album Dialog ──
    if (showCreateAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false; newAlbumName = "" },
            title = { Text("Create New Album", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        cursorColor = NeonGreen, focusedBorderColor = NeonGreen, unfocusedBorderColor = TextGray
                    )
                )
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    if (newAlbumName.isNotBlank()) {
                        viewModel.createAlbum(newAlbumName.trim())
                        showCreateAlbumDialog = false; newAlbumName = ""
                    }
                }) { Text("CREATE", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false; newAlbumName = "" }) {
                    Text("CANCEL", color = TextGray)
                }
            }
        )
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AutoBackService::class.java)
    val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return setting.split(':').any { ComponentName.unflattenFromString(it) == expected }
}
