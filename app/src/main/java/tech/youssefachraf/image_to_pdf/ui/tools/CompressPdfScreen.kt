package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.ui.common.OutputFileNameField
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
import tech.youssefachraf.image_to_pdf.utils.CompressionLevel
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolOperationState
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

@Composable
fun CompressPdfScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.compressState.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) toolsViewModel.selectCompressUri(uri)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "Compress PDF",
                subtitle = "Reduce file size locally on your device",
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
                onSelectRecent = toolsViewModel::selectCompressSource,
                onBrowseDevice = { filePicker.launch(arrayOf("application/pdf")) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Compression preset",
                    color = SecondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                CompressionLevel.entries.forEach { preset ->
                    val selected = state.level == preset
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isRunning) {
                                toolsViewModel.setCompressionLevel(preset)
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) AccentBlue.copy(alpha = 0.14f) else DarkCard
                        ),
                        border = BorderStroke(1.dp, if (selected) AccentBlue else DarkBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { toolsViewModel.setCompressionLevel(preset) },
                                enabled = !isRunning,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentBlue,
                                    unselectedColor = SecondaryText,
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(preset.label, color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(preset.description, color = SecondaryText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (state.source != null) {
                OutputFileNameField(
                    value = state.outputName,
                    onValueChange = toolsViewModel::setCompressOutputName,
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

            state.result?.let { result ->
                val savingsText = if (result.compressedSizeBytes < result.originalSizeBytes) {
                    "${formatFileSize(result.originalSizeBytes)} → ${formatFileSize(result.compressedSizeBytes)} (${result.savedPercent}% smaller)"
                } else {
                    "Saved ${result.pdfInfo.name} (${formatFileSize(result.compressedSizeBytes)})"
                }
                ToolSuccessCard(
                    title = result.pdfInfo.name,
                    subtitle = savingsText,
                    onOpen = { openPdf(context, result.pdfInfo.uri) },
                    onShare = { sharePdf(context, result.pdfInfo.uri) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { toolsViewModel.runCompress(homeViewModel::insertPdfAtTop) },
                enabled = state.source != null && state.outputName.isNotBlank() && !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(
                    text = if (isRunning) "Compressing…" else "Compress PDF",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
