package tech.youssefachraf.image_to_pdf.ui.files

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import tech.youssefachraf.image_to_pdf.ui.home.components.PdfCard
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel

private val filterTabs = listOf("All Files", "Recent", "Favorites")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var renameTarget by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filteredPdfs = when (selectedTab) {
        1 -> uiState.recentPdfs.take(10)
        else -> uiState.recentPdfs
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Files", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = PrimaryText,
                ),
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, "Search", tint = PrimaryText)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                filterTabs.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(label, fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor = Color.White,
                            containerColor = DarkCard,
                            labelColor = SecondaryText,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    Icons.Default.FilterList,
                    "Filter",
                    tint = SecondaryText,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (filteredPdfs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No files found",
                        color = SecondaryText,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredPdfs, key = { it.uri.toString() }) { pdf ->
                        PdfCard(
                            pdfInfo = pdf,
                            onClick = { openPdf(context, pdf.uri) },
                            onShare = { sharePdf(context, pdf.uri) },
                            onRename = {
                                renameText = pdf.name.removeSuffix(".pdf")
                                renameTarget = Pair(pdf.uri, pdf.name)
                            },
                            onDelete = { homeViewModel.deletePdf(pdf.uri) },
                            onOpen = { openPdf(context, pdf.uri) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    renameTarget?.let { (uri, _) ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = DarkCard,
            title = { Text("Rename PDF", color = PrimaryText) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("New name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    homeViewModel.renamePdf(uri, renameText)
                    renameTarget = null
                }) {
                    Text("Rename", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        )
    }
}

private fun openPdf(context: android.content.Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) { }
}

private fun sharePdf(context: android.content.Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, null))
}

