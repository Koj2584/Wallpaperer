package com.vomelaj.wallpaperer.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vomelaj.wallpaperer.FolderInfo
import com.vomelaj.wallpaperer.MainViewModel
import com.vomelaj.wallpaperer.ui.WideAlbumCard
import com.vomelaj.wallpaperer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onOpenAlbum: (FolderInfo) -> Unit,
    onActivateAlbum: (FolderInfo) -> Unit
) {
    val folders = viewModel.folders
    val activeFolderUri = viewModel.activeFolderUri
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, folders) {
        if (query.isBlank()) folders
        else folders.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search albums...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = TextGray) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
                cursorColor = NeonGreen, focusedBorderColor = NeonGreen, unfocusedBorderColor = TextGray.copy(alpha = 0.5f),
                focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Type to search albums" else "No albums found",
                    color = TextGray
                )
            }
        } else {
            Text(
                "${filtered.size} result${if (filtered.size != 1) "s" else ""}",
                color = TextGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.uri }) { folder ->
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
                        onGearClick = {} // no gear in search results
                    )
                }
            }
        }
    }
}
