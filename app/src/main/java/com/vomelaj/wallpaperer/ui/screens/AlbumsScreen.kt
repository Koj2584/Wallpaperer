package com.vomelaj.wallpaperer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import coil.compose.AsyncImage
import com.vomelaj.wallpaperer.*
import com.vomelaj.wallpaperer.ui.*
import com.vomelaj.wallpaperer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "AlbumsScreen"

@Composable
fun AlbumsScreen(
    viewModel: MainViewModel,
    onOpenAlbum: (FolderInfo) -> Unit,
    onCreateAlbum: () -> Unit,
    onActivateAlbum: (FolderInfo) -> Unit
) {
    val context = LocalContext.current
    val folders = viewModel.folders
    val activeFolderUri = viewModel.activeFolderUri

    // Gear menu dialog state
    var menuFolder by remember { mutableStateOf<FolderInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<FolderInfo?>(null) }

    // Cover change launcher
    var coverChangeFolder by remember { mutableStateOf<FolderInfo?>(null) }
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && coverChangeFolder != null) {
            val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
            scope.launch {
                viewModel.addPhotosToAlbum(listOf(uri), coverChangeFolder!!.uri)
                viewModel.loadData()
            }
        }
        coverChangeFolder = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (folders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No albums yet.\nTap + to create one.", color = TextGray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("ALL ALBUMS", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(folders, key = { it.uri }) { folder ->
                    var photoCount by remember(folder.uri) { mutableIntStateOf(0) }
                    var coverUri by remember(folder.uri) { mutableStateOf<Uri?>(null) }
                    LaunchedEffect(folder.uri) {
                        val photos = withContext(Dispatchers.IO) { viewModel.getPhotos(folder.uri) }
                        photoCount = photos.size
                        coverUri = photos.firstOrNull()
                    }
                    WideAlbumCard(
                        title = folder.name,
                        imageUri = coverUri,
                        isActive = folder.uri == activeFolderUri,
                        photoCount = photoCount,
                        onSelect = { onOpenAlbum(folder) },
                        onActivate = { onActivateAlbum(folder) },
                        onGearClick = { menuFolder = folder }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateAlbum,
            containerColor = NeonGreen,
            contentColor = Color.Black,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, "Create Album")
        }
    }

    // ── Gear Menu Dialog ──
    if (menuFolder != null) {
        AlertDialog(
            onDismissRequest = { menuFolder = null },
            title = { Text(menuFolder!!.name, color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Rename",
                        color = TextWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .clickable {
                                renameText = menuFolder!!.name
                                showRenameDialog = true
                                // keep menuFolder for rename dialog
                            }
                            .padding(16.dp)
                    )
                    Text(
                        "Change Cover Photo",
                        color = TextWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .clickable {
                                coverChangeFolder = menuFolder
                                menuFolder = null
                                coverPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .padding(16.dp)
                    )
                    Text(
                        "Delete Album",
                        color = DangerRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .clickable { folderToDelete = menuFolder; menuFolder = null }
                            .padding(16.dp)
                    )
                }
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = { menuFolder = null }) { Text("CLOSE", color = TextGray) }
            }
        )
    }

    // ── Rename Dialog ──
    if (showRenameDialog && menuFolder != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; menuFolder = null },
            title = { Text("Rename Album", color = TextWhite) },
            text = {
                OutlinedTextField(
                    value = renameText, onValueChange = { renameText = it },
                    label = { Text("Album Name") }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                        cursorColor = NeonGreen, focusedBorderColor = NeonGreen, unfocusedBorderColor = TextGray
                    )
                )
            },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.updateFolder(menuFolder!!.copy(name = renameText.trim()))
                    }
                    showRenameDialog = false; menuFolder = null
                }) { Text("SAVE", color = NeonGreen) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false; menuFolder = null }) { Text("CANCEL", color = TextGray) } }
        )
    }

    // ── Delete Confirmation ──
    if (folderToDelete != null) {
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete Album", color = TextWhite) },
            text = { Text("Delete \"${folderToDelete!!.name}\"? All photos will be removed.", color = TextWhite) },
            containerColor = DarkSurface,
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAlbum(folderToDelete!!); folderToDelete = null }) { Text("DELETE", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("CANCEL", color = TextGray) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(folder: FolderInfo, onBack: () -> Unit, viewModel: MainViewModel) {
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
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = { Text(folder.name, color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickPhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                containerColor = NeonGreen, contentColor = Color.Black
            ) { Icon(Icons.Default.Add, "Add Photos") }
        }
    ) { innerPadding ->
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No photos yet. Click + to add.", color = TextGray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                items(photos) { fileUri ->
                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(model = fileUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                        IconButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val path = fileUri.path
                                    val deleted = if (!path.isNullOrEmpty()) {
                                        try { File(path).let { it.exists() && it.delete() } }
                                        catch (e: Exception) { Log.e(TAG, "Error deleting", e); false }
                                    } else false
                                    if (deleted) {
                                        val newPhotos = viewModel.getPhotos(folder.uri)
                                        withContext(Dispatchers.Main) { photos = newPhotos }
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) { Icon(Icons.Default.Delete, "Delete", tint = DangerRed, modifier = Modifier.padding(4.dp)) }
                    }
                }
            }
        }
    }
}
