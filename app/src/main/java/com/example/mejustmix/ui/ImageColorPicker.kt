package com.example.mejustmix.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.get
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.util.LruCache

@Composable
fun ImageColorPicker(
    images: List<Uri>,
    onImagesChanged: (List<Uri>) -> Unit,
    onColorPicked: (Color) -> Unit,
    onSaveImage: ((Uri) -> Unit)? = null,
    modifier: Modifier = Modifier,
    externalSelection: Uri? = null,
    onExternalSelectionHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    var selectedImageUri by rememberSaveable(images, stateSaver = listSaver(
        save = { listOf(it?.toString() ?: "") },
        restore = { 
            val str = it[0]
            if (str.isEmpty()) null else str.toUri()
        }
    )) { 
        mutableStateOf(images.firstOrNull()) 
    }

    LaunchedEffect(externalSelection) {
        if (externalSelection != null) {
            selectedImageUri = externalSelection
            onExternalSelectionHandled()
        }
    }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDragOver by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    var isImporting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    
    // Helper to process URIs in background
    fun processAndAddUris(newUris: List<Uri>) {
        if (newUris.isEmpty()) return
        
        isImporting = true
        scope.launch(Dispatchers.IO) {
            val processedUris = newUris.map { uri ->
                // Transcode detailed HEIC/etc to cached JPG immediately
                transcodeToJpeg(context, uri) ?: uri
            }
            
            withContext(Dispatchers.Main) {
                isImporting = false
                val newUniqueUris = processedUris.filter { it !in images }
                if (newUniqueUris.isNotEmpty()) {
                    val updatedList = images + newUniqueUris
                    onImagesChanged(updatedList)
                    if (selectedImageUri == null) {
                        selectedImageUri = newUniqueUris.firstOrNull()
                    }
                }
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        processAndAddUris(uris)
    }

    val dropTarget = remember(images) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragOver = false
                val dragEvent = event.toAndroidDragEvent()
                activity?.requestDragAndDropPermissions(dragEvent)?.let { permissions ->
                    val uris = (0 until dragEvent.clipData.itemCount).mapNotNull { i ->
                        val item = dragEvent.clipData.getItemAt(i)
                        item.uri
                    }
                    
                    // Process URIs using the same pipeline (transcoding happens there)
                    processAndAddUris(uris)

                    permissions.release()
                }
                return true
            }
        }
    }

    // OPTIMIZATION: Use optimized bitmap loading with caching and downsampling
    LaunchedEffect(selectedImageUri) {
        selectedImageUri?.let { uri ->
            currentBitmap = loadOptimizedBitmap(context, uri)
        } ?: run {
            currentBitmap = null
        }
    }

    if (isFullscreen && currentBitmap != null) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                ImageCanvas(
                    bitmap = currentBitmap!!,
                    onColorPicked = {}, // No color picking in fullscreen
                    selectedImageUri = null, // Disable saving in fullscreen
                    onSaveImage = null,
                    isFullscreen = true,
                    onToggleFullscreen = { isFullscreen = false }
                )
            }
        }
    }

    val surfaceColor by animateColorAsState(if (isDragOver) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer, label = "surfaceColor")

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event ->
                        event.toAndroidDragEvent().clipDescription.hasMimeType("image/*")
                    },
                    target = dropTarget
                ),
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (currentBitmap != null) {
                    ImageCanvas(
                        bitmap = currentBitmap!!,
                        onColorPicked = onColorPicked,
                        selectedImageUri = selectedImageUri,
                        onSaveImage = onSaveImage,
                        isFullscreen = false,
                        onToggleFullscreen = { isFullscreen = true }
                    )
                } else if (!isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Image, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if(isDragOver) "Drop images here" else "Tap to add photos",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isDragOver && currentBitmap != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)))
                }
                
                // Processing Overlay
                if (isImporting) {
                     Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Importing...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (images.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                }
                items(images) { uri ->
                    val isSelected = uri == selectedImageUri
                    Box(modifier = Modifier.size(60.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { selectedImageUri = uri }
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(0.6f))
                                    .clickable {
                                        val newList = images
                                            .toMutableList()
                                            .apply { remove(uri) }
                                        onImagesChanged(newList)
                                        if (selectedImageUri == uri) selectedImageUri = newList.firstOrNull()
                                    }
                            ) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp).padding(2.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCanvas(
    bitmap: Bitmap,
    onColorPicked: (Color) -> Unit,
    selectedImageUri: Uri? = null,
    onSaveImage: ((Uri) -> Unit)? = null,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val minScale = if (viewSize.width > 0 && viewSize.height > 0) {
        min(viewSize.width.toFloat() / bitmap.width, viewSize.height.toFloat() / bitmap.height)
    } else {
        1f
    }

    LaunchedEffect(bitmap, viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            scale = minScale
            offset = Offset(
                x = (viewSize.width - bitmap.width * scale) / 2f,
                y = (viewSize.height - bitmap.height * scale) / 2f
            )
        }
    }

    fun updateZoom(zoomChange: Float, centroid: Offset) {
        val oldScale = scale
        val newScale = (scale * zoomChange).coerceIn(minScale, 10f)

        offset = centroid - (centroid - offset) * (newScale / oldScale)
        scale = newScale
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.lastOrNull() ?: continue

                        val scrollDelta = change.scrollDelta.y
                        if (scrollDelta != 0f) {
                            val zoomFactor = if (scrollDelta < 0) 1.1f else 0.9f
                            updateZoom(zoomFactor, change.position)
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    offset += pan
                    updateZoom(zoom, centroid)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if (!isFullscreen) {
                        val untransformedOffset = (tapOffset - offset) / scale
                        pickColor(untransformedOffset, viewSize, bitmap, onColorPicked)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawImage(bitmap.asImageBitmap())
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp)
        ) {
            if (onSaveImage != null && selectedImageUri != null) {
                IconButton(onClick = { onSaveImage(selectedImageUri) }) {
                    Icon(Icons.Outlined.Save, "Save to Library", tint = Color.White)
                }
            }
            
            IconButton(onClick = { updateZoom(1.2f, Offset(viewSize.width / 2f, viewSize.height / 2f)) }) {
                Icon(Icons.Default.ZoomIn, "Zoom In", tint = Color.White)
            }
            IconButton(onClick = { updateZoom(0.8f, Offset(viewSize.width / 2f, viewSize.height / 2f)) }) {
                Icon(Icons.Default.ZoomOut, "Zoom Out", tint = Color.White)
            }
            IconButton(onClick = {
                if (viewSize.width > 0 && viewSize.height > 0) {
                    scale = minScale
                    offset = Offset(
                        (viewSize.width - bitmap.width * scale) / 2f,
                        (viewSize.height - bitmap.height * scale) / 2f
                    )
                }
            }) {
                Icon(Icons.Default.Refresh, "Reset", tint = Color.White)
            }
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                    tint = Color.White
                )
            }
        }
    }
}

private fun pickColor(
    touchPosition: Offset, 
    viewSize: IntSize,
    bitmap: Bitmap,
    onColorPicked: (Color) -> Unit
) {
    if (viewSize.width == 0 || viewSize.height == 0) return

    val x = touchPosition.x.toInt()
    val y = touchPosition.y.toInt()

    if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) {
        return
    }

    val pixel = bitmap[x, y]
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF

    onColorPicked(Color(r, g, b, 255))
}

private fun transcodeToJpeg(context: Context, sourceUri: Uri): Uri? {
    return try {
        // Fast path: if already in cache and ends with .jpg, might reuse (optional, but skipping for safety)
        
        val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
        val fileName = "cached_image_${System.currentTimeMillis()}_${(0..1000).random()}.png"
        val file = File(context.cacheDir, fileName)

        // Decode to Bitmap (handles HEIC) -> Compress to PNG
        inputStream.use { input ->
             // Using BitmapFactory automatically handles HEIC if supported by OS (Android P+)
             val originalBitmap = BitmapFactory.decodeStream(input)
             if (originalBitmap != null) {
                 // Downscale if too large (Max 2600px on longest side ~ 5MP)
                 // Increased from 2048px (3MP) to 2600px (5MP) per user request
                 val maxDimension = 2600
                 val bitmap = if (originalBitmap.width > maxDimension || originalBitmap.height > maxDimension) {
                     val scale = maxDimension.toFloat() / kotlin.math.max(originalBitmap.width, originalBitmap.height)
                     val newWidth = (originalBitmap.width * scale).toInt()
                     val newHeight = (originalBitmap.height * scale).toInt()
                     val scaled = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                     if (scaled != originalBitmap) originalBitmap.recycle()
                     scaled
                 } else {
                     originalBitmap
                 }

                 FileOutputStream(file).use { output ->
                     // Use PNG for lossless quality during color picking
                     bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                 }
                 if (bitmap != originalBitmap) bitmap.recycle() // Recycle scaled if different
                 if (bitmap == originalBitmap) bitmap.recycle() // Recycle original if used
             } else {
                 return null // Failed to decode (not an image?)
             }
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * In-memory LRU cache for decoded bitmaps.
 * Sized to ~1/8 of available memory, holds recently viewed images.
 */
private object LocalBitmapCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory
    
    val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
    
    fun get(uri: Uri): Bitmap? = cache.get(uri.toString())
    
    fun put(uri: Uri, bitmap: Bitmap) {
        cache.put(uri.toString(), bitmap)
    }
}

/**
 * Load bitmap on IO thread with caching and downsampling.
 * HEIC and other slow formats benefit from caching - decode once, reuse.
 */
private suspend fun loadOptimizedBitmap(context: Context, uri: Uri): Bitmap? {
    // Check cache first (fast path)
    LocalBitmapCache.get(uri)?.let { return it }
    
    // Decode on IO thread
    return withContext(Dispatchers.IO) {
        try {
            // First, get image dimensions without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            // Calculate sample size for downsampling large images
            val maxDimension = 2048 // Max dimension to keep memory reasonable
            val sampleSize = calculateInSampleSize(options, maxDimension, maxDimension)
            
            // Now load the actual bitmap with downsampling
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            var bitmap: Bitmap? = null
            context.contentResolver.openInputStream(uri)?.use { stream ->
                bitmap = BitmapFactory.decodeStream(stream, null, loadOptions)
            }
            
            // Handle EXIF rotation
            val result = bitmap?.let { bmp ->
                val rotation = getExifRotation(context, uri)
                if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    if (rotated != bmp) bmp.recycle()
                    rotated
                } else {
                    bmp
                }
            }
            
            // Cache the result
            result?.let { LocalBitmapCache.put(uri, it) }
            
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun getExifRotation(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}