package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.ui.common.OutputFileNameField
import tech.youssefachraf.image_to_pdf.ui.common.PageThumbnailSelectorGrid
import tech.youssefachraf.image_to_pdf.ui.common.PdfSourceSelectorCard
import tech.youssefachraf.image_to_pdf.ui.common.ToolSuccessCard
import tech.youssefachraf.image_to_pdf.ui.common.ToolTopBar
import tech.youssefachraf.image_to_pdf.ui.common.formatFileSize
import tech.youssefachraf.image_to_pdf.ui.common.openPdf
import tech.youssefachraf.image_to_pdf.ui.common.sharePdf
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolOperationState
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

@Composable
fun SplitPdfScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.splitState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running
    var rangeInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) toolsViewModel.selectSplitUri(uri)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "Split & Extract PDF",
                subtitle = "Extract selected pages or split into individual PDFs",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PdfSourceSelectorCard(
                selectedSource = state.source,
                recentPdfs = homeState.recentPdfs,
                onSelectRecent = toolsViewModel::selectSplitSource,
                onBrowseDevice = { filePicker.launch(arrayOf("application/pdf")) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Output mode", color = SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitModeChip(
                        label = "Combine into 1 PDF",
                        selected = !state.splitSeparate,
                        onClick = { toolsViewModel.setSplitSeparate(false) },
                    )
                    SplitModeChip(
                        label = "Separate PDFs",
                        selected = state.splitSeparate,
                        onClick = { toolsViewModel.setSplitSeparate(true) },
                    )
                }
            }

            state.source?.let { source ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = rangeInput,
                        onValueChange = { rangeInput = it },
                        singleLine = true,
                        label = { Text("Page range (e.g. 1-3, 5)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrimaryText,
                            unfocusedTextColor = PrimaryText,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkBorder,
                            cursorColor = AccentBlue,
                        ),
                    )
                    OutlinedButton(
                        onClick = { toolsViewModel.applyPageRangeExpression(rangeInput) },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Apply", color = AccentBlue)
                    }
                }

                PageThumbnailSelectorGrid(
                    pdfUri = source.uri,
                    pageCount = source.pageCount,
                    selectedPages = state.selectedPages,
                    onTogglePage = toolsViewModel::toggleSplitPage,
                    onSelectAll = toolsViewModel::selectAllSplitPages,
                    onClearAll = toolsViewModel::clearSplitPages,
                )

                OutputFileNameField(
                    value = state.outputBaseName,
                    onValueChange = toolsViewModel::setSplitOutputName,
                    label = if (state.splitSeparate) "Base file name" else "Output file name",
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

            if (state.results.isNotEmpty()) {
                val first = state.results.first()
                val title = if (state.results.size == 1) first.name else "Created ${state.results.size} PDF files"
                val subtitle = if (state.results.size == 1) {
                    "${first.pageCount} pages · ${formatFileSize(first.sizeBytes)}"
                } else {
                    "Saved individually to Downloads"
                }
                ToolSuccessCard(
                    title = title,
                    subtitle = subtitle,
                    onOpen = { openPdf(context, first.uri) },
                    onShare = { sharePdf(context, first.uri) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { toolsViewModel.runSplit(homeViewModel::insertPdfsAtTop) },
                enabled = state.source != null && state.selectedPages.isNotEmpty() && !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(
                    text = if (isRunning) "Extracting…" else "Extract ${state.selectedPages.size} Page(s)",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SplitModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentBlue.copy(alpha = 0.22f),
            selectedLabelColor = PrimaryText,
            containerColor = DarkCard,
            labelColor = SecondaryText,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DarkBorder,
            selectedBorderColor = AccentBlue,
        ),
    )
}
