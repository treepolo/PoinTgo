package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession

class CsvSessionExporter : SessionExporter {
    override val format: ExportFormat = ExportFormat.CSV

    override fun export(session: MeasurementSession): ExportPayload {
        val output = buildString {
            appendLine(
                listOf(
                    "timestamp_nanos",
                    "linear_accel_x_mps2",
                    "linear_accel_y_mps2",
                    "linear_accel_z_mps2",
                    "linear_accel_magnitude_mps2",
                    "angular_velocity_x_rad_s",
                    "angular_velocity_y_rad_s",
                    "angular_velocity_z_rad_s",
                    "angular_accel_x_rad_s2",
                    "angular_accel_y_rad_s2",
                    "angular_accel_z_rad_s2",
                    "angular_accel_magnitude_rad_s2",
                ).joinToString(",")
            )
            session.samples.forEach { sample ->
                val angularAcceleration = sample.angularAccelerationRadPerSec2
                appendLine(
                    listOf(
                        sample.timestampNanos,
                        sample.linearAccelerationMps2.x,
                        sample.linearAccelerationMps2.y,
                        sample.linearAccelerationMps2.z,
                        sample.linearAccelerationMagnitudeMps2,
                        sample.angularVelocityRadPerSec.x,
                        sample.angularVelocityRadPerSec.y,
                        sample.angularVelocityRadPerSec.z,
                        angularAcceleration?.x ?: "",
                        angularAcceleration?.y ?: "",
                        angularAcceleration?.z ?: "",
                        angularAcceleration?.magnitude() ?: "",
                    ).joinToString(",")
                )
            }
            if (session.annotations.isNotEmpty()) {
                appendLine()
                appendLine("annotation_timestamp_nanos,label,note")
                session.annotations.forEach { annotation ->
                    appendLine(
                        listOf(
                            annotation.timestampNanos.toString(),
                            csvEscape(annotation.label),
                            csvEscape(annotation.note),
                        ).joinToString(",")
                    )
                }
            }
        }
        return ExportPayload(
            fileName = "pointgo-${session.id}.csv",
            mimeType = format.mimeType,
            content = output,
        )
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
