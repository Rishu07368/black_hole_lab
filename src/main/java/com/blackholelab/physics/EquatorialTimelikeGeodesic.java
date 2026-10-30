package com.blackholelab.physics;

/**
 * Equatorial timelike Kerr geodesic integrator using the separated
 * Boyer-Lindquist radial potential and proper time, in units G = c = M = 1.
 * This conservative solver has no accretion drag or radiation reaction.
 */
public final class EquatorialTimelikeGeodesic {
    private final KerrMetric metric;
    private final double energy;
    private final double angularMomentum;
    private State state;
    private boolean captured;

    public EquatorialTimelikeGeodesic(
            KerrMetric metric, double radius, double radialVelocity, double phi,
            double specificEnergy, double specificAngularMomentum) {
        if (metric == null || !Double.isFinite(radius) || radius <= metric.outerHorizon()
                || !Double.isFinite(radialVelocity) || !Double.isFinite(phi)
                || !Double.isFinite(specificEnergy) || !Double.isFinite(specificAngularMomentum)) {
            throw new IllegalArgumentException("Invalid initial geodesic state");
        }
        this.metric = metric;
        this.energy = specificEnergy;
        this.angularMomentum = specificAngularMomentum;
        this.state = new State(radius, radialVelocity, phi, 0.0);
    }

    public static EquatorialTimelikeGeodesic circular(
            KerrMetric metric, double radius, boolean prograde) {
        KerrMetric.CircularOrbit orbit = metric.equatorialCircularOrbit(radius, prograde);
        return new EquatorialTimelikeGeodesic(metric, radius, 0.0, 0.0,
                orbit.specificEnergy(), orbit.specificAngularMomentum());
    }

    public State state() {
        return state;
    }

    public boolean isCaptured() {
        return captured;
    }

    public double normalizationResidual() {
        double r = state.radius();
        double radial = radialPotential(r);
        double measured = r * r * state.radialVelocity() * state.radialVelocity();
        return radial / (r * r) - measured;
    }

    public State advance(double properTimeStep) {
        if (!Double.isFinite(properTimeStep) || properTimeStep <= 0.0) {
            throw new IllegalArgumentException("Proper-time step must be finite and positive");
        }
        if (captured) {
            return state;
        }

        Derivative k1 = derivative(state);
        Derivative k2 = derivative(add(state, k1, properTimeStep * 0.5));
        Derivative k3 = derivative(add(state, k2, properTimeStep * 0.5));
        Derivative k4 = derivative(add(state, k3, properTimeStep));
        state = new State(
                state.radius() + properTimeStep / 6.0 * (k1.radius + 2 * k2.radius + 2 * k3.radius + k4.radius),
                state.radialVelocity() + properTimeStep / 6.0
                        * (k1.radialVelocity + 2 * k2.radialVelocity + 2 * k3.radialVelocity + k4.radialVelocity),
                state.phi() + properTimeStep / 6.0 * (k1.phi + 2 * k2.phi + 2 * k3.phi + k4.phi),
                state.properTime() + properTimeStep);

        if (state.radius() <= metric.outerHorizon()) {
            state = new State(metric.outerHorizon(), state.radialVelocity(), state.phi(), state.properTime());
            captured = true;
        } else if (!Double.isFinite(state.radius()) || !Double.isFinite(state.radialVelocity())
                || !Double.isFinite(state.phi())) {
            throw new ArithmeticException("Geodesic integration diverged");
        }
        return state;
    }

    private Derivative derivative(State point) {
        double r = Math.max(point.radius(), metric.outerHorizon() * (1.0 + 1e-10));
        double delta = metric.delta(r);
        double a = metric.spin();
        double p = energy * (r * r + a * a) - a * angularMomentum;
        double q = angularMomentum - a * energy;
        double potential = p * p - delta * (r * r + q * q);
        double derivativePotential = 4.0 * energy * r * p
                - (2.0 * r - 2.0) * (r * r + q * q) - 2.0 * r * delta;
        double radialAcceleration = derivativePotential / (2.0 * r * r * r * r)
                - 2.0 * potential / (r * r * r * r * r);
        double phiRate = (q + a * p / delta) / (r * r);
        return new Derivative(point.radialVelocity(), radialAcceleration, phiRate);
    }

    private double radialPotential(double radius) {
        double a = metric.spin();
        double p = energy * (radius * radius + a * a) - a * angularMomentum;
        double q = angularMomentum - a * energy;
        return p * p - metric.delta(radius) * (radius * radius + q * q);
    }

    private static State add(State state, Derivative derivative, double scale) {
        return new State(state.radius() + derivative.radius * scale,
                state.radialVelocity() + derivative.radialVelocity * scale,
                state.phi() + derivative.phi * scale,
                state.properTime() + scale);
    }

    private record Derivative(double radius, double radialVelocity, double phi) {}

    public record State(double radius, double radialVelocity, double phi, double properTime) {}
}
