# Particle motion and accretion

The CPU solver supports an ensemble of 100, 500, or 1,000 equatorial massive test particles. Each particle follows a conservative timelike Kerr geodesic in Boyer–Lindquist coordinates, parameterized by proper time. The population is deterministically initialized on prograde circular orbits outside the analytic ISCO and can be paused, time-scaled, reset, or changed in size. Its state is advanced on the Swing simulation timer. Captured geodesics are counted and excluded from the visible population.

Circular-orbit constants are calculated from the Kerr metric. Conservative circular motion does not spiral inward. The current view displays small GPU point markers at a direct camera projection of CPU coordinates. The markers are not lensed, depth-tested against the ray-traced shadow, or accompanied by trajectory trails. No dissipative drag, radiation reaction, particle injection, escape tracking, inspection, or history export is implemented. A future accretion experiment must expose its dissipation model separately from this conservative mode.

The solver is equatorial-only; it does not support inclined Kerr trajectories or integrate distant-observer coordinate time. The capture timestamp is proper time.
