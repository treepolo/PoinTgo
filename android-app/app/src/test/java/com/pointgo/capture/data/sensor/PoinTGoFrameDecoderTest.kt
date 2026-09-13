package com.pointgo.capture.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PoinTGoFrameDecoderTest {
    @Test
    fun decodesCapturedHciNotificationAsTwoSamples() {
        val payload =
            "049e239c2323f120f1e005e105670166010500ebff550021004401d1000000000000000000240028000000000000000000040005001100000022000000"
                .hexBytes()
        val receiveNanos = 10_000_000_000L
        val samples = PoinTGoFrameDecoder().decode(payload, receiveNanos)

        assertEquals(2, samples.size)
        val accelerationScale = 9.80665 * 4.0 / 32768.0
        assertEquals(9_118 * accelerationScale, samples[0].linearAccelerationMps2.x, 1e-9)
        assertEquals(-3_805 * accelerationScale, samples[0].linearAccelerationMps2.y, 1e-9)
        assertEquals(5 * ((2000.0 / 32768.0) * PI / 180.0), samples[0].angularVelocityRadPerSec.y, 1e-12)
        assertEquals(17_000_000L, samples[1].timestampNanos - samples[0].timestampNanos)
    }

    @Test
    fun decodesAotBatchWithLittleEndianSignedValues() {
        val payload = byteArrayOf(
            0x04,
            0x02,
            0x00,
            0x00,
            0x40,
            0x42,
            0x0f,
            0x00,
            0x10,
            0x00,
            0xf0.toByte(),
            0xff.toByte(),
            0x20,
            0x00,
            0x2c,
            0x01,
            0x34,
            0x12,
            0x78,
            0x56,
            0xbc.toByte(),
            0x9a.toByte(),
            0x02,
            0x00,
            0x04,
            0x00,
            0x06,
            0x00,
            0x08,
            0x00,
            0x0a,
            0x00,
        )
        val samples = PoinTGoFrameDecoder().decode(payload, 2_000_000_000L)

        assertEquals(2, samples.size)
        val accelScale = 0.0047884033203125
        val gyroScale = (2000.0 / 32768.0) * PI / 180.0
        assertEquals(16 * accelScale, samples[0].linearAccelerationMps2.x, 1e-12)
        assertEquals(-16 * accelScale, samples[0].linearAccelerationMps2.y, 1e-12)
        assertEquals(0x12c * accelScale, samples[0].angularVelocityRadPerSec.x, 1e-12)
        assertEquals(0x1234 * gyroScale, samples[0].angularVelocityRadPerSec.y, 1e-12)
        assertEquals(0x5678 * gyroScale, samples[0].angularVelocityRadPerSec.z, 1e-12)
        assertTrue(samples[1].timestampNanos > samples[0].timestampNanos)
    }

    @Test
    fun rejectsMalformedRawImuPacket() {
        val malformed = byteArrayOf(0x04, 0x02, 0x00, 0x00, 0x00)
        assertTrue(PoinTGoFrameDecoder().decode(malformed, 1L).isEmpty())
    }

    private fun String.hexBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
