package tech.youssefachraf.image_to_pdf.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import tech.youssefachraf.image_to_pdf.ui.files.FilesScreen
import tech.youssefachraf.image_to_pdf.ui.home.HomeScreen
import tech.youssefachraf.image_to_pdf.ui.picker.PreviewReorderScreen
import tech.youssefachraf.image_to_pdf.ui.search.SearchScreen
import tech.youssefachraf.image_to_pdf.ui.settings.SettingsScreen
import tech.youssefachraf.image_to_pdf.ui.splash.SplashScreen
import tech.youssefachraf.image_to_pdf.viewmodel.HomeViewModel
import tech.youssefachraf.image_to_pdf.viewmodel.PickerViewModel

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val FILES = "files"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PREVIEW_REORDER = "preview_reorder"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel = viewModel(),
    pickerViewModel: PickerViewModel = viewModel(),
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
