package com.aidelle.sensorread.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aidelle.sensorread.data.model.DataTypes
import com.aidelle.sensorread.data.model.HealthRecord
import com.aidelle.sensorread.ui.theme.*

/**
 * A card displaying a single health data reading with icon and color.
 */
@Composable
fun HealthDataCard(
    record: HealthRecord,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = getDataTypeInfo(record.dataType)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Data content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatValue(record.value, record.dataType),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text(
                        text = record.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Summary card showing counts by data type.
 */
@Composable
fun DataSummaryCard(
    records: List<HealthRecord>,
    modifier: Modifier = Modifier
) {
    val grouped = records.groupBy { it.dataType }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Data Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            grouped.forEach { (dataType, typeRecords) ->
                val (icon, color, label) = getDataTypeInfo(dataType)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "${typeRecords.size} records",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            if (grouped.isEmpty()) {
                Text(
                    text = "No data loaded yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Server connection status indicator.
 */
@Composable
fun ServerStatusChip(
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (connected) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connected) Success else Error)
            )
            Text(
                text = if (connected) "Server Online" else "Server Offline",
                style = MaterialTheme.typography.labelMedium,
                color = if (connected) Success else Error
            )
        }
    }
}

// --- Helper functions ---

private data class DataTypeInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun getDataTypeInfo(dataType: String): DataTypeInfo {
    return when (dataType) {
        DataTypes.HEART_RATE -> DataTypeInfo(Icons.Rounded.FavoriteBorder, HeartRateColor, "Heart Rate")
        DataTypes.STEPS -> DataTypeInfo(Icons.Rounded.DirectionsWalk, StepsColor, "Steps")
        DataTypes.OXYGEN_SATURATION -> DataTypeInfo(Icons.Rounded.Air, OxygenColor, "Blood Oxygen")
        DataTypes.SLEEP -> DataTypeInfo(Icons.Rounded.Bedtime, SleepColor, "Sleep")
        DataTypes.BODY_TEMPERATURE -> DataTypeInfo(Icons.Rounded.Thermostat, TemperatureColor, "Body Temperature")
        else -> DataTypeInfo(Icons.Rounded.HealthAndSafety, Primary, dataType)
    }
}

private fun formatValue(value: Double, dataType: String): String {
    return when (dataType) {
        DataTypes.STEPS -> value.toLong().toString()
        DataTypes.SLEEP -> {
            val hours = (value / 60).toInt()
            val minutes = (value % 60).toInt()
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
        DataTypes.HEART_RATE -> value.toInt().toString()
        DataTypes.OXYGEN_SATURATION -> String.format("%.1f", value)
        DataTypes.BODY_TEMPERATURE -> String.format("%.1f", value)
        else -> String.format("%.1f", value)
    }
}
