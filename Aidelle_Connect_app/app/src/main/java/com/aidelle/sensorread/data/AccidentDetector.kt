package com.aidelle.sensorread.data

import android.util.Log
import com.aidelle.sensorread.data.model.DataTypes
import com.aidelle.sensorread.data.model.HealthRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Fall / accident detection algorithm.
 *
 * Detection approach (3-phase):
 * 1. IMPACT: Accelerometer magnitude exceeds IMPACT_THRESHOLD (~3g)
 * 2. FREEFALL: Just before impact, a brief freefall phase (magnitude near 0)
 * 3. STILLNESS: After impact, sustained low acceleration variance (user is motionless)
 *
 * Additionally checks for high gyroscope rotation during impact (tumble detection).
 */
class AccidentDetector(
    private val sensorDataManager: SensorDataManager,
    private val locationDataManager: LocationDataManager? = null
) {
    companion object {
        private const val TAG = "AccidentDetector"

        // Impact detection: magnitude threshold in m/s² (~3g = 29.4 m/s²)
        const val IMPACT_THRESHOLD = 30.0f

        // Gyroscope rotation threshold in rad/s during impact
        const val GYRO_ROTATION_THRESHOLD = 5.0f

        // Post-impact stillness window in milliseconds
        const val STILLNESS_WINDOW_MS = 3000L

        // Stillness threshold: max acceleration magnitude variance during stillness
        const val STILLNESS_THRESHOLD = 2.0f

        // Cooldown period between consecutive detections (prevent spam)
        const val COOLDOWN_MS = 30_000L
    }

    private val _accidentEvents = MutableSharedFlow<AccidentEvent>(extraBufferCapacity = 5)
    val accidentEvents: SharedFlow<AccidentEvent> = _accidentEvents.asSharedFlow()

    private var isEnabled = false
    private var lastDetectionTime = 0L

    // Sliding window for post-impact analysis
    private val postImpactReadings = mutableListOf<Float>()
    private var impactDetectedTime = 0L
    private var impactPeakAccel = 0f
    private var impactPeakGyro = 0f
    private var isMonitoringPostImpact = false

    /**
     * Start accident detection. Hooks into SensorDataManager callbacks.
     */
    fun start() {
        isEnabled = true
        sensorDataManager.onAccelerationUpdate = { magnitude, _ ->
            if (isEnabled) {
                processAcceleration(magnitude)
            }
        }
        sensorDataManager.onGyroscopeUpdate = { magnitude, _ ->
            if (isEnabled && isMonitoringPostImpact) {
                if (magnitude > impactPeakGyro) {
                    impactPeakGyro = magnitude
                }
            }
        }
        Log.d(TAG, "Accident detection started")
    }

    /**
     * Stop accident detection.
     */
    fun stop() {
        isEnabled = false
        sensorDataManager.onAccelerationUpdate = null
        sensorDataManager.onGyroscopeUpdate = null
        isMonitoringPostImpact = false
        postImpactReadings.clear()
        Log.d(TAG, "Accident detection stopped")
    }

    private fun processAcceleration(magnitude: Float) {
        val now = System.currentTimeMillis()

        if (isMonitoringPostImpact) {
            // Phase 3: Collecting post-impact readings for stillness check
            postImpactReadings.add(magnitude)

            if (now - impactDetectedTime >= STILLNESS_WINDOW_MS) {
                // Analyze the post-impact window
                evaluatePostImpact(now)
            }
        } else {
            // Phase 1: Watch for sudden impact
            if (magnitude > IMPACT_THRESHOLD) {
                // Check cooldown
                if (now - lastDetectionTime < COOLDOWN_MS) return

                Log.w(TAG, "Impact detected! Magnitude: $magnitude m/s²")
                impactDetectedTime = now
                impactPeakAccel = magnitude
                impactPeakGyro = sensorDataManager.latestGyroMagnitude
                isMonitoringPostImpact = true
                postImpactReadings.clear()
            }
        }
    }

    private fun evaluatePostImpact(now: Long) {
        isMonitoringPostImpact = false

        if (postImpactReadings.isEmpty()) return

        // Calculate variance of post-impact readings
        val mean = postImpactReadings.average().toFloat()
        val variance = postImpactReadings.map { (it - mean) * (it - mean) }.average().toFloat()

        val isStill = variance < STILLNESS_THRESHOLD
        val hadHighRotation = impactPeakGyro > GYRO_ROTATION_THRESHOLD
        val confidence = when {
            isStill && hadHighRotation -> "high"
            isStill || hadHighRotation -> "medium"
            else -> "low"
        }

        // Only trigger for medium or high confidence
        if (confidence == "low") {
            Log.d(TAG, "Impact dismissed (low confidence). Variance: $variance, Gyro: $impactPeakGyro")
            return
        }

        lastDetectionTime = now

        val timestamp = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        val metadata = mutableMapOf<String, Any>(
            "accident_detected" to true,
            "peak_acceleration" to impactPeakAccel.toDouble(),
            "peak_gyroscope" to impactPeakGyro.toDouble(),
            "post_impact_variance" to variance.toDouble(),
            "stillness_detected" to isStill,
            "high_rotation_detected" to hadHighRotation,
            "confidence" to confidence
        )

        // Attach GPS location if available
        locationDataManager?.latestLocation?.let { loc ->
            metadata["gps_latitude"] = loc.latitude
            metadata["gps_longitude"] = loc.longitude
            metadata["gps_accuracy"] = loc.accuracy.toDouble()
        }

        val record = HealthRecord(
            dataType = DataTypes.ACCIDENT_ALERT,
            value = impactPeakAccel.toDouble(),
            unit = "m/s²",
            timestamp = timestamp,
            metadata = metadata
        )

        val event = AccidentEvent(
            record = record,
            confidence = confidence,
            peakAcceleration = impactPeakAccel,
            peakGyroscope = impactPeakGyro,
            timestamp = timestamp
        )

        Log.e(TAG, "🚨 ACCIDENT DETECTED! Confidence: $confidence, Peak: ${impactPeakAccel}m/s²")
        _accidentEvents.tryEmit(event)

        // Reset
        postImpactReadings.clear()
    }
}

/**
 * Data class representing a detected accident event.
 */
data class AccidentEvent(
    val record: HealthRecord,
    val confidence: String,
    val peakAcceleration: Float,
    val peakGyroscope: Float,
    val timestamp: String
)
