package com.aidelle.sensorread.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import com.aidelle.sensorread.R
import com.aidelle.sensorread.data.SensorPreferences
import com.aidelle.sensorread.data.SensorToggles
import com.aidelle.sensorread.data.model.HealthRecord
import com.aidelle.sensorread.ui.components.DataSummaryCard
import com.aidelle.sensorread.ui.components.HealthDataCard
import com.aidelle.sensorread.ui.components.ServerStatusChip
import com.aidelle.sensorread.ui.theme.*
import com.aidelle.sensorread.viewmodel.HealthUiState

/**
 * Main home screen displaying health data dashboard, sensor toggles, and sync controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HealthUiState,
    healthRecords: List<HealthRecord>,
    sensorToggles: SensorToggles,
    onRequestPermissions: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onSyncNow: () -> Unit,
    onReadData: () -> Unit,
    onSendData: () -> Unit,
    onCheckServer: () -> Unit,
    onUpdateServerUrl: (String) -> Unit,
    onUpdateSensorToggle: (Preferences.Key<Boolean>, Boolean) -> Unit,
    onDismissAccidentAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(uiState.serverUrl) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.aidelle_logo),
                            contentDescription = "Aidelle Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(
                                text = "Aidelle Connect",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Health Connect Monitor",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    ServerStatusChip(connected = uiState.serverConnected)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // --- Accident Alert Banner ---
            if (uiState.accidentDetected) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AccidentColor.copy(alpha = 0.12f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Accident Alert",
                                tint = AccidentColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accident Detected!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccidentColor
                                )
                                Text(
                                    text = "A potential fall or impact was detected. Alert has been sent to the server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = onDismissAccidentAlert) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Dismiss",
                                    tint = AccidentColor
                                )
                            }
                        }
                    }
                }
            }

            // --- Settings Panel (collapsible) ---
            if (showSettings) {
                // Server Configuration
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Server Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("Server URL") },
                                placeholder = { Text("http://192.168.x.x:8000") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onUpdateServerUrl(serverUrlInput) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save")
                                }
                                OutlinedButton(
                                    onClick = onCheckServer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Test")
                                }
                            }
                        }
                    }
                }

                // Sensor Configuration
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sensor Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Enable or disable individual sensor readings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Health Connect sensors
                            Text(
                                text = "Health Connect",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            SensorToggleRow(
                                label = "Heart Rate",
                                emoji = "❤️",
                                checked = sensorToggles.heartRate,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_HEART_RATE, it) }
                            )
                            SensorToggleRow(
                                label = "Steps",
                                emoji = "👣",
                                checked = sensorToggles.steps,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_STEPS, it) }
                            )
                            SensorToggleRow(
                                label = "Blood Oxygen (SpO2)",
                                emoji = "🩸",
                                checked = sensorToggles.oxygenSaturation,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_OXYGEN, it) }
                            )
                            SensorToggleRow(
                                label = "Sleep",
                                emoji = "🛏️",
                                checked = sensorToggles.sleep,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_SLEEP, it) }
                            )
                            SensorToggleRow(
                                label = "Body Temperature",
                                emoji = "🌡️",
                                checked = sensorToggles.bodyTemperature,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_BODY_TEMP, it) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Device sensors
                            Text(
                                text = "Device Sensors",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GyroscopeColor
                            )

                            SensorToggleRow(
                                label = "Gyroscope / Accelerometer",
                                emoji = "📐",
                                checked = sensorToggles.gyroscope,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_GYROSCOPE, it) }
                            )
                            SensorToggleRow(
                                label = "GPS Location",
                                emoji = "📍",
                                checked = sensorToggles.gps,
                                onCheckedChange = {
                                    onUpdateSensorToggle(SensorPreferences.KEY_GPS, it)
                                    if (it && !uiState.locationPermissionGranted) {
                                        onRequestLocationPermission()
                                    }
                                },
                                subtitle = if (sensorToggles.gps && !uiState.locationPermissionGranted)
                                    "⚠️ Location permission required" else null
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // Safety
                            Text(
                                text = "Safety",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccidentColor
                            )

                            SensorToggleRow(
                                label = "Accident Detection",
                                emoji = "🚨",
                                checked = sensorToggles.accidentDetection,
                                onCheckedChange = { onUpdateSensorToggle(SensorPreferences.KEY_ACCIDENT, it) },
                                subtitle = "Detects falls using accelerometer & gyroscope"
                            )
                        }
                    }
                }
            }

            // --- Status Card ---
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.healthConnectAvailable)
                                    Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = if (uiState.healthConnectAvailable)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.healthConnectStatus,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (uiState.healthConnectAvailable) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.permissionsGranted)
                                        Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = if (uiState.permissionsGranted)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = uiState.permissionStatus,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        uiState.lastSyncTime?.let {
                            Text(
                                text = "Last sync: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // --- Permission Button ---
            if (uiState.healthConnectAvailable && !uiState.permissionsGranted) {
                item {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Health Connect Permissions", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // --- Action Buttons ---
            if (uiState.healthConnectAvailable && uiState.permissionsGranted) {
                item {
                    // Primary sync button
                    Button(
                        onClick = onSyncNow,
                        enabled = !uiState.isLoading && !uiState.isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (uiState.isLoading || uiState.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (uiState.isLoading) "Reading..." else "Sending...",
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Now", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onReadData,
                            enabled = !uiState.isLoading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Read Only")
                        }

                        OutlinedButton(
                            onClick = onSendData,
                            enabled = !uiState.isSending && healthRecords.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send Only")
                        }
                    }
                }

                // --- Latest History (5 entries) ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Sync History (Last 5 Entries)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (healthRecords.isEmpty()) {
                                Text(
                                    text = "No sync data available yet. Tap 'Read Only' or 'Sync Now' to fetch data.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                // Show latest 5 records (sorted by timestamp descending)
                                healthRecords.sortedByDescending { it.timestamp }.take(5).forEach { record ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = record.dataType.replace("Record", ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = record.value.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = try { record.timestamp.split("T").last().take(5) } catch(e: Exception) { "--:--" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Data Summary ---
            if (healthRecords.isNotEmpty()) {
                item {
                    DataSummaryCard(records = healthRecords)
                }

                item {
                    Text(
                        text = "Latest Readings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Show the most recent record per data type
                val latestByType = healthRecords
                    .groupBy { it.dataType }
                    .mapValues { it.value.maxByOrNull { r -> r.timestamp } }
                    .values
                    .filterNotNull()
                    .sortedBy { it.dataType }

                items(
                    items = latestByType,
                    key = { it.dataType }
                ) { record ->
                    HealthDataCard(
                        record = record,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

/**
 * Reusable row with a labeled switch toggle for enabling/disabling a sensor.
 */
@Composable
private fun SensorToggleRow(
    label: String,
    emoji: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = emoji)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
