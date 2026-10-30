# Physics model

The implementation uses Boyer–Lindquist coordinates and geometrized units `G = c = M = 1`. The dimensionless signed spin is `a* = J/M²`; the dimensional Kerr parameter is `a = a*M`. Positive spin points along positive azimuth; prograde and retrograde refer to orbit orientation relative to the spin. The physical domain enforced by the code is `|a*| < 1`.

The outer and inner horizons are `r± = 1 ± sqrt(1 - a*²)`. The equatorial ergosurface is at `r = 2`; away from the equator its radius is `1 + sqrt(1 - a*² cos²(theta))`. The prograde/retrograde equatorial ISCO is computed with the analytic Bardeen–Press–Teukolsky expression. Circular-orbit specific energy and angular momentum use the corresponding Kerr expressions.

The frame-dragging diagnostic is the Boyer–Lindquist angular velocity of a zero-angular-momentum observer, `omega = 2 a r / ((r² + a²)² - a² Delta sin²(theta))`.

The GPU fragment renderer integrates backward Kerr null rays in Boyer–Lindquist coordinates and detects approximate equatorial disk crossings. `KerrPhoton` supplies a CPU local-camera-frame momentum initialization and null-constraint check. The GPU geodesics have not yet been compared with an independent reference solver or benchmarked for convergence.

The displayed disk uses a simplified optically thin radial emissivity and approximate frequency transfer; it is not complete relativistic radiative transfer. Horizon capture uses an exterior cutoff, not horizon-penetrating coordinates. The CPU massive-particle implementation remains a conservative equatorial geodesic, not an accretion model. SI scaling and a quantitatively validated Kerr shadow solution are not yet implemented.
