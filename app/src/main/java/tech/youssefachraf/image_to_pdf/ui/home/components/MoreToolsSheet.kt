package tech.youssefachraf.image_to_pdf.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PdfBadgeRed
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText

enum class ExtraToolAction {
    SPLIT_PDF,
    TEXT_TO_PDF,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreToolsSheet(
    onSelectMainTool: (ToolAction) -> Unit,
    onSelectExtraTool: (ExtraToolAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "All PDF Tools",
                style = MaterialTheme.typography.titleLarge,
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Offline utilities running directly on your device",
                color = SecondaryText,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            MoreToolRow(
                icon = Icons.Default.ContentCut,
                tint = Color(0xFFFF7B72),
                title = "Split & Extract PDF",
                subtitle = "Extract specific pages or split into separate PDFs",
                badge = "New",
                onClick = { onSelectExtraTool(ExtraToolAction.SPLIT_PDF) },
            )
            MoreToolRow(
                icon = Icons.Default.EditNote,
                tint = Color(0xFF79C0FF),
                title = "Text & Notes to PDF",
                subtitle = "Write formatted notes and export clean A4/Letter PDFs",
                badge = "New",
                onClick = { onSelectExtraTool(ExtraToolAction.TEXT_TO_PDF) },
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Quick Launch",
                color = SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )

            MoreToolRow(
                icon = Icons.Default.Image,
                tint = AccentBlue,
                title = "Image to PDF",
                subtitle = "Pick gallery images, reorder pages, and export",
                onClick = { onSelectMainTool(ToolAction.IMAGE_TO_PDF) },
            )
            MoreToolRow(
                icon = Icons.Default.CameraAlt,
                tint = Color(0xFF3FB950),
                title = "Smart Scan",
                subtitle = "Scan documents with CameraX and contrast filters",
                onClick = { onSelectMainTool(ToolAction.SMART_SCAN) },
            )
            MoreToolRow(
                icon = Icons.Default.PictureAsPdf,
                tint = PdfBadgeRed,
                title = "Import PDF",
                subtitle = "Bring PDFs from storage into your Downloads library",
                onClick = { onSelectMainTool(ToolAction.IMPORT_PDF) },
            )
            MoreToolRow(
                icon = Icons.Default.Compress,
                tint = Color(0xFFD29922),
                title = "Compress PDF",
                subtitle = "Shrink PDF file size with 3 quality presets",
                onClick = { onSelectMainTool(ToolAction.COMPRESS) },
            )
            MoreToolRow(
                icon = Icons.Default.PhotoLibrary,
                tint = Color(0xFFA371F7),
                title = "PDF to JPG",
                subtitle = "Save selected PDF pages to your Pictures gallery",
                onClick = { onSelectMainTool(ToolAction.PDF_TO_JPG) },
            )
            MoreToolRow(
                icon = Icons.AutoMirrored.Filled.MergeType,
                tint = Color(0xFF39C5CF),
                title = "Merge PDF",
                subtitle = "Combine and reorder multiple PDFs into one file",
                onClick = { onSelectMainTool(ToolAction.MERGE_PDF) },
            )
            MoreToolRow(
                icon = Icons.Default.Description,
                tint = Color(0xFF58A6FF),
                title = "Docx to PDF",
                subtitle = "Convert Word (.docx) documents offline",
                onClick = { onSelectMainTool(ToolAction.DOCX_TO_PDF) },
            )
        }
    }
}

@Composable
private fun MoreToolRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
        border = BorderStroke(1.dp, DarkBorder.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = PrimaryText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    if (!badge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badge,
                                color = AccentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(text = subtitle, color = SecondaryText, fontSize = 12.sp)
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SecondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
