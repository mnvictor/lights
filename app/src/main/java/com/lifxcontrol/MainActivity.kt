package com.lifxcontrol

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifxcontrol.ui.AppScreen
import com.lifxcontrol.ui.MainViewModel
import com.lifxcontrol.ui.screens.ControlScreen
import com.lifxcontrol.ui.screens.HomeSelectionScreen
import com.lifxcontrol.ui.theme.LightControlTheme
import com.lifxcontrol.worker.LightMonitorWorker

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless; notifications are optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        LightMonitorWorker.schedule(this)

        setContent {
            LightControlTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                when (uiState.currentScreen) {
                    AppScreen.HOME_SELECTION -> HomeSelectionScreen(
                        uiState = uiState,
                        onApiTokenChange = viewModel::updateApiToken,
                        onConnect = viewModel::connectWithToken,
                        onSelectLocation = viewModel::selectLocation
                    )
                    AppScreen.CONTROL -> ControlScreen(
                        uiState = uiState,
                        onBack = viewModel::navigateToHomeSelection,
                        onToggleLights = viewModel::toggleLights,
                        onStartDisco = viewModel::startDiscoMode,
                        onStopDisco = viewModel::stopDiscoMode,
                        onRefresh = viewModel::refreshLights
                    )
                }
            }
        }
    }
}
