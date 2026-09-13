package com.pointgo.capture.data.sensor

import com.pointgo.capture.data.model.RawSample
import com.pointgo.capture.data.model.Vector3
import kotlin.math.PI

/**
 * Conversion constants recovered from the vendor Flutter AOT parser.
 *
 * The 61-byte HCI notification has a different outer layout and was observed
 * with a ±4g accelerometer scale. Keep both profiles explicit until a second
 * clean capture confirms whether firmware exposes the same range in every mode.
 */
data class PoinTGoCalibration(
    val hciAccelerationMps2PerCount: Double = 9.80665 * 4.0 / 32768.0,
    val aotAccelerationMps2PerCount: Double = 0.0047884033203125,
    val gyroRadPerSecPerCount: Double = (2000.0 / 32768.0) * PI / 180.0,
    val hciSampleRateHz: Double = 60.0,
    val aotSampleRateHz: Double = 60.0,
)

/** A stateful decoder because sensor timestamps wrap and must be unwrapped. */
interface RawBatchDecoder {
    fun reset()

    /** Decode one complete BLE notification into zero or more samples. */
    fun decode(payload: ByteArray, receiveTimestampNanos: Long): List<RawSample>
}

/**
 * Decoder for both formats found while reverse engineering Poin+T Go:
 *
 * 1. Vendor AOT raw batch: 04 N h k u32(base-us) [N × 6×i16].
 * 2. Captured NUS notification: 04 + 52 bytes containing two interleaved
 *    13×i16 records + two u32 millisecond timestamps.
 *
 * The second format is intentionally documented as an empirical outer frame;
 * it is not silently fed into the stricter AOT length contract.
 */
class PoinTGoFrameDecoder(
    private val calibration: PoinTGoCalibration = PoinTGoCalibration(),
) : RawBatchDecoder {
    private var firstSensorTimestamp: Long? = null
    private var timestampOriginNanos: Long = 0L
    private var lastExpandedTimestamp: Long? = null
    private var wrapCount: Long = 0L

    override fun reset() {
        firstSensorTimestamp = null
        timestampOriginNanos = 0L
        lastExpandedTimestamp = null
        wrapCount = 0L
    }

    override fun decode(payload: ByteArray, receiveTimestampNanos: Long): List<RawSample> {
        if (payload.isEmpty() || unsigned(payload[0]) != PACKET_TYPE_RAW_IMU) return emptyList()

        val aotCount = payload.getOrNull(1)?.let(::unsigned) ?: 0
        if (aotCount > 0 && payload.size == AOT_HEADER_BYTES + AOT_RECORD_BYTES * aotCount) {
            return decodeAotBatch(payload, aotCount, receiveTimestampNanos)
        }
        if (payload.size == HCI_NOTIFICATION_BYTES) {
            return decodeCapturedHciNotification(payload, receiveTimestampNanos)
        }
        return emptyList()
    }

    private fun decodeAotBatch(
        payload: ByteArray,
        sampleCount: Int,
        receiveTimestampNanos: Long,
    ): List<RawSample> {
        val baseMicros = uint32(payload, 4)
        val intervalMicros = (1_000_000.0 / calibration.aotSampleRateHz).toLong()
        return buildList(sampleCount) {
            repeat(sampleCount) { index ->
                val offset = AOT_HEADER_BYTES + index * AOT_RECORD_BYTES
                val v0 = int16(payload, offset)
                val v1 = int16(payload, offset + 2)
                val v2 = int16(payload, offset + 4)
                val v3 = int16(payload, offset + 6)
                val v4 = int16(payload, offset + 8)
                val v5 = int16(payload, offset + 10)
                val sensorTimestamp = baseMicros + index * intervalMicros
                add(
                    RawSample(
                        timestampNanos = timestampNanos(
                            rawTimestamp = sensorTimestamp,
                            receiveTimestampNanos = receiveTimestampNanos,
                            unitNanos = 1_000L,
                        ),
                        linearAccelerationMps2 = Vector3(
                            v0 * calibration.aotAccelerationMps2PerCount,
                            v1 * calibration.aotAccelerationMps2PerCount,
                            v2 * calibration.aotAccelerationMps2PerCount,
                        ),
                        // The vendor parser applies its acceleration constant
                        // to field 3 and gyro constant to fields 4/5. Preserve
                        // that exact wire behavior while exposing gx/gy/gz.
                        angularVelocityRadPerSec = Vector3(
                            v3 * calibration.aotAccelerationMps2PerCount,
                            v4 * calibration.gyroRadPerSecPerCount,
                            v5 * calibration.gyroRadPerSecPerCount,
                        ),
                    ),
                )
            }
        }
    }

    private fun decodeCapturedHciNotification(
        payload: ByteArray,
        receiveTimestampNanos: Long,
    ): List<RawSample> {
        // After the packet type there are two interleaved records. Each record
        // contains 13 signed 16-bit values; the final eight bytes are timestamps.
        val recordA = IntArray(HCI_FIELDS) { field -> int16(payload, 1 + field * 4) }
        val recordB = IntArray(HCI_FIELDS) { field -> int16(payload, 3 + field * 4) }
        val timestampA = uint32(payload, 53)
        val timestampB = uint32(payload, 57)
        return listOf(
            capturedSample(recordA, timestampA, receiveTimestampNanos),
            capturedSample(recordB, timestampB, receiveTimestampNanos),
        )
    }

    private fun capturedSample(
        fields: IntArray,
        timestampMillis: Long,
        receiveTimestampNanos: Long,
    ): RawSample = RawSample(
        timestampNanos = timestampNanos(
            rawTimestamp = timestampMillis,
            receiveTimestampNanos = receiveTimestampNanos,
            unitNanos = 1_000_000L,
        ),
        linearAccelerationMps2 = Vector3(
            fields[0] * calibration.hciAccelerationMps2PerCount,
            fields[1] * calibration.hciAccelerationMps2PerCount,
            fields[2] * calibration.hciAccelerationMps2PerCount,
        ),
        angularVelocityRadPerSec = Vector3(
            fields[3] * calibration.gyroRadPerSecPerCount,
            fields[4] * calibration.gyroRadPerSecPerCount,
            fields[5] * calibration.gyroRadPerSecPerCount,
        ),
    )

    /** Convert a wrapping u32 sensor clock into monotonic app-clock nanoseconds. */
    private fun timestampNanos(
        rawTimestamp: Long,
        receiveTimestampNanos: Long,
        unitNanos: Long,
    ): Long {
        val previousRaw = lastExpandedTimestamp?.and(UINT32_MASK)
        if (previousRaw != null && rawTimestamp < previousRaw &&
            previousRaw - rawTimestamp > WRAP_THRESHOLD
        ) {
            wrapCount += 1L
        }
        val expanded = rawTimestamp + wrapCount * (1L shl 32)
        lastExpandedTimestamp = expanded
        if (firstSensorTimestamp == null) {
            firstSensorTimestamp = expanded
            timestampOriginNanos = receiveTimestampNanos - expanded * unitNanos
        }
        return timestampOriginNanos + expanded * unitNanos
    }

    private companion object {
        const val PACKET_TYPE_RAW_IMU = 0x04
        const val AOT_HEADER_BYTES = 8
        const val AOT_RECORD_BYTES = 12
        const val HCI_NOTIFICATION_BYTES = 61
        const val HCI_FIELDS = 13
        const val UINT32_MASK = 0xffff_ffffL
        const val WRAP_THRESHOLD = 0x8000_0000L

        fun unsigned(value: Byte): Int = value.toInt() and 0xff

        fun int16(bytes: ByteArray, offset: Int): Int {
            val raw = (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8)
            return if (raw and 0x8000 != 0) raw - 0x1_0000 else raw
        }

        fun uint32(bytes: ByteArray, offset: Int): Long =
            (bytes[offset].toLong() and 0xff) or
                ((bytes[offset + 1].toLong() and 0xff) shl 8) or
                ((bytes[offset + 2].toLong() and 0xff) shl 16) or
                ((bytes[offset + 3].toLong() and 0xff) shl 24)
    }
}
