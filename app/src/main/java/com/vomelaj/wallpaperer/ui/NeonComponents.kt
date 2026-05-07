package com.vomelaj.wallpaperer.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vomelaj.wallpaperer.ui.theme.*

// ─── Reusable Neon Glow Modifier ─────────────────────────────────────────────

fun Modifier.neonGlow(
    color: Color,
    radius: Dp = 12.dp,
    alpha: Float = 0.55f,
    cornerRadius: Dp = 16.dp
): Modifier = this.drawBehind {
    val glowColor = color.copy(alpha = alpha).toArgb()
    drawIntoCanvas { canvas ->
        val paint = Paint().also {
            val fp = it.asFrameworkPaint()
            fp.color = glowColor
            fp.maskFilter = BlurMaskFilter(radius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            cornerRadius.toPx(), cornerRadius.toPx(),
            paint
        )
    }
}

// ─── Wallpaper Preview Card ──────────────────────────────────────────────────

@Composable
fun WallpaperPreviewCard(
    imageUri: Any?,
    albumName: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(260.dp),
        contentAlignment = Alignment.Center
    ) {
        // Phone frame
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(2.dp, TextGray.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .background(DarkSurface)
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Wallpaper preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No wallpaper\nselected",
                        color = TextGray,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Top status bar mock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("12:00", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }

            // Bottom home indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // Album name label below the phone
        if (albumName != null) {
            Text(
                text = albumName,
                color = NeonGreen,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 4.dp)
                    .background(DarkBackground.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ─── Wake Spark Toggle ───────────────────────────────────────────────────────

@Composable
fun WakeSparkToggle(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(DarkSurface, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "WAKE SPARK: ${if (isChecked) "ON" else "OFF"}",
                color = TextWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Auto-change wallpaper on wake",
                color = TextGray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(
            modifier = if (isChecked) Modifier.neonGlow(NeonGreen, radius = 18.dp, alpha = 0.35f, cornerRadius = 24.dp) else Modifier
        ) {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = TextGray,
                    uncheckedTrackColor = DarkBackground,
                    uncheckedBorderColor = TextGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}

// ─── Neon Album Card (compact for LazyRow) ───────────────────────────────────

@Composable
fun NeonAlbumCard(
    title: String,
    imageUri: Any?,
    isActive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    topRightContent: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier
            .width(140.dp)
            .height(180.dp)
            .then(
                if (isActive) Modifier.neonGlow(NeonGreen, radius = 12.dp, alpha = 0.45f) else Modifier
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, NeonGreen) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Album cover for $title",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No Image", color = TextGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isActive) NeonGreen else TextWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                topRightContent()
            }
        }
    }
}

// ─── Wide Album Card (for Albums list) ───────────────────────────────────────

@Composable
fun WideAlbumCard(
    title: String,
    imageUri: Any?,
    isActive: Boolean,
    photoCount: Int,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onGearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .then(
                if (isActive) Modifier.neonGlow(NeonGreen, radius = 8.dp, alpha = 0.25f) else Modifier
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, NeonGreen) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left: cover image
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Cover for $title",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📷", fontSize = 24.sp)
                    }
                }
            }

            // Middle: info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        color = if (isActive) NeonGreen else TextWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$photoCount photos${if (isActive) " • Active" else ""}",
                        color = if (isActive) NeonGreen.copy(alpha = 0.7f) else TextGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!isActive) {
                    Text(
                        "Activate",
                        color = NeonGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonGreen.copy(alpha = 0.12f))
                            .clickable { onActivate() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            // Right: gear icon
            IconButton(
                onClick = onGearClick,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Icon(Icons.Default.Settings, "Album settings", tint = TextGray)
            }
        }
    }
}

// ─── Settings Toggle Item ────────────────────────────────────────────────────

@Composable
fun NeonSettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextGray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonGreen,
                checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = TextGray,
                uncheckedTrackColor = DarkBackground,
                uncheckedBorderColor = TextGray.copy(alpha = 0.5f)
            )
        )
    }
}

// ─── Permission Row ──────────────────────────────────────────────────────────

@Composable
fun PermissionRow(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = TextWhite,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .background(
                    if (isGranted) NeonGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isGranted) "✓ Granted" else "✕ Not granted",
                color = if (isGranted) NeonGreen else DangerRed,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Bottom Navigation ───────────────────────────────────────────────────────

data class NavTab(val label: String, val icon: ImageVector)

@Composable
fun NeonBottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<NavTab> = listOf(
        NavTab("Home", Icons.Default.Home),
        NavTab("Albums", Icons.Default.List),
        NavTab("Search", Icons.Default.Search),
        NavTab("Settings", Icons.Default.Settings)
    )
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .navigationBarsPadding()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val isActive = selectedTab == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = if (isActive) Modifier.neonGlow(NeonGreen, radius = 18.dp, alpha = 0.55f, cornerRadius = 14.dp) else Modifier
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) NeonGreen else TextGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    color = if (isActive) NeonGreen else TextGray,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Overlay Badges ──────────────────────────────────────────────────────────

@Composable
fun ProBadge() {
    Box(
        modifier = Modifier
            .background(WarmAmber, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "PRO",
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DeleteButtonBadge(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(DangerRed, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Delete",
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}
