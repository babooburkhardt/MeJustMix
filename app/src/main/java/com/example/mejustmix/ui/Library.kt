package com.example.mejustmix.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mejustmix.ui.components.ModernPillTab
import com.example.mejustmix.ui.components.ModernPillTabRow
// EXPLICIT IMPORTS
import com.example.mejustmix.data.ColorFolder
import com.example.mejustmix.data.SavedColor
import com.example.mejustmix.data.SavedPhoto
import com.example.mejustmix.data.SortOption
import com.example.mejustmix.data.PhotoFolder

/**
 * Shimmer effect for loading states
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.LightGray.copy(alpha = 0.6f),
                Color.LightGray.copy(alpha = 0.2f),
                Color.LightGray.copy(alpha = 0.6f),
            ),
            start = androidx.compose.ui.geometry.Offset(startOffsetX, 0f),
            end = androidx.compose.ui.geometry.Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    )
        .onSizeChanged { size = it }
}

@Composable
fun LibraryItemSkeleton() {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .shimmerEffect()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Library(mixViewModel: MixViewModel) {
    val libraryItems by mixViewModel.library.collectAsState()
    val photoLibrary by mixViewModel.photoLibrary.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Colors", "Photos")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header Row: Just Tabs now
            Text(
                text = "Library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            // Pill-shaped Library Tabs
            ModernPillTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    ModernPillTab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = title,
                        icon = if (index == 0) Icons.Outlined.Palette else Icons.Outlined.PhotoLibrary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Content based on selected tab with horizontal slide animation
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val duration = 300
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(duration)) { it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { -it } + fadeOut(animationSpec = tween(duration)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(duration)) { -it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration)) { it } + fadeOut(animationSpec = tween(duration)))
                    }
                },
                label = "LibraryTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> ColorLibraryContent(mixViewModel, libraryItems)
                    1 -> PhotoLibraryContent(mixViewModel, photoLibrary)
                }
            }
        }
    }
}

// --- NEW COMPOSABLE: Per-Folder Sort Button ---
@Composable
fun FolderSortButton(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    isPhotoFolder: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Sort Folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Date (Newest)") },
                onClick = { 
                    onSortSelected(SortOption.DATE_DESC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Name (A-Z)") },
                onClick = { 
                    onSortSelected(SortOption.NAME_ASC)
                    expanded = false
                }
            )
            // Color sorting only for colors
            if (!isPhotoFolder) {
                DropdownMenuItem(
                    text = { Text("Color (Rainbow)") },
                    onClick = { 
                        onSortSelected(SortOption.HUE)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorLibraryContent(
    mixViewModel: MixViewModel,
    libraryItems: List<ColorFolder>
) {
    var editingColorId by remember { mutableStateOf<String?>(null) }
    var editingFolderId by remember { mutableStateOf<String?>(null) }
    var viewingPaletteFolderId by remember { mutableStateOf<String?>(null) }

    // Palette Dialog
    viewingPaletteFolderId?.let { folderId ->
        val folder = libraryItems.find { it.id == folderId }
        if (folder != null) {
            FolderPaletteDialog(
                folder = folder,
                mixViewModel = mixViewModel,
                onDismissRequest = { viewingPaletteFolderId = null }
            )
        }
    }

    // Edit color dialog
    editingColorId?.let { colorId ->
        val allColors = libraryItems.flatMap { it.colors }
        val color = allColors.find { it.id == colorId }
        color?.let {
            EditColorDialog(
                colorName = it.name.ifEmpty { "Unnamed Color" },
                onDismissRequest = { editingColorId = null },
                onRename = { newName ->
                    mixViewModel.renameColor(colorId, newName)
                    editingColorId = null
                },
                onDelete = {
                    mixViewModel.deleteColor(colorId)
                    editingColorId = null
                }
            )
        }
    }

    // Edit folder dialog
    editingFolderId?.let { folderId ->
        val folder = libraryItems.find { it.id == folderId }
        folder?.let {
            EditFolderDialog(
                folderName = it.name,
                isPhotoFolder = false,
                onDismissRequest = { editingFolderId = null },
                onRename = { newName ->
                    mixViewModel.renameFolder(folderId, newName)
                    editingFolderId = null
                },
                onDelete = {
                    mixViewModel.deleteFolder(folderId)
                    editingFolderId = null
                }
            )
        }
    }

    if (libraryItems.isEmpty()) {
        // Beautiful empty state
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No colors saved yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Mix a color and tap Save to\nbuild your palette",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            libraryItems.forEach { folder ->
                Column {
                    // Folder header with buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 1. Sort Button
                            FolderSortButton(
                                currentSort = folder.sortOption,
                                onSortSelected = { newSort ->
                                    mixViewModel.setFolderSort(folder.id, newSort)
                                },
                                isPhotoFolder = false
                            )
                            
                            // 2. Palette Button
                            IconButton(
                                onClick = { viewingPaletteFolderId = folder.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Palette,
                                    contentDescription = "View Palette",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // 3. Edit Button
                            IconButton(
                                onClick = { editingFolderId = folder.id },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Horizontal scrollable list for colors
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(folder.colors) { item: SavedColor ->
                            ColorTile(
                                color = item.color,
                                name = item.name,
                                onClick = { mixViewModel.setColor(item.color) },
                                onLongClick = { editingColorId = item.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoLibraryContent(
    mixViewModel: MixViewModel,
    photoLibrary: List<com.example.mejustmix.data.PhotoFolder>
) {
    var editingPhotoId by remember { mutableStateOf<String?>(null) }
    var editingFolderId by remember { mutableStateOf<String?>(null) }

    // Edit photo dialog
    editingPhotoId?.let { photoId ->
        val allPhotos = photoLibrary.flatMap { it.photos }
        val photo = allPhotos.find { it.id == photoId }
        photo?.let {
            EditPhotoDialog(
                photoName = it.name.ifEmpty { "Unnamed Photo" },
                onDismissRequest = { editingPhotoId = null },
                onRename = { newName ->
                    mixViewModel.renamePhoto(photoId, newName)
                    editingPhotoId = null
                },
                onDelete = {
                    mixViewModel.deletePhoto(photoId)
                    editingPhotoId = null
                }
            )
        }
    }

    // Edit folder dialog
    editingFolderId?.let { folderId ->
        val folder = photoLibrary.find { it.id == folderId }
        folder?.let {
            EditFolderDialog(
                folderName = it.name,
                isPhotoFolder = true,
                onDismissRequest = { editingFolderId = null },
                onRename = { newName ->
                    mixViewModel.renamePhotoFolder(folderId, newName)
                    editingFolderId = null
                },
                onDelete = {
                    mixViewModel.deletePhotoFolder(folderId)
                    editingFolderId = null
                }
            )
        }
    }

    if (photoLibrary.isEmpty()) {
        // Beautiful empty state
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No photos saved yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Upload a photo and tap Save to\ncreate your collection",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            photoLibrary.forEach { folder ->
                Column {
                    // Folder header with buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 1. Sort Button
                            FolderSortButton(
                                currentSort = folder.sortOption,
                                onSortSelected = { newSort ->
                                    mixViewModel.setPhotoFolderSort(folder.id, newSort)
                                },
                                isPhotoFolder = true
                            )
                            
                            // 2. Edit Button
                            IconButton(
                                onClick = { editingFolderId = folder.id },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit folder",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Horizontal scrollable list for photos
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(folder.photos) { item: SavedPhoto ->
                            PhotoTile(
                                uriString = item.uriString,
                                name = item.name,
                                onClick = { 
                                    mixViewModel.setCurrentImage(Uri.parse(item.uriString))
                                },
                                onLongClick = { editingPhotoId = item.id }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- MODERN PALETTE DASHBOARD ---

@Composable
fun FolderPaletteDialog(
    folder: ColorFolder,
    mixViewModel: MixViewModel,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = screenHeight * 0.8f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            PaletteDashboardView(
                folderName = folder.name,
                colors = folder.colors,
                onColorSelected = { color -> mixViewModel.setColor(color) },
                onTopOff = { color ->
                    mixViewModel.setColor(color)
                    val previousVolume = mixViewModel.totalVolume.value
                    mixViewModel.setTotalVolume(3.0f) // Small top-off amount
                    mixViewModel.sendMix()
                    mixViewModel.setTotalVolume(previousVolume)
                },
                onClose = onDismissRequest
            )
        }
    }
}

@Composable
fun PaletteDashboardView(
    folderName: String,
    colors: List<SavedColor>,
    onColorSelected: (Color) -> Unit,
    onTopOff: (Color) -> Unit,
    onClose: () -> Unit
) {
    var selectedColorIndex by remember { mutableIntStateOf(if (colors.isNotEmpty()) 0 else -1) }
    
    val activeColor = if (selectedColorIndex in colors.indices) {
        colors[selectedColorIndex].color
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    val animatedBgColor by animateColorAsState(
        targetValue = activeColor.copy(alpha = 0.1f),
        animationSpec = tween(500),
        label = "bgColor"
    )
    
    val animatedSurfaceColor by animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween(300), 
        label = "surfaceColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(
                colors = listOf(animatedBgColor, MaterialTheme.colorScheme.surface)
            ))
            .padding(16.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha=0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // --- MAIN STAGE (Selected Color Preview) ---
        Box(
            modifier = Modifier
                .weight(1.2f) 
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(animatedSurfaceColor)
                .clickable { /* Consumes clicks */ },
            contentAlignment = Alignment.Center
        ) {
            // "Liquid" Shine Effect
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(300f, 300f)
                        )
                    )
            )

            // Content in the stage
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // THE TOP OFF BUTTON
                val isLight = activeColor.luminance() > 0.5f
                val buttonContainer = if (isLight) Color.Black else Color.White
                val buttonContent = if (isLight) Color.White else Color.Black

                Button(
                    onClick = {
                         if (selectedColorIndex in colors.indices) {
                            onTopOff(colors[selectedColorIndex].color)
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainer,
                        contentColor = buttonContent
                    ),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Icon(Icons.Default.WaterDrop, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Top Off", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Color Info (Name Only)
                if (selectedColorIndex in colors.indices) {
                    val colorItem = colors[selectedColorIndex]
                    val textColor = if (isLight) Color.Black.copy(alpha=0.8f) else Color.White.copy(alpha=0.95f)
                    
                    if (colorItem.name.isNotEmpty()) {
                        Text(
                            text = colorItem.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // --- THE PALETTE GRID ---
        Text(
            "Palette",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 60.dp),
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(colors.size) { index ->
                val item = colors[index]
                val isSelected = index == selectedColorIndex
                
                // Swatch Item
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(item.color)
                        .clickable { 
                            selectedColorIndex = index
                            onColorSelected(item.color)
                        }
                        .then(
                            if (isSelected) Modifier.border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.3f), CircleShape)
                        )
                ) {
                    if (isSelected) {
                        // Inner white ring for contrast
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .border(2.dp, Color.White, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// --- STANDARD TILES ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorTile(
    color: Color,
    name: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        onClick()
                    },
                    onLongClick = {
                        onLongClick()
                    },
                    onClickLabel = "Select color",
                    onLongClickLabel = "Edit color"
                )
        )
        
        if (name.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    uriString: String,
    name: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f), RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        onClick()
                    },
                    onLongClick = {
                        onLongClick()
                    },
                    onClickLabel = "Select photo",
                    onLongClickLabel = "Edit photo"
                )
        ) {
            AsyncImage(
                model = uriString,
                contentDescription = name.ifEmpty { "Photo" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        if (name.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}