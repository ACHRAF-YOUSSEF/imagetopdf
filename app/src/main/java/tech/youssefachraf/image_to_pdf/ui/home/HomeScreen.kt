package tech.youssefachraf.image_to_pdf.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.ui.home.components.PdfCard
import tech.youssefachraf.image_to_pdf.ui.home.components.ToolGrid
import tech.youssefachraf.image_to_pdf.ui.picker.ImagePickerSheet
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.DarkSurface
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    pickerViewModel: PickerViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToPreview: () -> Unit,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPickerSheet by remember { mutableStateOf(false) }
    var selectedNavIndex by remember { mutableIntStateOf(0) }

    var renameTarget by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Home", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, "Search", tint = PrimaryText)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = PrimaryText)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPickerSheet = true },
                containerColor = AccentBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(60.dp),
            ) {
                Icon(Icons.Default.Add, "Add", modifier = Modifier.size(28.dp))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = DarkSurface, contentColor = PrimaryText) {
                NavigationBarItem(
                    selected = selectedNavIndex == 0,
                    onClick = { selectedNavIndex = 0 },
                    icon = {
                        Icon(
                            if (selectedNavIndex == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            "Home"
                        )
                    },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue, selectedTextColor = AccentBlue,
                        unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText,
                        indicatorColor = AccentBlue.copy(alpha = 0.12f),
                    )
                )
                NavigationBarItem(
                    selected = selectedNavIndex == 1,
                    onClick = { selectedNavIndex = 1; onNavigateToFiles() },
                    icon = {
                        Icon(
                            if (selectedNavIndex == 1) Icons.Filled.Description else Icons.Outlined.Description,
                            "Files"
                        )
                    },
                    label = { Text("Files") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue, selectedTextColor = AccentBlue,
                        unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText,
                        indicatorColor = AccentBlue.copy(alpha = 0.12f),
                    )
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            item {
                ToolGrid(
                    onImageToPdfClick = { showPickerSheet = true },
                    modifier = Modifier.height(220.dp),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "All (${uiState.recentPdfs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = SecondaryText, modifier = Modifier.size(20.dp))
                }
            }

            if (uiState.recentPdfs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No PDFs yet. Tap + to create one!", color = SecondaryText, fontSize = 14.sp)
                    }
                }
            }

            items(uiState.recentPdfs, key = { it.uri.toString() }) { pdf ->
                PdfCard(
                    pdfInfo = pdf,
                    onClick = { openPdf(context, pdf.uri) },
                    onShare = { sharePdf(context, pdf.uri) },
                    onRename = {
                        renameText = pdf.name.removeSuffix(".pdf")
                        renameTarget = Pair(pdf.uri, pdf.name)
                    },
                    onDelete = { homeViewModel.deletePdf(pdf.uri) },
                    onOpen = { openPdf(context, pdf.uri) },
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    renameTarget?.let { (uri, _) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = DarkCard,
            title = { Text("Rename PDF", color = PrimaryText) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.renamePdf(uri, renameText)
                    renameTarget = null
                }) { Text("Rename", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = SecondaryText) }
            }
        )
    }

    if (showPickerSheet) {
        ImagePickerSheet(
            pickerViewModel = pickerViewModel,
            onDismiss = { showPickerSheet = false },
            onConfirmed = {
                showPickerSheet = false
                onNavigateToPreview()
            },
        )
    }
}

private fun openPdf(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun sharePdf(context: android.content.Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}
