package com.aidelle.sensorread.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aidelle.sensorread.data.HealthConnectManager
import com.aidelle.sensorread.data.api.RetrofitClient
import com.aidelle.sensorread.data.model.HealthDataBatch
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("HealthSyncWorker", "Starting background sync...")
        val healthConnectManager = HealthConnectManager(applicationContext)

        try {
            if (!healthConnectManager.hasAllPermissions()) {
                Log.w("HealthSyncWorker", "Missing permissions for background sync")
                return Result.failure()
            }

            val end = Instant.now()
            val start = end.minus(24, ChronoUnit.HOURS)
            val records = healthConnectManager.readAllData(start, end)

            if (records.isEmpty()) {
                Log.d("HealthSyncWorker", "No data to sync")
                return Result.success()
            }

            val batch = HealthDataBatch(
                deviceId = "${Build.MANUFACTURER} ${Build.MODEL}",
                records = records
            )

            val response = RetrofitClient.getApiService().sendHealthData(batch)
            return if (response.isSuccessful) {
                Log.d("HealthSyncWorker", "Successfully synced ${records.size} records")
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
