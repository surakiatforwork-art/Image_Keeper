package com.branchphotovault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.branchphotovault.LocalAppContainer
import com.branchphotovault.ui.screens.AddBranchScreen
import com.branchphotovault.ui.screens.BranchListScreen
import com.branchphotovault.ui.screens.BranchPreviewScreen
import com.branchphotovault.ui.screens.CapturePhotoScreen
import com.branchphotovault.ui.screens.PhotoViewerScreen
import com.branchphotovault.ui.screens.SettingsScreen
import com.branchphotovault.ui.viewmodel.AddBranchViewModel
import com.branchphotovault.ui.viewmodel.BranchListViewModel
import com.branchphotovault.ui.viewmodel.BranchPreviewViewModel
import com.branchphotovault.ui.viewmodel.PhotoViewerViewModel
import com.branchphotovault.ui.viewmodel.SettingsViewModel

@Composable
fun BranchPhotoVaultNavHost() {
    val navController = rememberNavController()
    val container = LocalAppContainer.current
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Destination.BranchList.route
    ) {
        composable(Destination.BranchList.route) {
            val viewModel: BranchListViewModel = viewModel(
                factory = BranchListViewModel.provideFactory(
                    branchRepository = container.branchRepository,
                    settingsRepository = container.settingsRepository
                )
            )
            BranchListScreen(
                viewModel = viewModel,
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
                onAddBranch = { navController.navigate(Destination.AddBranch.route) },
                onOpenBranch = { account, branchCode ->
                    navController.navigate(Destination.BranchPreview.createRoute(account, branchCode))
                }
            )
        }

        composable(Destination.AddBranch.route) {
            val viewModel: AddBranchViewModel = viewModel(
                factory = AddBranchViewModel.provideFactory(container.branchRepository)
            )
            AddBranchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onBranchSaved = { account, branchCode ->
                    navController.navigate(
                        Destination.BranchPreview.createRoute(account, branchCode)
                    ) {
                        popUpTo(Destination.BranchList.route)
                    }
                }
            )
        }

        composable(
            route = Destination.BranchPreview.route,
            arguments = listOf(
                navArgument("account") { type = NavType.StringType },
                navArgument("branchCode") { type = NavType.StringType }
            )
        ) { entry ->
            val account = entry.arguments?.getString("account").orEmpty()
            val branchCode = entry.arguments?.getString("branchCode").orEmpty()
            val capturedTempPath = entry.savedStateHandle
                .getStateFlow<String?>("captured_path", null)
                .collectAsStateWithLifecycle()
                .value
            val viewModel: BranchPreviewViewModel = viewModel(
                factory = BranchPreviewViewModel.provideFactory(
                    branchRepository = container.branchRepository,
                    photoRepository = container.photoRepository,
                    workManagerContext = context.applicationContext,
                    account = account,
                    branchCode = branchCode
                )
            )
            BranchPreviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenPhoto = { photoId ->
                    navController.navigate(
                        Destination.PhotoViewer.createRoute(account, branchCode, photoId)
                    )
                },
                onOpenCamera = {
                    navController.navigate(Destination.CapturePhoto.createRoute(account, branchCode))
                },
                capturedTempPath = capturedTempPath,
                onCapturedPathConsumed = {
                    entry.savedStateHandle["captured_path"] = null
                }
            )
        }

        composable(
            route = Destination.PhotoViewer.route,
            arguments = listOf(
                navArgument("account") { type = NavType.StringType },
                navArgument("branchCode") { type = NavType.StringType },
                navArgument("photoId") { type = NavType.StringType }
            )
        ) { entry ->
            val account = entry.arguments?.getString("account").orEmpty()
            val branchCode = entry.arguments?.getString("branchCode").orEmpty()
            val photoId = entry.arguments?.getString("photoId").orEmpty()
            val viewModel: PhotoViewerViewModel = viewModel(
                factory = PhotoViewerViewModel.provideFactory(
                    photoRepository = container.photoRepository,
                    account = account,
                    branchCode = branchCode,
                    initialPhotoId = photoId
                )
            )
            PhotoViewerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destination.CapturePhoto.route,
            arguments = listOf(
                navArgument("account") { type = NavType.StringType },
                navArgument("branchCode") { type = NavType.StringType }
            )
        ) { entry ->
            val account = entry.arguments?.getString("account").orEmpty()
            val branchCode = entry.arguments?.getString("branchCode").orEmpty()
            CapturePhotoScreen(
                account = account,
                branchCode = branchCode,
                onBack = { navController.popBackStack() },
                onCaptured = { tempPath ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("captured_path", tempPath)
                    navController.popBackStack()
                }
            )
        }

        composable(Destination.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    settingsRepository = container.settingsRepository,
                    photoRepository = container.photoRepository,
                    branchRepository = container.branchRepository
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
