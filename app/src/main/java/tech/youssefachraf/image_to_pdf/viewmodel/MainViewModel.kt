package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.youssefachraf.image_to_pdf.data.FavoritesRepository
import tech.youssefachraf.image_to_pdf.utils.PdfConverter
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo
import tech.youssefachraf.image_to_pdf.utils.PdfStorageFolder
import tech.youssefachraf.image_to_pdf.utils.PdfToolkit

enum class PdfSortOrder(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first"),
}

data class HomeUiState(
    val recentPdfs: List<PdfFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: PdfSortOrder = PdfSortOrder.DATE_DESC,
    val favoriteUris: Set<String> = emptySet(),
    val linkedFolderUri: String? = null,
    val linkedFolderLabel: String? = null,
    val isFolderBannerDismissed: Boolean = false,
    val statusMessage: String? = null,
)

class HomeViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher,
    private val favoritesRepository: FavoritesRepository = FavoritesRepository(application),
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        ioDispatcher = Dispatchers.IO,
        favoritesRepository = FavoritesRepository(application),
    )

    private var rawPdfs: List<PdfFileInfo> = emptyList()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        observeStoragePreferences()
        loadPdfs()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            favoritesRepository.favoriteUris.collectLatest { favs ->
                _uiState.update { it.copy(favoriteUris = favs) }
            }
        }
    }

    private fun observeStoragePreferences() {
        viewModelScope.launch {
            favoritesRepository.isFolderBannerDismissed.collectLatest { dismissed ->
                _uiState.update { it.copy(isFolderBannerDismissed = dismissed) }
            }
        }
        viewModelScope.launch {
            favoritesRepository.linkedTreeUri.collectLatest { savedUri ->
                val app = getApplication<Application>()
                val parsed = savedUri?.takeIf { it.isNotBlank() }?.toUri()
                val activeUri = if (parsed != null && PdfStorageFolder.hasActivePermission(app, parsed)) {
                    parsed
                } else {
                    PdfStorageFolder.getActiveTreeUri(app)
                }
                val activeStr = activeUri?.toString()
                val label = activeUri?.let(PdfStorageFolder::formatTreeUriDisplayName)
                val changed = activeStr != _uiState.value.linkedFolderUri
                PdfStorageFolder.setPreferredTreeUri(activeUri)
                _uiState.update {
                    it.copy(
                        linkedFolderUri = activeStr,
                        linkedFolderLabel = label,
                    )
                }
                if (changed && activeUri != null) {
                    loadPdfs()
                }
            }
        }
    }

    fun loadPdfs() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            val pdfs = PdfConverter.queryAllPdfs(getApplication())
            rawPdfs = pdfs
            _uiState.update { state ->
                state.copy(
                    recentPdfs = sortPdfs(pdfs, state.sortOrder),
                    isLoading = false
                )
            }
        }
    }

    fun onStorageFolderSelected(treeUri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val app = getApplication<Application>()
            PdfStorageFolder.takePersistablePermission(app, treeUri)
            PdfStorageFolder.setPreferredTreeUri(treeUri)
            val label = PdfStorageFolder.formatTreeUriDisplayName(treeUri)
            favoritesRepository.setLinkedTreeUri(treeUri.toString())
            favoritesRepository.setFolderBannerDismissed(false)
            _uiState.update {
                it.copy(
                    linkedFolderUri = treeUri.toString(),
                    linkedFolderLabel = label,
                    isFolderBannerDismissed = false,
                    statusMessage = "Linked folder: $label"
                )
            }
            loadPdfs()
        }
    }

    fun disconnectStorageFolder() {
        viewModelScope.launch(ioDispatcher) {
            val app = getApplication<Application>()
            _uiState.value.linkedFolderUri?.toUri()?.let { uri ->
                PdfStorageFolder.releasePersistablePermission(app, uri)
            }
            PdfStorageFolder.setPreferredTreeUri(null)
            favoritesRepository.setLinkedTreeUri(null)
            _uiState.update {
                it.copy(
                    linkedFolderUri = null,
                    linkedFolderLabel = null,
                    statusMessage = "Disconnected linked folder"
                )
            }
            loadPdfs()
        }
    }

    fun dismissFolderBanner() {
        viewModelScope.launch(ioDispatcher) {
            favoritesRepository.setFolderBannerDismissed(true)
            _uiState.update { it.copy(isFolderBannerDismissed = true) }
        }
    }

    fun insertPdfAtTop(pdfInfo: PdfFileInfo) {
        insertPdfsAtTop(listOf(pdfInfo))
    }

    fun insertPdfsAtTop(newPdfs: List<PdfFileInfo>) {
        if (newPdfs.isEmpty()) return
        val newUris = newPdfs.mapTo(HashSet()) { it.uri.toString() }
        val combined = newPdfs + rawPdfs.filterNot { it.uri.toString() in newUris }
        rawPdfs = combined
        _uiState.update { state ->
            state.copy(recentPdfs = sortPdfs(combined, state.sortOrder))
        }
    }

    fun importPdfs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isImporting = true, statusMessage = null) }
            try {
                val imported = PdfToolkit.importPdfs(getApplication(), uris)
                insertPdfsAtTop(imported)
                val msg = if (imported.size == 1) {
                    "Imported \"${imported.first().name}\""
                } else {
                    "Imported ${imported.size} PDFs"
                }
                _uiState.update { it.copy(isImporting = false, statusMessage = msg) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        statusMessage = e.message ?: "Failed to import PDF"
                    )
                }
            }
        }
    }

    fun toggleFavorite(uri: Uri, pdfName: String? = null) {
        viewModelScope.launch(ioDispatcher) {
            favoritesRepository.toggleFavorite(uri.toString(), pdfName)
        }
    }

    fun setSortOrder(order: PdfSortOrder) {
        _uiState.update { state ->
            state.copy(
                sortOrder = order,
                recentPdfs = sortPdfs(rawPdfs, order)
            )
        }
    }

    fun deletePdf(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val resolvedName = rawPdfs.firstOrNull { it.uri == uri }?.name
            if (PdfConverter.deletePdf(getApplication(), uri)) {
                favoritesRepository.removeFavorite(uri.toString(), resolvedName)
                rawPdfs = rawPdfs.filter { it.uri != uri }
                _uiState.update { state ->
                    state.copy(
                        recentPdfs = sortPdfs(rawPdfs, state.sortOrder),
                        statusMessage = "PDF deleted"
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "Could not delete PDF") }
            }
        }
    }

    fun renamePdf(uri: Uri, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val previousName = rawPdfs.firstOrNull { it.uri == uri }?.name
            if (PdfConverter.renamePdf(getApplication(), uri, trimmed)) {
                val targetName = PdfConverter.ensurePdfExtension(trimmed)
                if (previousName != null) {
                    favoritesRepository.renameFavorite(uri.toString(), previousName, targetName)
                }
                loadPdfs()
                _uiState.update { it.copy(statusMessage = "Renamed to $targetName") }
            } else {
                _uiState.update { it.copy(statusMessage = "Could not rename PDF") }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    companion object {
        fun sortPdfs(list: List<PdfFileInfo>, order: PdfSortOrder): List<PdfFileInfo> {
            return when (order) {
                PdfSortOrder.DATE_DESC -> list.sortedByDescending { it.dateModified }
                PdfSortOrder.DATE_ASC -> list.sortedBy { it.dateModified }
                PdfSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                PdfSortOrder.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                PdfSortOrder.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            }
        }
    }
}
