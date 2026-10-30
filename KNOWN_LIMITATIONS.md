# Known limitations

- The GPU fragment shader performs Kerr Boyer–Lindquist null-ray integration, but it has not been cross-validated against an independent numerical geodesic solver or a published lensing benchmark.
- The null integrator uses fixed-step RK4, finite polar regularization, and an exterior horizon capture cutoff. It does not use horizon-penetrating coordinates or a controlled integration-error estimate.
- The equatorial disk is a simplified optically thin emissivity approximation. Its `g^4` transfer and color mapping are not a calibrated spectrum or complete relativistic radiative transfer.
- The sky is procedural, not catalog-based. No scientifically calibrated photon-ring width/brightness is reported.
- The CPU particle system supports 100/500/1,000 conservative equatorial timelike geodesics on prograde circular orbits. It has pause/time scaling/reset controls and screen-space GPU markers, but no dissipation/inspiral, lensed/depth-tested markers, trails, particle inspector, injection, or export.
- Controls currently include spin, inclination, distance, integration steps, exposure, Cinematic/Scientific tone-map selection, face-on/edge-on/elevated/reset camera presets, drag-to-orbit, wheel zoom, and particle count/pause/time scale/visibility/reset. Free camera motion, scientific data overlays, persistence, and full simulation controls remain incomplete.
- The user interface does not yet provide dynamic resolution scaling, a compute-shader path, bloom, or tested Low/Balanced/High/Cinematic performance presets.
- The active renderer observed so far is Intel UHD Graphics 770. RTX 3050 selection and benchmarking have not been verified; the app reports the actual OpenGL context and warns when Intel/software rendering is active.
- There is no packaged installer or cross-GPU performance benchmark.
