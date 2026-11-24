package com.example.pdfscan.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pdfscan.AppContainer
import com.example.pdfscan.ui.home.HomeScreen
import com.example.pdfscan.ui.home.HomeViewModel
import com.example.pdfscan.ui.preview.PreviewScreen
import com.example.pdfscan.ui.preview.ScanSessionViewModel

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    // Activity-scoped so Home -> Preview share the scan session.
    val session: ScanSessionViewModel = viewModel(
        factory = viewModelFactory { initializer { ScanSessionViewModel(container.pdfRepository) } }
    )

    NavHost(nav, startDestination = "home") {
        composable("home") {
            val vm: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HomeViewModel(container.pdfRepository, container.settings) }
                }
            )
            HomeScreen(vm, onScanned = { uris ->
                session.startSession(uris)
                nav.navigate("preview")
            })
        }
        composable("preview") {
            PreviewScreen(session, onExit = {
                session.reset()
                nav.popBackStack("home", inclusive = false)   // idempotent
            })
        }
    }
}
