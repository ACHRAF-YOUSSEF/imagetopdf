package tech.youssefachraf.image_to_pdf.ui.home.components

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText

data class ToolItem(
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
)

val toolItems = listOf(
    ToolItem("Image to PDF", Icons.Default.Image, isActive = true),
    ToolItem("Smart Scan", Icons.Default.CameraAlt),
    ToolItem("Import PDF", Icons.Default.PictureAsPdf),
    ToolItem("Compress", Icons.Default.Compress),
    ToolItem("PDF to JPG", Icons.Default.PhotoLibrary),
    ToolItem("Merge PDF", Icons.Default.MergeType),
    ToolItem("Docx to PDF", Icons.Default.Description),
    ToolItem("More", Icons.Default.MoreHoriz),
)

@Composable
fun ToolGrid(
    onImageToPdfClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(toolItems) { tool ->
            ToolGridItem(
                tool = tool,
                onClick = {
                    if (tool.isActive) {
                        onImageToPdfClick()
                    } else {
                        Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show()
                    }
                }
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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF21262D)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.label,
                tint = PrimaryText,
                modifier = Modifier.size(24.dp),
            )
        }

        Text(
            text = tool.label,
            color = SecondaryText,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

