# Rendering

The Swing application embeds a JOGL 2.6.0 `GLJPanel`. At startup it selects the best available programmable OpenGL profile and requires GL3 functionality. A fullscreen triangle invokes a GLSL 3.30 fragment shader; each fragment initializes a local camera ray and integrates that ray backward through Kerr spacetime. The shader reports compile/link failures and checks `glGetError()` after each frame.

## Current image formation

The ray state uses Boyer–Lindquist `(r, theta, phi)` and covariant momenta. Stationarity and axisymmetry conserve the time and azimuthal momenta. The shader evaluates the inverse Kerr metric and its derivatives, evolves Hamilton's equations with bounded classical RK4 steps, checks the equatorial disk plane for crossings, and terminates rays at an exterior horizon cutoff or a distant escape radius. Disk emission is accumulated at detected crossings; escaped rays sample a procedural sky in their asymptotic direction. Spin controls the metric and the analytic prograde ISCO, rather than rotating a Schwarzschild texture. The default budget is 420 integration steps, adjustable in the inspector; rays also stop after six disk crossings to bound GPU work.

The disk is a geometrically thin, optically thin equatorial model with a simple radial emissivity/color profile and approximate `g^4` frequency-transfer weighting. It is not a Novikov–Thorne solution or a full relativistic radiative-transfer calculation. The cinematic checkbox applies tone mapping/exposure presentation; it does not change the geodesic model.

## Controls and diagnostics

The default composition uses a near-edge-on view and a rolled camera to show the disk diagonally around the shadow. The inspector controls dimensionless spin, viewing inclination, camera distance, integration step budget, exposure, and tone mapping. Drag the viewport to change camera azimuth/elevation and use the wheel to zoom. The app reports actual `GL_VENDOR`, `GL_RENDERER`, OpenGL and GLSL versions, JOGL profile, hardware-rasterizer flag, and frame timing. Intel-active or software contexts are visually warned; adapter presence alone is not treated as proof of selection.

## Numerical and visual caveats

- Rays are integrated in Boyer–Lindquist coordinates, which are singular at the horizon and polar axis. The implementation uses a finite capture cutoff, radial null-constraint projection, polar reflection, and a sub-pixel azimuth-direction floor near the axis. These are practical numerical safeguards, not regular coordinates.
- Fixed RK4 steps are bounded and radius-dependent but are not error-controlled. Strong-field near-critical rays can be under-resolved or fail to converge.
- Disk intersections and the optical-depth/emission treatment are simplified. Brightness is illustrative, not calibrated observational flux.
- The procedural sky is not an astronomical catalog.
- Rays that exhaust their step budget, stop after six disk crossings, or fail the constraint check fall back to the initial camera direction for sky sampling unless they already escaped or were captured; those particular stars are not lens-mapped. This bounded-work fallback is a visible approximation, not a completed geodesic.
- There is no independent CPU photon-ray tracer yet, so the GPU output has not been cross-validated against a reference trajectory or published lensing benchmark.
- No dynamic resolution scaling, compute-shader path, bloom pipeline, or measured RTX 3050 performance target is currently provided.

See [NUMERICAL_METHODS.md](NUMERICAL_METHODS.md) and [VALIDATION.md](VALIDATION.md) for the corresponding model and test scope.
