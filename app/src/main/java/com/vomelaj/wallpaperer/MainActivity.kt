package com.vomelaj.wallpaperer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.ViewTreeObserver
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "MainActivity"

// --- Colors ---
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF252525)
val NeonGreen = Color(0xFF00E676)
val DangerRed = Color(0xFFD32F2F)
val TextWhite = Color(0xFFEEEEEE)
val TextGray = Color(0xFFAAAAAA)

data class FolderInfo(
    val uri: String, // Cesta k interní složce (file://...)
    val name: String,
    val notificationKeyword: String? = null
)

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NeonGreen,
                    background = DarkBackground,
                    surface = DarkSurface,
                    onPrimary = Color.Black,
                    onBackground = TextWhite,
                    onSurface = TextWhite
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
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
fun WallpaperApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // --- State for Data ---
    var folders by remember { mutableStateOf(loadFolders(context)) }
    var activeFolderUri by remember { mutableStateOf(loadActiveFolderUri(context)) }
    
    // --- State for Navigation ---
    // If null, we show the main list. If set, we show the details of that folder.
    var currentOpenedFolder by remember { mutableStateOf<FolderInfo?>(null) }
    
    val isWallpaperActive = WallpaperService.activeEngineCount > 0
    
    // --- State for Dialogs ---
    var showSamsungFixDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }

    var showEditDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<FolderInfo?>(null) }
    var editKeywordText by remember { mutableStateOf("") }

    // Check Permissions on start
    LaunchedEffect(Unit) {
        val packageName = context.packageName
        val isGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)
        if (!isGranted) showPermissionDialog = true
        if (!isAccessibilityServiceEnabled(context)) showAccessibilityDialog = true
    }
    
    // Listen to Window Focus (reload data when returning to app)
    val rootView = activity?.window?.decorView
    DisposableEffect(rootView) {
        val focusChangeListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                folders = loadFolders(context)
                activeFolderUri = loadActiveFolderUri(context)
            }
        }
        rootView?.viewTreeObserver?.addOnWindowFocusChangeListener(focusChangeListener)
        onDispose { rootView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focusChangeListener) }
    }

    // Navigation Back Handler
    BackHandler(enabled = currentOpenedFolder != null) {
        currentOpenedFolder = null
    }

    // --- VIEW SWITCHING ---
    if (currentOpenedFolder != null) {
        // === DETAIL SCREEN ===
        AlbumDetailScreen(
            folder = currentOpenedFolder!!,
            onBack = { currentOpenedFolder = null },
            onContentChanged = {
                // If the active folder content changed, we might want to notify service (optional)
                if (activeFolderUri == currentOpenedFolder!!.uri) {
                     saveActiveFolderUri(context, activeFolderUri) // Trigger reload
                }
            }
        )
    } else {
        // === MAIN LIST SCREEN ===
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                TopAppBar(
                    title = { Text("WALLPAPERER", color = TextWhite, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBackground,
                        titleContentColor = TextWhite
                    ),
                    actions = {
                        IconButton(onClick = { showSamsungFixDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Samsung Fix",
                                tint = TextGray
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateAlbumDialog = true },
                    containerColor = NeonGreen,
                    contentColor = Color.Black
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    if (isWallpaperActive) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            val wm = WallpaperManager.getInstance(context)
                                            val info = wm.wallpaperInfo
                                            // Check if we are running on Home Screen
                                            val isHome = info != null && info.packageName == context.packageName && info.serviceName == WallpaperService::class.java.name
                                            
                                            if (isHome) {
                                                // If on Home, clear standard (removes from Home & Lock usually)
                                                wm.clear()
                                            } else {
                                                // If not on Home (but active), assume Lock Screen only
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                    wm.clear(WallpaperManager.FLAG_LOCK)
                                                } else {
                                                    wm.clear()
                                                }
                                            }
                                        }
                                        Toast.makeText(context, "Wallpaper deactivated", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error toggling wallpaper service", e)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("DEACTIVATE", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                intent.putExtra(
                                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, WallpaperService::class.java)
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ACTIVATE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                Text(
                    "MY ALBUMS",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp), 
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    items(folders) { folder ->
                        FolderItem(
                            folder = folder,
                            isActive = folder.uri == activeFolderUri,
                            onSelect = {
                                activeFolderUri = folder.uri
                                saveActiveFolderUri(context, folder.uri)
                                
                                val dismissIntent = Intent(NotificationCleanerService.ACTION_DISMISS_SMARTTHINGS)
                                dismissIntent.setPackage(context.packageName)
                                val keywordToDismiss = folder.notificationKeyword.takeUnless { it.isNullOrEmpty() } ?: folder.name
                                dismissIntent.putExtra(NotificationCleanerService.EXTRA_FOLDER_NAME, keywordToDismiss)
                                context.sendBroadcast(dismissIntent)
                            },
                            onManagePhotos = {
                                currentOpenedFolder = folder
                            },
                            onEdit = {
                                folderToEdit = folder
                                editKeywordText = folder.notificationKeyword ?: ""
                                showEditDialog = true
                            },
                            onDelete = {
                                deleteInternalAlbum(folder.uri)
                                val updatedFolders = folders.filter { it.uri != folder.uri }
                                folders = updatedFolders
                                saveFolders(context, updatedFolders)
                                
                                if (activeFolderUri == folder.uri) {
                                    activeFolderUri = null
                                    saveActiveFolderUri(context, null)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS IMPLEMENTATION ---
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
                        val newFolder = createInternalAlbum(context, newAlbumName.trim())
                        if (newFolder != null) {
                            val updatedFolders = folders + newFolder
                            folders = updatedFolders
                            saveFolders(context, updatedFolders)
                            if (folders.size == 1) {
                                activeFolderUri = newFolder.uri
                                saveActiveFolderUri(context, newFolder.uri)
                            }
                        }
                        showCreateAlbumDialog = false
                        newAlbumName = ""
                    }
                }) { Text("CREATE", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false; newAlbumName = "" }) { Text("CANCEL", color = TextGray) }
            }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Notification Permission", color = TextWhite) },
            text = { Text("To dismiss SmartThings notifications automatically, please grant Notification Access.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("OPEN SETTINGS", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("LATER", color = TextGray) }
            }
        )
    }
    
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("Accessibility Service", color = TextWhite) },
            text = { Text("To automatically close the Google Assistant overlay, please enable 'Wallpaperer' in Accessibility Settings.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("OPEN SETTINGS", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) { Text("LATER", color = TextGray) }
            }
        )
    }

    if (showSamsungFixDialog) {
        AlertDialog(
            onDismissRequest = { showSamsungFixDialog = false },
            title = { Text("Samsung Fix", color = TextWhite) },
            text = { Text("1. DISABLE 'Pause app activity'\n2. ALLOW 'Unrestricted' battery.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    showSamsungFixDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(intent)
                }) { Text("OPEN SETTINGS", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showSamsungFixDialog = false }) { Text("CLOSE", color = TextGray) }
            }
        )
    }

    if (showEditDialog && folderToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Notification Keyword", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = editKeywordText,
                    onValueChange = { editKeywordText = it },
                    label = { Text("Keyword") },
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
                    if (folderToEdit != null) {
                        val updatedList = folders.map { 
                            if (it.uri == folderToEdit!!.uri) it.copy(notificationKeyword = editKeywordText.trim()) else it 
                        }
                        folders = updatedList
                        saveFolders(context, updatedList)
                    }
                    showEditDialog = false
                    folderToEdit = null
                }) { Text("SAVE", color = NeonGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false; folderToEdit = null }) { Text("CANCEL", color = TextGray) }
            }
        )
    }
}

// === NEW DETAIL SCREEN ===

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    folder: FolderInfo,
    onBack: () -> Unit,
    onContentChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Load local files
    var photos by remember { mutableStateOf(getAlbumPhotos(folder.uri)) }
    
    // Photo Picker Launcher
    val pickPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = copyPhotosToInternalAlbum(context, uris, folder.uri)
                if (count > 0) {
                    // Refresh list
                    photos = getAlbumPhotos(folder.uri)
                    Toast.makeText(context, "Added $count photos", Toast.LENGTH_SHORT).show()
                    onContentChanged()
                }
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(folder.name, color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    pickPhotosLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                containerColor = NeonGreen,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Photos")
            }
        }
    ) { innerPadding ->
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No photos yet. Click + to add.", color = TextGray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(photos) { fileUri ->
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = fileUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        
                        // Delete Button
                        IconButton(
                            onClick = {
                                val file = File(fileUri.path ?: "")
                                if (file.exists()) {
                                    if (file.delete()) {
                                        photos = getAlbumPhotos(folder.uri)
                                        onContentChanged()
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = DangerRed,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: FolderInfo,
    isActive: Boolean,
    onSelect: () -> Unit,
    onManagePhotos: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = if (isActive) BorderStroke(1.dp, NeonGreen) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    RadioButton(
                        selected = isActive,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(selectedColor = NeonGreen, unselectedColor = TextGray)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (isActive) NeonGreen else TextWhite)
                        if (!folder.notificationKeyword.isNullOrEmpty()) {
                             Text("Keyword: ${folder.notificationKeyword}", style = MaterialTheme.typography.bodySmall, color = TextGray)
                        }
                    }
                }
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = TextGray) }
                    IconButton(onClick = onDelete) { Text("✕", color = TextGray) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManagePhotos,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, TextGray.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
            ) {
                Text("MANAGE PHOTOS")
            }
        }
    }
}

// --- Helpers ---

fun getAlbumPhotos(folderUriStr: String): List<Uri> {
    val folder = File(Uri.parse(folderUriStr).path ?: return emptyList())
    if (!folder.exists() || !folder.isDirectory) return emptyList()
    
    return folder.listFiles()
        ?.filter { it.isFile && isImageFile(it.name) }
        ?.map { Uri.fromFile(it) }
        ?: emptyList()
}

fun isImageFile(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
}

fun createInternalAlbum(context: Context, name: String): FolderInfo? {
    val folderId = UUID.randomUUID().toString()
    val albumDir = File(context.filesDir, "albums/$folderId")
    if (!albumDir.exists() && albumDir.mkdirs()) {
        return FolderInfo(Uri.fromFile(albumDir).toString(), name)
    }
    return null
}

fun deleteInternalAlbum(uriStr: String) {
    try {
        val file = File(Uri.parse(uriStr).path ?: return)
        if (file.exists()) file.deleteRecursively()
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting internal album", e)
    }
}

suspend fun copyPhotosToInternalAlbum(context: Context, sourceUris: List<Uri>, targetAlbumUriStr: String): Int {
    return withContext(Dispatchers.IO) {
        var count = 0
        try {
            val albumDir = File(Uri.parse(targetAlbumUriStr).path ?: return@withContext 0)
            if (!albumDir.exists()) albumDir.mkdirs()

            for (srcUri in sourceUris) {
                try {
                    val fileName = getFileName(context, srcUri) ?: "img_${System.currentTimeMillis()}_${count}.jpg"
                    var destFile = File(albumDir, fileName)
                    var dupeCounter = 1
                    while (destFile.exists()) {
                        val nameWithoutExt = fileName.substringBeforeLast(".")
                        val ext = fileName.substringAfterLast(".", "")
                        destFile = File(albumDir, "${nameWithoutExt}_${dupeCounter}.${ext}")
                        dupeCounter++
                    }
                    context.contentResolver.openInputStream(srcUri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                            count++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying individual photo", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in photo copy process", e)
        }
        count
    }
}

@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        } finally { cursor?.close() }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) result = result?.substring(cut + 1)
    }
    return result
}

// --- Persistence ---

fun saveFolders(context: Context, folders: List<FolderInfo>) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString("saved_folders_json", Gson().toJson(folders)) }
}

fun loadFolders(context: Context): List<FolderInfo> {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("saved_folders_json", null) ?: return emptyList()
    return Gson().fromJson(json, object : TypeToken<List<FolderInfo>>() {}.type) ?: emptyList()
}

fun saveActiveFolderUri(context: Context, uri: String?) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString("active_folder_uri", uri) }
}

fun loadActiveFolderUri(context: Context): String? {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getString("active_folder_uri", null)
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, AutoBackService::class.java)
    val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) return true
    }
    return false
}
