package com.pointgo.capture.data.export

import com.pointgo.capture.data.model.MeasurementSession

class JsonSessionExporter : SessionExporter {
    override val format: ExportFormat = ExportFormat.JSON

    override fun export(session: MeasurementSession): ExportPayload {
        val output = buildString {
            append("{\"id\":")
            append(jsonString(session.id))
            append(",\"startedAtEpochMillis\":")
            append(session.startedAtEpochMillis)
            append(",\"endedAtEpochMillis\":")
            append(session.endedAtEpochMillis)
            append(",\"samples\":[")
            session.samples.forEachIndexed { index, sample ->
                if (index > 0) append(',')
                append("{\"timestampNanos\":")
                append(sample.timestampNanos)
                append(",\"linearAccelerationMps2\":")
                append(vectorJson(sample.linearAccelerationMps2))
                append(",\"angularVelocityRadPerSec\":")
                append(vectorJson(sample.angularVelocityRadPerSec))
                append(",\"angularAccelerationRadPerSec2\":")
                append(sample.angularAccelerationRadPerSec2?.let(::vectorJson) ?: "null")
                append('}')
            }
            append("],\"annotations\":[")
            session.annotations.forEachIndexed { index, annotation ->
                if (index > 0) append(',')
                append("{\"timestampNanos\":")
                append(annotation.timestampNanos)
                append(",\"label\":")
                append(jsonString(annotation.label))
                append(",\"note\":")
                append(jsonString(annotation.note))
                append('}')
            }
            append("]}")
        }
        return ExportPayload(
            fileName = "pointgo-${session.id}.json",
            mimeType = format.mimeType,
            content = output,
        )
    }

    private fun vectorJson(vector: com.pointgo.capture.data.model.Vector3): String =
        "{\"x\":${vector.x},\"y\":${vector.y},\"z\":${vector.z}}"

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}
