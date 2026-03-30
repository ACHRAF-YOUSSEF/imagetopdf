package tech.youssefachraf.image_to_pdf.ui.picker

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PickerState
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewReorderScreen(
    pickerViewModel: PickerViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onDone: () -> Unit,
) {
    val uiState by pickerViewModel.uiState.collectAsStateWithLifecycle()
    val images = uiState.selectedImages
    val pickerState = uiState.pickerState
    val context = LocalContext.current

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to -> pickerViewModel.moveImage(from.index, to.index) }
    )

    var previewUri by remember { mutableStateOf<Uri?>(null) }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) pickerViewModel.addImages(uris) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) imagePickerLauncher.launch("image/*") }

    fun launchPicker() {
        if (ContextCompat.checkSelfPermission(context, mediaPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) imagePickerLauncher.launch("image/*")
        else permissionLauncher.launch(mediaPermission)
    }

    if (uiState.showNameDialog) {
        PdfNameDialog(
            name = uiState.pdfName,
            onNameChange = pickerViewModel::setPdfName,
            onGenerate = { pickerViewModel.generatePdf() },
            onDismiss = pickerViewModel::dismissNameDialog,
        )
    }

    if (pickerState is PickerState.FileConflict) {
        FileConflictDialog(
            originalName = pickerState.originalName,
            suggestedName = pickerState.suggestedName,
            onOverwrite = { pickerViewModel.generatePdf(pickerState.originalName) },
            onSaveAs = { pickerViewModel.generatePdf(pickerState.suggestedName) },
            onCancel = pickerViewModel::dismissConflict,
        )
    }

    if (pickerState is PickerState.Success) {
        LaunchedEffect(pickerState) {
            homeViewModel.insertPdfAtTop(pickerState.pdfInfo)
            pickerViewModel.reset()
            onDone()
        }
    }

    previewUri?.let { uri ->
        FullscreenImagePreview(
            uri = uri,
            pageLabel = "Page ${images.indexOf(uri) + 1}",
            onDismiss = { previewUri = null },
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Preview & Reorder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                        Text(
                            "${images.size} image${if (images.size != 1) "s" else ""} · Drag to reorder",
                            color = SecondaryText,
                            fontSize = 13.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    IconButton(onClick = ::launchPicker) {
                        Icon(Icons.Default.Add, "Add more images", tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(DarkBackground)) {
                if (pickerState is PickerState.Converting) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentBlue,
                    )
                }
                Button(
                    onClick = pickerViewModel::showNameDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    enabled = images.isNotEmpty() && pickerState !is PickerState.Converting,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (pickerState is PickerState.Converting) "Generating PDF…" else "Continue",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    ) { paddingValues ->
        if (pickerState is PickerState.Error) {
            Text(
                "Error: ${pickerState.message}",
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(paddingValues).padding(16.dp),
            )
        }

        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text("No images. Go back to pick some.", color = SecondaryText, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images.size, key = { images[it].toString() }) { index ->
                    val uri = images[index]
                    ReorderableItem(reorderableState, key = uri.toString()) { isDragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (isDragging) 8.dp else 1.dp,
                            label = "drag_elevation"
                        )
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    modifier = Modifier.draggableHandle(),
                                    onClick = {},
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = SecondaryText,
                                    )
                                }

                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { previewUri = uri },
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { previewUri = uri }
                                ) {
                                    Text(
                                        "Page ${index + 1}",
                                        color = PrimaryText,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                    )
                                    Text(
                                        "Tap to preview",
                                        color = AccentBlue,
                                        fontSize = 12.sp,
                                    )
                                }

                                IconButton(onClick = { pickerViewModel.removeImage(uri) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFE74C3C),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenImagePreview(
    uri: Uri,
    pageLabel: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
            ) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }

            Text(
                text = pageLabel,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp),
            )

            AsyncImage(
                model = uri,
                contentDescription = "Full preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) Offset(offset.x + pan.x, offset.y + pan.y)
                            else Offset.Zero
                        }
                    },
            )

            Text(
                text = "Pinch to zoom · Tap to close",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun PdfNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Name your PDF", color = PrimaryText) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text("File name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onGenerate,
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) { Text("Generate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SecondaryText) }
        },
    )
}

@Composable
private fun FileConflictDialog(
    originalName: String,
    suggestedName: String,
    onOverwrite: () -> Unit,
    onSaveAs: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = DarkCard,
        title = { Text("File Already Exists", color = PrimaryText) },
        text = {
            Text(
                "A file named \"$originalName.pdf\" already exists. Overwrite or save as \"$suggestedName.pdf\"?",
                color = SecondaryText,
            )
        },
        confirmButton = {
            Button(
                onClick = onOverwrite,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) { Text("Overwrite") }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onSaveAs) { Text("Save as $suggestedName", color = AccentBlue) }
                TextButton(onClick = onCancel) { Text("Cancel", color = SecondaryText) }
            }
        },
    )
}

