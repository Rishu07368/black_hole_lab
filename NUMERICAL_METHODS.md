# Numerical methods

All Kerr calculations use geometrized units `G = c = M = 1`, Boyer–Lindquist coordinates, and dimensionless spin `a* = J/M²`. The CPU analytic metric provides horizons, ISCOs, frame dragging, circular-orbit constants, and the separated null radial potential.

## GPU null rays

Each pixel builds a past-directed null momentum from a local zero-angular-momentum-observer tetrad. The initial local direction has unit Euclidean norm; the matching CPU `KerrPhoton` reference computes the covariant momentum and checks the null Hamiltonian constraint. In the fragment shader the state is `(r, theta, phi, p_r, p_theta)`, with `p_t` and `p_phi` constant. Hamilton's equations use the inverse Boyer–Lindquist metric and analytically evaluated derivatives. A bounded classical RK4 integrator advances each ray, with step length scaled by radius and limited by a user-selected step count.

The radial/ polar-separated Kerr null potential and a CPU camera-ray constraint are covered by unit tests. These are useful algebraic and initialization checks, not a CPU/GPU trajectory comparison. There is no adaptive error estimate or validated strong-field convergence bound.

The horizon is treated as captured when the ray reaches `r+ + 0.05 M`; Boyer–Lindquist integration is not continued through the horizon. Rays sample the procedural background sky after an outward crossing of `r = 20 M`, using that finite-radius direction as an asymptotic approximation. The default ray-step budget is 420, and integration also stops after six disk crossings to bound GPU work. Rays that stop without escaping or being captured, fail the constraint check, or encounter the polar cutoff before an apparent capture use the initial camera direction for the background rather than presenting a numerically uncertain path as a black-hole capture. Stars on those rays are not lens-mapped, and disk emission after the crossing limit is omitted. A finite polar-angle floor, momentum reflection with an azimuthal half-turn, a sub-pixel local azimuth-direction floor, and smaller steps near the polar axis regularize the coordinate singularity. The radial momentum is projected back onto the null Hamiltonian constraint after each integration step to limit constraint drift. These stabilizations and fixed-step RK4 can introduce errors, particularly for rays near the critical photon region; they are not a substitute for an independent convergence/reference comparison.

## Massive particles

`EquatorialTimelikeGeodesic` evolves the equatorial Kerr radial and azimuthal equations from the separated timelike radial potential, parameterized by particle proper time. It uses fixed-step classical RK4 and marks capture at the Kerr outer horizon. This is a conservative test-particle model; it includes no drag, radiation reaction, or accretion prescription. Distant-observer Boyer–Lindquist time and general-inclination massive geodesics are not integrated.
