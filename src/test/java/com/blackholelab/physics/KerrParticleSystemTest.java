package com.blackholelab.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KerrParticleSystemTest {
    @Test
    void createsRequestedPopulationOnStableCircularOrbits() {
        KerrParticleSystem system = new KerrParticleSystem(new KerrMetric(0.95), 100);

        assertEquals(100, system.particleCount());
        assertEquals(100, system.positions().size());
        assertTrue(system.positions().stream().allMatch(position ->
                position.radius() > new KerrMetric(0.95).iscoRadius(true)));
        assertEquals(0, system.capturedCount());
    }

    @Test
    void advancesParticlesWithoutClaimingConservativeAccretion() {
        KerrParticleSystem system = new KerrParticleSystem(new KerrMetric(0.7), 500);

        system.advance(0.01);
        system.advance(0.01);

        assertEquals(500, system.positions().size());
        assertEquals(0, system.capturedCount());
        assertTrue(system.positions().stream().allMatch(position ->
                Double.isFinite(position.radius()) && Double.isFinite(position.azimuth())));
    }

    @Test
    void supportsMaximumPopulationAndRejectsUnsupportedInputs() {
        assertEquals(1_000, new KerrParticleSystem(new KerrMetric(0.0), 1_000).particleCount());
        assertThrows(IllegalArgumentException.class,
                () -> new KerrParticleSystem(new KerrMetric(0.5), 250));
        assertThrows(IllegalArgumentException.class,
                () -> new KerrParticleSystem(new KerrMetric(0.5), 100).advance(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new KerrParticleSystem(null, 100));
    }
}
