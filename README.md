# Gargantua — Black Hole Lab

Gargantua is a Java 17+ Swing desktop application using JOGL and GLSL. It renders a continuously updated, GPU-integrated Kerr null-ray visualization; the default dimensionless spin is `a* = 0.95` (`|a*| < 1`). This is an actively developed scientific prototype, not a fully validated astrophysical renderer.

## Run on Windows

Install a 64-bit JDK 17+ and Maven, open PowerShell in this directory, then run:

```powershell
mvn clean test
.\run.ps1
```

`run.ps1` resolves Maven runtime dependencies and launches a separate Java process. This avoids JOGL factory-thread shutdown problems observed with Maven's in-process `exec:java` launcher. For a finite context/shader/first-frame and spin-control smoke check:

```powershell
.\run.ps1 -GraphicsSmokeTest
```

### Build a click-to-run Windows distribution

Install a 64-bit JDK that includes `jlink` and `jpackage` (JDK 17+), then run:

```powershell
.\scripts\package-windows.ps1
```

This builds the project and creates `dist\Gargantua\Gargantua.exe`, a Windows app image with a bundled Java runtime and JOGL runtime dependencies. Double-click `dist\Gargantua\Gargantua.exe` to launch it. The portable ZIP at `dist\Gargantua-0.1.0-windows-x64.zip` can be extracted elsewhere; run the EXE inside the extracted `Gargantua` folder and keep the adjacent `app` and `runtime` folders together. The distribution is Windows x64-specific.

The window opens with a near-edge-on, diagonally framed accretion disk around the Kerr shadow. Drag the viewport to orbit the camera, use the mouse wheel to zoom, or use Face-on, Edge-on, Elevated, and Reset camera presets. Adjust spin, inclination, ray-step budget (default 420), exposure, and Cinematic/Scientific presentation in the inspector; the modes share the ray integration and vary tone mapping only. The particle panel runs 100, 500, or 1,000 conservative equatorial CPU test-particle geodesics with pause, time scale, reset, and GPU point markers. The shader bounds work by the selected step budget and stops after six disk crossings; incomplete rays use the documented straight-direction sky fallback. Runtime diagnostics show the actual OpenGL vendor, renderer, OpenGL/GLSL versions, JOGL profile, hardware-rasterizer status, and frame timing.

JOGL and GlueGen 2.6.0 are used with platform natives from their `*-main` Maven dependencies. The Swing viewport uses `GLJPanel` and requests a programmable profile at runtime. Shader compilation/link failures and OpenGL errors are surfaced rather than hidden.

## GPU selection

The renderer reports the adapter actually used by the OpenGL context; it does not claim that an installed NVIDIA card is active based on device enumeration. The current development context has reported Intel UHD Graphics 770. See [GPU_TROUBLESHOOTING.md](GPU_TROUBLESHOOTING.md) to set Windows/NVIDIA high-performance preferences and verify an RTX 3050 context.

## Scientific scope

The fragment shader traces backward null rays using Kerr Boyer–Lindquist Hamilton equations and bounded RK4 steps. It handles approximate capture/escape termination, equatorial thin-disk crossings, an analytic prograde ISCO, a procedural distant star field, and approximate frequency-dependent disk brightness. The CPU side contains analytic Kerr properties, an equatorial timelike-geodesic solver, and a local-frame null-ray initialization reference.

Important limitations remain: the GPU integrator has not been numerically benchmarked against an independent Kerr geodesic solver; horizon capture uses an exterior cutoff rather than horizon-penetrating coordinates; the disk is an optically thin emission approximation rather than relativistic radiative transfer; particle markers use straight camera projection rather than Kerr lensing and particle trails, dissipation, persistence, dynamic render scaling, and RTX 3050 runtime verification are not implemented. The conservative particle orbits do not spiral inward. See [RENDERING.md](RENDERING.md), [NUMERICAL_METHODS.md](NUMERICAL_METHODS.md), [PARTICLE_ACCRETION.md](PARTICLE_ACCRETION.md), [VALIDATION.md](VALIDATION.md), and [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md).
