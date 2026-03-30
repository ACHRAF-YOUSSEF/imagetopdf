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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

    if (uiState.selectedImages.isEmpty() && uiState.pickerState is PickerState.Idle) {
        LaunchedEffect(Unit) { launchPicker() }
    }

    ModalBottomSheet(
        onDismissRequest = {
            pickerViewModel.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Select Images",
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryText,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.selectedImages.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(uiState.selectedImages, key = { it.toString() }) { uri ->
                        SmallImageThumbnail(uri = uri, onRemove = { pickerViewModel.removeImage(uri) })
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF21262D))
                                .clickable { launchPicker() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Add, "Add more", tint = SecondaryText, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${uiState.selectedImages.size} image${if (uiState.selectedImages.size != 1) "s" else ""} selected",
                    color = SecondaryText,
                    fontSize = 13.sp,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No images selected", color = SecondaryText, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = ::launchPicker) {
                            Text("Pick Images", color = AccentBlue)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onConfirmed,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.selectedImages.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Confirm Selection", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SmallImageThumbnail(uri: Uri, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(80.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}
