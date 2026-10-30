package com.blackholelab.rendering;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.common.nio.Buffers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Full-screen Kerr null-geodesic renderer. All pixel ray integration runs in GLSL. */
public final class KerrRayTracer implements GLEventListener {
    private static final String VERTEX_SHADER = "/com/blackholelab/rendering/kerr-raytrace.vert";
    private static final String FRAGMENT_SHADER = "/com/blackholelab/rendering/kerr-raytrace.frag";
    private static final String PARTICLE_VERTEX_SHADER = "/com/blackholelab/rendering/particles.vert";
    private static final String PARTICLE_FRAGMENT_SHADER = "/com/blackholelab/rendering/particles.frag";
    private static final int MAX_SHADER_STEPS = 420;
    private static final float MIN_RENDER_SCALE = 0.12f;
    private static final float TARGET_FPS = 30.0f;
    private static final float MIN_INCLINATION = (float) Math.toRadians(2.0);
    private static final float MAX_INCLINATION = (float) Math.toRadians(88.0);

    private final Consumer<String> graphicsReporter;
    private final Consumer<String> performanceReporter;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private volatile float spin = 0.95f;
    private volatile float inclination = 1.40f;
    private volatile float azimuth;
    private volatile float cameraRoll = -0.58f;
    private volatile float cameraDistance = 42.0f;
    private volatile float exposure;
    private volatile int maxSteps = 48;
    private volatile float renderScale = 0.25f;
    private volatile boolean cinematic = true;
    private volatile float[] particlePositions = new float[0];
    private volatile boolean particlesVisible = true;
    private int program;
    private int vertexArray;
    private int particleProgram;
    private int particleVertexArray;
    private int particleBuffer;
    private int renderFramebuffer;
    private int renderTexture;
    private int renderWidth;
    private int renderHeight;
    private long startedNanos;
    private long lastReportNanos;
    private int frames;

    public KerrRayTracer(Consumer<String> graphicsReporter, Consumer<String> performanceReporter) {
        this.graphicsReporter = graphicsReporter;
        this.performanceReporter = performanceReporter;
    }

    public void setSpin(float spin) {
        if (!Float.isFinite(spin) || spin < 0.0f || spin >= 1.0f) {
            throw new IllegalArgumentException("Spin must be finite and in [0, 1)");
        }
        this.spin = spin;
    }

    public float spin() {
        return spin;
    }

    public void setInclination(float inclinationRadians) {
        if (!Float.isFinite(inclinationRadians)
                || inclinationRadians < MIN_INCLINATION || inclinationRadians > MAX_INCLINATION) {
            throw new IllegalArgumentException("Inclination must be in [2, 88] degrees");
        }
        inclination = inclinationRadians;
    }

    public void setAzimuth(float azimuthRadians) {
        if (!Float.isFinite(azimuthRadians)) {
            throw new IllegalArgumentException("Camera azimuth must be finite");
        }
        azimuth = azimuthRadians;
    }

    public float inclination() {
        return inclination;
    }

    public float azimuth() {
        return azimuth;
    }

    public float cameraRoll() {
        return cameraRoll;
    }

    public void orbit(float azimuthDelta, float inclinationDelta) {
        if (!Float.isFinite(azimuthDelta) || !Float.isFinite(inclinationDelta)) {
            throw new IllegalArgumentException("Camera deltas must be finite");
        }
        azimuth += azimuthDelta;
        inclination = Math.max(MIN_INCLINATION, Math.min(MAX_INCLINATION, inclination + inclinationDelta));
    }

    public void setCameraDistance(float distance) {
        if (!Float.isFinite(distance) || distance < 18.0f || distance > 60.0f) {
            throw new IllegalArgumentException("Camera distance must be in [18, 60] M");
        }
        cameraDistance = distance;
    }

    public float cameraDistance() {
        return cameraDistance;
    }

    public void setQualitySteps(int steps) {
        if (steps < 48 || steps > MAX_SHADER_STEPS) {
            throw new IllegalArgumentException("Ray steps must be in [48, 420]");
        }
        maxSteps = steps;
    }

    public int qualitySteps() {
        return maxSteps;
    }

    public void setRenderScale(float scale) {
        if (!Float.isFinite(scale) || scale < MIN_RENDER_SCALE || scale > 1.0f) {
            throw new IllegalArgumentException("Render scale must be in [0.12, 1]");
        }
        renderScale = scale;
    }

    public float renderScale() {
        return renderScale;
    }

    public void setExposure(float exposure) {
        if (!Float.isFinite(exposure) || exposure < -4.0f || exposure > 4.0f) {
            throw new IllegalArgumentException("Exposure must be in [-4, 4]");
        }
        this.exposure = exposure;
    }

    public void setCinematic(boolean cinematic) {
        this.cinematic = cinematic;
    }

    public boolean isCinematic() {
        return cinematic;
    }

    public void setParticlePositions(float[] positions) {
        if (positions == null || positions.length % 2 != 0) {
            throw new IllegalArgumentException("Particle positions must be non-null x/y pairs");
        }
        for (float position : positions) {
            if (!Float.isFinite(position)) {
                throw new IllegalArgumentException("Particle screen positions must be finite");
            }
        }
        particlePositions = positions.clone();
    }

    public void setParticlesVisible(boolean visible) {
        particlesVisible = visible;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        startedNanos = System.nanoTime();
        GL3 gl = drawable.getGL().getGL3();
        int vertex = compileShader(gl, GL2ES2.GL_VERTEX_SHADER, loadShader(VERTEX_SHADER));
        int fragment;
        try {
            fragment = compileShader(gl, GL2ES2.GL_FRAGMENT_SHADER, loadShader(FRAGMENT_SHADER));
        } catch (RuntimeException exception) {
            gl.glDeleteShader(vertex);
            throw exception;
        }
        program = gl.glCreateProgram();
        gl.glAttachShader(program, vertex);
        gl.glAttachShader(program, fragment);
        gl.glLinkProgram(program);
        gl.glDeleteShader(vertex);
        gl.glDeleteShader(fragment);

        int[] linkStatus = new int[1];
        gl.glGetProgramiv(program, GL2ES2.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GL.GL_FALSE) {
            String log = programLog(gl, program);
            gl.glDeleteProgram(program);
            program = 0;
            throw new GLException("Kerr ray-tracing shader link failed: " + log);
        }

        int particleVertex = compileShader(gl, GL2ES2.GL_VERTEX_SHADER, loadShader(PARTICLE_VERTEX_SHADER));
        int particleFragment;
        try {
            particleFragment = compileShader(gl, GL2ES2.GL_FRAGMENT_SHADER, loadShader(PARTICLE_FRAGMENT_SHADER));
        } catch (RuntimeException exception) {
            gl.glDeleteShader(particleVertex);
            gl.glDeleteProgram(program);
            program = 0;
            throw exception;
        }
        particleProgram = gl.glCreateProgram();
        gl.glAttachShader(particleProgram, particleVertex);
        gl.glAttachShader(particleProgram, particleFragment);
        gl.glLinkProgram(particleProgram);
        gl.glDeleteShader(particleVertex);
        gl.glDeleteShader(particleFragment);
        gl.glGetProgramiv(particleProgram, GL2ES2.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == GL.GL_FALSE) {
            String log = programLog(gl, particleProgram);
            gl.glDeleteProgram(particleProgram);
            gl.glDeleteProgram(program);
            particleProgram = 0;
            program = 0;
            throw new GLException("Particle marker shader link failed: " + log);
        }

        uniforms.clear();
        for (String name : new String[]{"uResolution", "uSpin", "uInclination", "uAzimuth",
                "uCameraRoll", "uCameraDistance", "uExposure", "uTime", "uDiskOuter", "uFov",
                "uMaxSteps", "uDiskDirection", "uCinematic"}) {
            int location = gl.glGetUniformLocation(program, name);
            if (location < 0) {
                gl.glDeleteProgram(program);
                program = 0;
                throw new GLException("Required ray-tracing uniform was optimized out or not found: " + name);
            }
            uniforms.put(name, location);
        }

        int[] arrays = new int[1];
        gl.glGenVertexArrays(1, arrays, 0);
        vertexArray = arrays[0];
        if (vertexArray == 0) {
            gl.glDeleteProgram(program);
            program = 0;
            throw new GLException("OpenGL could not allocate the ray-tracing vertex array");
        }
        int[] particleArrays = new int[1];
        gl.glGenVertexArrays(1, particleArrays, 0);
        particleVertexArray = particleArrays[0];
        gl.glGenBuffers(1, particleArrays, 0);
        particleBuffer = particleArrays[0];
        if (particleVertexArray == 0 || particleBuffer == 0) {
            throw new GLException("OpenGL could not allocate particle marker buffers");
        }
        gl.glBindVertexArray(particleVertexArray);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, particleBuffer);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0L);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        int[] framebuffers = new int[1];
        gl.glGenFramebuffers(1, framebuffers, 0);
        renderFramebuffer = framebuffers[0];
        if (renderFramebuffer == 0) {
            throw new GLException("OpenGL could not allocate the scaled ray-tracing framebuffer");
        }
        reportGraphics(drawable);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();
        int width = Math.max(1, drawable.getSurfaceWidth());
        int height = Math.max(1, drawable.getSurfaceHeight());
        int scaledWidth = Math.max(1, Math.round(width * renderScale));
        int scaledHeight = Math.max(1, Math.round(height * renderScale));
        ensureRenderTarget(gl, scaledWidth, scaledHeight);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, renderFramebuffer);
        gl.glViewport(0, 0, scaledWidth, scaledHeight);
        gl.glClearColor(0.004f, 0.006f, 0.012f, 1.0f);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glUseProgram(program);
        gl.glBindVertexArray(vertexArray);
        set2f(gl, "uResolution", scaledWidth, scaledHeight);
        set1f(gl, "uSpin", spin);
        set1f(gl, "uInclination", inclination);
        set1f(gl, "uAzimuth", azimuth);
        set1f(gl, "uCameraRoll", cameraRoll);
        set1f(gl, "uCameraDistance", cameraDistance);
        set1f(gl, "uExposure", exposure);
        set1f(gl, "uTime", (System.nanoTime() - startedNanos) / 1_000_000_000.0f);
        set1f(gl, "uDiskOuter", 16.0f);
        set1f(gl, "uFov", (float) Math.toRadians(42.0));
        gl.glUniform1i(uniforms.get("uMaxSteps"), maxSteps);
        gl.glUniform1i(uniforms.get("uDiskDirection"), 1);
        gl.glUniform1i(uniforms.get("uCinematic"), cinematic ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, renderFramebuffer);
        gl.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER, 0);
        gl.glBlitFramebuffer(0, 0, scaledWidth, scaledHeight,
                0, 0, width, height, GL.GL_COLOR_BUFFER_BIT, GL.GL_LINEAR);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glViewport(0, 0, width, height);
        renderParticles(gl);

        int error = gl.glGetError();
        if (error != GL.GL_NO_ERROR) {
            throw new GLException(String.format("OpenGL error 0x%04X during Kerr ray tracing", error));
        }
        reportFrameTime();
    }

    private void renderParticles(GL3 gl) {
        float[] positions = particlePositions;
        if (!particlesVisible || positions.length == 0) {
            return;
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, particleBuffer);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) positions.length * Float.BYTES,
                Buffers.newDirectFloatBuffer(positions), GL2ES2.GL_STREAM_DRAW);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE);
        gl.glEnable(GL3.GL_PROGRAM_POINT_SIZE);
        gl.glUseProgram(particleProgram);
        gl.glBindVertexArray(particleVertexArray);
        gl.glDrawArrays(GL.GL_POINTS, 0, positions.length / 2);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDisable(GL3.GL_PROGRAM_POINT_SIZE);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private void reportGraphics(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();
        String vendor = valueOrUnknown(gl.glGetString(GL.GL_VENDOR));
        String renderer = valueOrUnknown(gl.glGetString(GL.GL_RENDERER));
        String version = valueOrUnknown(gl.glGetString(GL.GL_VERSION));
        String shadingLanguage = valueOrUnknown(gl.glGetString(GL2ES2.GL_SHADING_LANGUAGE_VERSION));
        boolean hardware = drawable.getContext().isHardwareRasterizer();
        String report = "Vendor: " + vendor + " | Renderer: " + renderer
                + " | OpenGL: " + version + " | GLSL: " + shadingLanguage
                + " | Profile: " + drawable.getGLProfile()
                + " | Hardware rasterizer: " + hardware;
        System.out.println("GPU ray tracer initialized: " + report);
        graphicsReporter.accept(report);
    }

    private void reportFrameTime() {
        frames++;
        long now = System.nanoTime();
        if (lastReportNanos == 0) {
            lastReportNanos = now;
            return;
        }
        long elapsed = now - lastReportNanos;
        if (elapsed < 1_000_000_000L) {
            return;
        }
        float fps = frames * 1_000_000_000.0f / elapsed;
        float frameMilliseconds = elapsed / (frames * 1_000_000.0f);
        String report = String.format("%.1f FPS | %.2f ms | %d steps | %d x %d (%.0f%%)",
                fps, frameMilliseconds, maxSteps, renderWidth, renderHeight, renderScale * 100.0f);
        System.out.println("Render performance: " + report);
        performanceReporter.accept(report);
        if (fps < TARGET_FPS && renderScale > MIN_RENDER_SCALE) {
            renderScale = Math.max(MIN_RENDER_SCALE, renderScale * 0.9f);
        }
        frames = 0;
        lastReportNanos = now;
    }

    private void ensureRenderTarget(GL3 gl, int width, int height) {
        if (renderWidth == width && renderHeight == height && renderTexture != 0) {
            return;
        }
        if (renderTexture != 0) {
            gl.glDeleteTextures(1, new int[]{renderTexture}, 0);
        }
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        renderTexture = textures[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, renderTexture);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL2ES2.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL2ES2.GL_CLAMP_TO_EDGE);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, width, height,
                0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, renderFramebuffer);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0,
                GL.GL_TEXTURE_2D, renderTexture, 0);
        int status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        if (status != GL.GL_FRAMEBUFFER_COMPLETE) {
            throw new GLException(String.format("Scaled ray-tracing framebuffer is incomplete (0x%04X)", status));
        }
        renderWidth = width;
        renderHeight = height;
    }

    private void set1f(GL3 gl, String name, float value) {
        gl.glUniform1f(uniforms.get(name), value);
    }

    private void set2f(GL3 gl, String name, float first, float second) {
        gl.glUniform2f(uniforms.get(name), first, second);
    }

    private static int compileShader(GL3 gl, int type, String source) {
        int shader = gl.glCreateShader(type);
        gl.glShaderSource(shader, 1, new String[]{source}, (int[]) null, 0);
        gl.glCompileShader(shader);
        int[] compileStatus = new int[1];
        gl.glGetShaderiv(shader, GL2ES2.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == GL.GL_FALSE) {
            String log = shaderLog(gl, shader);
            gl.glDeleteShader(shader);
            throw new GLException("Kerr ray-tracing shader compilation failed: " + log);
        }
        return shader;
    }

    private static String loadShader(String resource) {
        try (InputStream stream = KerrRayTracer.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new GLException("Missing shader resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GLException("Unable to read shader resource " + resource, exception);
        }
    }

    private static String shaderLog(GL3 gl, int shader) {
        int[] length = new int[1];
        gl.glGetShaderiv(shader, GL2ES2.GL_INFO_LOG_LENGTH, length, 0);
        if (length[0] <= 1) {
            return "no compiler log";
        }
        byte[] bytes = new byte[length[0]];
        gl.glGetShaderInfoLog(shader, bytes.length, new int[1], 0, bytes, 0);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private static String programLog(GL3 gl, int program) {
        int[] length = new int[1];
        gl.glGetProgramiv(program, GL2ES2.GL_INFO_LOG_LENGTH, length, 0);
        if (length[0] <= 1) {
            return "no linker log";
        }
        byte[] bytes = new byte[length[0]];
        gl.glGetProgramInfoLog(program, bytes.length, new int[1], 0, bytes, 0);
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unavailable" : value;
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        drawable.getGL().getGL3().glViewport(x, y, width, height);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();
        if (particleBuffer != 0) {
            gl.glDeleteBuffers(1, new int[]{particleBuffer}, 0);
            particleBuffer = 0;
        }
        if (renderTexture != 0) {
            gl.glDeleteTextures(1, new int[]{renderTexture}, 0);
            renderTexture = 0;
        }
        if (renderFramebuffer != 0) {
            gl.glDeleteFramebuffers(1, new int[]{renderFramebuffer}, 0);
            renderFramebuffer = 0;
        }
        renderWidth = 0;
        renderHeight = 0;
        if (particleVertexArray != 0) {
            gl.glDeleteVertexArrays(1, new int[]{particleVertexArray}, 0);
            particleVertexArray = 0;
        }
        if (particleProgram != 0) {
            gl.glDeleteProgram(particleProgram);
            particleProgram = 0;
        }
        if (vertexArray != 0) {
            gl.glDeleteVertexArrays(1, new int[]{vertexArray}, 0);
            vertexArray = 0;
        }
        if (program != 0) {
            gl.glDeleteProgram(program);
            program = 0;
        }
    }
}
