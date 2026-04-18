package com.aidelle.sensorread.viewmodel

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aidelle.sensorread.data.HealthConnectAvailability
import com.aidelle.sensorread.data.HealthConnectManager
import com.aidelle.sensorread.data.api.RetrofitClient
import com.aidelle.sensorread.data.model.HealthDataBatch
import com.aidelle.sensorread.data.model.HealthRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ViewModel managing Health Connect data and server communication.
 */
class HealthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HealthViewModel"
    }

    val healthConnectManager = HealthConnectManager(application)

    // --- UI State ---

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    private val _healthRecords = MutableStateFlow<List<HealthRecord>>(emptyList())
    val healthRecords: StateFlow<List<HealthRecord>> = _healthRecords.asStateFlow()

    init {
        checkHealthConnectAvailability()
        scheduleBackgroundSync()
    }

    /**
     * Schedule a periodic sync every 15 minutes (minimum allowed by WorkManager).
     * For 5-minute sync, one would typically use Foreground Services, but for this
     * demo we will stick to WorkManager's minimum.
     */
    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<com.aidelle.sensorread.worker.HealthSyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication())
            .enqueueUniquePeriodicWork(
                "HealthSyncWork",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
    }

    /**
     * Check if Health Connect is available on this device.
     */
    fun checkHealthConnectAvailability() {
        val availability = healthConnectManager.checkAvailability()
        _uiState.value = _uiState.value.copy(
            healthConnectAvailable = availability is HealthConnectAvailability.Available,
            healthConnectStatus = when (availability) {
                is HealthConnectAvailability.Available -> "Health Connect is available"
                is HealthConnectAvailability.NotInstalled -> "Health Connect needs to be updated"
                is HealthConnectAvailability.NotSupported -> "Health Connect is not supported on this device"
            }
        )
    }

    /**
     * Check and update permission status.
     */
    fun checkPermissions() {
        viewModelScope.launch {
            try {
                val hasAll = healthConnectManager.hasAllPermissions()
                _uiState.value = _uiState.value.copy(
                    permissionsGranted = hasAll,
                    permissionStatus = if (hasAll) "All permissions granted" else "Some permissions missing"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error checking permissions", e)
                _uiState.value = _uiState.value.copy(
                    permissionStatus = "Error checking permissions: ${e.message}"
                )
            }
        }
    }

    /**
     * Called when permissions result is received from the activity.
     */
    fun onPermissionsResult() {
        checkPermissions()
    }

    /**
     * Read all health data from the last 24 hours.
     */
    fun readHealthData(hoursBack: Long = 24) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = "Reading health data..."
            )

            try {
                val end = Instant.now()
                val start = end.minus(hoursBack, ChronoUnit.HOURS)

                val records = healthConnectManager.readAllData(start, end)
                _healthRecords.value = records

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Read ${records.size} records from the last ${hoursBack}h",
                    lastReadTime = Instant.now().toString()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading health data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Error reading data: ${e.message}",
                    error = e.message
                )
            }
        }
    }

    /**
     * Send the currently loaded health records to the FastAPI server.
     */
    fun sendDataToServer() {
        val records = _healthRecords.value
        if (records.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "No data to send. Read health data first."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSending = true,
                statusMessage = "Sending ${records.size} records to server..."
            )

            try {
                val batch = HealthDataBatch(
                    deviceId = "${Build.MANUFACTURER} ${Build.MODEL}",
                    records = records
                )
                val response = RetrofitClient.getApiService().sendHealthData(batch)

                if (response.isSuccessful) {
                    val syncResponse = response.body()
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        serverConnected = true,
                        statusMessage = "✅ Sent ${syncResponse?.recordsStored ?: 0} records successfully",
                        lastSyncTime = Instant.now().toString(),
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        serverConnected = false,
                        statusMessage = "❌ Server error: ${response.code()} ${response.message()}",
                        error = "HTTP ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data to server", e)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    serverConnected = false,
                    statusMessage = "❌ Connection failed: ${e.message}",
                    error = e.message
                )
            }
        }
    }

    /**
     * Read health data and immediately send to server.
     */
    fun syncNow(hoursBack: Long = 24) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                statusMessage = "Syncing..."
            )

            try {
                // Step 1: Read data
                val end = Instant.now()
                val start = end.minus(hoursBack, ChronoUnit.HOURS)
                val records = healthConnectManager.readAllData(start, end)
                _healthRecords.value = records

                if (records.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "No health data found in the last ${hoursBack}h"
                    )
                    return@launch
                }

                // Step 2: Send to server
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Sending ${records.size} records..."
                )

                val batch = HealthDataBatch(
                    deviceId = "${Build.MANUFACTURER} ${Build.MODEL}",
                    records = records
                )
                val response = RetrofitClient.getApiService().sendHealthData(batch)

                if (response.isSuccessful) {
                    val syncResponse = response.body()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        serverConnected = true,
                        statusMessage = "✅ Synced ${syncResponse?.recordsStored ?: 0} records",
                        lastSyncTime = Instant.now().toString(),
                        lastReadTime = Instant.now().toString(),
                        error = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "❌ Server error: ${response.code()}",
                        error = "HTTP ${response.code()}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "❌ Sync failed: ${e.message}",
                    error = e.message
                )
            }
        }
    }

    /**
     * Check if the FastAPI server is reachable.
     */
    fun checkServerConnection() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.getApiService().getServerStatus()
                _uiState.value = _uiState.value.copy(
                    serverConnected = response.isSuccessful,
                    statusMessage = if (response.isSuccessful) {
                        val status = response.body()
                        "🟢 Server online (${status?.totalRecords ?: 0} records stored)"
                    } else {
                        "🔴 Server returned ${response.code()}"
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    serverConnected = false,
                    statusMessage = "🔴 Server unreachable: ${e.message}"
                )
            }
        }
    }

    /**
     * Update the server URL.
     */
    fun updateServerUrl(url: String) {
        RetrofitClient.setBaseUrl(url)
        _uiState.value = _uiState.value.copy(
            serverUrl = url,
            statusMessage = "Server URL updated to $url"
        )
        checkServerConnection()
    }
}

/**
 * UI state for the health data screen.
 */
data class HealthUiState(
    val healthConnectAvailable: Boolean = false,
    val healthConnectStatus: String = "Checking...",
    val permissionsGranted: Boolean = false,
    val permissionStatus: String = "Not checked",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val serverConnected: Boolean = false,
    val serverUrl: String = "http://10.0.2.2:8000/",
    val statusMessage: String = "Ready",
    val lastReadTime: String? = null,
    val lastSyncTime: String? = null,
    val error: String? = null,
)
