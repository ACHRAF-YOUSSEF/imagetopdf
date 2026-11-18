package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.ui.common.OutputFileNameField
import tech.youssefachraf.image_to_pdf.ui.common.PageSizeSelectorRow
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

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
fun DocxToPdfScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.docxState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running || state.isInspecting

    val docxPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) toolsViewModel.selectDocxUri(uri)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "Docx to PDF",
                subtitle = "Offline Word document converter",
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentBlue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Description, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.summary?.fileName ?: "Select a Word (.docx) file",
                                color = PrimaryText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.summary?.let {
                                    "${it.paragraphCount} paragraphs · ${it.tableCount} tables · ${it.imageCount} images"
                                } ?: "Supports formatted text, headings, lists, tables, and embedded images",
                                color = SecondaryText,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Button(
                        onClick = { docxPicker.launch(arrayOf(DOCX_MIME, "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    ) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose .docx File")
                    }
                }
            }

            state.summary?.let { summary ->
                if (summary.previewText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF11161D)),
                        border = BorderStroke(1.dp, DarkBorder),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Content Preview", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = summary.previewText,
                                color = PrimaryText.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                PageSizeSelectorRow(
                    selectedSize = state.pageSize,
                    includeAuto = false,
                    onSelect = toolsViewModel::setDocxPageSize,
                )

                OutputFileNameField(
                    value = state.outputName,
                    onValueChange = toolsViewModel::setDocxOutputName,
                )
            }

            if (isRunning) {
                val message = (state.state as? ToolOperationState.Running)?.message ?: "Reading .docx file…"
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(message, color = SecondaryText, fontSize = 13.sp)
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

            state.result?.let { pdf ->
                ToolSuccessCard(
                    title = pdf.name,
                    subtitle = "${pdf.pageCount} pages · ${formatFileSize(pdf.sizeBytes)}",
                    onOpen = { openPdf(context, pdf.uri) },
                    onShare = { sharePdf(context, pdf.uri) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { toolsViewModel.runDocxToPdf(homeViewModel::insertPdfAtTop) },
                enabled = state.summary != null && state.outputName.isNotBlank() && !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(
                    text = if (isRunning) "Converting…" else "Convert to PDF",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
