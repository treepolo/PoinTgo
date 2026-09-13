package com.pointgo.capture.data.sensor

import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.data.model.Vector3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Deterministic demo stream retained for unit/UI previews; production uses BLE. */
class FakeSensorTransport : SensorTransport, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _connectionState = MutableStateFlow<SensorConnectionState>(
        SensorConnectionState.Disconnected,
    )
    private val _samples = MutableSharedFlow<RawSample>(extraBufferCapacity = 256)
    private var streamJob: Job? = null
    private var sampleIndex = 0L

    override val connectionState: StateFlow<SensorConnectionState> = _connectionState.asStateFlow()
    override val samples: SharedFlow<RawSample> = _samples.asSharedFlow()

    override suspend fun connect(deviceAddress: String?) {
        _connectionState.value = SensorConnectionState.Connecting(deviceAddress ?: "demo")
        delay(150)
        _connectionState.value = SensorConnectionState.Connected("PoinT Go demo")
    }

    override suspend fun disconnect() {
        stopStream()
        _connectionState.value = SensorConnectionState.Disconnected
    }

    override suspend fun startStream() {
        if (_connectionState.value !is SensorConnectionState.Connected &&
            _connectionState.value !is SensorConnectionState.Streaming
        ) {
            _connectionState.value = SensorConnectionState.Error("Connect the sensor first")
            return
        }
        if (streamJob?.isActive == true) return

        _connectionState.value = SensorConnectionState.Streaming("PoinT Go demo")
        streamJob = scope.launch {
            while (true) {
                val index = sampleIndex++
                val timeSeconds = index / 60.0
                val linear = Vector3(
                    x = 1.8 * sin(timeSeconds * 5.0),
                    y = 0.8 * cos(timeSeconds * 3.0),
                    z = 9.81 + 2.5 * sin(timeSeconds * 2.0),
                )
                val angularVelocity = Vector3(
                    x = 2.2 * sin(timeSeconds * 4.0),
                    y = 1.0 * cos(timeSeconds * 2.5),
                    z = 0.6 * sin(timeSeconds * 7.0),
                )
                val angularAcceleration = Vector3(
                    x = 2.2 * 4.0 * cos(timeSeconds * 4.0),
                    y = -1.0 * 2.5 * sin(timeSeconds * 2.5),
                    z = 0.6 * 7.0 * cos(timeSeconds * 7.0),
                )
                _samples.emit(
                    RawSample(
                        timestampNanos = System.nanoTime(),
                        linearAccelerationMps2 = linear,
                        angularVelocityRadPerSec = angularVelocity,
                        angularAccelerationRadPerSec2 = angularAcceleration,
                    ),
                )
                delay(16)
            }
        }
    }

    override suspend fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        if (_connectionState.value is SensorConnectionState.Streaming) {
            _connectionState.value = SensorConnectionState.Connected("PoinT Go demo")
        }
    }

    override fun close() {
        streamJob?.cancel()
        scope.cancel()
    }
}
