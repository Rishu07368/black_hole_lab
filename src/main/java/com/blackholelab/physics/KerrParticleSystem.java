package com.blackholelab.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic ensemble of conservative, equatorial Kerr test particles.
 * The particles orbit; this model does not include accretion or dissipation.
 */
public final class KerrParticleSystem {
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double MIN_RADIAL_OFFSET = 1.5;
    private static final double RADIAL_SPAN = 8.0;

    private final KerrMetric metric;
    private final List<Particle> particles;
    private long capturedCount;

    public KerrParticleSystem(KerrMetric metric, int particleCount) {
        if (metric == null) {
            throw new IllegalArgumentException("Kerr metric is required");
        }
        if (particleCount != 100 && particleCount != 500 && particleCount != 1_000) {
            throw new IllegalArgumentException("Particle count must be 100, 500, or 1000");
        }
        this.metric = metric;
        this.particles = new ArrayList<>(particleCount);
        double innerRadius = metric.iscoRadius(true) + MIN_RADIAL_OFFSET;
        for (int i = 0; i < particleCount; i++) {
            double radius = innerRadius + RADIAL_SPAN * (i % 100) / 99.0;
            double phase = i * GOLDEN_ANGLE;
            KerrMetric.CircularOrbit orbit = metric.equatorialCircularOrbit(radius, true);
            EquatorialTimelikeGeodesic geodesic = new EquatorialTimelikeGeodesic(
                    metric, radius, 0.0, phase,
                    orbit.specificEnergy(), orbit.specificAngularMomentum());
            particles.add(new Particle(geodesic));
        }
    }

    public int particleCount() {
        return particles.size();
    }

    public long capturedCount() {
        return capturedCount;
    }

    public void advance(double properTimeStep) {
        if (!Double.isFinite(properTimeStep) || properTimeStep <= 0.0) {
            throw new IllegalArgumentException("Proper-time step must be finite and positive");
        }
        for (Particle particle : particles) {
            if (!particle.geodesic.isCaptured()) {
                particle.geodesic.advance(properTimeStep);
                if (particle.geodesic.isCaptured()) {
                    capturedCount++;
                }
            }
        }
    }

    public List<Position> positions() {
        List<Position> positions = new ArrayList<>(particles.size() - (int) capturedCount);
        for (Particle particle : particles) {
            if (!particle.geodesic.isCaptured()) {
                EquatorialTimelikeGeodesic.State state = particle.geodesic.state();
                positions.add(new Position(state.radius(), state.phi()));
            }
        }
        return List.copyOf(positions);
    }

    public record Position(double radius, double azimuth) {}

    private record Particle(EquatorialTimelikeGeodesic geodesic) {}
}
