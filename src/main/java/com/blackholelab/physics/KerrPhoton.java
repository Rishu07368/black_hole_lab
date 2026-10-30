package com.blackholelab.physics;

/**
 * A null ray initialized in a local zero-angular-momentum observer frame.
 * The constants and covariant momentum use G = c = M = 1.
 */
public record KerrPhoton(double covariantTime, double covariantRadius,
                         double covariantPolar, double covariantAzimuth) {
    public KerrPhoton {
        if (!Double.isFinite(covariantTime) || !Double.isFinite(covariantRadius)
                || !Double.isFinite(covariantPolar) || !Double.isFinite(covariantAzimuth)) {
            throw new IllegalArgumentException("Photon momentum must be finite");
        }
    }

    /**
     * Builds a past-directed camera ray from a unit local direction. The camera
     * tetrad is the orthonormal frame of a zero-angular-momentum observer.
     */
    public static KerrPhoton fromCamera(KerrMetric metric, double radius, double polarAngle,
                                        double radialDirection, double polarDirection,
                                        double azimuthalDirection) {
        if (metric == null || !Double.isFinite(radius) || radius <= metric.outerHorizon()
                || !Double.isFinite(polarAngle) || polarAngle <= 0.0 || polarAngle >= Math.PI
                || !Double.isFinite(radialDirection) || !Double.isFinite(polarDirection)
                || !Double.isFinite(azimuthalDirection)) {
            throw new IllegalArgumentException("Invalid camera ray coordinates or direction");
        }
        double directionNorm = Math.sqrt(radialDirection * radialDirection
                + polarDirection * polarDirection + azimuthalDirection * azimuthalDirection);
        if (Math.abs(directionNorm - 1.0) > 1e-10) {
            throw new IllegalArgumentException("Camera ray direction must have unit norm");
        }

        double spin = metric.spin();
        double sin = Math.sin(polarAngle);
        double sinSquared = sin * sin;
        double sigma = radius * radius + spin * spin * Math.cos(polarAngle) * Math.cos(polarAngle);
        double delta = metric.delta(radius);
        double aSquared = spin * spin;
        double A = (radius * radius + aSquared) * (radius * radius + aSquared)
                - aSquared * delta * sinSquared;
        double lapse = Math.sqrt(sigma * delta / A);
        double frameDragging = 2.0 * spin * radius / A;
        double gPhiPhi = A * sinSquared / sigma;

        double pTimeContravariant = -1.0 / lapse;
        double pRadiusContravariant = radialDirection * Math.sqrt(delta / sigma);
        double pPolarContravariant = polarDirection / Math.sqrt(sigma);
        double pAzimuthContravariant = frameDragging * pTimeContravariant
                + azimuthalDirection / Math.sqrt(gPhiPhi);
        double gTimeTime = -(1.0 - 2.0 * radius / sigma);
        double gTimePhi = -2.0 * spin * radius * sinSquared / sigma;

        return new KerrPhoton(
                gTimeTime * pTimeContravariant + gTimePhi * pAzimuthContravariant,
                sigma / delta * pRadiusContravariant,
                sigma * pPolarContravariant,
                gTimePhi * pTimeContravariant + gPhiPhi * pAzimuthContravariant);
    }

    public double nullHamiltonianResidual(KerrMetric metric, double radius, double polarAngle) {
        if (metric == null || !Double.isFinite(radius) || radius <= metric.outerHorizon()
                || !Double.isFinite(polarAngle) || polarAngle <= 0.0 || polarAngle >= Math.PI) {
            throw new IllegalArgumentException("Invalid coordinates for null constraint");
        }
        double spin = metric.spin();
        double sin = Math.sin(polarAngle);
        double sigma = radius * radius + spin * spin * Math.cos(polarAngle) * Math.cos(polarAngle);
        double delta = metric.delta(radius);
        double A = (radius * radius + spin * spin) * (radius * radius + spin * spin)
                - spin * spin * delta * sin * sin;
        double gTimeTimeInverse = -A / (sigma * delta);
        double gTimePhiInverse = -2.0 * spin * radius / (sigma * delta);
        double gPhiPhiInverse = (delta - spin * spin * sin * sin)
                / (sigma * delta * sin * sin);
        double gRadiusRadiusInverse = delta / sigma;
        double gPolarPolarInverse = 1.0 / sigma;
        return 0.5 * (gTimeTimeInverse * covariantTime * covariantTime
                + 2.0 * gTimePhiInverse * covariantTime * covariantAzimuth
                + gPhiPhiInverse * covariantAzimuth * covariantAzimuth
                + gRadiusRadiusInverse * covariantRadius * covariantRadius
                + gPolarPolarInverse * covariantPolar * covariantPolar);
    }

    public double carterConstant(KerrMetric metric, double polarAngle) {
        if (metric == null || !Double.isFinite(polarAngle) || polarAngle <= 0.0 || polarAngle >= Math.PI) {
            throw new IllegalArgumentException("Invalid angle for Carter constant");
        }
        double energy = covariantTime;
        double angularMomentum = -covariantAzimuth;
        double cosine = Math.cos(polarAngle);
        double sine = Math.sin(polarAngle);
        return covariantPolar * covariantPolar + cosine * cosine
                * (angularMomentum * angularMomentum / (sine * sine)
                - metric.spin() * metric.spin() * energy * energy);
    }
}
