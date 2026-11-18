package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
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
import tech.youssefachraf.image_to_pdf.utils.CompressionLevel
import tech.youssefachraf.image_to_pdf.utils.CompressionResult
import tech.youssefachraf.image_to_pdf.utils.DocxSummary
import tech.youssefachraf.image_to_pdf.utils.DocxToPdfConverter
import tech.youssefachraf.image_to_pdf.utils.JpgQuality
import tech.youssefachraf.image_to_pdf.utils.PageSize
import tech.youssefachraf.image_to_pdf.utils.PdfConverter
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo
import tech.youssefachraf.image_to_pdf.utils.PdfToolkit

sealed interface ToolOperationState {
    data object Idle : ToolOperationState
    data class Running(val message: String) : ToolOperationState
    data class Error(val message: String) : ToolOperationState
}

data class SelectedPdfSource(
    val uri: Uri,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long,
)

data class CompressUiState(
    val source: SelectedPdfSource? = null,
    val level: CompressionLevel = CompressionLevel.BALANCED,
    val outputName: String = "",
    val state: ToolOperationState = ToolOperationState.Idle,
    val result: CompressionResult? = null,
)

data class MergeUiState(
    val items: List<SelectedPdfSource> = emptyList(),
    val outputName: String = "",
    val state: ToolOperationState = ToolOperationState.Idle,
    val result: PdfFileInfo? = null,
)

data class PdfToJpgUiState(
    val source: SelectedPdfSource? = null,
    val selectedPages: Set<Int> = emptySet(),
    val quality: JpgQuality = JpgQuality.STANDARD,
    val outputBaseName: String = "",
    val state: ToolOperationState = ToolOperationState.Idle,
    val exportedUris: List<Uri> = emptyList(),
)

data class DocxToPdfUiState(
    val sourceUri: Uri? = null,
    val summary: DocxSummary? = null,
    val pageSize: PageSize = PageSize.A4,
    val outputName: String = "",
    val isInspecting: Boolean = false,
    val state: ToolOperationState = ToolOperationState.Idle,
    val result: PdfFileInfo? = null,
)

data class SplitPdfUiState(
    val source: SelectedPdfSource? = null,
    val selectedPages: Set<Int> = emptySet(),
    val splitSeparate: Boolean = false,
    val outputBaseName: String = "",
    val state: ToolOperationState = ToolOperationState.Idle,
    val results: List<PdfFileInfo> = emptyList(),
)

data class TextToPdfUiState(
    val title: String = "",
    val body: String = "",
    val pageSize: PageSize = PageSize.A4,
    val outputName: String = PdfConverter.generateDefaultFileName("notes"),
    val state: ToolOperationState = ToolOperationState.Idle,
    val result: PdfFileInfo? = null,
)

class ToolsViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, Dispatchers.IO)

    private val _compressState = MutableStateFlow(CompressUiState())
    val compressState: StateFlow<CompressUiState> = _compressState.asStateFlow()

    private val _mergeState = MutableStateFlow(MergeUiState())
    val mergeState: StateFlow<MergeUiState> = _mergeState.asStateFlow()

    private val _pdfToJpgState = MutableStateFlow(PdfToJpgUiState())
    val pdfToJpgState: StateFlow<PdfToJpgUiState> = _pdfToJpgState.asStateFlow()

    private val _docxState = MutableStateFlow(DocxToPdfUiState())
    val docxState: StateFlow<DocxToPdfUiState> = _docxState.asStateFlow()

    private val _splitState = MutableStateFlow(SplitPdfUiState())
    val splitState: StateFlow<SplitPdfUiState> = _splitState.asStateFlow()

    private val _textState = MutableStateFlow(TextToPdfUiState())
    val textState: StateFlow<TextToPdfUiState> = _textState.asStateFlow()

    // --- Compress PDF ---

    fun selectCompressSource(pdfInfo: PdfFileInfo) {
        val source = SelectedPdfSource(pdfInfo.uri, pdfInfo.name, pdfInfo.pageCount, pdfInfo.sizeBytes)
        applyCompressSource(source)
    }

    fun selectCompressUri(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val source = loadPdfSource(uri)
            if (source == null) {
                _compressState.update {
                    it.copy(state = ToolOperationState.Error("Selected file is not a valid PDF."))
                }
            } else {
                applyCompressSource(source)
            }
        }
    }

    private fun applyCompressSource(source: SelectedPdfSource) {
        val base = PdfConverter.sanitizeBaseName(source.name) + "_compressed"
        val (_, unique) = PdfConverter.resolveFileName(getApplication(), base)
        _compressState.update {
            it.copy(
                source = source,
                outputName = unique,
                state = ToolOperationState.Idle,
                result = null
            )
        }
    }

    fun setCompressionLevel(level: CompressionLevel) {
        _compressState.update { it.copy(level = level) }
    }

    fun setCompressOutputName(name: String) {
        _compressState.update { it.copy(outputName = name) }
    }

    fun runCompress(onPdfCreated: (PdfFileInfo) -> Unit) {
        val current = _compressState.value
        val source = current.source ?: return
        val name = current.outputName.trim()
        if (name.isBlank()) return

        viewModelScope.launch(ioDispatcher) {
            _compressState.update {
                it.copy(state = ToolOperationState.Running("Compressing PDF pages…"), result = null)
            }
            try {
                val (_, uniqueBase) = PdfConverter.resolveFileName(getApplication(), name)
                val result = PdfToolkit.compressPdf(
                    context = getApplication(),
                    sourceUri = source.uri,
                    level = current.level,
                    outputBaseName = uniqueBase
                )
                onPdfCreated(result.pdfInfo)
                _compressState.update {
                    it.copy(state = ToolOperationState.Idle, result = result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _compressState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Compression failed"))
                }
            }
        }
    }

    // --- Merge PDF ---

    fun addMergePdf(pdfInfo: PdfFileInfo) {
        val source = SelectedPdfSource(pdfInfo.uri, pdfInfo.name, pdfInfo.pageCount, pdfInfo.sizeBytes)
        appendMergeSources(listOf(source))
    }

    fun addMergeUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            val loaded = uris.mapNotNull { loadPdfSource(it) }
            if (loaded.isEmpty()) {
                _mergeState.update {
                    it.copy(state = ToolOperationState.Error("Selected file(s) could not be read as PDFs."))
                }
                return@launch
            }
            appendMergeSources(loaded)
        }
    }

    private fun appendMergeSources(newSources: List<SelectedPdfSource>) {
        _mergeState.update { state ->
            val existingUris = state.items.mapTo(HashSet()) { it.uri }
            val merged = state.items + newSources.filterNot { it.uri in existingUris }
            val defaultName = state.outputName.ifBlank {
                PdfConverter.generateDefaultFileName("merged")
            }
            state.copy(
                items = merged,
                outputName = defaultName,
                state = ToolOperationState.Idle,
                result = null
            )
        }
    }

    fun moveMergeItem(from: Int, to: Int) {
        _mergeState.update { state ->
            val list = state.items.toMutableList()
            if (from in list.indices && to in list.indices) {
                val moved = list.removeAt(from)
                list.add(to, moved)
            }
            state.copy(items = list)
        }
    }

    fun removeMergeItem(uri: Uri) {
        _mergeState.update { state ->
            state.copy(items = state.items.filterNot { it.uri == uri })
        }
    }

    fun setMergeOutputName(name: String) {
        _mergeState.update { it.copy(outputName = name) }
    }

    fun runMerge(onPdfCreated: (PdfFileInfo) -> Unit) {
        val current = _mergeState.value
        if (current.items.size < 2) return
        val name = current.outputName.trim()
        if (name.isBlank()) return

        viewModelScope.launch(ioDispatcher) {
            _mergeState.update {
                it.copy(state = ToolOperationState.Running("Merging ${current.items.size} PDFs…"), result = null)
            }
            try {
                val (_, uniqueBase) = PdfConverter.resolveFileName(getApplication(), name)
                val info = PdfToolkit.mergePdfs(
                    context = getApplication(),
                    sourceUris = current.items.map { it.uri },
                    outputBaseName = uniqueBase
                )
                onPdfCreated(info)
                _mergeState.update { it.copy(state = ToolOperationState.Idle, result = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mergeState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Merge failed"))
                }
            }
        }
    }

    // --- PDF to JPG ---

    fun selectPdfToJpgSource(pdfInfo: PdfFileInfo) {
        val source = SelectedPdfSource(pdfInfo.uri, pdfInfo.name, pdfInfo.pageCount, pdfInfo.sizeBytes)
        applyPdfToJpgSource(source)
    }

    fun selectPdfToJpgUri(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val source = loadPdfSource(uri)
            if (source == null) {
                _pdfToJpgState.update {
                    it.copy(state = ToolOperationState.Error("Selected file is not a valid PDF."))
                }
            } else {
                applyPdfToJpgSource(source)
            }
        }
    }

    private fun applyPdfToJpgSource(source: SelectedPdfSource) {
        val allPages = (0 until source.pageCount).toSet()
        _pdfToJpgState.update {
            it.copy(
                source = source,
                selectedPages = allPages,
                outputBaseName = PdfConverter.sanitizeBaseName(source.name, "pdf_page"),
                state = ToolOperationState.Idle,
                exportedUris = emptyList()
            )
        }
    }

    fun toggleJpgPage(pageIndex: Int) {
        _pdfToJpgState.update { state ->
            val updated = if (pageIndex in state.selectedPages) {
                state.selectedPages - pageIndex
            } else {
                state.selectedPages + pageIndex
            }
            state.copy(selectedPages = updated)
        }
    }

    fun selectAllJpgPages() {
        _pdfToJpgState.update { state ->
            val count = state.source?.pageCount ?: 0
            state.copy(selectedPages = (0 until count).toSet())
        }
    }

    fun clearJpgPages() {
        _pdfToJpgState.update { it.copy(selectedPages = emptySet()) }
    }

    fun setJpgQuality(quality: JpgQuality) {
        _pdfToJpgState.update { it.copy(quality = quality) }
    }

    fun runPdfToJpg() {
        val current = _pdfToJpgState.value
        val source = current.source ?: return
        if (current.selectedPages.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            _pdfToJpgState.update {
                it.copy(
                    state = ToolOperationState.Running("Exporting ${current.selectedPages.size} image(s)…"),
                    exportedUris = emptyList()
                )
            }
            try {
                val uris = PdfToolkit.pdfToJpg(
                    context = getApplication(),
                    sourceUri = source.uri,
                    selectedPageIndices = current.selectedPages,
                    quality = current.quality,
                    baseName = current.outputBaseName
                )
                _pdfToJpgState.update {
                    it.copy(state = ToolOperationState.Idle, exportedUris = uris)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _pdfToJpgState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Export failed"))
                }
            }
        }
    }

    // --- DOCX to PDF ---

    fun selectDocxUri(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            _docxState.update {
                it.copy(
                    sourceUri = uri,
                    summary = null,
                    isInspecting = true,
                    state = ToolOperationState.Idle,
                    result = null
                )
            }
            try {
                val summary = DocxToPdfConverter.inspectDocx(getApplication(), uri)
                val base = PdfConverter.sanitizeBaseName(
                    summary.fileName.removeSuffix(".docx").removeSuffix(".DOCX"),
                    "word_document"
                )
                val (_, unique) = PdfConverter.resolveFileName(getApplication(), base)
                _docxState.update {
                    it.copy(
                        summary = summary,
                        outputName = unique,
                        isInspecting = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _docxState.update {
                    it.copy(
                        isInspecting = false,
                        state = ToolOperationState.Error(e.message ?: "Unable to read .docx file")
                    )
                }
            }
        }
    }

    fun setDocxPageSize(pageSize: PageSize) {
        _docxState.update { it.copy(pageSize = pageSize) }
    }

    fun setDocxOutputName(name: String) {
        _docxState.update { it.copy(outputName = name) }
    }

    fun runDocxToPdf(onPdfCreated: (PdfFileInfo) -> Unit) {
        val current = _docxState.value
        val uri = current.sourceUri ?: return
        val name = current.outputName.trim()
        if (name.isBlank()) return

        viewModelScope.launch(ioDispatcher) {
            _docxState.update {
                it.copy(state = ToolOperationState.Running("Converting Word document…"), result = null)
            }
            try {
                val (_, uniqueBase) = PdfConverter.resolveFileName(getApplication(), name)
                val info = DocxToPdfConverter.convertDocxToPdf(
                    context = getApplication(),
                    uri = uri,
                    outputBaseName = uniqueBase,
                    pageSize = current.pageSize
                )
                onPdfCreated(info)
                _docxState.update { it.copy(state = ToolOperationState.Idle, result = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _docxState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Conversion failed"))
                }
            }
        }
    }

    // --- Split / Extract PDF ---

    fun selectSplitSource(pdfInfo: PdfFileInfo) {
        val source = SelectedPdfSource(pdfInfo.uri, pdfInfo.name, pdfInfo.pageCount, pdfInfo.sizeBytes)
        applySplitSource(source)
    }

    fun selectSplitUri(uri: Uri) {
        viewModelScope.launch(ioDispatcher) {
            val source = loadPdfSource(uri)
            if (source == null) {
                _splitState.update {
                    it.copy(state = ToolOperationState.Error("Selected file is not a valid PDF."))
                }
            } else {
                applySplitSource(source)
            }
        }
    }

    private fun applySplitSource(source: SelectedPdfSource) {
        val base = PdfConverter.sanitizeBaseName(source.name, "document") + "_split"
        _splitState.update {
            it.copy(
                source = source,
                selectedPages = (0 until source.pageCount).toSet(),
                outputBaseName = base,
                state = ToolOperationState.Idle,
                results = emptyList()
            )
        }
    }

    fun toggleSplitPage(pageIndex: Int) {
        _splitState.update { state ->
            val updated = if (pageIndex in state.selectedPages) {
                state.selectedPages - pageIndex
            } else {
                state.selectedPages + pageIndex
            }
            state.copy(selectedPages = updated)
        }
    }

    fun selectAllSplitPages() {
        _splitState.update { state ->
            val count = state.source?.pageCount ?: 0
            state.copy(selectedPages = (0 until count).toSet())
        }
    }

    fun clearSplitPages() {
        _splitState.update { it.copy(selectedPages = emptySet()) }
    }

    fun applyPageRangeExpression(rangeExpression: String) {
        val count = _splitState.value.source?.pageCount ?: return
        val parsed = parsePageRangeExpression(rangeExpression, count)
        if (parsed.isNotEmpty()) {
            _splitState.update { it.copy(selectedPages = parsed) }
        }
    }

    fun setSplitSeparate(separate: Boolean) {
        _splitState.update { it.copy(splitSeparate = separate) }
    }

    fun setSplitOutputName(name: String) {
        _splitState.update { it.copy(outputBaseName = name) }
    }

    fun runSplit(onPdfsCreated: (List<PdfFileInfo>) -> Unit) {
        val current = _splitState.value
        val source = current.source ?: return
        if (current.selectedPages.isEmpty()) return
        val name = current.outputBaseName.trim().ifBlank { "extracted" }

        viewModelScope.launch(ioDispatcher) {
            _splitState.update {
                it.copy(state = ToolOperationState.Running("Extracting selected pages…"), results = emptyList())
            }
            try {
                val created = PdfToolkit.splitOrExtractPdf(
                    context = getApplication(),
                    sourceUri = source.uri,
                    pageIndices = current.selectedPages.sorted(),
                    splitIntoSeparateFiles = current.splitSeparate,
                    outputBaseName = name
                )
                onPdfsCreated(created)
                _splitState.update { it.copy(state = ToolOperationState.Idle, results = created) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _splitState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Split failed"))
                }
            }
        }
    }

    // --- Text to PDF ---

    fun setTextTitle(title: String) {
        _textState.update { it.copy(title = title) }
    }

    fun setTextBody(body: String) {
        _textState.update { it.copy(body = body) }
    }

    fun setTextPageSize(pageSize: PageSize) {
        _textState.update { it.copy(pageSize = pageSize) }
    }

    fun setTextOutputName(name: String) {
        _textState.update { it.copy(outputName = name) }
    }

    fun runTextToPdf(onPdfCreated: (PdfFileInfo) -> Unit) {
        val current = _textState.value
        if (current.title.isBlank() && current.body.isBlank()) return
        val name = current.outputName.trim().ifBlank {
            PdfConverter.generateDefaultFileName("notes")
        }

        viewModelScope.launch(ioDispatcher) {
            _textState.update {
                it.copy(state = ToolOperationState.Running("Creating PDF document…"), result = null)
            }
            try {
                val (_, uniqueBase) = PdfConverter.resolveFileName(getApplication(), name)
                val info = PdfToolkit.textToPdf(
                    context = getApplication(),
                    title = current.title,
                    body = current.body,
                    pageSize = current.pageSize,
                    outputBaseName = uniqueBase
                )
                onPdfCreated(info)
                _textState.update { it.copy(state = ToolOperationState.Idle, result = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _textState.update {
                    it.copy(state = ToolOperationState.Error(e.message ?: "Failed to create PDF"))
                }
            }
        }
    }

    private suspend fun loadPdfSource(uri: Uri): SelectedPdfSource? {
        val context = getApplication<Application>()
        val pageCount = PdfToolkit.readPageCount(context, uri)
        if (pageCount <= 0) return null
        val name = PdfToolkit.resolveDisplayName(context, uri, "document.pdf")
        val sizeBytes = PdfToolkit.measureUriSizeBytes(context, uri)
        return SelectedPdfSource(uri = uri, name = name, pageCount = pageCount, sizeBytes = sizeBytes)
    }

    companion object {
        /**
         * Parses 1-based page ranges like "1-3, 5, 8-10" into 0-based page indices within [0, pageCount).
         */
        fun parsePageRangeExpression(expression: String, pageCount: Int): Set<Int> {
            if (pageCount <= 0 || expression.isBlank()) return emptySet()
            val selected = linkedSetOf<Int>()
            expression.split(",").forEach { token ->
                val part = token.trim()
                if (part.isEmpty()) return@forEach
                if ('-' in part) {
                    val bounds = part.split('-', limit = 2)
                    val start = bounds[0].trim().toIntOrNull()
                    val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                    if (start != null && end != null) {
                        val low = minOf(start, end).coerceIn(1, pageCount)
                        val high = maxOf(start, end).coerceIn(1, pageCount)
                        for (pageNum in low..high) {
                            selected.add(pageNum - 1)
                        }
                    }
                } else {
                    val single = part.toIntOrNull()
                    if (single != null && single in 1..pageCount) {
                        selected.add(single - 1)
                    }
                }
            }
            return selected
        }
    }
}
