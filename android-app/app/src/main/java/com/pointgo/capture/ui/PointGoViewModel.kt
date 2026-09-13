package com.pointgo.capture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointgo.capture.data.export.ExportFormat
import com.pointgo.capture.data.export.ExportPayload
import com.pointgo.capture.data.export.SessionExporter
import com.pointgo.capture.data.model.MeasurementSession
import com.pointgo.capture.data.model.Metric
import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.data.model.SessionAnnotation
import com.pointgo.capture.data.model.Vector3
import com.pointgo.capture.data.repository.SessionRepository
import com.pointgo.capture.data.sensor.SensorConnectionState
import com.pointgo.capture.data.sensor.SensorTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.UUID
import kotlin.math.max

data class LiveUiState(
    val connectionState: SensorConnectionState = SensorConnectionState.Disconnected,
    val isRecording: Boolean = false,
    val samples: List<RawSample> = emptyList(),
    val selectedMetric: Metric = Metric.LINEAR_ACCELERATION_MAGNITUDE,
    val advancedPanelExpanded: Boolean = false,
    val deviceAddress: String = "",
    val smoothingWindow: Int = 1,
    val warningThreshold: Double = 0.0,
    val peakLinearAccelerationMps2: Double = 0.0,
    val peakAngularAccelerationRadPerSec2: Double = 0.0,
    val currentValue: Double = 0.0,
    val sessions: List<MeasurementSession> = emptyList(),
    val selectedSessionId: String? = null,
)

class PointGoViewModel(
    private val transport: SensorTransport,
    private val repository: SessionRepository,
    private val exporters: Map<ExportFormat, SessionExporter>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private var recordingStartedAtEpochMillis: Long? = null
    private var previousSample: RawSample? = null
    private val smoothingBuffer = ArrayDeque<RawSample>()

    init {
        transport.connectionState
            .onEach { state -> _uiState.update { it.copy(connectionState = state) } }
            .launchIn(viewModelScope)
        transport.samples
            .onEach(::onTransportSample)
            .launchIn(viewModelScope)
        repository.sessions
            .onEach { sessions ->
                _uiState.update { current ->
                    current.copy(
                        sessions = sessions,
                        selectedSessionId = current.selectedSessionId
                            ?.takeIf { selected -> sessions.any { it.id == selected } }
                            ?: sessions.firstOrNull()?.id,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun connect() {
        viewModelScope.launch {
            transport.connect(_uiState.value.deviceAddress.trim().ifBlank { null })
        }
    }

    fun disconnect() {
        viewModelScope.launch { transport.disconnect() }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) stopRecording() else startRecording()
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return
        previousSample = null
        smoothingBuffer.clear()
        recordingStartedAtEpochMillis = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRecording = true,
                samples = emptyList(),
                peakLinearAccelerationMps2 = 0.0,
                peakAngularAccelerationRadPerSec2 = 0.0,
                currentValue = 0.0,
            )
        }
        viewModelScope.launch { transport.startStream() }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        _uiState.update { it.copy(isRecording = false) }
        viewModelScope.launch { transport.stopStream() }

        val startedAt = recordingStartedAtEpochMillis ?: System.currentTimeMillis()
        val samples = _uiState.value.samples
        if (samples.isNotEmpty()) {
            viewModelScope.launch {
                repository.save(
                    MeasurementSession(
                        id = UUID.randomUUID().toString(),
                        startedAtEpochMillis = startedAt,
                        endedAtEpochMillis = System.currentTimeMillis(),
                        samples = samples,
                    ),
                )
            }
        }
        recordingStartedAtEpochMillis = null
        previousSample = null
        smoothingBuffer.clear()
    }

    fun setSelectedMetric(metric: Metric) {
        _uiState.update { state ->
            val latest = state.samples.lastOrNull()?.let(metric::value) ?: 0.0
            state.copy(selectedMetric = metric, currentValue = latest)
        }
    }

    fun setAdvancedPanelExpanded(expanded: Boolean) {
        _uiState.update { it.copy(advancedPanelExpanded = expanded) }
    }

    fun setDeviceAddress(address: String) {
        _uiState.update { it.copy(deviceAddress = address) }
    }

    fun setSmoothingWindow(window: Int) {
        smoothingBuffer.clear()
        _uiState.update { it.copy(smoothingWindow = window.coerceIn(1, 9)) }
    }

    fun setWarningThreshold(threshold: Double) {
        _uiState.update { it.copy(warningThreshold = threshold.coerceAtLeast(0.0)) }
    }

    fun selectSession(sessionId: String) {
        _uiState.update { it.copy(selectedSessionId = sessionId) }
    }

    fun addAnnotation(label: String, note: String) {
        val state = _uiState.value
        val sessionId = state.selectedSessionId ?: return
        val timestamp = state.sessions.firstOrNull { it.id == sessionId }
            ?.samples?.lastOrNull()?.timestampNanos ?: System.nanoTime()
        if (label.isBlank() && note.isBlank()) return
        viewModelScope.launch {
            repository.addAnnotation(
                sessionId = sessionId,
                annotation = SessionAnnotation(
                    timestampNanos = timestamp,
                    label = label.trim().ifBlank { "Note" },
                    note = note.trim(),
                ),
            )
        }
    }

    fun exportSelected(format: ExportFormat): ExportPayload? {
        val session = _uiState.value.sessions.firstOrNull {
            it.id == _uiState.value.selectedSessionId
        } ?: return null
        return exporters[format]?.export(session)
    }

    private fun onTransportSample(sample: RawSample) {
        if (!_uiState.value.isRecording) return

        // Differentiate the raw angular velocity before smoothing it. This
        // keeps the derivative's time base tied to the sensor clock while the
        // optional moving average only changes what is displayed/exported.
        val derived = deriveAngularAcceleration(sample, previousSample)
        previousSample = derived
        val enriched = smooth(derived)
        _uiState.update { current ->
            val nextSamples = (current.samples + enriched).takeLast(MAX_SAMPLES)
            val nextMetricValue = current.selectedMetric.value(enriched)
            current.copy(
                samples = nextSamples,
                peakLinearAccelerationMps2 = max(
                    current.peakLinearAccelerationMps2,
                    enriched.linearAccelerationMagnitudeMps2,
                ),
                peakAngularAccelerationRadPerSec2 = max(
                    current.peakAngularAccelerationRadPerSec2,
                    enriched.angularAccelerationMagnitudeRadPerSec2,
                ),
                currentValue = nextMetricValue,
            )
        }
    }

    private fun smooth(sample: RawSample): RawSample {
        val window = _uiState.value.smoothingWindow
        if (window <= 1) return sample
        smoothingBuffer.addLast(sample)
        while (smoothingBuffer.size > window) smoothingBuffer.removeFirst()
        val values = smoothingBuffer.toList()
        val count = values.size.toDouble()
        fun average(selector: (RawSample) -> Vector3): Vector3 {
            val sum = values.map(selector).fold(Vector3.ZERO, Vector3::plus)
            return sum * (1.0 / count)
        }
        val angularAcceleration = if (values.any { it.angularAccelerationRadPerSec2 != null }) {
            val available = values.mapNotNull { it.angularAccelerationRadPerSec2 }
            val sum = available.fold(Vector3.ZERO, Vector3::plus)
            sum * (1.0 / available.size)
        } else {
            null
        }
        return sample.copy(
            linearAccelerationMps2 = average(RawSample::linearAccelerationMps2),
            angularVelocityRadPerSec = average(RawSample::angularVelocityRadPerSec),
            angularAccelerationRadPerSec2 = angularAcceleration,
        )
    }

    private fun deriveAngularAcceleration(sample: RawSample, previous: RawSample?): RawSample {
        if (sample.angularAccelerationRadPerSec2 != null || previous == null) return sample
        val deltaSeconds = (sample.timestampNanos - previous.timestampNanos) / NANOS_PER_SECOND
        if (deltaSeconds <= 0.0) return sample.copy(angularAccelerationRadPerSec2 = Vector3.ZERO)
        return sample.copy(
            angularAccelerationRadPerSec2 =
                (sample.angularVelocityRadPerSec - previous.angularVelocityRadPerSec) / deltaSeconds,
        )
    }

    override fun onCleared() {
        (transport as? Closeable)?.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_SAMPLES = 120_000
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
