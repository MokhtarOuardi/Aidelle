package com.aidelle.sensorread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aidelle.sensorread.data.HealthConnectManager
import com.aidelle.sensorread.ui.screens.HomeScreen
import com.aidelle.sensorread.ui.theme.SensorReadTheme
import com.aidelle.sensorread.viewmodel.HealthViewModel

class MainActivity : ComponentActivity() {

    // Health Connect permission request launcher
    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            // Notify ViewModel that permissions were granted/denied
            healthViewModel?.onPermissionsResult()
        }

    private var healthViewModel: HealthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SensorReadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: HealthViewModel = viewModel()
                    healthViewModel = viewModel

                    val uiState by viewModel.uiState.collectAsState()
                    val healthRecords by viewModel.healthRecords.collectAsState()

                    // Check permissions on launch
                    androidx.compose.runtime.LaunchedEffect(uiState.healthConnectAvailable) {
                        if (uiState.healthConnectAvailable) {
                            viewModel.checkPermissions()
                            viewModel.checkServerConnection()
                        }
                    }

                    HomeScreen(
                        uiState = uiState,
                        healthRecords = healthRecords,
                        onRequestPermissions = {
                            requestPermissions.launch(HealthConnectManager.PERMISSIONS)
                        },
                        onSyncNow = { viewModel.syncNow() },
                        onReadData = { viewModel.readHealthData() },
                        onSendData = { viewModel.sendDataToServer() },
                        onCheckServer = { viewModel.checkServerConnection() },
                        onUpdateServerUrl = { url -> viewModel.updateServerUrl(url) }
                    )
                }
            }
        }
    }
}
