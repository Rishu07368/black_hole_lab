package com.blackholelab.physics;

/**
 * Kerr geometry in Boyer-Lindquist coordinates with G = c = M = 1.
 * Spin is dimensionless and signed relative to the positive azimuthal direction.
 */
public final class KerrMetric {
    private final double spin;

    public KerrMetric(double dimensionlessSpin) {
        if (!Double.isFinite(dimensionlessSpin) || Math.abs(dimensionlessSpin) >= 1.0) {
            throw new IllegalArgumentException("Kerr spin must be finite and satisfy |a*| < 1");
        }
        this.spin = dimensionlessSpin;
    }

    public double spin() {
        return spin;
    }

    public double outerHorizon() {
        return 1.0 + Math.sqrt(1.0 - spin * spin);
    }

    public double innerHorizon() {
        return 1.0 - Math.sqrt(1.0 - spin * spin);
    }

    public double ergosphereRadius(double polarAngle) {
        if (!Double.isFinite(polarAngle) || polarAngle < 0.0 || polarAngle > Math.PI) {
            throw new IllegalArgumentException("Polar angle must be finite and in [0, pi]");
        }
        return 1.0 + Math.sqrt(1.0 - spin * spin * Math.cos(polarAngle) * Math.cos(polarAngle));
    }

    /** Boyer-Lindquist Delta = r^2 - 2 M r + a^2, with M = 1. */
    public double delta(double radius) {
        requirePositiveRadius(radius);
        return radius * radius - 2.0 * radius + spin * spin;
    }

    /**
     * Separated radial potential for a Kerr null geodesic:
     * R = [E(r^2 + a^2) - a L]^2 - Delta [Q + (L - a E)^2].
     * The affine-time radial equation is Sigma^2 (dr/dlambda)^2 = R.
     */
    public double nullRadialPotential(double radius, double energy, double angularMomentum, double carterConstant) {
        requirePositiveRadius(radius);
        if (!Double.isFinite(energy) || !Double.isFinite(angularMomentum)
                || !Double.isFinite(carterConstant)) {
            throw new IllegalArgumentException("Null-geodesic constants must be finite");
        }
        double radialMomentum = energy * (radius * radius + spin * spin) - spin * angularMomentum;
        double shiftedAngularMomentum = angularMomentum - spin * energy;
        return radialMomentum * radialMomentum
                - delta(radius) * (shiftedAngularMomentum * shiftedAngularMomentum + carterConstant);
    }

    /**
     * Coordinate angular velocity of a zero-angular-momentum observer.
     * This is the local frame-dragging angular velocity in these coordinates.
     */
    public double frameDraggingAngularVelocity(double radius, double polarAngle) {
        requireOutsideRing(radius);
        double sin = Math.sin(polarAngle);
        double sigma = radius * radius + spin * spin * Math.cos(polarAngle) * Math.cos(polarAngle);
        double aTerm = (radius * radius + spin * spin) * (radius * radius + spin * spin)
                - spin * spin * delta(radius) * sin * sin;
        if (!(aTerm > 0.0)) {
            throw new IllegalArgumentException("Frame-dragging expression is not defined at this coordinate");
        }
        return 2.0 * spin * radius / aTerm;
    }

    /** Marginally stable equatorial circular-orbit radius in units of M. */
    public double iscoRadius(boolean prograde) {
        double a = prograde ? Math.abs(spin) : -Math.abs(spin);
        double z1 = 1.0 + Math.cbrt(1.0 - a * a) * (Math.cbrt(1.0 + a) + Math.cbrt(1.0 - a));
        double z2 = Math.sqrt(3.0 * a * a + z1 * z1);
        return 3.0 + z2 - Math.signum(a == 0.0 ? 1.0 : a)
                * Math.sqrt((3.0 - z1) * (3.0 + z1 + 2.0 * z2));
    }

    public CircularOrbit equatorialCircularOrbit(double radius, boolean prograde) {
        requireOutsideRing(radius);
        double spinDirection = spin == 0.0 ? 1.0 : Math.signum(spin);
        double direction = (prograde ? 1.0 : -1.0) * spinDirection;
        double root = Math.sqrt(radius);
        double denominatorSquared = radius * root - 3.0 * root + 2.0 * direction * spin;
        if (!(denominatorSquared > 0.0)) {
            throw new IllegalArgumentException("No timelike circular orbit at this radius");
        }
        double denominator = Math.pow(radius, 0.75) * Math.sqrt(denominatorSquared);
        double energy = (radius * root - 2.0 * root + direction * spin) / denominator;
        double angularMomentum = direction
                * (radius * radius - 2.0 * direction * spin * root + spin * spin) / denominator;
        return new CircularOrbit(energy, angularMomentum);
    }

    private static void requirePositiveRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Radius must be finite and positive");
        }
    }

    private void requireOutsideRing(double radius) {
        requirePositiveRadius(radius);
        if (radius <= outerHorizon()) {
            throw new IllegalArgumentException("Radius must be outside the outer event horizon");
        }
    }

    public record CircularOrbit(double specificEnergy, double specificAngularMomentum) {}
}
