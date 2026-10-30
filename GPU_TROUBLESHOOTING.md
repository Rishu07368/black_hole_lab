# Windows GPU and JOGL troubleshooting

## JOGL initialization

The original Windows failure occurred during `GLCanvas.addNotify()` while JOGL selected a WGL `GraphicsConfiguration`; JOGL/GlueGen natives and hardware profiles were already discovered. JOGL and GlueGen were upgraded together to 2.6.0 and the Swing viewport now uses `GLJPanel`. The app requests `GLProfile.getMaxProgrammable(true)`, requires GL3, compiles GLSL 3.30 shaders, and reports initialization, shader, and OpenGL errors instead of claiming success.

The `jogl-all-main` and `gluegen-rt-main` dependencies provide the appropriate native classifier, including Windows AMD64. To inspect the resolved versions:

```powershell
mvn dependency:tree
```

Java 25 was not established as the original cause: native loading and profile discovery worked, and a real context has since been created with the GLJPanel path. Use the included launcher rather than Maven's in-process `exec:java` command:

```powershell
.\run.ps1
```

## Verify the active adapter

The inspector reports actual context strings (`GL_VENDOR`, `GL_RENDERER`, OpenGL/GLSL versions, profile, and hardware-rasterizer flag). These are the evidence of the renderer in use; the presence of a GPU in Windows or `nvidia-smi` does not mean JOGL selected it.

The development machine has so far reported:

- Vendor: `Intel`
- Renderer: `Intel(R) UHD Graphics 770`
- OpenGL: `4.6.0 - Build 32.0.101.7092`
- GLSL: `4.60 - Build 32.0.101.7092`
- Hardware rasterizer: `true`

This confirms an Intel hardware context, not an RTX 3050 context. The app marks Intel-active and software contexts distinctly in the inspector. NVIDIA use and RTX performance remain unverified.

To prefer NVIDIA on Windows 11:

1. Find the Java executable used by the launcher with `(Get-Command java).Source`.
2. Open **Settings → System → Display → Graphics**, add that exact `java.exe`, select **Options → High performance**, and save.
3. If available, open **NVIDIA Control Panel → Manage 3D settings → Program Settings**, add that same Java executable, and select **High-performance NVIDIA processor**.
4. Close Gargantua and relaunch with `.\run.ps1`.
5. Verify the context report explicitly names `NVIDIA` and `GeForce RTX 3050`. Do not infer this from adapter detection or a hardware-rasterizer flag.

On hybrid laptops, display wiring, Windows per-app selection, driver settings, and remote/virtual display sessions can influence WGL adapter choice. If Intel remains active, try the NVIDIA preferences and an external output routed to the discrete GPU, then verify the context again. The application does not silently pretend to force NVIDIA.

## Context or shader failure

- Preserve the startup dialog and console output; they contain JOGL profile/shader diagnostics.
- Confirm 64-bit Java and inspect `java -version` and `mvn -version`.
- Check that JOGL and GlueGen resolve to matching 2.6.0 artifacts with Windows AMD64 natives.
- Update both Intel and NVIDIA drivers.
- Try outside Remote Desktop or virtual display software.
- Do not copy arbitrary DLLs into the project or force a fixed WGL pixel format.

To exercise context creation, shader compilation, first-frame OpenGL error checking, and the spin control:

```powershell
.\run.ps1 -GraphicsSmokeTest
```
