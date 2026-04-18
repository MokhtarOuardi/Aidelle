package com.aidelle.sensorread.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.aidelle.sensorread.data.model.DataTypes
import com.aidelle.sensorread.data.model.HealthRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

/**
 * Manager class wrapping Android SensorManager for accelerometer and gyroscope readings.
 * Buffers the latest readings and exposes them as HealthRecord objects.
 */
class SensorDataManager(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SensorDataManager"
        private const val MAX_BUFFER_SIZE = 100
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val accelerometerBuffer = mutableListOf<HealthRecord>()
    private val gyroscopeBuffer = mutableListOf<HealthRecord>()

    // Latest raw values for accident detection
    var latestAcceleration: FloatArray = floatArrayOf(0f, 0f, 0f)
        private set
    var latestGyroscope: FloatArray = floatArrayOf(0f, 0f, 0f)
        private set
    var latestAccelMagnitude: Float = 0f
        private set
    var latestGyroMagnitude: Float = 0f
        private set

    // Listener for real-time acceleration updates (used by AccidentDetector)
    var onAccelerationUpdate: ((magnitude: Float, values: FloatArray) -> Unit)? = null
    var onGyroscopeUpdate: ((magnitude: Float, values: FloatArray) -> Unit)? = null

    val hasAccelerometer: Boolean get() = accelerometer != null
    val hasGyroscope: Boolean get() = gyroscope != null

    /**
     * Start listening for accelerometer and gyroscope sensor events.
     */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer listener registered")
        } ?: Log.w(TAG, "No accelerometer sensor available on this device")

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Gyroscope listener registered")
        } ?: Log.w(TAG, "No gyroscope sensor available on this device")
    }

    /**
     * Stop listening for sensor events (call in onPause or onDestroy).
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Sensor listeners unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        val now = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAcceleration = floatArrayOf(x, y, z)
                latestAccelMagnitude = magnitude

                val record = HealthRecord(
                    dataType = DataTypes.ACCELEROMETER,
                    value = magnitude.toDouble(),
                    unit = "m/s²",
                    timestamp = now,
                    metadata = mapOf(
                        "x" to x.toDouble(),
                        "y" to y.toDouble(),
                        "z" to z.toDouble()
                    )
                )

                synchronized(accelerometerBuffer) {
                    accelerometerBuffer.add(record)
                    if (accelerometerBuffer.size > MAX_BUFFER_SIZE) {
                        accelerometerBuffer.removeAt(0)
                    }
                }

                onAccelerationUpdate?.invoke(magnitude, event.values)
            }

            Sensor.TYPE_GYROSCOPE -> {
                latestGyroscope = floatArrayOf(x, y, z)
                latestGyroMagnitude = magnitude

                val record = HealthRecord(
                    dataType = DataTypes.GYROSCOPE,
                    value = magnitude.toDouble(),
                    unit = "rad/s",
                    timestamp = now,
                    metadata = mapOf(
                        "x" to x.toDouble(),
                        "y" to y.toDouble(),
                        "z" to z.toDouble()
                    )
                )

                synchronized(gyroscopeBuffer) {
                    gyroscopeBuffer.add(record)
                    if (gyroscopeBuffer.size > MAX_BUFFER_SIZE) {
                        gyroscopeBuffer.removeAt(0)
                    }
                }

                onGyroscopeUpdate?.invoke(magnitude, event.values)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    /**
     * Get buffered accelerometer readings and clear the buffer.
     */
    fun drainAccelerometerRecords(): List<HealthRecord> {
        synchronized(accelerometerBuffer) {
            val records = accelerometerBuffer.toList()
            accelerometerBuffer.clear()
            return records
        }
    }

    /**
     * Get buffered gyroscope readings and clear the buffer.
     */
    fun drainGyroscopeRecords(): List<HealthRecord> {
        synchronized(gyroscopeBuffer) {
            val records = gyroscopeBuffer.toList()
            gyroscopeBuffer.clear()
            return records
        }
    }

    /**
     * Get the latest single reading of each type (for dashboard display).
     */
    fun getLatestReadings(): List<HealthRecord> {
        val results = mutableListOf<HealthRecord>()
        synchronized(accelerometerBuffer) {
            accelerometerBuffer.lastOrNull()?.let { results.add(it) }
        }
        synchronized(gyroscopeBuffer) {
            gyroscopeBuffer.lastOrNull()?.let { results.add(it) }
        }
        return results
    }
}
