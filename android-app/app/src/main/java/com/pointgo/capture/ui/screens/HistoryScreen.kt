package com.pointgo.capture.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pointgo.capture.data.export.ExportFormat
import com.pointgo.capture.data.export.ExportPayload
import com.pointgo.capture.data.model.MeasurementSession
import com.pointgo.capture.data.model.Metric
import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.ui.LiveUiState
import com.pointgo.capture.ui.components.TimeSeriesChart
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: LiveUiState,
    onSelectSession: (String) -> Unit,
    onAddAnnotation: (String, String) -> Unit,
    onExport: (ExportFormat) -> ExportPayload?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedSession = state.sessions.firstOrNull { it.id == state.selectedSessionId }
    var annotationLabel by rememberSaveable(selectedSession?.id) { mutableStateOf("") }
    var annotationNote by rememberSaveable(selectedSession?.id) { mutableStateOf("") }
    var replayMetric by rememberSaveable(selectedSession?.id) {
        mutableStateOf(Metric.LINEAR_ACCELERATION_MAGNITUDE.name)
    }
    var segmentStartFraction by rememberSaveable(selectedSession?.id) { mutableStateOf(0f) }
    var segmentEndFraction by rememberSaveable(selectedSession?.id) { mutableStateOf(1f) }
    val metric = runCatching { Metric.valueOf(replayMetric) }
        .getOrDefault(Metric.LINEAR_ACCELERATION_MAGNITUDE)

    LaunchedEffect(state.selectedSessionId) {
        if (state.selectedSessionId == null && state.sessions.isNotEmpty()) {
            onSelectSession(state.sessions.first().id)
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Session replay", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Review the complete acceleration-time trace, select a segment, mark moments, compare sessions, and export data or charts.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.sessions.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "No saved sessions yet. Start a free recording on Live.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Text("Saved sessions", style = MaterialTheme.typography.titleMedium)
            }
            items(state.sessions, key = MeasurementSession::id) { session ->
                SessionRow(
                    session = session,
                    selected = session.id == selectedSession?.id,
                    onClick = { onSelectSession(session.id) },
                )
            }
            selectedSession?.let { session ->
                val selectedSegment = segmentFor(session, segmentStartFraction, segmentEndFraction)
                val comparison = state.sessions.firstOrNull { it.id != session.id }
                val comparisonSegment = comparison?.let {
                    segmentFor(it, segmentStartFraction, segmentEndFraction)
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Replay trace", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${session.samples.size} samples · ${session.annotations.size} annotations · full session shown",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Linear acceleration · resultant (m/s²)", style = MaterialTheme.typography.labelLarge)
                            val plotted = downsample(session.samples, MAX_REPLAY_POINTS)
                            TimeSeriesChart(
                                values = plotted.map { it.linearAccelerationMagnitudeMps2.toFloat() },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Angular acceleration · resultant (rad/s²)", style = MaterialTheme.typography.labelLarge)
                            TimeSeriesChart(
                                values = plotted.map { it.angularAccelerationMagnitudeRadPerSec2.toFloat() },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Segment window", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${segmentIndexText(session, segmentStartFraction, segmentEndFraction)} · ${selectedSegment.size} samples",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("Start", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = segmentStartFraction,
                                onValueChange = {
                                    segmentStartFraction = it.coerceIn(0f, segmentEndFraction)
                                },
                                valueRange = 0f..1f,
                            )
                            Text("End", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = segmentEndFraction,
                                onValueChange = {
                                    segmentEndFraction = it.coerceIn(segmentStartFraction, 1f)
                                },
                                valueRange = 0f..1f,
                            )
                            Text(
                                "Selected segment peak: linear ${peakText(selectedSegment.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0, "m/s²")}, angular ${peakText(selectedSegment.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0, "rad/s²")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = metric == Metric.LINEAR_ACCELERATION_MAGNITUDE,
                                    onClick = {
                                        replayMetric = Metric.LINEAR_ACCELERATION_MAGNITUDE.name
                                    },
                                    label = { Text("Focus linear") },
                                )
                                FilterChip(
                                    selected = metric == Metric.ANGULAR_ACCELERATION_MAGNITUDE,
                                    onClick = {
                                        replayMetric = Metric.ANGULAR_ACCELERATION_MAGNITUDE.name
                                    },
                                    label = { Text("Focus angular") },
                                )
                            }
                            Text(
                                "Focus: ${metric.label} (${metric.unit})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                item {
                    ComparisonCard(
                        selected = session,
                        selectedSegment = selectedSegment,
                        comparison = comparison,
                        comparisonSegment = comparisonSegment,
                    )
                }
                item {
                    AnnotationCard(
                        label = annotationLabel,
                        note = annotationNote,
                        onLabelChanged = { annotationLabel = it },
                        onNoteChanged = { annotationNote = it },
                        onAdd = {
                            onAddAnnotation(annotationLabel, annotationNote)
                            annotationLabel = ""
                            annotationNote = ""
                        },
                    )
                }
                item {
                    ExportCard(
                        onExport = { format ->
                            onExport(format)?.let { payload -> sharePayload(context, payload) }
                        },
                    )
                }
                if (session.annotations.isNotEmpty()) {
                    item {
                        Text("Annotations", style = MaterialTheme.typography.titleMedium)
                    }
                    items(session.annotations) { annotation ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(annotation.label, fontWeight = FontWeight.Bold)
                                Text(annotation.note.ifBlank { "No note" })
                                Text(
                                    "t=${annotation.timestampNanos} ns",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: MeasurementSession, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(session.startedAtEpochMillis), fontWeight = FontWeight.Bold)
                Text(
                    "${session.samples.size} samples · peak linear ${peakText(session.samples.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0, "m/s²")} · peak angular ${peakText(session.samples.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0, "rad/s²")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilterChip(selected = selected, onClick = onClick, label = { Text(if (selected) "Open" else "View") })
        }
    }
}

@Composable
private fun ComparisonCard(
    selected: MeasurementSession,
    selectedSegment: List<RawSample>,
    comparison: MeasurementSession?,
    comparisonSegment: List<RawSample>?,
) {
    if (comparison == null || comparisonSegment == null) return
    val selectedLinear = selected.samples.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0
    val selectedAngular = selected.samples.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0
    val comparisonLinear = comparison.samples.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0
    val comparisonAngular = comparison.samples.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0
    val selectedSegmentLinear = selectedSegment.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0
    val selectedSegmentAngular = selectedSegment.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0
    val comparisonSegmentLinear = comparisonSegment.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0
    val comparisonSegmentAngular = comparisonSegment.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Session comparison", style = MaterialTheme.typography.titleMedium)
            Text("Other session: ${formatDate(comparison.startedAtEpochMillis)}")
            Text(
                "Full linear peak ${peakText(selectedLinear, "m/s²")} vs ${peakText(comparisonLinear, "m/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Full angular peak ${peakText(selectedAngular, "rad/s²")} vs ${peakText(comparisonAngular, "rad/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text("Same normalized segment", fontWeight = FontWeight.Bold)
            Text(
                "Linear ${peakText(selectedSegmentLinear, "m/s²")} vs ${peakText(comparisonSegmentLinear, "m/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Angular ${peakText(selectedSegmentAngular, "rad/s²")} vs ${peakText(comparisonSegmentAngular, "rad/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnnotationCard(
    label: String,
    note: String,
    onLabelChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Add replay annotation", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd, enabled = label.isNotBlank() || note.isNotBlank()) {
                Text("Mark latest sample")
            }
        }
    }
}

@Composable
private fun ExportCard(onExport: (ExportFormat) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Export data and charts", style = MaterialTheme.typography.titleMedium)
            Text("CSV/JSON contain every sample; SVG is a vector chart of both metrics.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExport(ExportFormat.CSV) }) { Text("Share CSV") }
                Button(onClick = { onExport(ExportFormat.JSON) }) { Text("Share JSON") }
                Button(onClick = { onExport(ExportFormat.SVG) }) { Text("Share SVG") }
            }
        }
    }
}

private fun sharePayload(context: Context, payload: ExportPayload) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = payload.mimeType
        putExtra(Intent.EXTRA_SUBJECT, payload.fileName)
        putExtra(Intent.EXTRA_TEXT, payload.content)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export ${payload.fileName}"))
}

private fun segmentFor(
    session: MeasurementSession,
    startFraction: Float,
    endFraction: Float,
): List<RawSample> {
    if (session.samples.isEmpty()) return emptyList()
    val start = (startFraction.coerceIn(0f, 1f) * (session.samples.lastIndex)).roundToInt()
    val end = (endFraction.coerceIn(startFraction, 1f) * (session.samples.lastIndex)).roundToInt()
    return session.samples.subList(start.coerceAtMost(end), end.coerceAtLeast(start) + 1)
}

private fun segmentIndexText(
    session: MeasurementSession,
    startFraction: Float,
    endFraction: Float,
): String {
    if (session.samples.isEmpty()) return "empty session"
    val start = (startFraction.coerceIn(0f, 1f) * session.samples.lastIndex).roundToInt()
    val end = (endFraction.coerceIn(startFraction, 1f) * session.samples.lastIndex).roundToInt()
    val startTime = session.samples[start].timestampNanos
    val endTime = session.samples[end].timestampNanos
    return "samples $start–$end · ${((endTime - startTime).coerceAtLeast(0L) / 1_000_000_000.0).formatSeconds()} s"
}

private fun downsample(samples: List<RawSample>, maxPoints: Int): List<RawSample> {
    if (samples.size <= maxPoints || maxPoints < 2) return samples
    return List(maxPoints) { index ->
        val sourceIndex = (index.toLong() * samples.lastIndex / (maxPoints - 1)).toInt()
        samples[sourceIndex]
    }
}

private fun peakText(value: Double, unit: String): String =
    "${"%.2f".format(java.util.Locale.US, value)} $unit"

private fun Double.formatSeconds(): String =
    "%.3f".format(java.util.Locale.US, this)

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private const val MAX_REPLAY_POINTS = 2_000
