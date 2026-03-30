package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class PickerUiState(
    val selectedImages: List<Uri> = emptyList(),
    val pdfName: String = "",
    val showNameDialog: Boolean = false,
    val pickerState: PickerState = PickerState.Idle,
)

class PickerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PickerUiState())
    val uiState: StateFlow<PickerUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        _uiState.update { state ->
            val existing = state.selectedImages.toHashSet()
            val newUris = uris.filter { it !in existing }
            state.copy(selectedImages = state.selectedImages + newUris)
        }
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
        val name = finalName ?: _uiState.value.pdfName.trim()
        if (name.isBlank()) return

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

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(showNameDialog = false, pickerState = PickerState.Converting) }
            try {
                val resultUri = PdfConverter.convert(
                    context = context,
                    uris = images,
                    pageSize = PageSize.A4,
                    fileName = name
                )
                val allPdfs = PdfConverter.queryAllPdfs(context)
                val newPdf = allPdfs.firstOrNull { it.uri == resultUri }
                    ?: PdfFileInfo(
                        uri = resultUri,
                        name = "$name.pdf",
                        pageCount = images.size,
                        sizeBytes = 0L,
                        dateModified = System.currentTimeMillis()
                    )
                _uiState.update { it.copy(pickerState = PickerState.Success(newPdf)) }
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
