package com.pointgo.capture.ui

import android.content.Context
import com.pointgo.capture.data.export.CsvSessionExporter
import com.pointgo.capture.data.export.ExportFormat
import com.pointgo.capture.data.export.JsonSessionExporter
import com.pointgo.capture.data.export.SvgSessionExporter
import com.pointgo.capture.data.repository.InMemorySessionRepository
import com.pointgo.capture.data.sensor.BleSensorTransport

/** Composition root for the real Poin+T BLE transport. */
class AppContainer(context: Context) {
    private val repository = InMemorySessionRepository()
    private val transport = BleSensorTransport(context.applicationContext)

    val viewModel = PointGoViewModel(
        transport = transport,
        repository = repository,
        exporters = mapOf(
            ExportFormat.CSV to CsvSessionExporter(),
            ExportFormat.JSON to JsonSessionExporter(),
            ExportFormat.SVG to SvgSessionExporter(),
        ),
    )
}
