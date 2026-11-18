package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.ui.common.PageThumbnailSelectorGrid
import tech.youssefachraf.image_to_pdf.ui.common.PdfSourceSelectorCard
import tech.youssefachraf.image_to_pdf.ui.common.ToolSuccessCard
import tech.youssefachraf.image_to_pdf.ui.common.ToolTopBar
import tech.youssefachraf.image_to_pdf.ui.common.shareImages
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.utils.JpgQuality
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolOperationState
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

@Composable
fun PdfToJpgScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.pdfToJpgState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) toolsViewModel.selectPdfToJpgUri(uri)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "PDF to JPG",
                subtitle = "Export selected PDF pages to Pictures/ImageToPdf",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PdfSourceSelectorCard(
                selectedSource = state.source,
                recentPdfs = homeState.recentPdfs,
                onSelectRecent = toolsViewModel::selectPdfToJpgSource,
                onBrowseDevice = { filePicker.launch(arrayOf("application/pdf")) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Export quality", color = SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    JpgQuality.entries.forEach { preset ->
                        val selected = state.quality == preset
                        FilterChip(
                            selected = selected,
                            onClick = { toolsViewModel.setJpgQuality(preset) },
                            label = { Text(preset.label, fontSize = 12.sp) },
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
                }
            }

            state.source?.let { source ->
                PageThumbnailSelectorGrid(
                    pdfUri = source.uri,
                    pageCount = source.pageCount,
                    selectedPages = state.selectedPages,
                    onTogglePage = toolsViewModel::toggleJpgPage,
                    onSelectAll = toolsViewModel::selectAllJpgPages,
                    onClearAll = toolsViewModel::clearJpgPages,
                )
            }

            if (isRunning) {
                val running = state.state as ToolOperationState.Running
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(running.message, color = SecondaryText, fontSize = 13.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
                }
            }

            if (state.state is ToolOperationState.Error) {
                Text(
                    text = (state.state as ToolOperationState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            if (state.exportedUris.isNotEmpty()) {
                ToolSuccessCard(
                    title = "Exported ${state.exportedUris.size} JPG image(s)",
                    subtitle = "Saved to Pictures/ImageToPdf",
                    onShare = { shareImages(context, state.exportedUris) },
                    shareLabel = "Share Images",
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = toolsViewModel::runPdfToJpg,
                enabled = state.source != null && state.selectedPages.isNotEmpty() && !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(
                    text = if (isRunning) "Exporting…" else "Export ${state.selectedPages.size} Page(s) to JPG",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
