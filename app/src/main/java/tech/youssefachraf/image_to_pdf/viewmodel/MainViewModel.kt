package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.youssefachraf.image_to_pdf.utils.PdfConverter
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo

data class HomeUiState(
    val recentPdfs: List<PdfFileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
)

class HomeViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    application: Application
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadPdfs()
    }

    fun loadPdfs() {
        viewModelScope.launch(dispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            val pdfs = PdfConverter.queryAllPdfs(getApplication())
            _uiState.update { it.copy(recentPdfs = pdfs, isLoading = false) }
        }
    }

    fun insertPdfAtTop(pdfInfo: PdfFileInfo) {
        _uiState.update { state ->
            state.copy(recentPdfs = listOf(pdfInfo) + state.recentPdfs)
        }
    }

    fun deletePdf(uri: Uri) {
        viewModelScope.launch(dispatcher) {
            if (PdfConverter.deletePdf(getApplication(), uri)) {
                _uiState.update { state ->
                    state.copy(recentPdfs = state.recentPdfs.filter { it.uri != uri })
                }
            }
        }
    }

    fun renamePdf(uri: Uri, newName: String) {
        viewModelScope.launch(dispatcher) {
            if (PdfConverter.renamePdf(getApplication(), uri, newName)) {
                loadPdfs()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }
}
