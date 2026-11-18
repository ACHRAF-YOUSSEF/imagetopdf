package tech.youssefachraf.image_to_pdf.ui.search

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.youssefachraf.image_to_pdf.data.FavoritesRepository
import tech.youssefachraf.image_to_pdf.ui.common.RenamePdfDialog
import tech.youssefachraf.image_to_pdf.ui.common.openPdf
import tech.youssefachraf.image_to_pdf.ui.common.sharePdf
import tech.youssefachraf.image_to_pdf.ui.home.components.PdfCard
import tech.youssefachraf.image_to_pdf.ui.theme.AccentBlue
import tech.youssefachraf.image_to_pdf.ui.theme.DarkBackground
import tech.youssefachraf.image_to_pdf.ui.theme.DarkCard
import tech.youssefachraf.image_to_pdf.ui.theme.PrimaryText
import tech.youssefachraf.image_to_pdf.ui.theme.SecondaryText
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    homeViewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var renameTarget by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = remember(uiState.recentPdfs, query) {
        if (query.isBlank()) {
            uiState.recentPdfs
        } else {
            uiState.recentPdfs.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search PDFs…", color = SecondaryText) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrimaryText,
                            unfocusedTextColor = PrimaryText,
                            cursorColor = AccentBlue,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = DarkCard,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, "Clear", tint = SecondaryText)
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                ),
            )
        }
    ) { paddingValues ->
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (query.isBlank()) "Type to search" else "No results found",
                    color = SecondaryText,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(paddingValues)
                    .imePadding(),
                contentPadding = paddingValues,
            ) {
                items(
                    items = filtered,
                    key = { it.uri.toString() },
                    contentType = { "pdf_card" },
                ) { pdf ->
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
                        isFavorite = FavoritesRepository.isFavorite(pdf, uiState.favoriteUris),
                        onToggleFavorite = { homeViewModel.toggleFavorite(pdf.uri, pdf.name) },
                        appContext = context,
                    )
                }
                item(key = "search_bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    renameTarget?.let { (uri, _) ->
        RenamePdfDialog(
            name = renameText,
            onNameChange = { renameText = it },
            onConfirm = {
                homeViewModel.renamePdf(uri, renameText)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}
