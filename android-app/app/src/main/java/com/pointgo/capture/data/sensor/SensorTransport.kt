package com.pointgo.capture.data.sensor

import com.pointgo.capture.data.model.RawSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface SensorConnectionState {
    data object Disconnected : SensorConnectionState
    data object Scanning : SensorConnectionState
    data class Connecting(val deviceAddress: String) : SensorConnectionState
    data class Connected(val deviceName: String?) : SensorConnectionState
    data class Streaming(val deviceName: String?) : SensorConnectionState
    data class Error(val message: String) : SensorConnectionState
}

/**
 * Protocol-independent seam used by the recorder. A real BLE implementation
 * can be swapped in without changing the UI, persistence, or export layers.
 */
interface SensorTransport {
    val connectionState: StateFlow<SensorConnectionState>
    val samples: Flow<RawSample>

    suspend fun connect(deviceAddress: String? = null)

    suspend fun disconnect()

    suspend fun startStream()

    suspend fun stopStream()
}
