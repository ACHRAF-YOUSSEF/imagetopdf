package tech.youssefachraf.image_to_pdf.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBorder
import tech.youssefachraf.image_to_pdf.ui.theme.ImagetopdfTheme
import tech.youssefachraf.image_to_pdf.ui.theme.PdfBadgeRed
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText

enum class ToolAction {
    IMAGE_TO_PDF,
    SMART_SCAN,
    IMPORT_PDF,
    COMPRESS,
    PDF_TO_JPG,
    MERGE_PDF,
    DOCX_TO_PDF,
    MORE,
}

data class ToolItem(
    val action: ToolAction,
    val label: String,
    val icon: ImageVector,
    val tintColor: Color,
)

val toolItems = listOf(
    ToolItem(ToolAction.IMAGE_TO_PDF, "Image to PDF", Icons.Default.Image, AccentBlue),
    ToolItem(ToolAction.SMART_SCAN, "Smart Scan", Icons.Default.CameraAlt, Color(0xFF3FB950)),
    ToolItem(ToolAction.IMPORT_PDF, "Import PDF", Icons.Default.PictureAsPdf, PdfBadgeRed),
    ToolItem(ToolAction.COMPRESS, "Compress", Icons.Default.Compress, Color(0xFFD29922)),
    ToolItem(ToolAction.PDF_TO_JPG, "PDF to JPG", Icons.Default.PhotoLibrary, Color(0xFFA371F7)),
    ToolItem(ToolAction.MERGE_PDF, "Merge PDF", Icons.AutoMirrored.Filled.MergeType, Color(0xFF39C5CF)),
    ToolItem(ToolAction.DOCX_TO_PDF, "Docx to PDF", Icons.Default.Description, Color(0xFF58A6FF)),
    ToolItem(ToolAction.MORE, "More", Icons.Default.MoreHoriz, SecondaryText),
)

@Composable
fun ToolGrid(
    onToolClick: (ToolAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = toolItems,
            key = { it.action.name },
            contentType = { "tool_grid_item" },
        ) { tool ->
            ToolGridItem(
                tool = tool,
                onClick = { onToolClick(tool.action) }
            )
        }
    }
}

@Composable
private fun ToolGridItem(
    tool: ToolItem,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(tool.tintColor.copy(alpha = 0.14f))
                .border(BorderStroke(1.dp, DarkBorder.copy(alpha = 0.7f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = if (tool.action == ToolAction.MORE) PrimaryText else tool.tintColor,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = tool.label,
            color = PrimaryText.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolGridPreview() {
    ImagetopdfTheme {
        Box(modifier = Modifier.background(DarkBackground)) {
            ToolGrid(onToolClick = {})
        }
    }
}
