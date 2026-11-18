package tech.youssefachraf.image_to_pdf.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import tech.youssefachraf.image_to_pdf.data.FavoritesRepository
import tech.youssefachraf.image_to_pdf.ui.common.OpenPdfFolderTreeContract
import tech.youssefachraf.image_to_pdf.ui.common.PersistentStorageBanner
import tech.youssefachraf.image_to_pdf.ui.common.RenamePdfDialog
import tech.youssefachraf.image_to_pdf.ui.common.openPdf
import tech.youssefachraf.image_to_pdf.ui.common.sharePdf
import tech.youssefachraf.image_to_pdf.ui.home.components.ExtraToolAction
import tech.youssefachraf.image_to_pdf.ui.home.components.MoreToolsSheet
import tech.youssefachraf.image_to_pdf.ui.home.components.PdfCard
import tech.youssefachraf.image_to_pdf.ui.home.components.ToolAction
import tech.youssefachraf.image_to_pdf.ui.home.components.ToolGrid
import tech.youssefachraf.image_to_pdf.ui.picker.ImagePickerSheet
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkSurface
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.utils.PdfStorageFolder
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PdfSortOrder
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
    onNavigateToSmartScan: () -> Unit,
    onNavigateToCompress: () -> Unit,
    onNavigateToPdfToJpg: () -> Unit,
    onNavigateToMerge: () -> Unit,
    onNavigateToDocxToPdf: () -> Unit,
    onNavigateToSplit: () -> Unit,
    onNavigateToTextToPdf: () -> Unit,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showPickerSheet by remember { mutableStateOf(false) }
    var showMoreToolsSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<Pair<android.net.Uri, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    val importPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            homeViewModel.importPdfs(uris)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        OpenPdfFolderTreeContract()
    ) { treeUri ->
        if (treeUri != null) {
            homeViewModel.onStorageFolderSelected(treeUri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        homeViewModel.clearStatusMessage()
    }

    fun handleToolAction(action: ToolAction) {
        when (action) {
            ToolAction.IMAGE_TO_PDF -> showPickerSheet = true
            ToolAction.SMART_SCAN -> onNavigateToSmartScan()
            ToolAction.IMPORT_PDF -> importPdfLauncher.launch(arrayOf("application/pdf"))
            ToolAction.COMPRESS -> onNavigateToCompress()
            ToolAction.PDF_TO_JPG -> onNavigateToPdfToJpg()
            ToolAction.MERGE_PDF -> onNavigateToMerge()
            ToolAction.DOCX_TO_PDF -> onNavigateToDocxToPdf()
            ToolAction.MORE -> showMoreToolsSheet = true
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                Icon(Icons.Default.Add, "Add images", modifier = Modifier.size(28.dp))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = DarkSurface, contentColor = PrimaryText) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Home, "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = SecondaryText,
                        unselectedTextColor = SecondaryText,
                        indicatorColor = AccentBlue.copy(alpha = 0.12f),
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToFiles,
                    icon = { Icon(Icons.Outlined.Description, "Files") },
                    label = { Text("Files") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBlue,
                        selectedTextColor = AccentBlue,
                        unselectedIconColor = SecondaryText,
                        unselectedTextColor = SecondaryText,
                        indicatorColor = AccentBlue.copy(alpha = 0.12f),
                    )
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(paddingValues),
            contentPadding = paddingValues,
        ) {
            if (uiState.isImporting) {
                item(key = "import_progress", contentType = "progress_banner") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
                }
            }

            item(key = "tool_grid", contentType = "tool_grid") {
                ToolGrid(
                    onToolClick = ::handleToolAction,
                    modifier = Modifier.height(228.dp),
                )
            }

            if (uiState.linkedFolderUri == null && !uiState.isFolderBannerDismissed) {
                item(key = "persistent_storage_banner", contentType = "storage_banner") {
                    PersistentStorageBanner(
                        onLinkFolder = {
                            folderLauncher.launch(PdfStorageFolder.buildInitialFolderUri())
                        },
                        onImportPdfs = {
                            importPdfLauncher.launch(arrayOf("application/pdf"))
                        },
                        onDismiss = homeViewModel::dismissFolderBanner,
                    )
                }
            }

            item(key = "section_header", contentType = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "All (${uiState.recentPdfs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort PDFs",
                                tint = SecondaryText,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            PdfSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    onClick = {
                                        showSortMenu = false
                                        homeViewModel.setSortOrder(order)
                                    },
                                    trailingIcon = if (uiState.sortOrder == order) {
                                        { Icon(Icons.Default.Check, null, tint = AccentBlue) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.recentPdfs.isEmpty()) {
                item(key = "empty_state", contentType = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No PDFs yet. Tap + to create one!", color = SecondaryText, fontSize = 14.sp)
                    }
                }
            }

            items(
                items = uiState.recentPdfs,
                key = { it.uri.toString() },
                contentType = { "pdf_card" },
            ) { pdf ->
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
                    isFavorite = FavoritesRepository.isFavorite(pdf, uiState.favoriteUris),
                    onToggleFavorite = { homeViewModel.toggleFavorite(pdf.uri, pdf.name) },
                    appContext = context,
                )
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    renameTarget?.let { (uri, _) ->
        RenamePdfDialog(
            name = renameText,
            onNameChange = { renameText = it },
            onConfirm = {
                homeViewModel.renamePdf(uri, renameText)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
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

    if (showMoreToolsSheet) {
        MoreToolsSheet(
            onSelectMainTool = { action ->
                showMoreToolsSheet = false
                handleToolAction(action)
            },
            onSelectExtraTool = { extra ->
                showMoreToolsSheet = false
                when (extra) {
                    ExtraToolAction.SPLIT_PDF -> onNavigateToSplit()
                    ExtraToolAction.TEXT_TO_PDF -> onNavigateToTextToPdf()
                }
            },
            onDismiss = { showMoreToolsSheet = false },
        )
    }
}
