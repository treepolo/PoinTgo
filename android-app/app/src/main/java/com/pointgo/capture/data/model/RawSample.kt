package com.pointgo.capture.data.model

import kotlin.math.sqrt

/** A three-axis vector in the unit stated by the containing field. */
data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Double): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Double): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)

    fun magnitude(): Double = sqrt(x * x + y * y + z * z)

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)
    }
}

/**
 * One timestamped IMU frame. The transport should fill linear acceleration
 * and angular velocity; angular acceleration may be supplied by the device or
 * derived by the recorder from adjacent frames.
 */
data class RawSample(
    val timestampNanos: Long,
    val linearAccelerationMps2: Vector3,
    val angularVelocityRadPerSec: Vector3,
    val angularAccelerationRadPerSec2: Vector3? = null,
) {
    val linearAccelerationMagnitudeMps2: Double
        get() = linearAccelerationMps2.magnitude()

    val angularVelocityMagnitudeRadPerSec: Double
        get() = angularVelocityRadPerSec.magnitude()

    val angularAccelerationMagnitudeRadPerSec2: Double
        get() = angularAccelerationRadPerSec2?.magnitude() ?: 0.0
}

data class SessionAnnotation(
    val timestampNanos: Long,
    val label: String,
    val note: String = "",
)

data class MeasurementSession(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val samples: List<RawSample>,
    val annotations: List<SessionAnnotation> = emptyList(),
)

enum class Metric(val label: String, val unit: String) {
    LINEAR_ACCELERATION("線性加速度", "m/s²"),
    ANGULAR_ACCELERATION("角加速度", "rad/s²"),
    ANGULAR_VELOCITY("角速度", "rad/s"),
    LINEAR_ACCELERATION_MAGNITUDE("線性加速度合成值", "m/s²"),
    ANGULAR_ACCELERATION_MAGNITUDE("角加速度合成值", "rad/s²"),
    ;

    fun value(sample: RawSample): Double = when (this) {
        LINEAR_ACCELERATION -> sample.linearAccelerationMps2.x
        ANGULAR_ACCELERATION -> sample.angularAccelerationRadPerSec2?.x ?: 0.0
        ANGULAR_VELOCITY -> sample.angularVelocityRadPerSec.x
        LINEAR_ACCELERATION_MAGNITUDE -> sample.linearAccelerationMagnitudeMps2
        ANGULAR_ACCELERATION_MAGNITUDE -> sample.angularAccelerationMagnitudeRadPerSec2
    }
}
