package tech.youssefachraf.image_to_pdf.ui.picker

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.PickerState
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerSheet(
    pickerViewModel: PickerViewModel,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val uiState by pickerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var permissionRequested by remember { mutableStateOf(false) }

    val permissions = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasImagePermission(): Boolean {
        val fullAccess = ContextCompat.checkSelfPermission(
            context, permissions[0]
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val selectedAccess = Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fullAccess || selectedAccess
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (hasImagePermission()) pickerViewModel.loadGalleryImages()
    }

    LaunchedEffect(Unit) {
        if (hasImagePermission()) {
            pickerViewModel.loadGalleryImages()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(permissions)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(680.dp)
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Select Images", style = MaterialTheme.typography.titleLarge, color = PrimaryText)
                    Text(
                        "${uiState.selectedImages.size} selected · No 100-item picker limit",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = pickerViewModel::clearSelection) {
                    Text("Clear", color = AccentBlue)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (uiState.galleryAlbums.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.selectedAlbumId == null,
                        onClick = { pickerViewModel.selectAlbum(null) },
                        label = { Text("All photos (${uiState.galleryImages.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryText,
                            labelColor = SecondaryText
                        )
                    )
                    uiState.galleryAlbums.forEach { album ->
                        FilterChip(
                            selected = uiState.selectedAlbumId == album.id,
                            onClick = { pickerViewModel.selectAlbum(album.id) },
                            label = { Text("${album.name} (${album.imageCount})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                                selectedLabelColor = PrimaryText,
                                labelColor = SecondaryText
                            )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val visibleImages = uiState.selectedAlbumId?.let { albumId ->
                uiState.galleryImages.filter { it.albumId == albumId }
            } ?: uiState.galleryImages

            val allVisibleSelected = visibleImages.isNotEmpty() &&
                visibleImages.all { it.uri in uiState.selectedImages }

            Text(
                "Long-press and drag across photos to select multiple.",
                color = SecondaryText,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        pickerViewModel.setImagesSelected(
                            visibleImages.map { it.uri },
                            selected = !allVisibleSelected
                        )
                    },
                    enabled = visibleImages.isNotEmpty() && !uiState.isGalleryLoading
                ) {
                    Text(
                        if (allVisibleSelected) "Deselect all" else "Select all",
                        color = AccentBlue
                    )
                }
            }

            when {
                !hasImagePermission() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Photo access is required to browse your gallery.", color = SecondaryText)
                            TextButton(onClick = { permissionLauncher.launch(permissions) }) {
                                Text("Grant access", color = AccentBlue)
                            }
                        }
                    }
                }
                uiState.isGalleryLoading -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
                uiState.galleryImages.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, null, tint = SecondaryText, modifier = Modifier.size(40.dp))
                            Text("No accessible images found", color = SecondaryText)
                        }
                    }
                }
                else -> {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(visibleImages, uiState.selectedAlbumId) {
                                var lastSelectedUri: Uri? = null

                                fun selectImageAt(x: Float, y: Float) {
                                    val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                        x >= info.offset.x &&
                                            x < info.offset.x + info.size.width &&
                                            y >= info.offset.y &&
                                            y < info.offset.y + info.size.height
                                    } ?: return
                                    val image = visibleImages.getOrNull(item.index) ?: return
                                    if (image.uri == lastSelectedUri) return
                                    lastSelectedUri = image.uri
                                    pickerViewModel.setImagesSelected(
                                        listOf(image.uri),
                                        selected = true
                                    )
                                }

                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        selectImageAt(offset.x, offset.y)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        selectImageAt(change.position.x, change.position.y)
                                    }
                                )
                            },
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(visibleImages, key = { it.uri.toString() }) { image ->
                            val selected = image.uri in uiState.selectedImages
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(116.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { pickerViewModel.toggleImage(image.uri) }
                            ) {
                                AsyncImage(
                                    model = image.uri,
                                    contentDescription = image.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                if (selected) {
                                    Box(
                                        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(25.dp)
                                            .background(AccentBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, "Selected", tint = Color.White, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onConfirmed,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedImages.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Use ${uiState.selectedImages.size} image${if (uiState.selectedImages.size == 1) "" else "s"}",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
