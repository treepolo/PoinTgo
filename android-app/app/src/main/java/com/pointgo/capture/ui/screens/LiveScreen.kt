package com.pointgo.capture.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pointgo.capture.data.model.Metric
import com.pointgo.capture.data.sensor.SensorConnectionState
import com.pointgo.capture.ui.LiveUiState
import com.pointgo.capture.ui.components.TimeSeriesChart

@Composable
fun LiveScreen(
    state: LiveUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleRecording: () -> Unit,
    onMetricSelected: (Metric) -> Unit,
    onToggleAdvanced: () -> Unit,
    onDeviceAddressChanged: (String) -> Unit,
    onSmoothingChanged: (Int) -> Unit,
    onThresholdChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleSamples = state.samples.takeLast(1_200)
    val linearValues = visibleSamples.map { it.linearAccelerationMagnitudeMps2.toFloat() }
    val angularValues = visibleSamples.map { it.angularAccelerationMagnitudeRadPerSec2.toFloat() }
    val threshold = state.warningThreshold.takeIf { it > 0.0 }?.toFloat()
    val warningActive = threshold != null && state.currentValue >= threshold

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ConnectionCard(
                connectionState = state.connectionState,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Live monitor", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Start a free recording without choosing a sport mode.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Linear acceleration · resultant (m/s²)", style = MaterialTheme.typography.labelLarge)
                    TimeSeriesChart(
                        values = linearValues,
                        threshold = if (state.selectedMetric == Metric.LINEAR_ACCELERATION_MAGNITUDE) {
                            threshold
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Angular acceleration · resultant (rad/s²)", style = MaterialTheme.typography.labelLarge)
                    TimeSeriesChart(
                        values = angularValues,
                        threshold = if (state.selectedMetric == Metric.ANGULAR_ACCELERATION_MAGNITUDE) {
                            threshold
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Selected: ${state.selectedMetric.label} (${state.selectedMetric.unit})",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        valueText(state.currentValue, state.selectedMetric.unit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetricPicker(state.selectedMetric, onMetricSelected)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeakCard(
                    modifier = Modifier.weight(1f),
                    title = "Peak linear accel",
                    value = valueText(state.peakLinearAccelerationMps2, "m/s²"),
                )
                PeakCard(
                    modifier = Modifier.weight(1f),
                    title = "Peak angular accel",
                    value = valueText(state.peakAngularAccelerationRadPerSec2, "rad/s²"),
                )
            }
        }
        item {
            if (warningActive) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Threshold exceeded: ${valueText(state.currentValue, state.selectedMetric.unit)}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onToggleRecording, Modifier.weight(1f)) {
                        Text(if (state.isRecording) "Stop & save" else "Start free recording")
                    }
                    Text(
                        if (state.isRecording) "Recording" else "Ready",
                        color = if (state.isRecording) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Advanced controls", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Optional settings; blank address scans for Poin+T.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = onToggleAdvanced) {
                            Text(if (state.advancedPanelExpanded) "Hide" else "Show")
                        }
                    }
                    if (state.advancedPanelExpanded) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        OutlinedTextField(
                            value = state.deviceAddress,
                            onValueChange = onDeviceAddressChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("BLE address (optional)") },
                            placeholder = { Text("Blank = scan") },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Smoothing window: ${state.smoothingWindow} samples")
                        Slider(
                            value = state.smoothingWindow.toFloat(),
                            onValueChange = { onSmoothingChanged(it.toInt().coerceIn(1, 9)) },
                            valueRange = 1f..9f,
                            steps = 7,
                        )
                        Text("Warning threshold: ${valueText(state.warningThreshold, state.selectedMetric.unit)}")
                        Slider(
                            value = state.warningThreshold.toFloat().coerceIn(0f, 100f),
                            onValueChange = { onThresholdChanged(it.toDouble()) },
                            valueRange = 0f..100f,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    connectionState: SensorConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val status = when (connectionState) {
        SensorConnectionState.Disconnected -> "Disconnected"
        SensorConnectionState.Scanning -> "Scanning for Poin+T…"
        is SensorConnectionState.Connecting -> "Connecting…"
        is SensorConnectionState.Connected -> "Connected"
        is SensorConnectionState.Streaming -> "Streaming"
        is SensorConnectionState.Error -> "Error: ${connectionState.message}"
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sensor", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            if (connectionState is SensorConnectionState.Connected ||
                connectionState is SensorConnectionState.Streaming
            ) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                Button(onClick = onConnect) { Text("Scan & connect") }
            }
        }
    }
}

@Composable
private fun MetricPicker(selected: Metric, onSelected: (Metric) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Metric.LINEAR_ACCELERATION_MAGNITUDE,
                Metric.ANGULAR_ACCELERATION_MAGNITUDE,
            ).forEach { metric ->
                FilterChip(
                    selected = selected == metric,
                    onClick = { onSelected(metric) },
                    label = {
                        Text(if (metric == Metric.LINEAR_ACCELERATION_MAGNITUDE) "Linear" else "Angular")
                    },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selected == Metric.ANGULAR_VELOCITY,
                onClick = { onSelected(Metric.ANGULAR_VELOCITY) },
                label = { Text("Angular velocity") },
            )
            FilterChip(
                selected = selected == Metric.LINEAR_ACCELERATION,
                onClick = { onSelected(Metric.LINEAR_ACCELERATION) },
                label = { Text("Linear X") },
            )
        }
    }
}

@Composable
private fun PeakCard(modifier: Modifier, title: String, value: String) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun valueText(value: Double, unit: String): String =
    "${"%.2f".format(java.util.Locale.US, value)} $unit"
