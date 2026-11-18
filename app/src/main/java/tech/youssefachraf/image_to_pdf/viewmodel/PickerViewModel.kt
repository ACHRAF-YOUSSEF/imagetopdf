package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.youssefachraf.image_to_pdf.utils.PageSize
import tech.youssefachraf.image_to_pdf.utils.PdfConverter
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo

sealed class PickerState {
    data object Idle : PickerState()
    data object Converting : PickerState()
    data class Success(val pdfInfo: PdfFileInfo) : PickerState()
    data class Error(val message: String) : PickerState()
    data class FileConflict(
        val originalName: String,
        val suggestedName: String
    ) : PickerState()
}

data class GalleryImage(
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val albumId: Long,
    val albumName: String
)

data class GalleryAlbum(
    val id: Long?,
    val name: String,
    val imageCount: Int,
    val coverUri: Uri?
)

data class PickerUiState(
    val galleryImages: List<GalleryImage> = emptyList(),
    val galleryAlbums: List<GalleryAlbum> = emptyList(),
    val selectedAlbumId: Long? = null,
    val isGalleryLoading: Boolean = false,
    val selectedImages: List<Uri> = emptyList(),
    val pageSize: PageSize = PageSize.A4,
    val pdfName: String = "",
    val showNameDialog: Boolean = false,
    val pickerState: PickerState = PickerState.Idle,
)

class PickerViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, Dispatchers.IO)

    private val _uiState = MutableStateFlow(PickerUiState())
    val uiState: StateFlow<PickerUiState> = _uiState.asStateFlow()

    fun loadGalleryImages() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isGalleryLoading = true) }
            try {
                val resolver = getApplication<Application>().contentResolver
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME
                )
                val images = mutableListOf<GalleryImage>()
                resolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                    val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        images.add(
                            GalleryImage(
                                uri = uri,
                                displayName = cursor.getString(nameColumn) ?: "Image",
                                dateAdded = cursor.getLong(dateColumn),
                                albumId = cursor.getLong(bucketIdColumn),
                                albumName = cursor.getString(bucketNameColumn) ?: "Other"
                            )
                        )
                    }
                }
                val albums = images
                    .groupBy { it.albumId }
                    .map { (id, albumImages) ->
                        GalleryAlbum(
                            id = id,
                            name = albumImages.first().albumName,
                            imageCount = albumImages.size,
                            coverUri = albumImages.firstOrNull()?.uri
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                _uiState.update {
                    it.copy(
                        galleryImages = images,
                        galleryAlbums = albums,
                        selectedAlbumId = null,
                        isGalleryLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(isGalleryLoading = false) }
            }
        }
    }

    fun selectAlbum(albumId: Long?) {
        _uiState.update { it.copy(selectedAlbumId = albumId) }
    }

    fun toggleImage(uri: Uri) {
        _uiState.update { state ->
            val selected = state.selectedImages
            state.copy(
                selectedImages = if (uri in selected) selected.filterNot { it == uri }
                else selected + uri
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImages = emptyList()) }
    }

    fun setImagesSelected(uris: List<Uri>, selected: Boolean) {
        _uiState.update { state ->
            val current = state.selectedImages.toMutableList()
            if (selected) {
                val existing = current.toHashSet()
                uris.forEach { uri -> if (existing.add(uri)) current.add(uri) }
            } else {
                current.removeAll(uris.toSet())
            }
            state.copy(selectedImages = current)
        }
    }

    fun setScannedImages(uris: List<Uri>) {
        _uiState.update {
            it.copy(
                selectedImages = uris,
                pickerState = PickerState.Idle,
                showNameDialog = false
            )
        }
    }

    fun setPageSize(pageSize: PageSize) {
        _uiState.update { it.copy(pageSize = pageSize) }
    }

    fun removeImage(uri: Uri) {
        _uiState.update { state ->
            state.copy(selectedImages = state.selectedImages.filter { it != uri })
        }
    }

    fun moveImage(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            state.copy(selectedImages = list)
        }
    }

    fun showNameDialog() {
        val defaultName = PdfConverter.generateDefaultFileName()
        _uiState.update { it.copy(pdfName = defaultName, showNameDialog = true) }
    }

    fun setPdfName(name: String) {
        _uiState.update { it.copy(pdfName = name) }
    }

    fun dismissNameDialog() {
        _uiState.update { it.copy(showNameDialog = false) }
    }

    fun generatePdf(finalName: String? = null) {
        val context = getApplication<Application>()
        val name = (finalName ?: _uiState.value.pdfName).trim()
        if (name.isBlank()) return

        val currentPickerState = _uiState.value.pickerState
        val overwrite = finalName != null &&
            currentPickerState is PickerState.FileConflict &&
            finalName == currentPickerState.originalName

        if (finalName == null) {
            val (exists, suggested) = PdfConverter.resolveFileName(context, name)
            if (exists) {
                _uiState.update {
                    it.copy(
                        showNameDialog = false,
                        pickerState = PickerState.FileConflict(name, suggested)
                    )
                }
                return
            }
        }

        val images = _uiState.value.selectedImages
        if (images.isEmpty()) return
        val chosenPageSize = _uiState.value.pageSize

        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(showNameDialog = false, pickerState = PickerState.Converting) }
            try {
                val resultUri = PdfConverter.convert(
                    context = context,
                    uris = images,
                    pageSize = chosenPageSize,
                    fileName = name,
                    overwriteExisting = overwrite
                )
                val newPdf = PdfConverter.querySinglePdfInfo(
                    context = context,
                    uri = resultUri,
                    fallbackName = PdfConverter.ensurePdfExtension(name),
                    fallbackPageCount = images.size
                )
                _uiState.update { it.copy(pickerState = PickerState.Success(newPdf)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(pickerState = PickerState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun dismissConflict() {
        _uiState.update { it.copy(pickerState = PickerState.Idle, showNameDialog = true) }
    }

    fun reset() {
        _uiState.update { PickerUiState() }
    }
}
