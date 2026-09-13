import com.treepolo.pointgo.clone.VendorMotionEngine;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, offline regression vectors for the vendor-compatible engine.
 * This is deliberately outside src so it is never packaged into the APK.
 */
public final class VendorMotionEngineTest {
    private static final double G = VendorMotionEngine.GRAVITY;

    public static void main(String[] args) throws Exception {
        testSixFaceCalibration();
        testStillGyroCalibration();
        testDerivedPipeline();
        testThrowAndRotationEvents();
        testVbtAndOneRm();
        testJumpAndCmjEvents();
        System.out.println("VENDOR_ENGINE_TEST_OK");
    }

    private static void testSixFaceCalibration() {
        double bx = 0.15;
        double by = -0.09;
        double bz = 0.04;
        double gx = 0.02;
        double gy = -0.03;
        double gz = 0.01;
        VendorMotionEngine.CalibrationSession session =
                new VendorMotionEngine.CalibrationSession();
        for (VendorMotionEngine.Face face : VendorMotionEngine.Face.values()) {
            ArrayList<VendorMotionEngine.RawSample> faceValues = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                double ax = bx;
                double ay = by;
                double az = bz;
                if (face == VendorMotionEngine.Face.LEFT) ax += G;
                if (face == VendorMotionEngine.Face.RIGHT) ax -= G;
                if (face == VendorMotionEngine.Face.FRONT) ay += G;
                if (face == VendorMotionEngine.Face.BACK) ay -= G;
                if (face == VendorMotionEngine.Face.TOP) az += G;
                if (face == VendorMotionEngine.Face.BOTTOM) az -= G;
                faceValues.add(new VendorMotionEngine.RawSample(index * 8L,
                        ax, ay, az, gx, gy, gz));
            }
            session.add(face, faceValues);
        }
        assertTrue(session.isComplete(), "six-face complete");
        VendorMotionEngine.Profile profile = session.finish(
                VendorMotionEngine.Profile.defaultProfile());
        assertTrue(profile.isSixFaceComplete(), "six-face profile");
        assertTrue(profile.isGyroComplete(), "six-face gyro");
        VendorMotionEngine.DerivedSample calibrated = new VendorMotionEngine(profile).process(
                new VendorMotionEngine.RawSample(0L, G + bx, by, bz, gx, gy, gz));
        assertNear(G, calibrated.calibratedAx, 0.0001, "calibrated +X");
        assertNear(0.0, calibrated.gy, 0.0001, "calibrated gyro Y");
    }

    private static void testStillGyroCalibration() {
        ArrayList<VendorMotionEngine.RawSample> values = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            values.add(new VendorMotionEngine.RawSample(index * 8L, 0.0, 0.0, G,
                    0.11, -0.07, 0.03));
        }
        VendorMotionEngine.Profile profile = VendorMotionEngine.calibrateStill(
                VendorMotionEngine.Profile.defaultProfile(), values);
        assertTrue(profile.isGyroComplete(), "still gyro complete");
        VendorMotionEngine.DerivedSample sample = new VendorMotionEngine(profile).process(values.get(0));
        assertNear(0.0, sample.gx, 0.0001, "still gyro X");
        assertNear(0.0, sample.gy, 0.0001, "still gyro Y");
        assertNear(0.0, sample.gz, 0.0001, "still gyro Z");
    }

    private static void testDerivedPipeline() {
        VendorMotionEngine engine = new VendorMotionEngine(
                VendorMotionEngine.Profile.defaultProfile());
        ArrayList<VendorMotionEngine.DerivedSample> values = new ArrayList<>();
        for (int index = 0; index < 600; index++) {
            double t = index / 120.0;
            values.add(engine.process(new VendorMotionEngine.RawSample(
                    index * 8L, 2.5 * Math.sin(t * 8.0), 0.0, G,
                    0.0, 1.2 * Math.sin(t * 6.0), 0.0)));
        }
        VendorMotionEngine.AnalysisResult result = VendorMotionEngine.analyze(
                values, VendorMotionEngine.Module.GENERAL, 75.0, 20.0);
        assertNear(600.0, result.metrics.get("sampleCount"), 0.0, "derived sample count");
        assertTrue(result.metrics.get("peakLinearAccelerationMps2") > 0.0,
                "derived linear acceleration");
        assertTrue(result.metrics.get("peakAngularAccelerationRadps2") > 0.0,
                "derived angular acceleration");
        assertTrue(result.toJson().contains("algorithmVersion"), "derived JSON version");
    }

    private static void testThrowAndRotationEvents() throws Exception {
        ArrayList<VendorMotionEngine.DerivedSample> throwValues = new ArrayList<>();
        for (int cycle = 0; cycle < 3; cycle++) {
            appendSignal(throwValues, cycle * 30, false, true);
            appendSignal(throwValues, cycle * 30 + 15, false, false);
        }
        VendorMotionEngine.AnalysisResult throwResult = VendorMotionEngine.analyze(
                throwValues, VendorMotionEngine.Module.THROW, 75.0, 20.0);
        assertTrue(throwResult.repetitionCount == 3, "throw repetition count");
        assertTrue(throwResult.events.get(0).kind.equals("throw"), "throw event kind");

        ArrayList<VendorMotionEngine.DerivedSample> rotationValues = new ArrayList<>();
        for (int cycle = 0; cycle < 3; cycle++) {
            appendSignal(rotationValues, cycle * 30, true, true);
            appendSignal(rotationValues, cycle * 30 + 15, true, false);
        }
        VendorMotionEngine.AnalysisResult rotationResult = VendorMotionEngine.analyze(
                rotationValues, VendorMotionEngine.Module.ROTATION, 75.0, 20.0);
        assertTrue(rotationResult.repetitionCount == 3, "rotation repetition count");
        assertTrue(rotationResult.events.get(0).kind.equals("rotation"), "rotation event kind");
    }

    private static void appendSignal(List<VendorMotionEngine.DerivedSample> target,
                                     int offset, boolean angular, boolean active)
            throws Exception {
        for (int index = 0; index < 15; index++) {
            double speed = !angular && active ? 1.2 : 0.0;
            double angularSpeed = angular && active ? 1.0 : 0.0;
            target.add(sample(offset + index, speed, angularSpeed, 0.0));
        }
    }

    private static void testVbtAndOneRm() throws Exception {
        ArrayList<VendorMotionEngine.DerivedSample> values = new ArrayList<>();
        int index = 0;
        for (int rep = 0; rep < 3; rep++) {
            for (int step = 0; step < 15; step++) {
                double speed = step <= 7 ? step * 0.14 : (14 - step) * 0.14;
                values.add(sample(index++, speed, 0.0, 3.0));
            }
            for (int step = 0; step < 12; step++) values.add(sample(index++, 0.0, 0.0, 0.0));
        }
        VendorMotionEngine.AnalysisResult vbt = VendorMotionEngine.analyze(
                values, VendorMotionEngine.Module.VBT, 75.0, 20.0);
        assertTrue(vbt.repetitionCount == 3, "VBT repetition count");
        assertTrue(vbt.metrics.containsKey("vbtPeakVelocityMps"), "VBT peak metric");
        assertTrue(vbt.metrics.containsKey("velocityLoss"), "VBT velocity loss");

        VendorMotionEngine.AnalysisResult oneRm = VendorMotionEngine.analyze(
                values, VendorMotionEngine.Module.ONE_RM, 75.0, 20.0);
        assertTrue(oneRm.repetitionCount == 3, "1RM repetition count");
        assertTrue(oneRm.metrics.containsKey("epley1RmKg"), "1RM Epley");
        assertTrue(oneRm.metrics.containsKey("brzycki1RmKg"), "1RM Brzycki");
        assertTrue(oneRm.metrics.containsKey("lander1RmKg"), "1RM Lander");
        assertTrue(oneRm.metrics.containsKey("mayhew1RmKg"), "1RM Mayhew");
        assertTrue(oneRm.metrics.containsKey("oconner1RmKg"), "1RM O'Conner");
        assertTrue(oneRm.metrics.containsKey("lvpSlopeKgPerMps"), "1RM LVP slope");
    }

    private static void testJumpAndCmjEvents() throws Exception {
        ArrayList<VendorMotionEngine.DerivedSample> values = new ArrayList<>();
        int index = 0;
        for (int jump = 0; jump < 2; jump++) {
            for (int step = 0; step < 8; step++) values.add(sample(index++, 0.0, 0.0, 0.0));
            for (int step = 0; step < 4; step++) values.add(sample(index++, 0.0, 0.0, -0.4));
            values.add(sample(index++, 0.0, 0.0, 0.8));
            for (int step = 0; step < 10; step++) values.add(sample(index++, 0.0, 0.0, 0.8));
            values.add(sample(index++, 0.0, 0.0, -0.4));
            for (int step = 0; step < 10; step++) values.add(sample(index++, 0.0, 0.0, 0.0));
        }
        VendorMotionEngine.AnalysisResult jump = VendorMotionEngine.analyze(
                values, VendorMotionEngine.Module.JUMP, 75.0, 20.0);
        assertTrue(jump.repetitionCount == 2, "jump repetition count");
        assertTrue(jump.metrics.containsKey("jumpHeightM"), "jump height");
        VendorMotionEngine.AnalysisResult cmj = VendorMotionEngine.analyze(
                values, VendorMotionEngine.Module.CMJ, 75.0, 20.0);
        assertTrue(cmj.repetitionCount == 2, "CMJ repetition count");
        assertTrue(hasEvent(cmj, "countermovement"), "CMJ countermovement event");
        assertTrue(cmj.metrics.containsKey("reactiveStrengthIndex"), "CMJ RSI");
    }

    private static boolean hasEvent(VendorMotionEngine.AnalysisResult result, String kind) {
        for (VendorMotionEngine.Event event : result.events) if (kind.equals(event.kind)) return true;
        return false;
    }

    private static VendorMotionEngine.DerivedSample sample(int index, double speed,
                                                            double angularSpeed,
                                                            double velocityZ) throws Exception {
        Constructor<?> constructor = VendorMotionEngine.DerivedSample.class
                .getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        VendorMotionEngine.RawSample raw = new VendorMotionEngine.RawSample(
                index * 10L, speed, 0.0, G, 0.0, angularSpeed, 0.0);
        Object[] args = new Object[]{
                index * 10L, index / 100.0, raw,
                speed, 0.0, G,
                0.0, 0.0, G,
                speed, 0.0, 0.0,
                speed, 0.0, 0.0,
                speed, 0.0, velocityZ,
                speed, speed,
                0.0, angularSpeed, 0.0,
                0.0, 0.0, 0.0,
                angularSpeed, angularSpeed,
                1.0, 0.0, 0.0, 0.0};
        return (VendorMotionEngine.DerivedSample) constructor.newInstance(args);
    }

    private static void assertNear(double expected, double actual, double tolerance, String name) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
