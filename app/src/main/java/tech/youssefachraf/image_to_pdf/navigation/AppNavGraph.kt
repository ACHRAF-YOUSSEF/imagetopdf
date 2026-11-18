package tech.youssefachraf.image_to_pdf.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import tech.youssefachraf.image_to_pdf.ui.files.FilesScreen
import tech.youssefachraf.image_to_pdf.ui.home.HomeScreen
import tech.youssefachraf.image_to_pdf.ui.picker.PreviewReorderScreen
import tech.youssefachraf.image_to_pdf.ui.scan.SmartScanScreen
import tech.youssefachraf.image_to_pdf.ui.search.SearchScreen
import tech.youssefachraf.image_to_pdf.ui.settings.SettingsScreen
import tech.youssefachraf.image_to_pdf.ui.splash.SplashScreen
import tech.youssefachraf.image_to_pdf.ui.tools.CompressPdfScreen
import tech.youssefachraf.image_to_pdf.ui.tools.DocxToPdfScreen
import tech.youssefachraf.image_to_pdf.ui.tools.MergePdfScreen
import tech.youssefachraf.image_to_pdf.ui.tools.PdfToJpgScreen
import tech.youssefachraf.image_to_pdf.ui.tools.SplitPdfScreen
import tech.youssefachraf.image_to_pdf.ui.tools.TextToPdfScreen
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.ToolsViewModel

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val FILES = "files"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PREVIEW_REORDER = "preview_reorder"
    const val SMART_SCAN = "smart_scan"
    const val COMPRESS_PDF = "compress_pdf"
    const val PDF_TO_JPG = "pdf_to_jpg"
    const val MERGE_PDF = "merge_pdf"
    const val DOCX_TO_PDF = "docx_to_pdf"
    const val SPLIT_PDF = "split_pdf"
    const val TEXT_TO_PDF = "text_to_pdf"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel(),
    pickerViewModel: PickerViewModel = viewModel(),
    toolsViewModel: ToolsViewModel = viewModel(),
) {
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                homeViewModel = homeViewModel,
                pickerViewModel = pickerViewModel,
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToFiles = { navController.navigate(Routes.FILES) },
                onNavigateToPreview = { navController.navigate(Routes.PREVIEW_REORDER) },
                onNavigateToSmartScan = { navController.navigate(Routes.SMART_SCAN) },
                onNavigateToCompress = { navController.navigate(Routes.COMPRESS_PDF) },
                onNavigateToPdfToJpg = { navController.navigate(Routes.PDF_TO_JPG) },
                onNavigateToMerge = { navController.navigate(Routes.MERGE_PDF) },
                onNavigateToDocxToPdf = { navController.navigate(Routes.DOCX_TO_PDF) },
                onNavigateToSplit = { navController.navigate(Routes.SPLIT_PDF) },
                onNavigateToTextToPdf = { navController.navigate(Routes.TEXT_TO_PDF) },
            )
        }

        composable(Routes.PREVIEW_REORDER) {
            PreviewReorderScreen(
                pickerViewModel = pickerViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onDone = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }

        composable(Routes.SMART_SCAN) {
            SmartScanScreen(
                pickerViewModel = pickerViewModel,
                onNavigateBack = { navController.popBackStack() },
                onProceedToReorder = {
                    navController.navigate(Routes.PREVIEW_REORDER) {
                        popUpTo(Routes.SMART_SCAN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.COMPRESS_PDF) {
            CompressPdfScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PDF_TO_JPG) {
            PdfToJpgScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MERGE_PDF) {
            MergePdfScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.DOCX_TO_PDF) {
            DocxToPdfScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SPLIT_PDF) {
            SplitPdfScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TEXT_TO_PDF) {
            TextToPdfScreen(
                toolsViewModel = toolsViewModel,
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FILES) {
            FilesScreen(
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                homeViewModel = homeViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
