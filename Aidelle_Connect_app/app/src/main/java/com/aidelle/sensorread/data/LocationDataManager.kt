package com.aidelle.sensorread.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.aidelle.sensorread.data.model.DataTypes
import com.aidelle.sensorread.data.model.HealthRecord
import com.google.android.gms.location.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Manager class wrapping FusedLocationProviderClient for GPS data.
 * Collects location readings and converts them into HealthRecord objects.
 */
class LocationDataManager(private val context: Context) {

    companion object {
        private const val TAG = "LocationDataManager"
        private const val DEFAULT_INTERVAL_MS = 30_000L   // 30 seconds
        private const val FASTEST_INTERVAL_MS = 10_000L    // 10 seconds
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationBuffer = mutableListOf<HealthRecord>()
    private var isTracking = false

    // Latest location for accident detection context
    var latestLocation: Location? = null
        private set

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        DEFAULT_INTERVAL_MS
    )
        .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
        .setWaitForAccurateLocation(false)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                latestLocation = location

                val now = Instant.now()
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                val record = HealthRecord(
                    dataType = DataTypes.GPS,
                    value = location.speed.toDouble(),  // m/s
                    unit = "m/s",
                    timestamp = now,
                    metadata = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "altitude" to location.altitude,
                        "accuracy" to location.accuracy.toDouble(),
                        "speed" to location.speed.toDouble(),
                        "bearing" to location.bearing.toDouble()
                    )
                )

                synchronized(locationBuffer) {
                    locationBuffer.add(record)
                    if (locationBuffer.size > 100) {
                        locationBuffer.removeAt(0)
                    }
                }

                Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    /**
     * Check if location permission is granted.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start tracking location updates. Requires location permission.
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted. Cannot start tracking.")
            return
        }
        if (isTracking) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        isTracking = true
        Log.d(TAG, "GPS tracking started")
    }

    /**
     * Stop tracking location updates.
     */
    fun stopTracking() {
        if (!isTracking) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isTracking = false
        Log.d(TAG, "GPS tracking stopped")
    }

    /**
     * Get buffered location records and clear the buffer.
     */
    fun drainLocationRecords(): List<HealthRecord> {
        synchronized(locationBuffer) {
            val records = locationBuffer.toList()
            locationBuffer.clear()
            return records
        }
    }

    /**
     * Get the latest location reading (for dashboard display).
     */
    fun getLatestReading(): HealthRecord? {
        synchronized(locationBuffer) {
            return locationBuffer.lastOrNull()
        }
    }
}
