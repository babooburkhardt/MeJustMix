package com.example.mejustmix.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PhotoPicker(
    colorPickerViewModel: ColorPickerViewModel,
    mixViewModel: MixViewModel
) {
    val context = LocalContext.current
    val imageUris by colorPickerViewModel.imageUris.collectAsState()
    val activeImageUri by colorPickerViewModel.activeImageUri.collectAsState()
    val bitmap by colorPickerViewModel.bitmap.collectAsState()
    val selectedColor by colorPickerViewModel.selectedColor.collectAsState()

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> uris.forEach { colorPickerViewModel.addImage(context, it) } }
    )

    LaunchedEffect(selectedColor) {
        mixViewModel.setColor(selectedColor)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp)
                .border(
                    width = 2.dp,
                    color = Color.Gray,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap == null) {
                Text(
                    "Drop photos here.",
                    modifier = Modifier.clickable { 
                        multiplePhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }.padding(96.dp)
                )
            } else {
                AsyncImage(
                    model = activeImageUri,
                    contentDescription = "Selected image",
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            colorPickerViewModel.sampleColor(offset.x, offset.y)
                        }
                    }.pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            colorPickerViewModel.sampleColor(change.position.x, change.position.y)
                            change.consume()
                        }
                    }
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(imageUris) { uri ->
                Thumbnail(uri, colorPickerViewModel)
            }
        }
    }
}

@Composable
private fun Thumbnail(uri: Uri, viewModel: ColorPickerViewModel) {
    val context = LocalContext.current
    val activeImageUri by viewModel.activeImageUri.collectAsState()
    val isActive = activeImageUri == uri

    Box {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.setActiveImage(context, uri) }
                .border(
                    width = if (isActive) 3.dp else 0.dp,
                    color = if (isActive) Color.Blue else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(if (isActive) 3.dp else 0.dp)
        )
        IconButton(
            onClick = { viewModel.removeImage(context, uri) },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}
