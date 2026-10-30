# User guide

1. Install a 64-bit JDK 17+ and Maven.
2. From PowerShell in the project directory, run `mvn clean test`.
3. Launch the separate Java process with `.\run.ps1`.
4. Drag the rendered viewport to orbit the camera and use the mouse wheel to zoom. Use the Face-on, Edge-on, and Elevated presets to change inclination, or Reset camera to restore the default view.
5. Adjust dimensionless Kerr spin `a*`, observer inclination, integration step count, exposure, and Cinematic/Scientific presentation in the inspector. Both modes use the same ray integration; presentation mode changes tone mapping only.
6. In the CPU test-particle section, choose 100, 500, or 1,000 particles. Pause/resume their proper-time integration, adjust the time scale, toggle screen-space markers, or reset the population. Changing spin rebuilds the population for the new Kerr metric.
7. Confirm the actual OpenGL adapter and driver information in the graphics diagnostics. Intel-active contexts are warned about; do not assume that the RTX 3050 is selected.

Particle markers are projected directly onto the view and are not occluded or lensed by the GPU ray tracer. Their CPU orbits are conservative, equatorial Kerr timelike geodesics; no accretion drag is applied, so they do not inspiral. This prototype integrates photon rays in a fragment shader and uses simplified disk emission. It is not a validated radiative-transfer renderer. See [PARTICLE_ACCRETION.md](PARTICLE_ACCRETION.md) and [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for model boundaries.
