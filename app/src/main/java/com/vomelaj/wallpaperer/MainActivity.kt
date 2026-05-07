package com.vomelaj.wallpaperer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vomelaj.wallpaperer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallpapererTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val folders = viewModel.folders
    val activeFolderUri = viewModel.activeFolderUri
    
    var currentOpenedFolder by remember { mutableStateOf<FolderInfo?>(null) }
    val isWallpaperActive = WallpaperService.activeEngineCount > 0
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderInfo?>(null) }
    var editKeywordText by remember { mutableStateOf("") }
    
    // State pro potvrzovací dialog smazání alba
    var folderToDelete by remember { mutableStateOf<FolderInfo?>(null) }

    LaunchedEffect(Unit) {
        val packageName = context.packageName
        val isGranted = withContext(Dispatchers.IO) {
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        }
        if (!isGranted) showPermissionDialog = true
        if (!isAccessibilityServiceEnabled(context)) showAccessibilityDialog = true
    }
    
    val rootView = activity?.window?.decorView
    DisposableEffect(rootView) {
        val focusChangeListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) viewModel.loadData()
        }
        rootView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusChangeListener)
        onDispose { rootView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focusChangeListener) }
    }

    BackHandler(enabled = currentOpenedFolder != null) { currentOpenedFolder = null }

    if (currentOpenedFolder != null) {
        AlbumDetailScreen(
            folder = currentOpenedFolder!!,
            onBack = { currentOpenedFolder = null },
            viewModel = viewModel
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("WALLPAPERER", color = TextWhite, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = TextGray)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreateAlbumDialog = true }, containerColor = NeonGreen, contentColor = Color.Black) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            },
            bottomBar = {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
                    if (isWallpaperActive) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val wm = WallpaperManager.getInstance(context)
                                        val info = wm.wallpaperInfo
                                        val isHome = info != null && info.packageName == context.packageName && info.serviceName == WallpaperService::class.java.name
                                        if (isHome) wm.clear()
                                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) wm.clear(WallpaperManager.FLAG_LOCK)
                                        else wm.clear()
                                    } catch (e: Exception) { Log.e(TAG, "Error", e) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("DEACTIVATE", fontWeight = FontWeight.Bold) }
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, WallpaperService::class.java))
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("ACTIVATE", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                Text("MY ALBUMS", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    items(folders, key = { it.uri }) { folder ->
                        FolderItem(
                            folder = folder,
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
                            },
                            onManagePhotos = { currentOpenedFolder = folder },
                            onEdit = { folderToEdit = folder; editKeywordText = folder.notificationKeyword ?: ""; showEditDialog = true },
                            onDelete = { folderToDelete = folder }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) SettingsDialog(onDismiss = { showSettingsDialog = false })

    if (showCreateAlbumDialog) {
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false; newAlbumName = "" },
            title = { Text("Create New Album", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = newAlbumName, onValueChange = { newAlbumName = it }, label = { Text("Album Name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, cursorColor = NeonGreen, focusedBorderColor = NeonGreen, unfocusedBorderColor = TextGray)
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
            dismissButton = { TextButton(onClick = { showCreateAlbumDialog = false; newAlbumName = "" }) { Text("CANCEL", color = TextGray) } }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Notification Permission", color = TextWhite) },
            text = { Text("To dismiss SmartThings notifications automatically, please grant Notification Access.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false; context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("OPEN SETTINGS", color = NeonGreen) }
            },
            dismissButton = { TextButton(onClick = { showPermissionDialog = false }) { Text("LATER", color = TextGray) } }
        )
    }
    
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("Accessibility Service", color = TextWhite) },
            text = { Text("To automatically close the Google Assistant overlay, please enable 'Wallpaperer'.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = { showAccessibilityDialog = false; context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("OPEN SETTINGS", color = NeonGreen) }
            },
            dismissButton = { TextButton(onClick = { showAccessibilityDialog = false }) { Text("LATER", color = TextGray) } }
        )
    }

    if (showEditDialog && folderToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Notification Keyword", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = editKeywordText, onValueChange = { editKeywordText = it }, label = { Text("Keyword") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, cursorColor = NeonGreen, focusedBorderColor = NeonGreen, unfocusedBorderColor = TextGray)
                )
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    if (folderToEdit != null) viewModel.updateFolder(folderToEdit!!.copy(notificationKeyword = editKeywordText.trim()))
                    showEditDialog = false; folderToEdit = null
                }) { Text("SAVE", color = NeonGreen) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false; folderToEdit = null }) { Text("CANCEL", color = TextGray) } }
        )
    }

    // Potvrzovací dialog pro smazání alba
    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Album", color = TextWhite) },
            text = { Text("Are you sure you want to delete album \"${folderToDelete!!.name}\"? All photos inside will be permanently removed.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(folderToDelete!!)
                    folderToDelete = null
                }) { Text("DELETE", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("CANCEL", color = TextGray) }
            }
        )
    }
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var contrastEnabled by remember { mutableStateOf(prefs.getBoolean("pref_contrast_darkening", true)) }
    var notificationCleaningEnabled by remember { mutableStateOf(prefs.getBoolean("pref_notification_cleaning", true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings", color = TextWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SettingsToggleItem(
                    title = "Contrast Darkening",
                    description = "Automatically darken the top area if the wallpaper is too bright for the clock.",
                    checked = contrastEnabled,
                    onCheckedChange = {
                        contrastEnabled = it
                        prefs.edit { putBoolean("pref_contrast_darkening", it) }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsToggleItem(
                    title = "Notification Cleaning",
                    description = "Automatically dismiss SmartThings notifications for active albums.",
                    checked = notificationCleaningEnabled,
                    onCheckedChange = {
                        notificationCleaningEnabled = it
                        prefs.edit { putBoolean("pref_notification_cleaning", it) }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = TextGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                Text("System Permissions", style = MaterialTheme.typography.labelLarge, color = NeonGreen)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", context.packageName, null) }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = TextWhite),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Samsung Fix (Battery/Activity)") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = TextWhite),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Notification Access") }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface, contentColor = TextWhite),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Accessibility Settings") }
            }
        },
        containerColor = DarkSurface,
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = NeonGreen) } }
    )
}

@Composable
fun SettingsToggleItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextGray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = NeonGreen, checkedTrackColor = NeonGreen.copy(alpha = 0.5f), uncheckedThumbColor = TextGray, uncheckedTrackColor = TextGray.copy(alpha = 0.3f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(folder: FolderInfo, onBack: () -> Unit, viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(folder.uri) { photos = viewModel.getPhotos(folder.uri) }
    
    val pickPhotosLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = viewModel.addPhotosToAlbum(uris, folder.uri)
                if (count > 0) photos = viewModel.getPhotos(folder.uri)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(folder.name, color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, containerColor = NeonGreen, contentColor = Color.Black) {
                Icon(Icons.Default.Add, contentDescription = "Add Photos")
            }
        }
    ) { innerPadding ->
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { Text("No photos yet. Click + to add.", color = TextGray) }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(photos) { fileUri ->
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(model = fileUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val path = fileUri.path
                                    val deleted = if (!path.isNullOrEmpty()) {
                                        try { 
                                            val file = File(path)
                                            file.exists() && file.delete() 
                                        } catch(e: Exception) { 
                                            Log.e(TAG, "Error deleting photo", e)
                                            false 
                                        }
                                    } else false
                                    
                                    if (deleted) {
                                        val newPhotos = viewModel.getPhotos(folder.uri)
                                        withContext(Dispatchers.Main) {
                                            photos = newPhotos
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed, modifier = Modifier.padding(4.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(folder: FolderInfo, isActive: Boolean, onSelect: () -> Unit, onManagePhotos: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = if (isActive) BorderStroke(1.dp, NeonGreen) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    RadioButton(selected = isActive, onClick = onSelect, colors = RadioButtonDefaults.colors(selectedColor = NeonGreen, unselectedColor = TextGray))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (isActive) NeonGreen else TextWhite)
                        if (!folder.notificationKeyword.isNullOrEmpty()) { Text("Keyword: ${folder.notificationKeyword}", style = MaterialTheme.typography.bodySmall, color = TextGray) }
                    }
                }
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = TextGray) }
                    IconButton(onClick = onDelete) { Text("✕", color = TextGray) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onManagePhotos, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, TextGray.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)) { Text("MANAGE PHOTOS") }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, AutoBackService::class.java)
    val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return setting.split(':').any { ComponentName.unflattenFromString(it) == expected }
}
