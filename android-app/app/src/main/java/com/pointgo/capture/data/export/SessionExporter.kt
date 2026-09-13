package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    SVG("svg", "image/svg+xml"),
}

data class ExportPayload(
    val fileName: String,
    val mimeType: String,
    val content: String,
)

interface SessionExporter {
    val format: ExportFormat

    fun export(session: MeasurementSession): ExportPayload
}
