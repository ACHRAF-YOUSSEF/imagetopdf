package tech.youssefachraf.image_to_pdf.viewmodel

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.youssefachraf.image_to_pdf.data.FavoritesRepository
import tech.youssefachraf.image_to_pdf.testutil.ShadowFileProvider
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfDocument
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfDocumentPage
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfRenderer
import tech.youssefachraf.image_to_pdf.testutil.ShadowPdfRendererPage
import tech.youssefachraf.image_to_pdf.utils.CompressionLevel
import tech.youssefachraf.image_to_pdf.utils.JpgQuality
import tech.youssefachraf.image_to_pdf.utils.PageSize
import tech.youssefachraf.image_to_pdf.utils.PdfFileInfo
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28],
    shadows = [
        ShadowFileProvider::class,
        ShadowPdfDocument::class,
        ShadowPdfDocumentPage::class,
        ShadowPdfRenderer::class,
        ShadowPdfRendererPage::class,
    ]
)
class HomeAndPickerViewModelTest {

    private lateinit var application: Application
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun homeViewModel_sortPdfsAndFavoritesAndSearchWorkCorrectly() = runTest(testDispatcher) {
        val prefsFile = File.createTempFile("favs_test_", ".preferences_pb", application.cacheDir)
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { prefsFile }
        val repo = FavoritesRepository(dataStore)
        val vm = HomeViewModel(application, testDispatcher, repo)

        val alpha = PdfFileInfo("content://pdf/1".toUri(), "alpha.pdf", 2, 300L, 100L)
        val beta = PdfFileInfo("content://pdf/2".toUri(), "beta.pdf", 5, 100L, 300L)
        val gamma = PdfFileInfo("content://pdf/3".toUri(), "gamma.pdf", 1, 200L, 200L)

        vm.insertPdfsAtTop(listOf(alpha, beta, gamma))
        assertEquals(listOf("beta.pdf", "gamma.pdf", "alpha.pdf"), vm.uiState.value.recentPdfs.map { it.name })

        vm.setSortOrder(PdfSortOrder.DATE_ASC)
        assertEquals(listOf("alpha.pdf", "gamma.pdf", "beta.pdf"), vm.uiState.value.recentPdfs.map { it.name })

        vm.setSortOrder(PdfSortOrder.NAME_ASC)
        assertEquals(listOf("alpha.pdf", "beta.pdf", "gamma.pdf"), vm.uiState.value.recentPdfs.map { it.name })

        vm.setSortOrder(PdfSortOrder.SIZE_DESC)
        assertEquals(listOf("alpha.pdf", "gamma.pdf", "beta.pdf"), vm.uiState.value.recentPdfs.map { it.name })

        vm.setSortOrder(PdfSortOrder.SIZE_ASC)
        assertEquals(listOf("beta.pdf", "gamma.pdf", "alpha.pdf"), vm.uiState.value.recentPdfs.map { it.name })

        vm.toggleFavorite(alpha.uri)
        assertTrue(alpha.uri.toString() in vm.uiState.value.favoriteUris)
        vm.toggleFavorite(alpha.uri)
        assertFalse(alpha.uri.toString() in vm.uiState.value.favoriteUris)

        vm.setSearchQuery("beta")
        assertEquals("beta", vm.uiState.value.searchQuery)
        vm.clearStatusMessage()
        assertNull(vm.uiState.value.statusMessage)
    }

    @Test
    fun homeViewModel_persistsBannerDismissalAndMatchesFavoritesAcrossUriChanges() = runTest(testDispatcher) {
        val prefsFile = File.createTempFile("storage_prefs_test_", ".preferences_pb", application.cacheDir)
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { prefsFile }
        val repo = FavoritesRepository(dataStore)
        val vm = HomeViewModel(application, testDispatcher, repo)

        assertFalse(vm.uiState.value.isFolderBannerDismissed)
        vm.dismissFolderBanner()
        assertTrue(vm.uiState.value.isFolderBannerDismissed)

        val originalPdf = PdfFileInfo("content://media/external/downloads/10".toUri(), "Report.pdf", 3, 4096L, 100L)
        vm.toggleFavorite(originalPdf.uri, originalPdf.name)
        assertTrue(FavoritesRepository.isFavorite(originalPdf, vm.uiState.value.favoriteUris))

        val rebuiltAppSafPdf = PdfFileInfo(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FImageToPdf/document/Report.pdf".toUri(),
            "Report.pdf",
            3,
            4096L,
            100L,
        )
        assertTrue(FavoritesRepository.isFavorite(rebuiltAppSafPdf, vm.uiState.value.favoriteUris))

        vm.toggleFavorite(rebuiltAppSafPdf.uri, rebuiltAppSafPdf.name)
        assertFalse(FavoritesRepository.isFavorite(rebuiltAppSafPdf, vm.uiState.value.favoriteUris))
        assertFalse(FavoritesRepository.isFavorite(originalPdf, vm.uiState.value.favoriteUris))
    }

    @Test
    fun pickerViewModel_selectionReorderAndPageSizeStateUpdates() {
        val vm = PickerViewModel(application, testDispatcher)
        val u1: Uri = "content://img/1".toUri()
        val u2: Uri = "content://img/2".toUri()
        val u3: Uri = "content://img/3".toUri()

        vm.setScannedImages(listOf(u1, u2))
        assertEquals(listOf(u1, u2), vm.uiState.value.selectedImages)

        vm.toggleImage(u3)
        assertEquals(listOf(u1, u2, u3), vm.uiState.value.selectedImages)

        vm.moveImage(0, 2)
        assertEquals(listOf(u2, u3, u1), vm.uiState.value.selectedImages)

        vm.removeImage(u3)
        assertEquals(listOf(u2, u1), vm.uiState.value.selectedImages)

        vm.setImagesSelected(listOf(u1, u3), selected = true)
        assertEquals(listOf(u2, u1, u3), vm.uiState.value.selectedImages)

        vm.setPageSize(PageSize.LETTER)
        assertEquals(PageSize.LETTER, vm.uiState.value.pageSize)

        vm.showNameDialog()
        assertTrue(vm.uiState.value.showNameDialog)
        vm.setPdfName("custom_doc")
        assertEquals("custom_doc", vm.uiState.value.pdfName)
        vm.dismissNameDialog()
        assertFalse(vm.uiState.value.showNameDialog)

        vm.clearSelection()
        assertTrue(vm.uiState.value.selectedImages.isEmpty())
        vm.reset()
        assertEquals(PageSize.A4, vm.uiState.value.pageSize)
    }

    @Test
    fun toolsViewModel_parsesRangesAndCreatesTextPdf() = runTest(testDispatcher) {
        assertEquals(
            setOf(0, 1, 2, 4),
            ToolsViewModel.parsePageRangeExpression("1-3, 5", pageCount = 6)
        )
        assertEquals(
            setOf(1, 2, 3),
            ToolsViewModel.parsePageRangeExpression("4-2, 99", pageCount = 5)
        )
        assertEquals(emptySet<Int>(), ToolsViewModel.parsePageRangeExpression("", pageCount = 5))

        val vm = ToolsViewModel(application, testDispatcher)
        vm.setCompressionLevel(CompressionLevel.EXTREME)
        assertEquals(CompressionLevel.EXTREME, vm.compressState.value.level)

        vm.setJpgQuality(JpgQuality.HD)
        assertEquals(JpgQuality.HD, vm.pdfToJpgState.value.quality)

        vm.setTextTitle("Meeting Notes")
        vm.setTextBody("Action item 1\nAction item 2")
        vm.setTextPageSize(PageSize.LETTER)
        vm.setTextOutputName("meeting_notes_test")

        val createdDeferred = CompletableDeferred<PdfFileInfo>()
        vm.runTextToPdf { createdDeferred.complete(it) }

        val createdInfo = withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                val info = createdDeferred.await()
                while (vm.textState.value.result == null) {
                    delay(10)
                }
                info
            }
        }

        assertNotNull(createdInfo)
        assertEquals("meeting_notes_test.pdf", createdInfo.name)
        assertNotNull(vm.textState.value.result)
    }
}
