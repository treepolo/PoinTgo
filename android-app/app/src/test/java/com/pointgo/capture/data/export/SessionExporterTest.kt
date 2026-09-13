package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession
import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.data.model.Vector3
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExporterTest {
    private val session = MeasurementSession(
        id = "test-session",
        startedAtEpochMillis = 1L,
        endedAtEpochMillis = 2L,
        samples = listOf(
            RawSample(
                timestampNanos = 10L,
                linearAccelerationMps2 = Vector3(1.0, 2.0, 3.0),
                angularVelocityRadPerSec = Vector3(4.0, 5.0, 6.0),
                angularAccelerationRadPerSec2 = Vector3(7.0, 8.0, 9.0),
            ),
        ),
    )

    @Test
    fun csvContainsAxesAndTimestamp() {
        val csv = CsvSessionExporter().export(session).content
        assertTrue(csv.startsWith("timestamp_nanos"))
        assertTrue(csv.contains("10,1.0,2.0,3.0"))
        assertTrue(csv.contains("7.0,8.0,9.0"))
    }

    @Test
    fun jsonContainsCompleteSampleShape() {
        val json = JsonSessionExporter().export(session).content
        assertTrue(json.contains("\"linearAccelerationMps2\""))
        assertTrue(json.contains("\"angularVelocityRadPerSec\""))
        assertTrue(json.contains("\"angularAccelerationRadPerSec2\""))
    }
}
