package tech.youssefachraf.image_to_pdf.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.utils.DocumentEnhancer
import tech.youssefachraf.image_to_pdf.utils.ScanFilter
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel
import java.io.File
import java.util.concurrent.Executors

private data class ScannedPage(
    val id: Long,
    val rawFile: File,
    val displayFile: File,
    val uri: Uri,
    val filter: ScanFilter,
    val revision: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartScanScreen(
    pickerViewModel: PickerViewModel,
    onNavigateBack: () -> Unit,
    onProceedToReorder: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(isCameraGranted(context))
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(ScanFilter.MAGIC_COLOR) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val scannedPages = remember { mutableStateListOf<ScannedPage>() }
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            val activity = context as? Activity
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
        }
    }

    val galleryFallbackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickerViewModel.setScannedImages(uris)
            onProceedToReorder()
        }
    }

    LaunchedEffect(Unit) {
        if (!isCameraGranted(context)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Smart Scan", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            if (scannedPages.isEmpty()) "Align document inside frame"
                            else "${scannedPages.size} page${if (scannedPages.size == 1) "" else "s"} captured",
                            color = SecondaryText,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                actions = {
                    if (hasCameraPermission && cameraError == null) {
                        IconButton(onClick = { flashMode = nextFlashMode(flashMode) }) {
                            Icon(flashIcon(flashMode), "Toggle flash", tint = PrimaryText)
                        }
                        IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                            Icon(Icons.Default.Cameraswitch, "Switch camera", tint = PrimaryText)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
            )
        },
        bottomBar = {
            SmartScanBottomControls(
                activeFilter = activeFilter,
                scannedPages = scannedPages,
                isCapturing = isCapturing,
                canCapture = hasCameraPermission && cameraError == null && imageCaptureRef.value != null,
                onFilterSelect = { selectedFilter ->
                    activeFilter = selectedFilter
                    val lastIndex = scannedPages.lastIndex
                    if (lastIndex >= 0) {
                        val lastPage = scannedPages[lastIndex]
                        scope.launch {
                            DocumentEnhancer.applyFilterToFile(
                                sourceFile = lastPage.rawFile,
                                filter = selectedFilter,
                                outputFile = lastPage.displayFile
                            )
                            scannedPages[lastIndex] = lastPage.copy(
                                filter = selectedFilter,
                                revision = lastPage.revision + 1
                            )
                        }
                    }
                },
                onCapture = {
                    val capture = imageCaptureRef.value ?: return@SmartScanBottomControls
                    if (isCapturing) return@SmartScanBottomControls
                    isCapturing = true
                    captureDocumentPhoto(
                        context = context,
                        imageCapture = capture,
                        executor = cameraExecutor,
                        filter = activeFilter,
                        onCaptured = { page ->
                            scope.launch {
                                val resolvedPage = applyInitialFilter(page)
                                scannedPages.add(resolvedPage)
                                isCapturing = false
                            }
                        },
                        onFailed = { message ->
                            scope.launch {
                                cameraError = message
                                isCapturing = false
                            }
                        }
                    )
                },
                onRemovePage = { pageId ->
                    val target = scannedPages.firstOrNull { it.id == pageId }
                    scannedPages.removeAll { it.id == pageId }
                    target?.rawFile?.delete()
                    target?.displayFile?.delete()
                },
                onDone = {
                    if (scannedPages.isNotEmpty()) {
                        pickerViewModel.setScannedImages(scannedPages.map { it.uri })
                        onProceedToReorder()
                    }
                },
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCameraPermission -> {
                    CameraPermissionCard(
                        permanentlyDenied = permanentlyDenied,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = { openAppSettings(context) },
                        onPickFromGallery = { galleryFallbackLauncher.launch("image/*") },
                    )
                }
                cameraError != null -> {
                    CameraUnavailableCard(
                        errorMessage = cameraError.orEmpty(),
                        onPickFromGallery = { galleryFallbackLauncher.launch("image/*") },
                        onRetry = { cameraError = null },
                    )
                }
                else -> {
                    CameraPreviewViewport(
                        flashMode = flashMode,
                        useFrontCamera = useFrontCamera,
                        onImageCaptureReady = { imageCaptureRef.value = it },
                        onCameraError = { cameraError = it },
                    )
                    DocumentFrameOverlay()
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewViewport(
    flashMode: Int,
    useFrontCamera: Boolean,
    onImageCaptureReady: (ImageCapture?) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(flashMode, useFrontCamera, lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val cameraProvider = providerFuture.get()
                    val selector = if (useFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    if (!cameraProvider.hasCamera(selector)) {
                        onImageCaptureReady(null)
                        onCameraError("No compatible camera detected on this device.")
                        return@addListener
                    }

                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashMode)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    onImageCaptureReady(capture)
                } catch (e: Exception) {
                    onImageCaptureReady(null)
                    onCameraError(e.message ?: "Unable to start camera preview.")
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DocumentFrameOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val frameWidth = size.width * 0.82f
                val frameHeight = (frameWidth * 1.414f).coerceAtMost(size.height * 0.82f)
                val left = (size.width - frameWidth) / 2f
                val top = (size.height - frameHeight) / 2f
                val stroke = Stroke(width = 3f)
                val corner = CornerRadius(20f, 20f)
                onDrawBehind {
                    drawRoundRect(
                        color = AccentBlue.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = Size(frameWidth, frameHeight),
                        cornerRadius = corner,
                        style = stroke,
                    )
                }
            }
    )
}

@Composable
private fun SmartScanBottomControls(
    activeFilter: ScanFilter,
    scannedPages: List<ScannedPage>,
    isCapturing: Boolean,
    canCapture: Boolean,
    onFilterSelect: (ScanFilter) -> Unit,
    onCapture: () -> Unit,
    onRemovePage: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (scannedPages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(
                    items = scannedPages,
                    key = { it.id },
                    contentType = { "scan_filmstrip_thumb" },
                ) { page ->
                    Box(modifier = Modifier.size(64.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(page.displayFile)
                                .memoryCacheKey("${page.id}_${page.revision}")
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .build(),
                            contentDescription = "Scanned page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable { onRemovePage(page.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, "Remove page", tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ScanFilter.entries.forEach { filter ->
                val selected = activeFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.22f),
                        selectedLabelColor = PrimaryText,
                        containerColor = DarkCard,
                        labelColor = SecondaryText,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.width(88.dp))

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (canCapture) AccentBlue else DarkCard)
                    .border(3.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .semantics(mergeDescendants = true) {}
                    .clickable(enabled = canCapture && !isCapturing, onClick = onCapture),
                contentAlignment = Alignment.Center,
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Default.CameraAlt, "Capture document", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }

            Button(
                onClick = onDone,
                enabled = scannedPages.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text("Review (${scannedPages.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CameraPermissionCard(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(24.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = AccentBlue, modifier = Modifier.size(44.dp))
            Text(
                text = "Camera Access Required",
                color = PrimaryText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (permanentlyDenied) {
                    "Camera permission was disabled. Enable Camera in App Settings or import photos from your gallery."
                } else {
                    "Allow camera access to scan multi-page documents with offline contrast enhancement."
                },
                color = SecondaryText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(if (permanentlyDenied) "Open App Settings" else "Grant Camera Access")
            }
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DarkBorder),
            ) {
                Icon(Icons.Default.PhotoLibrary, null, tint = PrimaryText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Choose from Gallery", color = PrimaryText)
            }
        }
    }
}

@Composable
private fun CameraUnavailableCard(
    errorMessage: String,
    onPickFromGallery: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.padding(24.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = SecondaryText, modifier = Modifier.size(40.dp))
            Text("Camera Preview Unavailable", color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(errorMessage, color = SecondaryText, fontSize = 13.sp, textAlign = TextAlign.Center)
            Button(
                onClick = onPickFromGallery,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text("Select Images from Gallery")
            }
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Retry Camera", color = PrimaryText)
            }
        }
    }
}

private fun isCameraGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun nextFlashMode(current: Int): Int {
    return when (current) {
        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
        else -> ImageCapture.FLASH_MODE_OFF
    }
}

private fun flashIcon(mode: Int) = when (mode) {
    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
    else -> Icons.Default.FlashOff
}

private fun captureDocumentPhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    filter: ScanFilter,
    onCaptured: (ScannedPage) -> Unit,
    onFailed: (String) -> Unit,
) {
    val scansDir = File(context.cacheDir, "scans").apply { mkdirs() }
    val timestamp = System.currentTimeMillis()
    val rawFile = File(scansDir, "raw_$timestamp.jpg")
    val displayFile = File(scansDir, "scan_$timestamp.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(rawFile).build()

    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    displayFile
                )
                onCaptured(
                    ScannedPage(
                        id = timestamp,
                        rawFile = rawFile,
                        displayFile = displayFile,
                        uri = uri,
                        filter = filter,
                    )
                )
            }

            override fun onError(exception: ImageCaptureException) {
                rawFile.delete()
                onFailed(exception.message ?: "Failed to capture image")
            }
        }
    )
}

private suspend fun applyInitialFilter(page: ScannedPage): ScannedPage {
    DocumentEnhancer.applyFilterToFile(
        sourceFile = page.rawFile,
        filter = page.filter,
        outputFile = page.displayFile
    )
    return page.copy(revision = page.revision + 1)
}
