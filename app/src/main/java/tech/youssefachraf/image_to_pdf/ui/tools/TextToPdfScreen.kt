package tech.youssefachraf.image_to_pdf.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolOperationState
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

@Composable
fun TextToPdfScreen(
    toolsViewModel: ToolsViewModel,
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val state by toolsViewModel.textState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isRunning = state.state is ToolOperationState.Running

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            ToolTopBar(
                title = "Text to PDF",
                subtitle = "Format notes into multi-page PDF documents",
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = toolsViewModel::setTextTitle,
                singleLine = true,
                label = { Text("Document title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkBorder,
                    cursorColor = AccentBlue,
                ),
            )

            OutlinedTextField(
                value = state.body,
                onValueChange = toolsViewModel::setTextBody,
                label = { Text("Content") },
                placeholder = { Text("Type or paste your notes here…", color = SecondaryText) },
                minLines = 7,
                maxLines = 14,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = DarkBorder,
                    cursorColor = AccentBlue,
                ),
            )

            Text(
                text = "${state.body.length} characters",
                color = SecondaryText,
                fontSize = 12.sp,
            )

            PageSizeSelectorRow(
                selectedSize = state.pageSize,
                includeAuto = false,
                onSelect = toolsViewModel::setTextPageSize,
            )

            OutputFileNameField(
                value = state.outputName,
                onValueChange = toolsViewModel::setTextOutputName,
            )

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
                onClick = { toolsViewModel.runTextToPdf(homeViewModel::insertPdfAtTop) },
                enabled = (state.title.isNotBlank() || state.body.isNotBlank()) &&
                    state.outputName.isNotBlank() &&
                    !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) {
                Text(
                    text = if (isRunning) "Generating…" else "Generate PDF",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}
