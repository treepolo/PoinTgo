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
            Text("量測回放", style = MaterialTheme.typography.headlineSmall)
            Text(
                "查看完整加速度—時間曲線、選取區段、標記時刻、比較量測並匯出資料或圖表。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.sessions.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "目前沒有儲存的量測，請先到「即時」開始自由記錄。",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Text("已儲存的量測", style = MaterialTheme.typography.titleMedium)
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
                            Text("回放曲線", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${session.samples.size} 筆資料・${session.annotations.size} 個標註・顯示完整量測",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("線性加速度・合成值 (m/s²)", style = MaterialTheme.typography.labelLarge)
                            val plotted = downsample(session.samples, MAX_REPLAY_POINTS)
                            TimeSeriesChart(
                                values = plotted.map { it.linearAccelerationMagnitudeMps2.toFloat() },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("角加速度・合成值 (rad/s²)", style = MaterialTheme.typography.labelLarge)
                            TimeSeriesChart(
                                values = plotted.map { it.angularAccelerationMagnitudeRadPerSec2.toFloat() },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("區段範圍", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${segmentIndexText(session, segmentStartFraction, segmentEndFraction)}・${selectedSegment.size} 筆",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("開始", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = segmentStartFraction,
                                onValueChange = {
                                    segmentStartFraction = it.coerceIn(0f, segmentEndFraction)
                                },
                                valueRange = 0f..1f,
                            )
                            Text("結束", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = segmentEndFraction,
                                onValueChange = {
                                    segmentEndFraction = it.coerceIn(segmentStartFraction, 1f)
                                },
                                valueRange = 0f..1f,
                            )
                            Text(
                                "選取區段峰值：線性 ${peakText(selectedSegment.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0, "m/s²")}、角 ${peakText(selectedSegment.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0, "rad/s²")}",
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
                                    label = { Text("聚焦線性") },
                                )
                                FilterChip(
                                    selected = metric == Metric.ANGULAR_ACCELERATION_MAGNITUDE,
                                    onClick = {
                                        replayMetric = Metric.ANGULAR_ACCELERATION_MAGNITUDE.name
                                    },
                                    label = { Text("聚焦角加速度") },
                                )
                            }
                            Text(
                                "聚焦：${metric.label} (${metric.unit})",
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
                        Text("標註", style = MaterialTheme.typography.titleMedium)
                    }
                    items(session.annotations) { annotation ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(annotation.label, fontWeight = FontWeight.Bold)
                                Text(annotation.note.ifBlank { "沒有備註" })
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
                    "${session.samples.size} 筆・線性峰值 ${peakText(session.samples.maxOfOrNull { it.linearAccelerationMagnitudeMps2 } ?: 0.0, "m/s²")}・角峰值 ${peakText(session.samples.maxOfOrNull { it.angularAccelerationMagnitudeRadPerSec2 } ?: 0.0, "rad/s²")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilterChip(selected = selected, onClick = onClick, label = { Text(if (selected) "已開啟" else "檢視") })
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
            Text("量測比較", style = MaterialTheme.typography.titleMedium)
            Text("另一筆量測：${formatDate(comparison.startedAtEpochMillis)}")
            Text(
                "完整量測線性峰值 ${peakText(selectedLinear, "m/s²")} vs ${peakText(comparisonLinear, "m/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "完整量測角峰值 ${peakText(selectedAngular, "rad/s²")} vs ${peakText(comparisonAngular, "rad/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text("相同比例區段", fontWeight = FontWeight.Bold)
            Text(
                "線性 ${peakText(selectedSegmentLinear, "m/s²")} vs ${peakText(comparisonSegmentLinear, "m/s²")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "角 ${peakText(selectedSegmentAngular, "rad/s²")} vs ${peakText(comparisonSegmentAngular, "rad/s²")}",
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
            Text("新增回放標註", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("標題") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("備註") },
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAdd, enabled = label.isNotBlank() || note.isNotBlank()) {
                Text("標記最新資料")
            }
        }
    }
}

@Composable
private fun ExportCard(onExport: (ExportFormat) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("匯出資料與圖表", style = MaterialTheme.typography.titleMedium)
            Text("CSV／JSON 包含每筆資料；SVG 是兩項指標的向量圖表。")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onExport(ExportFormat.CSV) }) { Text("分享 CSV") }
                Button(onClick = { onExport(ExportFormat.JSON) }) { Text("分享 JSON") }
                Button(onClick = { onExport(ExportFormat.SVG) }) { Text("分享 SVG") }
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
    context.startActivity(Intent.createChooser(shareIntent, "匯出 ${payload.fileName}"))
}

private fun segmentFor(
    session: MeasurementSession,
    startFraction: Float,
    endFraction: Float,
): List<RawSample> {
    if (session.samples.isEmpty()) return emptyList()
    val start = (startFraction.coerceIn(0f, 1f) * session.samples.lastIndex).roundToInt()
    val end = (endFraction.coerceIn(startFraction, 1f) * session.samples.lastIndex).roundToInt()
    return session.samples.subList(start.coerceAtMost(end), end.coerceAtLeast(start) + 1)
}

private fun segmentIndexText(
    session: MeasurementSession,
    startFraction: Float,
    endFraction: Float,
): String {
    if (session.samples.isEmpty()) return "空量測"
    val start = (startFraction.coerceIn(0f, 1f) * session.samples.lastIndex).roundToInt()
    val end = (endFraction.coerceIn(startFraction, 1f) * session.samples.lastIndex).roundToInt()
    val startTime = session.samples[start].timestampNanos
    val endTime = session.samples[end].timestampNanos
    return "第 $start–$end 筆・${((endTime - startTime).coerceAtLeast(0L) / 1_000_000_000.0).formatSeconds()} 秒"
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
