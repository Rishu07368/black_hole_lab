# Architecture

The Java application separates the analytic CPU physics, GPU ray tracing, and Swing interface:

- `com.blackholelab.physics.KerrMetric` contains analytic Boyer–Lindquist Kerr quantities, circular-orbit values, and the separated null radial potential.
- `com.blackholelab.physics.KerrPhoton` initializes a camera ray in a local ZAMO frame and provides a CPU null-Hamiltonian constraint reference.
- `com.blackholelab.physics.EquatorialTimelikeGeodesic` evolves a conservative massive test particle in the equatorial plane.
- `com.blackholelab.physics.KerrParticleSystem` manages deterministic ensembles of 100, 500, or 1,000 conservative equatorial test particles.
- `com.blackholelab.rendering.KerrRayTracer` owns JOGL shader compilation, uniforms, fullscreen rendering, actual context diagnostics, frame timing, and GL resource cleanup.
- `src/main/resources/com/blackholelab/rendering/kerr-raytrace.frag` performs per-fragment Kerr null-geodesic integration and simplified disk/sky radiance.
- `src/main/resources/com/blackholelab/rendering/particles.*` renders CPU-projected particle positions as GPU point markers; markers are not ray-lensed or depth-tested.
- `com.blackholelab.app.GargantuaApplication` creates the Swing window, controls, GLJPanel viewport, and camera interactions.

The renderer currently uses the same dimensionless spin and observer parameters shown in the UI, but there is not yet a shared simulation-state engine. Particle management is implemented for conservative equatorial orbits; dissipative accretion, trails, persistence, export, and a CPU/GPU trajectory comparison are not implemented.
