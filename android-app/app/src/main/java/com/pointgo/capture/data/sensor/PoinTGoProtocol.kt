package com.pointgo.capture.data.sensor

import java.util.UUID

/** UUIDs and command bytes observed from the Poin+T sensor and its Android app. */
object PoinTGoProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val COMMAND_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
    val NOTIFY_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * Writes captured during a normal high-rate measurement in the vendor app.
     * They are deliberately kept as a named profile so a future firmware can
     * replace the sequence without changing the BLE transport.
     *
     * These bytes are not a calibration command; they select the same raw IMU
     * stream that produced the HCI capture in analysis/hci/poinT_att.txt.
     */
    val CAPTURED_HIGH_RATE_START: List<ByteArray> = listOf(
        bytes(0x05, 0x01, 0x04),
        bytes(0x03, 0x03),
        bytes(0x03, 0x00),
        bytes(0x05, 0x01, 0x06),
        bytes(0x05, 0x01, 0x04),
        bytes(0x03, 0x03),
        bytes(0x03, 0x00),
        bytes(0x05, 0x01, 0x06),
        bytes(0x05, 0x01, 0x04),
        bytes(0x03, 0x00),
        bytes(0x05, 0x01, 0x06),
        bytes(0x05, 0x01, 0x08),
        bytes(0x04, 0x03, 0x01),
        bytes(0x04, 0x01, 0x01),
        bytes(0x04, 0x01, 0x06),
        bytes(0x04, 0x01, 0x07),
        bytes(0x04, 0x01, 0x04),
        bytes(0x04, 0x01, 0x05),
        bytes(0x04, 0x01, 0x02),
        bytes(0x04, 0x01, 0x03),
        bytes(0x04, 0x01, 0x08),
        bytes(0x05, 0x01, 0x06),
        bytes(0x05, 0x01, 0x04),
        bytes(0x03, 0x03),
    )

    /** The observed stop/reset write; kept separate from the start profile. */
    val CAPTURED_STOP: List<ByteArray> = listOf(bytes(0x03, 0x03))

    private fun bytes(vararg values: Int): ByteArray =
        values.map { it.toByte() }.toByteArray()
}
