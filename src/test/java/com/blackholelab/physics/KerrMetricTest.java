package com.blackholelab.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KerrMetricTest {
    @Test
    void horizonMatchesKerrFormulaAndSchwarzschildLimit() {
        assertEquals(2.0, new KerrMetric(0.0).outerHorizon(), 1e-14);
        assertEquals(1.3122499, new KerrMetric(0.95).outerHorizon(), 1e-6);
    }

    @Test
    void iscoSeparatesProgradeAndRetrogradeOrbits() {
        KerrMetric schwarzschild = new KerrMetric(0.0);
        assertEquals(6.0, schwarzschild.iscoRadius(true), 1e-12);
        KerrMetric rapid = new KerrMetric(0.95);
        assertEquals(1.93724, rapid.iscoRadius(true), 1e-5);
        assertEquals(8.85903, rapid.iscoRadius(false), 1e-5);
        assertTrue(rapid.iscoRadius(true) < 2.0);
        assertTrue(rapid.iscoRadius(false) > 8.0);
        KerrMetric retrogradeSpin = new KerrMetric(-0.95);
        assertEquals(rapid.iscoRadius(true), retrogradeSpin.iscoRadius(true), 1e-12);
        assertEquals(rapid.iscoRadius(false), retrogradeSpin.iscoRadius(false), 1e-12);
    }

    @Test
    void frameDraggingFollowsSpinAndDecreasesWithDistance() {
        KerrMetric positive = new KerrMetric(0.7);
        KerrMetric negative = new KerrMetric(-0.7);
        double near = positive.frameDraggingAngularVelocity(4.0, Math.PI / 2.0);
        assertTrue(near > 0.0);
        assertTrue(negative.frameDraggingAngularVelocity(4.0, Math.PI / 2.0) < 0.0);
        assertTrue(near > positive.frameDraggingAngularVelocity(10.0, Math.PI / 2.0));
    }

    @Test
    void rejectsExtremalAndInvalidSpin() {
        assertThrows(IllegalArgumentException.class, () -> new KerrMetric(1.0));
        assertThrows(IllegalArgumentException.class, () -> new KerrMetric(Double.NaN));
    }

    @Test
    void nullRadialPotentialHasFlatSpaceLimitAndRejectsInvalidConstants() {
        KerrMetric metric = new KerrMetric(0.95);
        double radius = 20.0;
        double energy = 1.0;
        double angularMomentum = 3.0;
        double carter = 4.0;
        double a = metric.spin();
        double expected = Math.pow(energy * (radius * radius + a * a) - a * angularMomentum, 2)
                - metric.delta(radius) * (Math.pow(angularMomentum - a * energy, 2) + carter);
        assertEquals(expected,
                metric.nullRadialPotential(radius, energy, angularMomentum, carter), 1e-10);
        assertTrue(Double.isFinite(metric.nullRadialPotential(radius, energy, angularMomentum, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> metric.nullRadialPotential(radius, Double.NaN, angularMomentum, 0.0));
    }

    @Test
    void cameraPhotonSatisfiesKerrNullConstraint() {
        KerrMetric metric = new KerrMetric(0.95);
        double radius = 30.0;
        double theta = 1.15;
        double radial = -0.8;
        double polar = 0.36;
        double azimuthal = Math.sqrt(1.0 - radial * radial - polar * polar);
        KerrPhoton photon = KerrPhoton.fromCamera(
                metric, radius, theta, radial, polar, azimuthal);

        assertEquals(0.0, photon.nullHamiltonianResidual(metric, radius, theta), 1e-12);
        assertTrue(Double.isFinite(photon.carterConstant(metric, theta)));
        assertTrue(Double.isFinite(metric.nullRadialPotential(radius, photon.covariantTime(),
                -photon.covariantAzimuth(), photon.carterConstant(metric, theta))));
    }

    @Test
    void rejectsNonUnitCameraPhotonDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> KerrPhoton.fromCamera(new KerrMetric(0.95), 30.0, 1.15, -1.0, 1.0, 0.0));
    }

    @Test
    void circularOrbitSatisfiesTimelikeNormalizationAtItsInitialPoint() {
        KerrMetric metric = new KerrMetric(0.7);
        EquatorialTimelikeGeodesic geodesic =
                EquatorialTimelikeGeodesic.circular(metric, metric.iscoRadius(true) + 1.0, true);
        assertEquals(0.0, geodesic.normalizationResidual(), 1e-10);
    }

    @Test
    void circularOrbitRemainsBoundUnderConservativeIntegration() {
        KerrMetric metric = new KerrMetric(0.7);
        EquatorialTimelikeGeodesic geodesic =
                EquatorialTimelikeGeodesic.circular(metric, metric.iscoRadius(true) + 2.0, true);

        for (int i = 0; i < 10_000; i++) {
            geodesic.advance(0.001);
        }

        assertFalse(geodesic.isCaptured());
        assertEquals(metric.iscoRadius(true) + 2.0, geodesic.state().radius(), 1e-8);
        assertTrue(Math.abs(geodesic.normalizationResidual()) < 1e-8);
    }

    @Test
    void radialInfallRecordsCaptureAtTheKerrOuterHorizon() {
        KerrMetric metric = new KerrMetric(0.7);
        double radius = 10.0;
        double a = metric.spin();
        double delta = metric.delta(radius);
        double potential = Math.pow(radius * radius + a * a, 2)
                - delta * (radius * radius + a * a);
        double inwardProperVelocity = -Math.sqrt(potential) / (radius * radius);
        EquatorialTimelikeGeodesic geodesic =
                new EquatorialTimelikeGeodesic(metric, radius, inwardProperVelocity, 0.0, 1.0, 0.0);

        for (int i = 0; i < 100_000 && !geodesic.isCaptured(); i++) {
            geodesic.advance(0.01);
        }

        assertTrue(geodesic.isCaptured());
        assertEquals(metric.outerHorizon(), geodesic.state().radius(), 1e-12);
        assertTrue(geodesic.state().properTime() > 0.0);
    }
}
