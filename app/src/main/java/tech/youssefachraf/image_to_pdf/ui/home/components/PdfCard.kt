package tech.youssefachraf.image_to_pdf.ui.home.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PdfBadgeRed
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.createBitmap

@Composable
fun PdfCard(
    pdfInfo: PdfFileInfo,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PdfThumbnail(uri = pdfInfo.uri)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdfInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                AccentBlue.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${pdfInfo.pageCount} pg",
                            color = AccentBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = formatDate(pdfInfo.dateModified),
                        color = SecondaryText,
                        fontSize = 12.sp,
                    )

                    Text(
                        text = formatSize(pdfInfo.sizeBytes),
                        color = SecondaryText,
                        fontSize = 12.sp,
                    )
                }
            }

            IconButton(onClick = onShare) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = SecondaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { showMenu = false; onRename() },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = PdfBadgeRed) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { showMenu = false; onShare() },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = { showMenu = false; onOpen() },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfThumbnail(uri: Uri) {
    val context = LocalContext.current
    var thumbnail by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bmp = createBitmap(page.width * 2, page.height * 2)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        bmp
                    } else {
                        renderer.close()
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF21262D)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp)
            )
        }

        // PDF badge overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp)
                .background(PdfBadgeRed, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "PDF",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)
    return sdf.format(Date(millis))
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.US, "%d kB", bytes / 1_000)
        else -> "$bytes B"
    }
}

