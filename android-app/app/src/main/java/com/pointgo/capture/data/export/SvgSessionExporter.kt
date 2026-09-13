package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession
import kotlin.math.max

/** A dependency-free vector export containing both requested time-series plots. */
class SvgSessionExporter : SessionExporter {
    override val format: ExportFormat = ExportFormat.SVG

    override fun export(session: MeasurementSession): ExportPayload {
        val samples = session.samples
        val stride = max(1, samples.size / MAX_POINTS)
        val plotted = samples.filterIndexed { index, _ -> index % stride == 0 }
        val linear = plotted.map { it.linearAccelerationMagnitudeMps2 }
        val angular = plotted.map { it.angularAccelerationMagnitudeRadPerSec2 }
        val width = 1_200.0
        val chartHeight = 260.0
        val gap = 56.0
        val height = 80.0 + chartHeight * 2 + gap
        val left = 72.0
        val right = width - 24.0
        val top1 = 38.0
        val top2 = top1 + chartHeight + gap
        val output = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">")
            append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>")
            append("<style>text{font-family:sans-serif;fill:#202124} .grid{stroke:#d9dce1;stroke-width:1} .line{fill:none;stroke:#1769aa;stroke-width:3} .angular{stroke:#a23b72}</style>")
            append("<text x=\"$left\" y=\"24\" font-size=\"20\" font-weight=\"bold\">Poin+T session ${escape(session.id)}</text>")
            appendChart(
                title = "Linear acceleration resultant (m/s²)",
                values = linear,
                top = top1,
                polylineClass = "line",
            )
            appendChart(
                title = "Angular acceleration resultant (rad/s²)",
                values = angular,
                top = top2,
                polylineClass = "line angular",
            )
            append("<text x=\"$left\" y=\"${height - 10}\" font-size=\"13\">samples=${samples.size}, peak linear=${"%.3f".format(java.util.Locale.US, linear.maxOrNull() ?: 0.0)}, peak angular=${"%.3f".format(java.util.Locale.US, angular.maxOrNull() ?: 0.0)}</text>")
            append("</svg>")
        }
        return ExportPayload(
            fileName = "pointgo-${session.id}.svg",
            mimeType = format.mimeType,
            content = output,
        )
    }

    private fun StringBuilder.appendChart(
        title: String,
        values: List<Double>,
        top: Double,
        polylineClass: String,
    ) {
        val chartWidth = 1_200.0 - 72.0 - 24.0
        append("<text x=\"72\" y=\"${top - 12}\" font-size=\"16\" font-weight=\"bold\">$title</text>")
        repeat(5) { row ->
            val y = top + 20.0 + (220.0 * row / 4.0)
            append("<line class=\"grid\" x1=\"72\" y1=\"$y\" x2=\"1176\" y2=\"$y\"/>")
        }
        val points = if (values.size > 1) {
            val minimum = values.minOrNull() ?: 0.0
            val maximum = values.maxOrNull() ?: 1.0
            val range = (maximum - minimum).takeIf { it > 1e-12 } ?: 1.0
            values.mapIndexed { index, value ->
                val x = 72.0 + chartWidth * index / (values.lastIndex.toDouble())
                val y = top + 20.0 + 220.0 * (maximum - value) / range
                "${"%.2f".format(java.util.Locale.US, x)},${"%.2f".format(java.util.Locale.US, y)}"
            }.joinToString(" ")
        } else {
            ""
        }
        append("<polyline class=\"$polylineClass\" points=\"$points\"/>")
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val MAX_POINTS = 2_000
    }
}
