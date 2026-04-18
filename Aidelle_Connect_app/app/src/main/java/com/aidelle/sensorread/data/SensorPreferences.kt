package com.aidelle.sensorread.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level DataStore singleton
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sensor_preferences")

/**
 * Persists per-sensor toggle settings using Jetpack DataStore Preferences.
 */
class SensorPreferences(private val context: Context) {

    companion object {
        val KEY_HEART_RATE = booleanPreferencesKey("sensor_heart_rate")
        val KEY_STEPS = booleanPreferencesKey("sensor_steps")
        val KEY_OXYGEN = booleanPreferencesKey("sensor_oxygen_saturation")
        val KEY_SLEEP = booleanPreferencesKey("sensor_sleep")
        val KEY_BODY_TEMP = booleanPreferencesKey("sensor_body_temperature")
        val KEY_GYROSCOPE = booleanPreferencesKey("sensor_gyroscope")
        val KEY_GPS = booleanPreferencesKey("sensor_gps")
        val KEY_ACCIDENT = booleanPreferencesKey("sensor_accident_detection")
    }

    /**
     * Observable flow of all sensor toggle states.
     */
    val toggles: Flow<SensorToggles> = context.dataStore.data.map { prefs ->
        SensorToggles(
            heartRate = prefs[KEY_HEART_RATE] ?: true,
            steps = prefs[KEY_STEPS] ?: true,
            oxygenSaturation = prefs[KEY_OXYGEN] ?: true,
            sleep = prefs[KEY_SLEEP] ?: true,
            bodyTemperature = prefs[KEY_BODY_TEMP] ?: true,
            gyroscope = prefs[KEY_GYROSCOPE] ?: true,
            gps = prefs[KEY_GPS] ?: false,              // GPS off by default (battery)
            accidentDetection = prefs[KEY_ACCIDENT] ?: true,
        )
    }

    /**
     * Update a single toggle value.
     */
    suspend fun updateToggle(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[key] = value
        }
    }
}

/**
 * Immutable snapshot of all sensor toggle states.
 */
data class SensorToggles(
    val heartRate: Boolean = true,
    val steps: Boolean = true,
    val oxygenSaturation: Boolean = true,
    val sleep: Boolean = true,
    val bodyTemperature: Boolean = true,
    val gyroscope: Boolean = true,
    val gps: Boolean = false,
    val accidentDetection: Boolean = true,
)
