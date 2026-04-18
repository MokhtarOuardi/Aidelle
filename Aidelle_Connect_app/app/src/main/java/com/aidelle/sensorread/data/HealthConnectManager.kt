package com.aidelle.sensorread.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.aidelle.sensorread.data.model.DataTypes
import com.aidelle.sensorread.data.model.HealthRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Manager class wrapping Health Connect client operations.
 * Handles availability checks, permission requests, and data reading.
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectManager"

        /** All permissions this app requests from Health Connect. */
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        )
    }

    private var _client: HealthConnectClient? = null

    /**
     * Check if Health Connect is available on this device.
     * Returns a status message.
     */
    fun checkAvailability(): HealthConnectAvailability {
        val status = HealthConnectClient.getSdkStatus(context)
        return when (status) {
            HealthConnectClient.SDK_AVAILABLE -> {
                _client = HealthConnectClient.getOrCreate(context)
                HealthConnectAvailability.Available
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                HealthConnectAvailability.NotInstalled
            }
            else -> {
                HealthConnectAvailability.NotSupported
            }
        }
    }

    /**
     * Get the Health Connect client. Must call checkAvailability() first.
     */
    fun getClient(): HealthConnectClient {
        return _client ?: throw IllegalStateException(
            "Health Connect client not initialized. Call checkAvailability() first."
        )
    }

    /**
     * Check which permissions are already granted.
     */
    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            getClient().permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            emptySet()
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return PERMISSIONS.all { it in granted }
    }

    // ---- Data Reading Functions ----

    /**
     * Read heart rate records within a time range.
     */
    suspend fun readHeartRate(start: Instant, end: Instant): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = getClient().readRecords(request)
            response.records.flatMap { record ->
                record.samples.map { sample ->
                    HealthRecord(
                        dataType = DataTypes.HEART_RATE,
                        value = sample.beatsPerMinute.toDouble(),
                        unit = "bpm",
                        timestamp = sample.time.atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading heart rate", e)
            emptyList()
        }
    }

    /**
     * Read step count records within a time range.
     */
    suspend fun readSteps(start: Instant, end: Instant): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = getClient().readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    dataType = DataTypes.STEPS,
                    value = record.count.toDouble(),
                    unit = "steps",
                    timestamp = record.startTime.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    endTimestamp = record.endTime.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps", e)
            emptyList()
        }
    }

    /**
     * Read oxygen saturation (SpO2) records within a time range.
     */
    suspend fun readOxygenSaturation(start: Instant, end: Instant): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = getClient().readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    dataType = DataTypes.OXYGEN_SATURATION,
                    value = record.percentage.value,
                    unit = "%",
                    timestamp = record.time.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading oxygen saturation", e)
            emptyList()
        }
    }

    /**
     * Read sleep session records within a time range.
     */
    suspend fun readSleepSessions(start: Instant, end: Instant): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = getClient().readRecords(request)
            response.records.map { record ->
                val durationMinutes = java.time.Duration.between(
                    record.startTime, record.endTime
                ).toMinutes().toDouble()

                val stagesMap = record.stages.groupBy { it.stage }.mapValues { it.value.size }

                HealthRecord(
                    dataType = DataTypes.SLEEP,
                    value = durationMinutes,
                    unit = "minutes",
                    timestamp = record.startTime.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    endTimestamp = record.endTime.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    metadata = mapOf("stages" to stagesMap)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep sessions", e)
            emptyList()
        }
    }

    /**
     * Read body temperature records within a time range.
     */
    suspend fun readBodyTemperature(start: Instant, end: Instant): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = BodyTemperatureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val response = getClient().readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    dataType = DataTypes.BODY_TEMPERATURE,
                    value = record.temperature.inCelsius,
                    unit = "°C",
                    timestamp = record.time.atOffset(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading body temperature", e)
            emptyList()
        }
    }

    /**
     * Read ALL health data types within a time range.
     */
    suspend fun readAllData(start: Instant, end: Instant): List<HealthRecord> {
        val allRecords = mutableListOf<HealthRecord>()
        allRecords.addAll(readHeartRate(start, end))
        allRecords.addAll(readSteps(start, end))
        allRecords.addAll(readOxygenSaturation(start, end))
        allRecords.addAll(readSleepSessions(start, end))
        allRecords.addAll(readBodyTemperature(start, end))
        return allRecords
    }
}

/**
 * Represents Health Connect availability status.
 */
sealed class HealthConnectAvailability {
    object Available : HealthConnectAvailability()
    object NotInstalled : HealthConnectAvailability()
    object NotSupported : HealthConnectAvailability()
}
