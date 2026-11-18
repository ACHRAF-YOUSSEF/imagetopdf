package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tech.youssefachraf.image_to_pdf.ui.common.OutputFileNameField
import tech.youssefachraf.image_to_pdf.ui.common.RecentPdfsPickerSheet
import tech.youssefachraf.image_to_pdf.ui.common.ToolSuccessCard
import tech.youssefachraf.image_to_pdf.ui.common.ToolTopBar
import tech.youssefachraf.image_to_pdf.ui.common.formatFileSize
import tech.youssefachraf.image_to_pdf.ui.common.openPdf
import tech.youssefachraf.image_to_pdf.ui.common.sharePdf
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PdfBadgeRed
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolOperationState
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

@Composable
fun MergePdfScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.mergeState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running
    var showRecentSheet by remember { mutableStateOf(false) }

    val totalPages = remember(state.items) { state.items.sumOf { it.pageCount } }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to -> toolsViewModel.moveMergeItem(from.index, to.index) }
    )

    val multiFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) toolsViewModel.addMergeUris(uris)
    }

    if (showRecentSheet) {
        RecentPdfsPickerSheet(
            recentPdfs = homeState.recentPdfs,
            onSelect = { pdf ->
                showRecentSheet = false
                toolsViewModel.addMergePdf(pdf)
            },
            onDismiss = { showRecentSheet = false },
        )
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "Merge PDFs",
                subtitle = "${state.items.size} file(s) · $totalPages total pages",
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.items.isNotEmpty()) {
                    OutputFileNameField(
                        value = state.outputName,
                        onValueChange = toolsViewModel::setMergeOutputName,
                    )
                }

                if (isRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
                }

                if (state.state is ToolOperationState.Error) {
                    Text(
                        text = (state.state as ToolOperationState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                state.result?.let { merged ->
                    ToolSuccessCard(
                        title = merged.name,
                        subtitle = "${merged.pageCount} pages · ${formatFileSize(merged.sizeBytes)}",
                        onOpen = { openPdf(context, merged.uri) },
                        onShare = { sharePdf(context, merged.uri) },
                    )
                }

                Button(
                    onClick = { toolsViewModel.runMerge(homeViewModel::insertPdfAtTop) },
                    enabled = state.items.size >= 2 && state.outputName.isNotBlank() && !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Text(
                        text = if (isRunning) "Merging…" else "Merge ${state.items.size} PDFs",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (homeState.recentPdfs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showRecentSheet = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.Add, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Recent", color = AccentBlue, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { multiFilePicker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add from Files", fontSize = 13.sp)
                }
            }

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Add at least 2 PDF files and drag handles to set page order.",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        items = state.items,
                        key = { _, item -> item.uri.toString() },
                        contentType = { _, _ -> "merge_pdf_item" },
                    ) { index, item ->
                        ReorderableItem(reorderableState, key = item.uri.toString()) { isDragging ->
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 1.dp,
                                label = "merge_drag_elevation"
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                border = BorderStroke(1.dp, DarkBorder),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(modifier = Modifier.draggableHandle(), onClick = {}) {
                                        Icon(Icons.Default.DragHandle, "Drag to reorder", tint = SecondaryText)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${index + 1}. ${item.name}",
                                            color = PrimaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${item.pageCount} pages · ${formatFileSize(item.sizeBytes)}",
                                            color = SecondaryText,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    IconButton(onClick = { toolsViewModel.removeMergeItem(item.uri) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = PdfBadgeRed,
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
}
