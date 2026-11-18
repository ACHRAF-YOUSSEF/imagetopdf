package tech.youssefachraf.image_to_pdf.ui.files

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import tech.youssefachraf.image_to_pdf.ui.common.RenamePdfDialog
import tech.youssefachraf.image_to_pdf.ui.common.openPdf
import tech.youssefachraf.image_to_pdf.ui.common.sharePdf
import tech.youssefachraf.image_to_pdf.ui.home.components.PdfCard
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PdfSortOrder

private val filterTabs = listOf("All Files", "Recent", "Favorites")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filteredPdfs = remember(uiState.recentPdfs, uiState.favoriteUris, selectedTab) {
        when (selectedTab) {
            1 -> uiState.recentPdfs.take(10)
            2 -> uiState.recentPdfs.filter { it.uri.toString() in uiState.favoriteUris }
            else -> uiState.recentPdfs
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Files", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, "Search", tint = PrimaryText)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                filterTabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = DarkCard,
                            labelColor = SecondaryText,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = "Sort & Filter",
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

            if (filteredPdfs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (selectedTab == 2) "No starred PDFs yet" else "No files found",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = filteredPdfs,
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
                            isFavorite = pdf.uri.toString() in uiState.favoriteUris,
                            onToggleFavorite = { homeViewModel.toggleFavorite(pdf.uri) },
                            appContext = context,
                        )
                    }
                    item(key = "files_bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
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
}
