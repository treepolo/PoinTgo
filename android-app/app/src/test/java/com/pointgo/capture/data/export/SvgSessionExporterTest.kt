package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession
import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.data.model.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgSessionExporterTest {
    @Test
    fun svgContainsBothRequestedChartsAndPeaks() {
        val session = MeasurementSession(
            id = "svg-test",
            startedAtEpochMillis = 1L,
            endedAtEpochMillis = 2L,
            samples = listOf(
                RawSample(1L, Vector3(1.0, 0.0, 0.0), Vector3(0.0, 0.0, 0.0), Vector3(2.0, 0.0, 0.0)),
                RawSample(2L, Vector3(3.0, 0.0, 0.0), Vector3(0.0, 0.0, 0.0), Vector3(4.0, 0.0, 0.0)),
            ),
        )
        val payload = SvgSessionExporter().export(session)

        assertEquals(ExportFormat.SVG, SvgSessionExporter().format)
        assertEquals("image/svg+xml", payload.mimeType)
        assertTrue(payload.content.contains("Linear acceleration resultant"))
        assertTrue(payload.content.contains("Angular acceleration resultant"))
        assertTrue(payload.content.contains("peak linear"))
        assertTrue(payload.content.contains("peak angular"))
    }
}
