package com.treepolo.pointgo.clone;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline motion pipeline used by the private clone.
 *
 * The public names and data stages mirror the vendor AOT models
 * (GlobalAcceleration, GlobalVelocity, RotationQuaternion, VBT/Jump/1RM
 * records).  The formulas are deliberately labelled as a compatible
 * reconstruction until a controlled vendor golden vector proves bit-level
 * equivalence.
 */
public final class VendorMotionEngine {
    public static final String ALGORITHM_VERSION = "vendor-compatible-reconstruction-2026.09";
    public static final double GRAVITY = 9.80665;

    public enum Module {
        GENERAL("通用連續資料"),
        THROW("甩球／投擲"),
        ROTATION("角運動"),
        VBT("重量訓練／VBT"),
        ONE_RM("1RM"),
        JUMP("跳躍"),
        CMJ("反向跳／CMJ");

        private final String label;

        Module(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Face {
        LEFT("左面"), RIGHT("右面"), FRONT("前面"), BACK("背面"), TOP("頂面"), BOTTOM("底面");

        private final String label;

        Face(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class RawSample {
        public final long timestampMillis;
        public final double ax;
        public final double ay;
        public final double az;
        public final double gx;
        public final double gy;
        public final double gz;

        public RawSample(long timestampMillis, double ax, double ay, double az,
                         double gx, double gy, double gz) {
            this.timestampMillis = timestampMillis;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
        }
    }

    public static final class DerivedSample {
        public final long timestampMillis;
        public final double elapsedSeconds;
        public final double rawAx;
        public final double rawAy;
        public final double rawAz;
        public final double rawGx;
        public final double rawGy;
        public final double rawGz;
        public final double calibratedAx;
        public final double calibratedAy;
        public final double calibratedAz;
        public final double gravityAx;
        public final double gravityAy;
        public final double gravityAz;
        public final double linearAx;
        public final double linearAy;
        public final double linearAz;
        public final double worldAx;
        public final double worldAy;
        public final double worldAz;
        public final double velocityX;
        public final double velocityY;
        public final double velocityZ;
        public final double linearMagnitude;
        public final double linearSpeed;
        public final double gx;
        public final double gy;
        public final double gz;
        public final double angularAx;
        public final double angularAy;
        public final double angularAz;
        public final double angularVelocityMagnitude;
        public final double angularAccelerationMagnitude;
        public final double quaternionW;
        public final double quaternionX;
        public final double quaternionY;
        public final double quaternionZ;

        private DerivedSample(long timestampMillis, double elapsedSeconds,
                              RawSample raw,
                              double calibratedAx, double calibratedAy, double calibratedAz,
                              double gravityAx, double gravityAy, double gravityAz,
                              double linearAx, double linearAy, double linearAz,
                              double worldAx, double worldAy, double worldAz,
                              double velocityX, double velocityY, double velocityZ,
                              double linearMagnitude, double linearSpeed,
                              double gx, double gy, double gz,
                              double angularAx, double angularAy, double angularAz,
                              double angularVelocityMagnitude, double angularAccelerationMagnitude,
                              double quaternionW, double quaternionX, double quaternionY,
                              double quaternionZ) {
            this.timestampMillis = timestampMillis;
            this.elapsedSeconds = elapsedSeconds;
            this.rawAx = raw.ax;
            this.rawAy = raw.ay;
            this.rawAz = raw.az;
            this.rawGx = raw.gx;
            this.rawGy = raw.gy;
            this.rawGz = raw.gz;
            this.calibratedAx = calibratedAx;
            this.calibratedAy = calibratedAy;
            this.calibratedAz = calibratedAz;
            this.gravityAx = gravityAx;
            this.gravityAy = gravityAy;
            this.gravityAz = gravityAz;
            this.linearAx = linearAx;
            this.linearAy = linearAy;
            this.linearAz = linearAz;
            this.worldAx = worldAx;
            this.worldAy = worldAy;
            this.worldAz = worldAz;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
            this.linearMagnitude = linearMagnitude;
            this.linearSpeed = linearSpeed;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.angularAx = angularAx;
            this.angularAy = angularAy;
            this.angularAz = angularAz;
            this.angularVelocityMagnitude = angularVelocityMagnitude;
            this.angularAccelerationMagnitude = angularAccelerationMagnitude;
            this.quaternionW = quaternionW;
            this.quaternionX = quaternionX;
            this.quaternionY = quaternionY;
            this.quaternionZ = quaternionZ;
        }
    }

    public static final class Profile {
        private String id;
        private String algorithmVersion;
        private long updatedAtMillis;
        private double accelBiasX;
        private double accelBiasY;
        private double accelBiasZ;
        private double accelScaleX = 1.0;
        private double accelScaleY = 1.0;
        private double accelScaleZ = 1.0;
        private double gyroBiasX;
        private double gyroBiasY;
        private double gyroBiasZ;
        private boolean sixFaceComplete;
        private boolean gyroComplete;

        private Profile() {
            id = "default";
            algorithmVersion = ALGORITHM_VERSION;
        }

        public static Profile defaultProfile() {
            return new Profile();
        }

        public Profile copy() {
            Profile copy = new Profile();
            copy.id = id;
            copy.algorithmVersion = algorithmVersion;
            copy.updatedAtMillis = updatedAtMillis;
            copy.accelBiasX = accelBiasX;
            copy.accelBiasY = accelBiasY;
            copy.accelBiasZ = accelBiasZ;
            copy.accelScaleX = accelScaleX;
            copy.accelScaleY = accelScaleY;
            copy.accelScaleZ = accelScaleZ;
            copy.gyroBiasX = gyroBiasX;
            copy.gyroBiasY = gyroBiasY;
            copy.gyroBiasZ = gyroBiasZ;
            copy.sixFaceComplete = sixFaceComplete;
            copy.gyroComplete = gyroComplete;
            return copy;
        }

        public void save(SharedPreferences preferences) {
            preferences.edit()
                    .putString("profile.id", id)
                    .putString("profile.algorithmVersion", algorithmVersion)
                    .putLong("profile.updatedAt", updatedAtMillis)
                    .putFloat("profile.accelBiasX", (float) accelBiasX)
                    .putFloat("profile.accelBiasY", (float) accelBiasY)
                    .putFloat("profile.accelBiasZ", (float) accelBiasZ)
                    .putFloat("profile.accelScaleX", (float) accelScaleX)
                    .putFloat("profile.accelScaleY", (float) accelScaleY)
                    .putFloat("profile.accelScaleZ", (float) accelScaleZ)
                    .putFloat("profile.gyroBiasX", (float) gyroBiasX)
                    .putFloat("profile.gyroBiasY", (float) gyroBiasY)
                    .putFloat("profile.gyroBiasZ", (float) gyroBiasZ)
                    .putBoolean("profile.sixFaceComplete", sixFaceComplete)
                    .putBoolean("profile.gyroComplete", gyroComplete)
                    .apply();
        }

        public static Profile load(SharedPreferences preferences) {
            Profile profile = defaultProfile();
            profile.id = preferences.getString("profile.id", "default");
            profile.algorithmVersion = preferences.getString(
                    "profile.algorithmVersion", ALGORITHM_VERSION);
            profile.updatedAtMillis = preferences.getLong("profile.updatedAt", 0L);
            profile.accelBiasX = preferences.getFloat("profile.accelBiasX", 0f);
            profile.accelBiasY = preferences.getFloat("profile.accelBiasY", 0f);
            profile.accelBiasZ = preferences.getFloat("profile.accelBiasZ", 0f);
            profile.accelScaleX = preferences.getFloat("profile.accelScaleX", 1f);
            profile.accelScaleY = preferences.getFloat("profile.accelScaleY", 1f);
            profile.accelScaleZ = preferences.getFloat("profile.accelScaleZ", 1f);
            profile.gyroBiasX = preferences.getFloat("profile.gyroBiasX", 0f);
            profile.gyroBiasY = preferences.getFloat("profile.gyroBiasY", 0f);
            profile.gyroBiasZ = preferences.getFloat("profile.gyroBiasZ", 0f);
            profile.sixFaceComplete = preferences.getBoolean("profile.sixFaceComplete", false);
            profile.gyroComplete = preferences.getBoolean("profile.gyroComplete", false);
            return profile;
        }

        public String getId() {
            return id;
        }

        public String getAlgorithmVersion() {
            return algorithmVersion;
        }

        public boolean isSixFaceComplete() {
            return sixFaceComplete;
        }

        public boolean isGyroComplete() {
            return gyroComplete;
        }

        public String describe() {
            String accel = sixFaceComplete ? "六面加速度校正" : "預設加速度比例（尚未六面校正）";
            String gyro = gyroComplete ? "陀螺儀偏置已校正" : "陀螺儀偏置未校正";
            return accel + " · " + gyro + " · " + algorithmVersion;
        }

        /** Export the actual profile parameters so a session can be reproduced offline. */
        public String toJson() {
            return String.format(Locale.US,
                    "{\"id\":\"%s\",\"algorithmVersion\":\"%s\",\"updatedAtMillis\":%d,"
                            + "\"sixFaceComplete\":%s,\"gyroComplete\":%s,"
                            + "\"accelBias\":[%.9f,%.9f,%.9f],"
                            + "\"accelScale\":[%.9f,%.9f,%.9f],"
                            + "\"gyroBias\":[%.9f,%.9f,%.9f]}",
                    escape(id), escape(algorithmVersion), updatedAtMillis,
                    Boolean.toString(sixFaceComplete), Boolean.toString(gyroComplete),
                    accelBiasX, accelBiasY, accelBiasZ,
                    accelScaleX, accelScaleY, accelScaleZ,
                    gyroBiasX, gyroBiasY, gyroBiasZ);
        }
    }

    public static final class CalibrationSession {
        private final EnumMap<Face, ArrayList<RawSample>> faceSamples =
                new EnumMap<>(Face.class);

        public CalibrationSession() {
            for (Face face : Face.values()) faceSamples.put(face, new ArrayList<RawSample>());
        }

        public int add(Face face, List<RawSample> samples) {
            ArrayList<RawSample> target = faceSamples.get(face);
            if (target == null || samples == null) return 0;
            target.clear();
            int from = Math.max(0, samples.size() - 120);
            for (int index = from; index < samples.size(); index++) {
                target.add(samples.get(index));
            }
            return target.size();
        }

        public boolean isComplete() {
            for (Face face : Face.values()) {
                if (faceSamples.get(face).size() < 8) return false;
            }
            return true;
        }

        public String status() {
            StringBuilder builder = new StringBuilder();
            for (Face face : Face.values()) {
                if (builder.length() > 0) builder.append("、");
                builder.append(face.getLabel()).append(" ")
                        .append(faceSamples.get(face).size() >= 8 ? "完成" : "待取樣");
            }
            return builder.toString();
        }

        /**
         * Returns an actionable warning when captured faces are not sufficiently still
         * or do not show gravity on the expected axis. An incomplete session is not
         * treated as a quality failure until all six faces have been captured.
         */
        public String qualityWarning() {
            if (!isComplete()) return "";
            for (Face face : Face.values()) {
                ArrayList<RawSample> values = faceSamples.get(face);
                double[] mean = new double[3];
                for (RawSample sample : values) {
                    mean[0] += sample.ax;
                    mean[1] += sample.ay;
                    mean[2] += sample.az;
                }
                double divisor = Math.max(1, values.size());
                for (int axis = 0; axis < 3; axis++) mean[axis] /= divisor;
                int expectedAxis = face == Face.LEFT || face == Face.RIGHT ? 0
                        : (face == Face.FRONT || face == Face.BACK ? 1 : 2);
                double dominant = Math.abs(mean[expectedAxis]);
                double transverseSquared = 0.0;
                double residualSquared = 0.0;
                for (int axis = 0; axis < 3; axis++) {
                    if (axis != expectedAxis) transverseSquared += mean[axis] * mean[axis];
                }
                for (RawSample sample : values) {
                    double dx = sample.ax - mean[0];
                    double dy = sample.ay - mean[1];
                    double dz = sample.az - mean[2];
                    residualSquared += dx * dx + dy * dy + dz * dz;
                }
                double residualRms = Math.sqrt(residualSquared / Math.max(1, values.size()));
                if (dominant < GRAVITY * 0.45) {
                    return face.getLabel() + " 的重力軸不足，請讓該面平放並保持靜止";
                }
                if (dominant < Math.sqrt(transverseSquared) * 1.4) {
                    return face.getLabel() + " 的方向不明，請重新對準平面";
                }
                if (residualRms > GRAVITY * 0.15) {
                    return face.getLabel() + " 取樣期間晃動過大，請保持靜止後重取";
                }
            }
            return "";
        }

        public boolean isQualitySufficient() {
            return qualityWarning().isEmpty();
        }
        public Profile finish(Profile base) {
            if (!isComplete() || !isQualitySufficient()) return base;
            double[][] means = new double[Face.values().length][3];
            double[][] gyroMeans = new double[Face.values().length][3];
            for (Face face : Face.values()) {
                ArrayList<RawSample> values = faceSamples.get(face);
                for (RawSample sample : values) {
                    means[face.ordinal()][0] += sample.ax;
                    means[face.ordinal()][1] += sample.ay;
                    means[face.ordinal()][2] += sample.az;
                    gyroMeans[face.ordinal()][0] += sample.gx;
                    gyroMeans[face.ordinal()][1] += sample.gy;
                    gyroMeans[face.ordinal()][2] += sample.gz;
                }
                double divisor = Math.max(1, values.size());
                for (int axis = 0; axis < 3; axis++) {
                    means[face.ordinal()][axis] /= divisor;
                    gyroMeans[face.ordinal()][axis] /= divisor;
                }
            }
            Profile result = base.copy();
            result.id = "six-face-" + System.currentTimeMillis();
            result.updatedAtMillis = System.currentTimeMillis();
            result.algorithmVersion = ALGORITHM_VERSION;
            result.accelBiasX = (means[Face.LEFT.ordinal()][0] + means[Face.RIGHT.ordinal()][0]) / 2.0;
            result.accelBiasY = (means[Face.FRONT.ordinal()][1] + means[Face.BACK.ordinal()][1]) / 2.0;
            result.accelBiasZ = (means[Face.TOP.ordinal()][2] + means[Face.BOTTOM.ordinal()][2]) / 2.0;
            result.accelScaleX = scaleFor(
                    means[Face.LEFT.ordinal()][0] - result.accelBiasX,
                    means[Face.RIGHT.ordinal()][0] - result.accelBiasX);
            result.accelScaleY = scaleFor(
                    means[Face.FRONT.ordinal()][1] - result.accelBiasY,
                    means[Face.BACK.ordinal()][1] - result.accelBiasY);
            result.accelScaleZ = scaleFor(
                    means[Face.TOP.ordinal()][2] - result.accelBiasZ,
                    means[Face.BOTTOM.ordinal()][2] - result.accelBiasZ);
            result.gyroBiasX = averageGyro(gyroMeans, 0);
            result.gyroBiasY = averageGyro(gyroMeans, 1);
            result.gyroBiasZ = averageGyro(gyroMeans, 2);
            result.sixFaceComplete = true;
            result.gyroComplete = true;
            return result;
        }

        private static double scaleFor(double positive, double negative) {
            double signedRange = positive - negative;
            if (Math.abs(signedRange) < 0.10) return 1.0;
            // Preserve the physical axis direction. Taking abs(range) would make an
            // inverted sensor report the opposite global direction after calibration.
            return 2.0 * GRAVITY / signedRange;
        }

        private static double averageGyro(double[][] values, int axis) {
            double result = 0.0;
            for (double[] value : values) result += value[axis];
            return result / Math.max(1, values.length);
        }
    }

    public static final class Event {
        public final String kind;
        public final String label;
        public final int startIndex;
        public final int endIndex;
        public final double startSeconds;
        public final double endSeconds;
        public final double value;

        Event(String kind, String label, int startIndex, int endIndex,
              double startSeconds, double endSeconds, double value) {
            this.kind = kind;
            this.label = label;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.value = value;
        }

        String toJson() {
            return String.format(Locale.US,
                    "{\"kind\":\"%s\",\"label\":\"%s\",\"startIndex\":%d,\"endIndex\":%d,\"startSeconds\":%.6f,\"endSeconds\":%.6f,\"value\":%.6f}",
                    escape(kind), escape(label), startIndex, endIndex,
                    startSeconds, endSeconds, value);
        }
    }

    public static final class AnalysisResult {
        public final Module module;
        public final String algorithmVersion;
        public final ArrayList<Event> events = new ArrayList<>();
        public final LinkedHashMap<String, Double> metrics = new LinkedHashMap<>();
        public int repetitionCount;
        public String status;

        AnalysisResult(Module module, String status) {
            this.module = module;
            this.algorithmVersion = ALGORITHM_VERSION;
            this.status = status;
        }

        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append(module.getLabel()).append(" · ").append(status)
                    .append(" · 判定次數 ").append(repetitionCount);
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                builder.append("\n").append(metricLabel(entry.getKey())).append(" ")
                        .append(String.format(Locale.US, "%.3f", entry.getValue()));
            }
            return builder.toString();
        }

        public String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append("{\"module\":\"").append(escape(module.name()))
                    .append("\",\"algorithmVersion\":\"")
                    .append(escape(algorithmVersion)).append("\",\"status\":\"")
                    .append(escape(status)).append("\",\"repetitionCount\":")
                    .append(repetitionCount).append(",\"metrics\":{");
            int index = 0;
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                if (index++ > 0) builder.append(",");
                builder.append("\"").append(escape(entry.getKey())).append("\":")
                        .append(String.format(Locale.US, "%.9f", entry.getValue()));
            }
            builder.append("},\"events\":[");
            for (int eventIndex = 0; eventIndex < events.size(); eventIndex++) {
                if (eventIndex > 0) builder.append(",");
                builder.append(events.get(eventIndex).toJson());
            }
            return builder.append("]}").toString();
        }
    }

    private Profile profile;
    private long lastTimestampMillis = Long.MIN_VALUE;
    private double elapsedSeconds;
    private double gravityAx;
    private double gravityAy;
    private double gravityAz;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double previousGx;
    private double previousGy;
    private double previousGz;
    private boolean initialized;
    private double quaternionW = 1.0;
    private double quaternionX;
    private double quaternionY;
    private double quaternionZ;

    public VendorMotionEngine(Profile profile) {
        this.profile = profile == null ? Profile.defaultProfile() : profile.copy();
        reset();
    }

    public Profile getProfile() {
        return profile.copy();
    }

    public void setProfile(Profile profile) {
        this.profile = profile == null ? Profile.defaultProfile() : profile.copy();
        reset();
    }

    public void reset() {
        lastTimestampMillis = Long.MIN_VALUE;
        elapsedSeconds = 0.0;
        gravityAx = 0.0;
        gravityAy = 0.0;
        gravityAz = GRAVITY;
        velocityX = 0.0;
        velocityY = 0.0;
        velocityZ = 0.0;
        previousGx = 0.0;
        previousGy = 0.0;
        previousGz = 0.0;
        initialized = false;
        quaternionW = 1.0;
        quaternionX = 0.0;
        quaternionY = 0.0;
        quaternionZ = 0.0;
    }

    public DerivedSample process(RawSample raw) {
        if (raw == null) return null;
        boolean hadPrevious = lastTimestampMillis != Long.MIN_VALUE;
        double dt = lastTimestampMillis == Long.MIN_VALUE
                ? 1.0 / 120.0
                : (raw.timestampMillis - lastTimestampMillis) / 1000.0;
        if (dt <= 0.0001 || dt > 0.25) dt = 1.0 / 120.0;
        if (lastTimestampMillis != Long.MIN_VALUE) elapsedSeconds += dt;
        lastTimestampMillis = raw.timestampMillis;

        double ax = (raw.ax - profile.accelBiasX) * profile.accelScaleX;
        double ay = (raw.ay - profile.accelBiasY) * profile.accelScaleY;
        double az = (raw.az - profile.accelBiasZ) * profile.accelScaleZ;
        double gx = raw.gx - profile.gyroBiasX;
        double gy = raw.gy - profile.gyroBiasY;
        double gz = raw.gz - profile.gyroBiasZ;

        if (!initialized) {
            gravityAx = ax;
            gravityAy = ay;
            gravityAz = az;
            initialized = true;
        }
        double gravityAlpha = dt > 0.08 ? 0.18 : 0.08;
        gravityAx += (ax - gravityAx) * gravityAlpha;
        gravityAy += (ay - gravityAy) * gravityAlpha;
        gravityAz += (az - gravityAz) * gravityAlpha;
        double linearAx = ax - gravityAx;
        double linearAy = ay - gravityAy;
        double linearAz = az - gravityAz;

        double angularSpeed = magnitude(gx, gy, gz);
        double halfAngle = angularSpeed * dt * 0.5;
        double sinScale = angularSpeed < 1.0e-9 ? dt * 0.5 : Math.sin(halfAngle) / angularSpeed;
        double dqW = -sinScale * (gx * quaternionX + gy * quaternionY + gz * quaternionZ);
        double dqX = sinScale * (gx * quaternionW + gy * quaternionZ - gz * quaternionY);
        double dqY = sinScale * (gy * quaternionW + gz * quaternionX - gx * quaternionZ);
        double dqZ = sinScale * (gz * quaternionW + gx * quaternionY - gy * quaternionX);
        quaternionW += dqW;
        quaternionX += dqX;
        quaternionY += dqY;
        quaternionZ += dqZ;
        normalizeQuaternion();

        double worldAx = rotateX(linearAx, linearAy, linearAz);
        double worldAy = rotateY(linearAx, linearAy, linearAz);
        double worldAz = rotateZ(linearAx, linearAy, linearAz);
        velocityX += worldAx * dt;
        velocityY += worldAy * dt;
        velocityZ += worldAz * dt;
        boolean stationary = magnitude(linearAx, linearAy, linearAz) < 0.16
                && angularSpeed < 0.12;
        if (stationary) {
            velocityX = 0.0;
            velocityY = 0.0;
            velocityZ = 0.0;
        } else {
            double leak = Math.exp(-0.18 * dt);
            velocityX *= leak;
            velocityY *= leak;
            velocityZ *= leak;
        }

        double angularAx = hadPrevious ? (gx - previousGx) / dt : 0.0;
        double angularAy = hadPrevious ? (gy - previousGy) / dt : 0.0;
        double angularAz = hadPrevious ? (gz - previousGz) / dt : 0.0;
        previousGx = gx;
        previousGy = gy;
        previousGz = gz;
        double angularAccelerationMagnitude = magnitude(angularAx, angularAy, angularAz);
        return new DerivedSample(
                raw.timestampMillis, elapsedSeconds, raw,
                ax, ay, az,
                gravityAx, gravityAy, gravityAz,
                linearAx, linearAy, linearAz,
                worldAx, worldAy, worldAz,
                velocityX, velocityY, velocityZ,
                magnitude(linearAx, linearAy, linearAz),
                magnitude(velocityX, velocityY, velocityZ),
                gx, gy, gz,
                angularAx, angularAy, angularAz,
                angularSpeed, angularAccelerationMagnitude,
                quaternionW, quaternionX, quaternionY, quaternionZ);
    }

    private void normalizeQuaternion() {
        double norm = Math.sqrt(quaternionW * quaternionW + quaternionX * quaternionX
                + quaternionY * quaternionY + quaternionZ * quaternionZ);
        if (norm < 1.0e-9) {
            quaternionW = 1.0;
            quaternionX = quaternionY = quaternionZ = 0.0;
            return;
        }
        quaternionW /= norm;
        quaternionX /= norm;
        quaternionY /= norm;
        quaternionZ /= norm;
    }

    private double rotateX(double x, double y, double z) {
        return (1.0 - 2.0 * (quaternionY * quaternionY + quaternionZ * quaternionZ)) * x
                + 2.0 * (quaternionX * quaternionY - quaternionW * quaternionZ) * y
                + 2.0 * (quaternionX * quaternionZ + quaternionW * quaternionY) * z;
    }

    private double rotateY(double x, double y, double z) {
        return 2.0 * (quaternionX * quaternionY + quaternionW * quaternionZ) * x
                + (1.0 - 2.0 * (quaternionX * quaternionX + quaternionZ * quaternionZ)) * y
                + 2.0 * (quaternionY * quaternionZ - quaternionW * quaternionX) * z;
    }

    private double rotateZ(double x, double y, double z) {
        return 2.0 * (quaternionX * quaternionZ - quaternionW * quaternionY) * x
                + 2.0 * (quaternionY * quaternionZ + quaternionW * quaternionX) * y
                + (1.0 - 2.0 * (quaternionX * quaternionX + quaternionY * quaternionY)) * z;
    }

    public static Profile calibrateStill(Profile base, List<RawSample> values) {
        Profile result = base == null ? Profile.defaultProfile() : base.copy();
        if (values == null || values.size() < 8) return result;
        double gx = 0.0;
        double gy = 0.0;
        double gz = 0.0;
        for (RawSample value : values) {
            gx += value.gx;
            gy += value.gy;
            gz += value.gz;
        }
        result.gyroBiasX = gx / values.size();
        result.gyroBiasY = gy / values.size();
        result.gyroBiasZ = gz / values.size();
        result.gyroComplete = true;
        result.algorithmVersion = ALGORITHM_VERSION;
        result.updatedAtMillis = System.currentTimeMillis();
        result.id = "still-" + result.updatedAtMillis;
        return result;
    }

    public static AnalysisResult analyze(List<DerivedSample> values, Module module,
                                         double bodyMassKg, double loadKg) {
        Module selected = module == null ? Module.GENERAL : module;
        AnalysisResult result = new AnalysisResult(selected,
                "官方相容重建；需以官方 golden vector 校對");
        if (values == null || values.size() < 2) {
            result.status = "等待足夠資料";
            return result;
        }
        populateGeneralMetrics(result, values);
        switch (selected) {
            case THROW:
                detectThrowReps(result, values);
                result.status = "甩球／投擲事件";
                break;
            case ROTATION:
                detectRotationReps(result, values);
                result.status = "角速度週期事件";
                break;
            case VBT:
                detectVbt(result, values, bodyMassKg);
                result.status = "VBT rep／速度曲線";
                break;
            case ONE_RM:
                detectVbt(result, values, bodyMassKg);
                populateOneRm(result, loadKg);
                result.status = "1RM 多公式＋LVP 重建";
                break;
            case JUMP:
                detectJump(result, values, false);
                result.status = "跳躍事件";
                break;
            case CMJ:
                detectJump(result, values, true);
                result.status = "反向跳／CMJ 事件";
                break;
            case GENERAL:
            default:
                result.status = "逐點連續資料";
                break;
        }
        result.metrics.put("repetitionCount", (double) result.repetitionCount);
        return result;
    }

    private static void populateGeneralMetrics(AnalysisResult result, List<DerivedSample> values) {
        double peakAccel = 0.0;
        double peakSpeed = 0.0;
        double peakAngular = 0.0;
        double peakAngularAccel = 0.0;
        double sumAccel2 = 0.0;
        double sumAngular2 = 0.0;
        for (DerivedSample value : values) {
            peakAccel = Math.max(peakAccel, value.linearMagnitude);
            peakSpeed = Math.max(peakSpeed, value.linearSpeed);
            peakAngular = Math.max(peakAngular, value.angularVelocityMagnitude);
            peakAngularAccel = Math.max(peakAngularAccel, value.angularAccelerationMagnitude);
            sumAccel2 += value.linearMagnitude * value.linearMagnitude;
            sumAngular2 += value.angularVelocityMagnitude * value.angularVelocityMagnitude;
        }
        double duration = Math.max(0.0, values.get(values.size() - 1).elapsedSeconds);
        result.metrics.put("durationSeconds", duration);
        result.metrics.put("peakLinearAccelerationMps2", peakAccel);
        result.metrics.put("peakLinearSpeedMps", peakSpeed);
        result.metrics.put("peakAngularVelocityRadps", peakAngular);
        result.metrics.put("peakAngularAccelerationRadps2", peakAngularAccel);
        result.metrics.put("linearAccelerationRmsMps2", Math.sqrt(sumAccel2 / values.size()));
        result.metrics.put("angularVelocityRmsRadps", Math.sqrt(sumAngular2 / values.size()));
        result.metrics.put("sampleCount", (double) values.size());
    }

    private static void detectThrowReps(AnalysisResult result,
                                        List<DerivedSample> values) {
        boolean active = false;
        int start = -1;
        int peakIndex = -1;
        double peakSpeed = 0.0;
        double peakAcceleration = 0.0;
        int refractory = 0;
        double totalPeakSpeed = 0.0;
        double totalPeakAcceleration = 0.0;
        for (int index = 0; index < values.size(); index++) {
            if (refractory > 0) refractory--;
            DerivedSample sample = values.get(index);
            double speed = sample.linearSpeed;
            if (!active && refractory == 0 && speed >= 0.85) {
                active = true;
                start = index;
                peakIndex = index;
                peakSpeed = speed;
                peakAcceleration = sample.linearMagnitude;
                continue;
            }
            if (!active) continue;
            if (speed > peakSpeed) {
                peakSpeed = speed;
                peakIndex = index;
            }
            peakAcceleration = Math.max(peakAcceleration, sample.linearMagnitude);
            if (index - start >= 4 && speed <= 0.30) {
                addEvent(result, "throw", "甩球／投擲",
                        values, start, index, peakSpeed);
                addEvent(result, "throwRelease", "甩球／投擲釋放",
                        values, peakIndex, peakIndex, peakAcceleration);
                result.repetitionCount++;
                totalPeakSpeed += peakSpeed;
                totalPeakAcceleration += peakAcceleration;
                active = false;
                refractory = 8;
            }
        }
        if (active && start >= 0) {
            addEvent(result, "throw", "甩球／投擲",
                    values, start, values.size() - 1, peakSpeed);
            addEvent(result, "throwRelease", "甩球／投擲釋放",
                    values, peakIndex, peakIndex, peakAcceleration);
            result.repetitionCount++;
            totalPeakSpeed += peakSpeed;
            totalPeakAcceleration += peakAcceleration;
        }
        if (result.repetitionCount > 0) {
            result.metrics.put("throwPeakSpeedMps",
                    totalPeakSpeed / result.repetitionCount);
            result.metrics.put("throwPeakAccelerationMps2",
                    totalPeakAcceleration / result.repetitionCount);
            result.metrics.put("throwHighThresholdMps", 0.85);
            result.metrics.put("throwLowThresholdMps", 0.30);
        }
    }

    /**
     * Rotation entry uses a dominant signed gyro axis and hysteresis. Direction
     * changes are recorded separately from completed cycles.
     */
    private static void detectRotationReps(AnalysisResult result,
                                           List<DerivedSample> values) {
        double[] energy = new double[3];
        for (DerivedSample sample : values) {
            energy[0] += sample.gx * sample.gx;
            energy[1] += sample.gy * sample.gy;
            energy[2] += sample.gz * sample.gz;
        }
        int axis = energy[1] > energy[0] ? 1 : 0;
        if (energy[2] > energy[axis]) axis = 2;
        // Use a robust median across the recording rather than assuming the
        // first 30 samples are stationary; recordings may begin mid-repetition.
        double bias = 0.0;
        if (!values.isEmpty()) {
            double[] biasSamples = new double[values.size()];
            for (int index = 0; index < values.size(); index++) {
                biasSamples[index] = signedGyro(values.get(index), axis);
            }
            java.util.Arrays.sort(biasSamples);
            // Lower median avoids a 50/50 active-vs-rest recording
            // producing a threshold-suppressing midpoint bias.
            int middle = (biasSamples.length - 1) / 2;
            bias = biasSamples[middle];
        }
        boolean active = false;
        int start = -1;
        int lastDirection = 0;
        int directionChanges = 0;
        double peak = 0.0;
        double peakAngularAcceleration = 0.0;
        double totalPeak = 0.0;
        double totalPeakAngularAcceleration = 0.0;
        int refractory = 0;
        for (int index = 0; index < values.size(); index++) {
            if (refractory > 0) refractory--;
            DerivedSample sample = values.get(index);
            double signed = signedGyro(sample, axis) - bias;
            double magnitude = Math.abs(signed);
            int direction = signed >= 0.0 ? 1 : -1;
            if (!active && refractory == 0 && magnitude >= 0.55) {
                active = true;
                start = index;
                lastDirection = direction;
                directionChanges = 0;
                peak = magnitude;
                peakAngularAcceleration = sample.angularAccelerationMagnitude;
                continue;
            }
            if (!active) continue;
            peak = Math.max(peak, magnitude);
            peakAngularAcceleration = Math.max(
                    peakAngularAcceleration, sample.angularAccelerationMagnitude);
            if (direction != lastDirection && magnitude >= 0.20) {
                directionChanges++;
                lastDirection = direction;
                addEvent(result, "rotationDirectionChange", "角運動方向變化",
                        values, index, index, signed);
            }
            if (index - start >= 4 && magnitude <= 0.20) {
                addEvent(result, "rotation", "角運動週期",
                        values, start, index, peak);
                result.repetitionCount++;
                totalPeak += peak;
                totalPeakAngularAcceleration += peakAngularAcceleration;
                active = false;
                refractory = 8;
            }
        }
        if (active && start >= 0) {
            addEvent(result, "rotation", "角運動週期",
                    values, start, values.size() - 1, peak);
            result.repetitionCount++;
            totalPeak += peak;
            totalPeakAngularAcceleration += peakAngularAcceleration;
        }
        result.metrics.put("rotationDominantAxis", (double) axis);
        result.metrics.put("rotationDirectionChanges", (double) directionChanges);
        if (result.repetitionCount > 0) {
            result.metrics.put("rotationPeakAngularVelocityRadps",
                    totalPeak / result.repetitionCount);
            result.metrics.put("rotationPeakAngularAccelerationRadps2",
                    totalPeakAngularAcceleration / result.repetitionCount);
        }
    }

    private static double signedGyro(DerivedSample sample, int axis) {
        if (axis == 1) return sample.gy;
        if (axis == 2) return sample.gz;
        return sample.gx;
    }
    private static void detectThresholdReps(AnalysisResult result,
                                            List<DerivedSample> values, boolean angular) {
        boolean active = false;
        int start = -1;
        double peak = 0.0;
        double high = angular ? 0.55 : 0.85;
        double low = angular ? 0.20 : 0.30;
        for (int index = 0; index < values.size(); index++) {
            DerivedSample sample = values.get(index);
            double signal = angular ? sample.angularVelocityMagnitude : sample.linearSpeed;
            if (!active && signal >= high) {
                active = true;
                start = index;
                peak = signal;
                continue;
            }
            if (active) {
                peak = Math.max(peak, signal);
                if (signal <= low && index - start >= 4) {
                    addEvent(result, angular ? "rotation" : "throw",
                            angular ? "角運動週期" : "甩球／投擲",
                            values, start, index, peak);
                    active = false;
                    result.repetitionCount++;
                }
            }
        }
        if (active && start >= 0) {
            addEvent(result, angular ? "rotation" : "throw",
                    angular ? "角運動週期" : "甩球／投擲",
                    values, start, values.size() - 1, peak);
            result.repetitionCount++;
        }
    }

    private static void detectVbt(AnalysisResult result, List<DerivedSample> values,
                                  double bodyMassKg) {
        ArrayList<Integer> peaks = new ArrayList<>();
        int lastPeak = -100000;
        for (int index = 1; index < values.size() - 1; index++) {
            double current = values.get(index).linearSpeed;
            if (current < 0.25 || index - lastPeak < 8) continue;
            if (current >= values.get(index - 1).linearSpeed
                    && current >= values.get(index + 1).linearSpeed) {
                peaks.add(index);
                lastPeak = index;
            }
        }
        double firstPeak = 0.0;
        double last = 0.0;
        double totalRom = 0.0;
        double totalMeanVelocity = 0.0;
        double totalPowerPerMass = 0.0;
        double maxPowerPerMass = 0.0;
        double totalEccentricDuration = 0.0;
        double totalEccentricMeanVelocity = 0.0;
        double totalEccentricMaxVelocity = 0.0;
        double totalEccentricRom = 0.0;
        double totalTempoRatio = 0.0;
        double totalTimeToPeak = 0.0;
        double totalRfdPerMass = 0.0;
        double totalDecelerationRate = 0.0;
        double totalRelativeTimeToPeak = 0.0;
        double totalEccentricConcentricRatio = 0.0;
        for (int rep = 0; rep < peaks.size(); rep++) {
            int peakIndex = peaks.get(rep);
            int start = peakIndex;
            while (start > 0 && values.get(start).linearSpeed > 0.08) start--;
            int end = peakIndex;
            while (end + 1 < values.size() && values.get(end).linearSpeed > 0.08) end++;
            double peakVelocity = values.get(peakIndex).linearSpeed;
            double sumVelocity = 0.0;
            double rom = 0.0;
            double power = 0.0;
            double repMaxPowerPerMass = 0.0;
            double eccentricSumVelocity = 0.0;
            double eccentricRom = 0.0;
            double eccentricMaxVelocity = 0.0;
            double decelerationRate = 0.0;
            int eccentricCount = 0;
            int count = 0;
            for (int index = start; index <= end; index++) {
                DerivedSample sample = values.get(index);
                double dt = index == start ? 1.0 / 120.0
                        : Math.max(1.0 / 240.0,
                        sample.elapsedSeconds - values.get(index - 1).elapsedSeconds);
                double speed = sample.linearSpeed;
                sumVelocity += speed;
                rom += speed * dt;
                double instantaneousPowerPerMass = sample.linearMagnitude * speed
                        / Math.max(1.0, bodyMassKg);
                power += sample.linearMagnitude * speed;
                repMaxPowerPerMass = Math.max(repMaxPowerPerMass, instantaneousPowerPerMass);
                if (index <= peakIndex) {
                    eccentricSumVelocity += speed;
                    eccentricRom += speed * dt;
                    eccentricMaxVelocity = Math.max(eccentricMaxVelocity, speed);
                    eccentricCount++;
                }
                if (index > start) {
                    double previousSpeed = values.get(index - 1).linearSpeed;
                    double rate = (speed - previousSpeed) / dt;
                    if (rate < 0.0) decelerationRate = Math.max(decelerationRate, -rate);
                }
                count++;
            }
            double meanVelocity = count == 0 ? 0.0 : sumVelocity / count;
            double powerPerMass = power / Math.max(1.0, bodyMassKg);
            double eccentricDuration = Math.max(0.0,
                    values.get(peakIndex).elapsedSeconds - values.get(start).elapsedSeconds);
            double concentricDuration = Math.max(0.0,
                    values.get(end).elapsedSeconds - values.get(peakIndex).elapsedSeconds);
            double eccentricMeanVelocity = eccentricCount == 0
                    ? 0.0 : eccentricSumVelocity / eccentricCount;
            double tempoRatio = concentricDuration > 1.0e-9
                    ? eccentricDuration / concentricDuration : 0.0;
            double timeToPeakVelocity = eccentricDuration;
            double totalDuration = Math.max(0.0,
                    values.get(end).elapsedSeconds - values.get(start).elapsedSeconds);
            double relativeTimeToPeak = totalDuration > 1.0e-9
                    ? timeToPeakVelocity / totalDuration : 0.0;
            double eccentricConcentricRatio = tempoRatio;
            double rfdPerMass = peakAcceleration(values, start, peakIndex)
                    / Math.max(1.0, bodyMassKg);
            if (rep == 0) firstPeak = peakVelocity;
            last = peakVelocity;
            totalRom += rom;
            totalMeanVelocity += meanVelocity;
            totalPowerPerMass += powerPerMass;
            maxPowerPerMass = Math.max(maxPowerPerMass, repMaxPowerPerMass);
            totalEccentricDuration += eccentricDuration;
            totalEccentricMeanVelocity += eccentricMeanVelocity;
            totalEccentricMaxVelocity += eccentricMaxVelocity;
            totalEccentricRom += eccentricRom;
            totalTempoRatio += tempoRatio;
            totalTimeToPeak += timeToPeakVelocity;
            totalRfdPerMass += rfdPerMass;
            totalDecelerationRate += decelerationRate;
            totalRelativeTimeToPeak += relativeTimeToPeak;
            totalEccentricConcentricRatio += eccentricConcentricRatio;
            addEvent(result, "vbtEccentric", "離心階段",
                    values, start, peakIndex, eccentricMeanVelocity);
            addEvent(result, "vbtConcentric", "向心階段",
                    values, peakIndex, end, meanVelocity);
            addEvent(result, "vbtRep", "VBT 第 " + (rep + 1) + " 次",
                    values, start, end, peakVelocity);
            result.repetitionCount++;
        }
        if (!peaks.isEmpty()) {
            double repCount = peaks.size();
            result.metrics.put("vbtPeakVelocityMps", firstPeak);
            result.metrics.put("vbtLastPeakVelocityMps", last);
            result.metrics.put("rangeOfMotionM", totalRom);
            result.metrics.put("rangeOfMotion", totalRom);
            result.metrics.put("meanVelocityMps", totalMeanVelocity / repCount);
            result.metrics.put("meanPowerPerMassWPerKg", totalPowerPerMass / repCount);
            result.metrics.put("meanPowerPerMass", totalPowerPerMass / repCount);
            result.metrics.put("maxPowerPerMass", maxPowerPerMass);
            result.metrics.put("eccentricDuration", totalEccentricDuration / repCount);
            result.metrics.put("eccentricMeanVelocity", totalEccentricMeanVelocity / repCount);
            result.metrics.put("eccentricMaxVelocity", totalEccentricMaxVelocity / repCount);
            result.metrics.put("eccentricRom", totalEccentricRom / repCount);
            result.metrics.put("tempoRatio", totalTempoRatio / repCount);
            result.metrics.put("timeToPeakVelocity", totalTimeToPeak / repCount);
            result.metrics.put("rfdPerMass", totalRfdPerMass / repCount);
            result.metrics.put("decelerationRate", totalDecelerationRate / repCount);
            result.metrics.put("relativeTimeToPeak", totalRelativeTimeToPeak / repCount);
            result.metrics.put("eccentricConcentricRatio",
                    totalEccentricConcentricRatio / repCount);
            result.metrics.put("velocityLoss", firstPeak < 1.0e-9 ? 0.0 : 1.0 - last / firstPeak);
            result.metrics.put("peakTimestampSeconds",
                    values.get(peaks.get(0)).elapsedSeconds);
        }
    }

    private static double peakAcceleration(List<DerivedSample> values, int start, int end) {
        double peak = 0.0;
        for (int index = start; index <= end && index < values.size(); index++) {
            peak = Math.max(peak, values.get(index).linearMagnitude);
        }
        return peak;
    }
    private static void populateOneRm(AnalysisResult result, double loadKg) {
        if (loadKg <= 0.0 || result.repetitionCount <= 0) {
            result.metrics.put("loadKg", Math.max(0.0, loadKg));
            result.status = "需要輸入負荷後計算 1RM";
            return;
        }
        int reps = result.repetitionCount;
        result.metrics.put("loadKg", loadKg);
        result.metrics.put("reps", (double) reps);
        result.metrics.put("epley1RmKg", loadKg * (1.0 + reps / 30.0));
        result.metrics.put("brzycki1RmKg", reps < 37 ? loadKg * 36.0 / (37.0 - reps) : 0.0);
        result.metrics.put("lander1RmKg", loadKg * 100.0
                / Math.max(1.0, 101.3 - 2.67123 * reps));
        result.metrics.put("mayhew1RmKg", loadKg * 100.0
                / (52.2 + 41.9 * Math.exp(-0.055 * reps)));
        result.metrics.put("oconner1RmKg", loadKg * (1.0 + reps / 40.0));
        double peakVelocity = value(result.metrics, "vbtPeakVelocityMps");
        result.metrics.put("lvpSlopeKgPerMps", peakVelocity > 1.0e-6 ? -loadKg / peakVelocity : 0.0);
        double lvpZeroVelocity = loadKg * (1.0 + peakVelocity / 0.3);
        result.metrics.put("lvpZeroVelocityLoadKg", lvpZeroVelocity);
        result.metrics.put("lvpVelocityBased1RmKg", lvpZeroVelocity);
        result.metrics.put("lvpInputCount", 1.0);

        // The official app exposes several conventional repetition-to-1RM
        // estimators alongside its velocity profile. Keep every estimator
        // visible so a session can be audited instead of silently choosing one.
        double lombardi = loadKg * Math.pow(Math.max(1.0, reps), 0.10);
        double wathan = loadKg * 100.0
                / (48.8 + 53.8 * Math.exp(-0.075 * reps));
        result.metrics.put("lombardi1RmKg", lombardi);
        result.metrics.put("wathan1RmKg", wathan);
        double sum = 0.0;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        String[] formulaKeys = new String[]{
                "epley1RmKg", "brzycki1RmKg", "lander1RmKg",
                "mayhew1RmKg", "oconner1RmKg", "lombardi1RmKg", "wathan1RmKg"};
        for (String key : formulaKeys) {
            double estimate = value(result.metrics, key);
            if (estimate <= 0.0) continue;
            sum += estimate;
            minimum = Math.min(minimum, estimate);
            maximum = Math.max(maximum, estimate);
        }
        int estimateCount = 0;
        for (String key : formulaKeys) {
            if (value(result.metrics, key) > 0.0) estimateCount++;
        }
        result.metrics.put("average1RmKg", estimateCount == 0 ? 0.0 : sum / estimateCount);
        result.metrics.put("minimum1RmKg",
                minimum == Double.POSITIVE_INFINITY ? 0.0 : minimum);
        result.metrics.put("maximum1RmKg",
                maximum == Double.NEGATIVE_INFINITY ? 0.0 : maximum);
        result.metrics.put("formulaSpreadKg",
                minimum == Double.POSITIVE_INFINITY ? 0.0 : maximum - minimum);
        result.metrics.put("lvpMeanVelocityMps", value(result.metrics, "meanVelocityMps"));
        result.metrics.put("lvpQualityInputCount", 1.0);
    }

    private static void detectJump(AnalysisResult result, List<DerivedSample> values,
                                   boolean counterMovement) {
        boolean airborne = false;
        int takeoff = -1;
        int counterStart = -1;
        int counterBottom = -1;
        double previousVz = values.get(0).velocityZ;
        int previousLanding = -1;
        double totalHeight = 0.0;
        double bestHeight = 0.0;
        double totalFlight = 0.0;
        double lastFlight = 0.0;
        double totalTakeoffVelocity = 0.0;
        double totalLandingVelocity = 0.0;
        double bestFlightVelocity = 0.0;
        double totalCountermovementDepth = 0.0;
        int countermovementCount = 0;
        double totalConcentricDuration = 0.0;
        double totalContact = 0.0;
        int contactCount = 0;

        for (int index = 1; index < values.size(); index++) {
            DerivedSample sample = values.get(index);
            double vz = sample.velocityZ;
            if (!airborne && vz < -0.20) {
                if (counterStart < 0) counterStart = index;
                if (counterBottom < 0
                        || vz < values.get(counterBottom).velocityZ) {
                    counterBottom = index;
                }
            }
            if (!airborne && vz > 0.55 && previousVz <= 0.55) {
                takeoff = index;
                airborne = true;
                int concentricStart = counterBottom >= 0
                        ? counterBottom : Math.max(0, index - 1);
                double depth = counterStart >= 0
                        ? integrateNegativeVelocity(values, counterStart,
                        Math.max(counterStart, concentricStart)) : 0.0;
                if (counterMovement && counterStart >= 0) {
                    addEvent(result, "countermovement", "下沉／離心",
                            values, counterStart, Math.max(counterStart, concentricStart), depth);
                    countermovementCount++;
                    totalCountermovementDepth += depth;
                }
                addEvent(result, "concentric", "向心推蹬",
                        values, concentricStart, index, vz);
                addEvent(result, "takeoff", "起跳", values, index, index, vz);
                totalConcentricDuration += Math.max(0.0,
                        values.get(index).elapsedSeconds
                                - values.get(concentricStart).elapsedSeconds);
                if (previousLanding >= 0) {
                    double contact = values.get(index).elapsedSeconds
                            - values.get(previousLanding).elapsedSeconds;
                    if (contact > 0.0) {
                        totalContact += contact;
                        contactCount++;
                        result.metrics.put("contactTimeSeconds", contact);
                    }
                }
                continue;
            }
            if (airborne && index - takeoff > 8 && vz < -0.25
                    && previousVz >= -0.25) {
                int landing = index;
                double flight = Math.max(0.0,
                        sample.elapsedSeconds - values.get(takeoff).elapsedSeconds);
                double height = GRAVITY * flight * flight / 8.0;
                double peakFlightVelocity = 0.0;
                for (int cursor = takeoff; cursor <= landing; cursor++) {
                    peakFlightVelocity = Math.max(peakFlightVelocity,
                            Math.abs(values.get(cursor).velocityZ));
                }
                addEvent(result, "flight", "飛行", values, takeoff, landing, flight);
                addEvent(result, "landing", "落地／吸收",
                        values, landing, landing, Math.abs(vz));
                result.metrics.put("jumpHeightM", Math.max(
                        value(result.metrics, "jumpHeightM"), height));
                result.metrics.put("flightTimeSeconds", flight);
                totalHeight += height;
                bestHeight = Math.max(bestHeight, height);
                totalFlight += flight;
                lastFlight = flight;
                totalTakeoffVelocity += values.get(takeoff).velocityZ;
                totalLandingVelocity += Math.abs(vz);
                bestFlightVelocity = Math.max(bestFlightVelocity, peakFlightVelocity);
                previousLanding = landing;
                result.repetitionCount++;
                airborne = false;
                takeoff = -1;
                counterStart = -1;
                counterBottom = -1;
            }
            previousVz = vz;
        }
        if (result.repetitionCount > 0) {
            double contact = value(result.metrics, "contactTimeSeconds");
            result.metrics.put("jumpCount", (double) result.repetitionCount);
            result.metrics.put("bestJumpHeightM", bestHeight);
            result.metrics.put("meanJumpHeightM", totalHeight / result.repetitionCount);
            result.metrics.put("meanFlightTimeSeconds", totalFlight / result.repetitionCount);
            result.metrics.put("jumpDurationSeconds", totalFlight / result.repetitionCount);
            result.metrics.put("takeoffVelocityMps",
                    totalTakeoffVelocity / result.repetitionCount);
            result.metrics.put("landingVelocityMps",
                    totalLandingVelocity / result.repetitionCount);
            result.metrics.put("peakFlightVelocityMps", bestFlightVelocity);
            result.metrics.put("countermovementDepthM", countermovementCount > 0
                    ? totalCountermovementDepth / countermovementCount : 0.0);
            result.metrics.put("concentricDurationSeconds",
                    totalConcentricDuration / result.repetitionCount);
            if (contactCount > 0) {
                result.metrics.put("meanContactTimeSeconds", totalContact / contactCount);
            }
            result.metrics.put("reactiveStrengthIndex",
                    contact > 1.0e-6 ? bestHeight / contact : 0.0);
        }
        computeComSway(result, values);
    }

    private static double integrateNegativeVelocity(List<DerivedSample> values,
                                                    int start, int end) {
        double distance = 0.0;
        int safeStart = Math.max(0, Math.min(values.size() - 1, start));
        int safeEnd = Math.max(safeStart, Math.min(values.size() - 1, end));
        for (int index = safeStart; index <= safeEnd; index++) {
            double dt = index == safeStart ? 1.0 / 120.0
                    : Math.max(1.0 / 240.0,
                    values.get(index).elapsedSeconds
                            - values.get(index - 1).elapsedSeconds);
            distance += Math.max(0.0, -values.get(index).velocityZ) * dt;
        }
        return distance;
    }

    private static void computeComSway(AnalysisResult result, List<DerivedSample> values) {
        double x = 0.0;
        double y = 0.0;
        double meanX = 0.0;
        double meanY = 0.0;
        ArrayList<double[]> points = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            DerivedSample sample = values.get(index);
            double dt = index == 0 ? 1.0 / 120.0
                    : Math.max(1.0 / 240.0,
                    sample.elapsedSeconds - values.get(index - 1).elapsedSeconds);
            x += sample.velocityX * dt;
            y += sample.velocityY * dt;
            points.add(new double[]{x, y});
            meanX += x;
            meanY += y;
        }
        if (points.isEmpty()) return;
        meanX /= points.size();
        meanY /= points.size();
        double sum = 0.0;
        for (double[] point : points) {
            double dx = point[0] - meanX;
            double dy = point[1] - meanY;
            sum += dx * dx + dy * dy;
        }
        result.metrics.put("comSwayRmsM", Math.sqrt(sum / points.size()));
    }

    private static void addEvent(AnalysisResult result, String kind, String label,
                                 List<DerivedSample> values, int start, int end, double value) {
        int safeStart = Math.max(0, Math.min(values.size() - 1, start));
        int safeEnd = Math.max(safeStart, Math.min(values.size() - 1, end));
        result.events.add(new Event(kind, label, safeStart, safeEnd,
                values.get(safeStart).elapsedSeconds,
                values.get(safeEnd).elapsedSeconds, value));
    }

    private static double value(Map<String, Double> values, String key) {
        Double result = values.get(key);
        return result == null ? 0.0 : result;
    }

    private static String metricLabel(String key) {
        if ("durationSeconds".equals(key)) return "時間（秒）";
        if ("peakLinearAccelerationMps2".equals(key)) return "線性加速度峰值（m/s²）";
        if ("peakLinearSpeedMps".equals(key)) return "線性速度峰值（m/s）";
        if ("peakAngularVelocityRadps".equals(key)) return "角速度峰值（rad/s）";
        if ("peakAngularAccelerationRadps2".equals(key)) return "角加速度峰值（rad/s²）";
        if ("linearAccelerationRmsMps2".equals(key)) return "線性加速度 RMS（m/s²）";
        if ("angularVelocityRmsRadps".equals(key)) return "角速度 RMS（rad/s）";
        if ("sampleCount".equals(key)) return "樣本數";
        if ("repetitionCount".equals(key)) return "判定次數";
        if ("throwPeakSpeedMps".equals(key)) return "投擲峰值速度（m/s）";
        if ("throwPeakAccelerationMps2".equals(key)) return "投擲峰值加速度（m/s²）";
        if ("throwHighThresholdMps".equals(key)) return "投擲啟動門檻（m/s）";
        if ("throwLowThresholdMps".equals(key)) return "投擲結束門檻（m/s）";
        if ("rotationDominantAxis".equals(key)) return "角運動主軸（0=X／1=Y／2=Z）";
        if ("rotationDirectionChanges".equals(key)) return "角運動方向變化次數";
        if ("rotationPeakAngularVelocityRadps".equals(key)) return "角運動峰值角速度（rad/s）";
        if ("rotationPeakAngularAccelerationRadps2".equals(key)) {
            return "角運動峰值角加速度（rad/s²）";
        }
        if ("vbtPeakVelocityMps".equals(key)) return "VBT 峰值速度（m/s）";
        if ("vbtLastPeakVelocityMps".equals(key)) return "VBT 最後峰值速度（m/s）";
        if ("rangeOfMotionM".equals(key) || "rangeOfMotion".equals(key)) {
            return "動作幅度（m）";
        }
        if ("meanVelocityMps".equals(key)) return "平均速度（m/s）";
        if ("meanPowerPerMassWPerKg".equals(key)
                || "meanPowerPerMass".equals(key)) return "平均相對功率（W/kg）";
        if ("maxPowerPerMass".equals(key)) return "最大相對功率（W/kg）";
        if ("eccentricDuration".equals(key)) return "離心時間（秒）";
        if ("eccentricMeanVelocity".equals(key)) return "離心平均速度（m/s）";
        if ("eccentricMaxVelocity".equals(key)) return "離心最大速度（m/s）";
        if ("eccentricRom".equals(key)) return "離心動作幅度（m）";
        if ("tempoRatio".equals(key)) return "節奏比（離心／向心）";
        if ("timeToPeakVelocity".equals(key)) return "達峰時間（秒）";
        if ("rfdPerMass".equals(key)) return "相對發力率";
        if ("decelerationRate".equals(key)) return "減速率（m/s²）";
        if ("relativeTimeToPeak".equals(key)) return "相對達峰時間";
        if ("eccentricConcentricRatio".equals(key)) return "離心／向心時間比";
        if ("peakTimestampSeconds".equals(key)) return "峰值時間（秒）";
        if ("loadKg".equals(key)) return "負荷（kg）";
        if ("reps".equals(key)) return "次數";
        if ("epley1RmKg".equals(key)) return "Epley 估計 1RM（kg）";
        if ("brzycki1RmKg".equals(key)) return "Brzycki 估計 1RM（kg）";
        if ("lander1RmKg".equals(key)) return "Lander 估計 1RM（kg）";
        if ("mayhew1RmKg".equals(key)) return "Mayhew 估計 1RM（kg）";
        if ("oconner1RmKg".equals(key)) return "O'Conner 估計 1RM（kg）";
        if ("lombardi1RmKg".equals(key)) return "Lombardi 估計 1RM（kg）";
        if ("wathan1RmKg".equals(key)) return "Wathan 估計 1RM（kg）";
        if ("average1RmKg".equals(key)) return "公式平均 1RM（kg）";
        if ("minimum1RmKg".equals(key)) return "公式最低 1RM（kg）";
        if ("maximum1RmKg".equals(key)) return "公式最高 1RM（kg）";
        if ("formulaSpreadKg".equals(key)) return "公式差距（kg）";
        if ("lvpSlopeKgPerMps".equals(key)) return "LVP 斜率（kg／m/s）";
        if ("lvpZeroVelocityLoadKg".equals(key)
                || "lvpVelocityBased1RmKg".equals(key)) return "LVP 零速度 1RM（kg）";
        if ("lvpMeanVelocityMps".equals(key)) return "LVP 平均速度輸入（m/s）";
        if ("lvpInputCount".equals(key)
                || "lvpQualityInputCount".equals(key)) return "LVP 輸入筆數";
        if ("jumpCount".equals(key)) return "跳躍次數";
        if ("jumpHeightM".equals(key) || "bestJumpHeightM".equals(key)) return "跳高（m）";
        if ("meanJumpHeightM".equals(key)) return "平均跳高（m）";
        if ("flightTimeSeconds".equals(key)
                || "meanFlightTimeSeconds".equals(key)) return "飛行時間（秒）";
        if ("jumpDurationSeconds".equals(key)) return "跳躍週期時間（秒）";
        if ("takeoffVelocityMps".equals(key)) return "起跳速度（m/s）";
        if ("landingVelocityMps".equals(key)) return "落地速度（m/s）";
        if ("peakFlightVelocityMps".equals(key)) return "飛行峰值速度（m/s）";
        if ("countermovementDepthM".equals(key)) return "反向下沉深度（m）";
        if ("concentricDurationSeconds".equals(key)) return "向心推蹬時間（秒）";
        if ("meanContactTimeSeconds".equals(key)
                || "contactTimeSeconds".equals(key)) return "接觸時間（秒）";
        if ("reactiveStrengthIndex".equals(key)) return "反應力量指數（RSI）";
        if ("comSwayRmsM".equals(key)) return "重心擺動 RMS（m）";
        if ("velocityLoss".equals(key)) return "速度損失";
        return key;
    }
    private static double magnitude(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
