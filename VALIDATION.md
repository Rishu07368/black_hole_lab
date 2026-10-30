# Validation

Run the automated checks with:

```powershell
mvn clean test
mvn package
.\run.ps1 -GraphicsSmokeTest
```

JUnit tests cover Kerr horizon and ISCO limits, signed-spin orientation symmetry, frame dragging, invalid spin handling, the null radial potential, a camera-initialized Kerr photon's null Hamiltonian residual, circular timelike-orbit normalization/stability, radial capture, and active-renderer classification.

The graphics smoke test starts a real JOGL window, requires the shader context and first rendered frame to validate without an OpenGL error, changes spin from `0.95` to `0.70` and back, changes the particle population to 500, and verifies pause/reset controls before exiting. It is not a test of visual fidelity, particle-marker occlusion, or performance.

During development, a real context and shader run succeeded on `Intel(R) UHD Graphics 770`, OpenGL 4.6 / GLSL 4.60. The RTX 3050 has not yet been confirmed as the active OpenGL renderer on the target laptop. This environment observation must not be interpreted as an RTX benchmark.

Not yet validated: GPU ray trajectories against the CPU or a published Kerr lensing reference, conserved-quantity drift along an integrated GPU path, convergence over increasing step budgets, quantitative capture/escape classifications, radiative-transfer spectra, and behavior on the RTX 3050. A successful build or context smoke test alone does not establish those scientific results.
