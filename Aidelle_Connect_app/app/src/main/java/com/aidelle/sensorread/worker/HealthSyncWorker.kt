package com.aidelle.sensorread.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aidelle.sensorread.data.HealthConnectManager
import com.aidelle.sensorread.data.LocationDataManager
import com.aidelle.sensorread.data.SensorDataManager
import com.aidelle.sensorread.data.SensorPreferences
import com.aidelle.sensorread.data.api.RetrofitClient
import com.aidelle.sensorread.data.model.HealthDataBatch
import com.aidelle.sensorread.data.model.HealthRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("HealthSyncWorker", "Starting background sync...")
        val healthConnectManager = HealthConnectManager(applicationContext)
        val sensorPreferences = SensorPreferences(applicationContext)

        try {
            if (!healthConnectManager.hasAllPermissions()) {
                Log.w("HealthSyncWorker", "Missing permissions for background sync")
                return Result.failure()
            }

            // Read sensor toggle preferences
            val toggles = sensorPreferences.toggles.first()

            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)
            val allRecords = mutableListOf<HealthRecord>()

            // Health Connect data (filtered by toggles)
            val hcRecords = healthConnectManager.readAllData(start, end)
            allRecords.addAll(hcRecords.filter { record ->
                when (record.dataType) {
                    "heart_rate" -> toggles.heartRate
                    "steps" -> toggles.steps
                    "oxygen_saturation" -> toggles.oxygenSaturation
                    "sleep" -> toggles.sleep
                    "body_temperature" -> toggles.bodyTemperature
                    else -> true
                }
            })

            // Brief gyroscope/accelerometer capture (if enabled)
            if (toggles.gyroscope) {
                val sensorManager = SensorDataManager(applicationContext)
                sensorManager.start()
                delay(2000) // Collect 2 seconds of sensor data
                allRecords.addAll(sensorManager.drainAccelerometerRecords())
                allRecords.addAll(sensorManager.drainGyroscopeRecords())
                sensorManager.stop()
            }

            // Brief GPS capture (if enabled and permitted)
            if (toggles.gps) {
                val locationManager = LocationDataManager(applicationContext)
                if (locationManager.hasLocationPermission()) {
                    locationManager.startTracking()
                    delay(5000) // Wait for a location fix
                    allRecords.addAll(locationManager.drainLocationRecords())
                    locationManager.stopTracking()
                }
            }

            if (allRecords.isEmpty()) {
                Log.d("HealthSyncWorker", "No data to sync")
                return Result.success()
            }

            val batch = HealthDataBatch(
                deviceId = "${Build.MANUFACTURER} ${Build.MODEL}",
                records = allRecords
            )

            val response = RetrofitClient.getApiService().sendHealthData(batch)
            return if (response.isSuccessful) {
                Log.d("HealthSyncWorker", "Successfully synced ${allRecords.size} records")
                Result.success()
            } else {
                Log.e("HealthSyncWorker", "Server error: ${response.code()}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("HealthSyncWorker", "Sync failed", e)
            return Result.retry()
        }
    }
}
