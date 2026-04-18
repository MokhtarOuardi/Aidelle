package com.aidelle.sensorread.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data models matching the FastAPI Pydantic schemas.
 */

data class HealthRecord(
    @SerializedName("data_type") val dataType: String,
    @SerializedName("value") val value: Double,
    @SerializedName("unit") val unit: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("end_timestamp") val endTimestamp: String? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
)

data class HealthDataBatch(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("records") val records: List<HealthRecord>
)

data class SyncResponse(
    @SerializedName("message") val message: String,
    @SerializedName("records_received") val recordsReceived: Int,
    @SerializedName("records_stored") val recordsStored: Int
)

data class ServerStatus(
    @SerializedName("status") val status: String,
    @SerializedName("service") val service: String,
    @SerializedName("total_records") val totalRecords: Int,
    @SerializedName("timestamp") val timestamp: String
)

/**
 * Enum of supported health data types — must match the FastAPI DataType enum values.
 */
object DataTypes {
    const val HEART_RATE = "heart_rate"
    const val STEPS = "steps"
    const val OXYGEN_SATURATION = "oxygen_saturation"
    const val SLEEP = "sleep"
    const val BODY_TEMPERATURE = "body_temperature"
    const val ACCELEROMETER = "accelerometer"
    const val GYROSCOPE = "gyroscope"
    const val GPS = "gps"
    const val ACCIDENT_ALERT = "accident_alert"
}
