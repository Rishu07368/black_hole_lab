package com.blackholelab.app;

import com.blackholelab.physics.KerrMetric;
import com.blackholelab.physics.KerrParticleSystem;
import com.blackholelab.rendering.KerrRayTracer;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class GargantuaApplication {
    private static final Color PANEL = new Color(17, 22, 32);
    private static final Color TEXT = new Color(226, 231, 240);
    private static final Color MUTED = new Color(177, 187, 204);

    private GargantuaApplication() {}

    public static void main(String[] args) {
        boolean graphicsSmokeTest = Arrays.asList(args).contains("--graphics-smoke-test");
        SwingUtilities.invokeLater(() -> {
            try {
                launch(graphicsSmokeTest);
            } catch (RuntimeException exception) {
                showGraphicsError(exception);
            }
        });
    }

    private static void launch(boolean graphicsSmokeTest) {
        GLProfile.initSingleton();
        GLProfile profile = GLProfile.getMaxProgrammable(true);
        if (!profile.isGL3()) {
            throw new GLException("The selected OpenGL profile cannot run the GLSL 3.30 Kerr ray tracer."
                    + System.lineSeparator() + "Selected profile: " + profile
                    + System.lineSeparator() + GLProfile.glAvailabilityToString());
        }

        GLJPanel viewport = new GLJPanel(new GLCapabilities(profile));
        viewport.setOpaque(true);
        JLabel graphicsStatus = new JLabel("OpenGL context: initializing...");
        JLabel performanceStatus = new JLabel("Ray tracer: initializing...");
        AtomicReference<KerrParticleSystem> particleSystem =
                new AtomicReference<>(new KerrParticleSystem(new KerrMetric(0.95), 100));
        graphicsStatus.setForeground(MUTED);
        performanceStatus.setForeground(MUTED);
        AtomicBoolean contextInitialized = new AtomicBoolean();
        AtomicBoolean firstFrameValidated = new AtomicBoolean();
        KerrRayTracer rayTracer = new KerrRayTracer(
                status -> {
                    SwingUtilities.invokeLater(() -> {
                        graphicsStatus.setText("<html>" + status.replace(" | ", "<br>") + "</html>");
                        GraphicsDiagnostics.Adapter adapter = GraphicsDiagnostics.identifyReport(status);
                        graphicsStatus.setForeground(switch (adapter) {
                            case NVIDIA, OTHER_HARDWARE -> new Color(142, 220, 166);
                            case INTEL, UNKNOWN -> new Color(245, 190, 100);
                            case SOFTWARE -> new Color(255, 120, 120);
                        });
                    });
                    contextInitialized.set(true);
                },
                status -> SwingUtilities.invokeLater(() -> performanceStatus.setText(status)));
        viewport.addGLEventListener(rayTracer);

        JFrame frame = new JFrame("GARGANTUA — Kerr Black Hole Lab");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.getContentPane().setBackground(new Color(10, 13, 20));
        frame.add(viewport, BorderLayout.CENTER);

        Inspector inspector = buildInspector(rayTracer, viewport, graphicsStatus, performanceStatus,
                particleSystem);
        frame.add(inspector.panel(), BorderLayout.EAST);
        frame.setMinimumSize(new Dimension(1120, 680));
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null);

        AtomicBoolean renderingFailed = new AtomicBoolean();
        long[] lastSimulationNanos = {System.nanoTime()};
        Timer renderTimer = new Timer(8, event -> {
            if (renderingFailed.get()) {
                return;
            }
            try {
                long now = System.nanoTime();
                double elapsedSeconds = Math.min(0.05, (now - lastSimulationNanos[0]) / 1_000_000_000.0);
                lastSimulationNanos[0] = now;
                if (!inspector.particlesPaused().isSelected()) {
                    particleSystem.get().advance(elapsedSeconds * inspector.particleSpeed().getValue() / 10.0);
                }
                rayTracer.setParticlePositions(projectParticles(
                        particleSystem.get().positions(), rayTracer, viewport.getWidth(), viewport.getHeight()));
                inspector.particleStatus().setText("Particles: " + particleSystem.get().particleCount()
                        + " | captured: " + particleSystem.get().capturedCount());
                viewport.display();
                firstFrameValidated.set(true);
            } catch (RuntimeException exception) {
                if (renderingFailed.compareAndSet(false, true)) {
                    performanceStatus.setText("GPU rendering failed");
                    performanceStatus.setForeground(new Color(255, 120, 120));
                    showGraphicsError(exception);
                }
            }
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                renderTimer.stop();
                viewport.destroy();
            }
        });
        installCameraControls(viewport, rayTracer, inspector);
        frame.setVisible(true);
        renderTimer.start();

        if (graphicsSmokeTest) {
            runGraphicsSmokeTest(frame, rayTracer, inspector, particleSystem,
                    contextInitialized, firstFrameValidated);
        }
    }

    private static Inspector buildInspector(KerrRayTracer rayTracer, GLJPanel viewport,
                                            JLabel graphicsStatus, JLabel performanceStatus,
                                            AtomicReference<KerrParticleSystem> particleSystem) {
        JPanel panel = new JPanel(new BorderLayout(8, 14));
        panel.setPreferredSize(new Dimension(320, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 14, 18, 14));
        panel.setBackground(PANEL);
        JLabel title = new JLabel("KERR OBSERVATORY");
        title.setForeground(new Color(234, 239, 249));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(0, 1, 4, 6));
        controls.setOpaque(false);
        JComboBox<Integer> particleCount = new JComboBox<>(new Integer[]{100, 500, 1_000});
        particleCount.setSelectedItem(100);
        JLabel spinLabel = createLabel();
        JSlider spin = slider(0, 99, 95);
        spin.addChangeListener(event -> {
            float value = spin.getValue() / 100.0f;
            rayTracer.setSpin(value);
            particleSystem.set(new KerrParticleSystem(new KerrMetric(value),
                    (Integer) particleCount.getSelectedItem()));
            spinLabel.setText("Kerr spin a* = " + String.format("%.2f", value));
            if (!spin.getValueIsAdjusting()) {
                System.out.printf("Kerr spin control updated: a* = %.2f%n", value);
            }
        });
        spinLabel.setText("Kerr spin a* = 0.95");

        JLabel inclinationLabel = createLabel();
        JSlider inclination = slider(0, 90, 80);
        inclination.addChangeListener(event -> {
            int degrees = inclination.getValue();
            rayTracer.setInclination((float) Math.toRadians(Math.max(2, degrees)));
            inclinationLabel.setText("Observer inclination = " + degrees + " deg");
        });
        inclinationLabel.setText("Observer inclination = 80 deg");

        JLabel distanceLabel = createLabel();
        JSlider distance = slider(18, 60, 42);
        distance.addChangeListener(event -> {
            rayTracer.setCameraDistance(distance.getValue());
            distanceLabel.setText("Camera radius = " + distance.getValue() + " M");
        });
        distanceLabel.setText("Camera radius = 42 M");

        JLabel qualityLabel = createLabel();
        JSlider quality = slider(48, 420, 48);
        quality.setMajorTickSpacing(74);
        quality.setPaintTicks(true);
        quality.addChangeListener(event -> {
            int steps = quality.getValue();
            rayTracer.setQualitySteps(steps);
            qualityLabel.setText("Null-geodesic steps = " + steps);
        });
        qualityLabel.setText("Null-geodesic steps = 48");

        JLabel renderScaleLabel = createLabel();
        JSlider renderScale = slider(12, 100, 25);
        renderScale.setMajorTickSpacing(25);
        renderScale.setPaintTicks(true);
        renderScale.addChangeListener(event -> {
            float scale = renderScale.getValue() / 100.0f;
            rayTracer.setRenderScale(scale);
            renderScaleLabel.setText(String.format("Maximum render scale = %.0f%%", scale * 100.0f));
        });
        renderScaleLabel.setText("Maximum render scale = 25%");

        JLabel exposureLabel = createLabel();
        JSlider exposure = slider(-20, 20, 0);
        exposure.addChangeListener(event -> {
            float stops = exposure.getValue() / 5.0f;
            rayTracer.setExposure(stops);
            exposureLabel.setText("Exposure = " + String.format("%+.1f EV", stops));
        });
        exposureLabel.setText("Exposure = +0.0 EV");

        JComboBox<String> presentationMode = new JComboBox<>(new String[]{"Cinematic", "Scientific"});
        presentationMode.addActionListener(event ->
                rayTracer.setCinematic("Cinematic".equals(presentationMode.getSelectedItem())));

        JCheckBox particlesPaused = new JCheckBox("Pause particles", false);
        particlesPaused.setForeground(TEXT);
        particlesPaused.setOpaque(false);
        JCheckBox particlesVisible = new JCheckBox("Show particle markers", true);
        particlesVisible.setForeground(TEXT);
        particlesVisible.setOpaque(false);
        particlesVisible.addActionListener(event -> rayTracer.setParticlesVisible(particlesVisible.isSelected()));
        JSlider particleSpeed = slider(1, 40, 10);
        JLabel particleSpeedLabel = createLabel();
        particleSpeedLabel.setText("Particle time scale = 1.0x");
        particleSpeed.addChangeListener(event -> particleSpeedLabel.setText(
                String.format("Particle time scale = %.1fx", particleSpeed.getValue() / 10.0)));
        JLabel particleStatus = createLabel();
        particleStatus.setText("Particles: 100 | captured: 0");
        particleCount.addActionListener(event -> {
            particleSystem.set(new KerrParticleSystem(new KerrMetric(rayTracer.spin()),
                    (Integer) particleCount.getSelectedItem()));
            particleStatus.setText("Particles: " + particleSystem.get().particleCount() + " | captured: 0");
        });
        JButton resetParticles = new JButton("Reset particle orbits");
        resetParticles.addActionListener(event -> {
            particleSystem.set(new KerrParticleSystem(new KerrMetric(rayTracer.spin()),
                    (Integer) particleCount.getSelectedItem()));
            particleStatus.setText("Particles: " + particleSystem.get().particleCount() + " | captured: 0");
        });
        JPanel cameraPresets = new JPanel(new GridLayout(2, 2, 4, 4));
        cameraPresets.setOpaque(false);
        JButton faceOn = new JButton("Face-on");
        JButton edgeOn = new JButton("Edge-on");
        JButton elevated = new JButton("Elevated");
        JButton resetCamera = new JButton("Reset camera");
        faceOn.addActionListener(event -> applyCameraPreset(rayTracer, inclination, distance, 2, false));
        edgeOn.addActionListener(event -> applyCameraPreset(rayTracer, inclination, distance, 88, false));
        elevated.addActionListener(event -> applyCameraPreset(rayTracer, inclination, distance, 60, false));
        resetCamera.addActionListener(event -> applyCameraPreset(rayTracer, inclination, distance, 80, true));
        cameraPresets.add(faceOn);
        cameraPresets.add(edgeOn);
        cameraPresets.add(elevated);
        cameraPresets.add(resetCamera);

        JLabel modeText = new JLabel("<html>GPU Kerr null-geodesic tracing<br>"
                + "Coordinates: Kerr Boyer-Lindquist<br>"
                + "Disk: equatorial, prograde thin-disk approximation<br>"
                + "Drag to orbit; wheel to adjust view distance<br>"
                + "Shadow and disk images emerge from ray integration<br>"
                + "Particle markers are projected; their orbits use CPU timelike geodesics</html>");
        modeText.setForeground(MUTED);
        JLabel physics = createLabel();
        updatePhysicsReadout(physics, rayTracer);

        controls.add(spinLabel);
        controls.add(spin);
        controls.add(inclinationLabel);
        controls.add(inclination);
        controls.add(distanceLabel);
        controls.add(distance);
        controls.add(qualityLabel);
        controls.add(quality);
        controls.add(renderScaleLabel);
        controls.add(renderScale);
        controls.add(exposureLabel);
        controls.add(exposure);
        JLabel cameraPresetsLabel = createLabel();
        cameraPresetsLabel.setText("Camera presets");
        controls.add(cameraPresetsLabel);
        controls.add(cameraPresets);
        JLabel presentationModeLabel = createLabel();
        presentationModeLabel.setText("Presentation mode");
        controls.add(presentationModeLabel);
        controls.add(presentationMode);
        JLabel particlesLabel = createLabel();
        particlesLabel.setText("CPU test particles");
        controls.add(particlesLabel);
        controls.add(particleCount);
        controls.add(particleStatus);
        controls.add(particlesPaused);
        controls.add(particlesVisible);
        controls.add(particleSpeedLabel);
        controls.add(particleSpeed);
        controls.add(resetParticles);
        controls.add(modeText);
        controls.add(physics);
        controls.add(graphicsStatus);
        controls.add(performanceStatus);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(controls);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);

        spin.addChangeListener(event -> updatePhysicsReadout(physics, rayTracer));
        inclination.addChangeListener(event -> updatePhysicsReadout(physics, rayTracer));
        distance.addChangeListener(event -> updatePhysicsReadout(physics, rayTracer));
        return new Inspector(panel, spin, spinLabel, inclination, inclinationLabel, distance, distanceLabel,
                particleCount, particlesPaused, particleSpeed, particleStatus, resetParticles,
                faceOn, edgeOn, elevated, resetCamera, presentationMode);
    }

    private static float[] projectParticles(List<KerrParticleSystem.Position> particles,
                                            KerrRayTracer tracer, int width, int height) {
        float[] projected = new float[particles.size() * 2];
        int count = 0;
        double theta = tracer.inclination();
        double phi = tracer.azimuth();
        double sinTheta = Math.sin(theta);
        double cosTheta = Math.cos(theta);
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double radialX = sinTheta * cosPhi;
        double radialY = cosTheta;
        double radialZ = sinTheta * sinPhi;
        double thetaX = cosTheta * cosPhi;
        double thetaY = -sinTheta;
        double thetaZ = cosTheta * sinPhi;
        double phiX = -sinPhi;
        double phiZ = cosPhi;
        double cameraDistance = tracer.cameraDistance();
        double aspect = Math.max(1.0, width) / Math.max(1.0, height);
        double fovScale = Math.tan(Math.toRadians(42.0) * 0.5);
        double rollCos = Math.cos(tracer.cameraRoll());
        double rollSin = Math.sin(tracer.cameraRoll());

        for (KerrParticleSystem.Position particle : particles) {
            double x = particle.radius() * Math.cos(particle.azimuth()) - cameraDistance * radialX;
            double y = -cameraDistance * radialY;
            double z = particle.radius() * Math.sin(particle.azimuth()) - cameraDistance * radialZ;
            double forward = -(x * radialX + y * radialY + z * radialZ);
            if (forward <= 0.0) {
                continue;
            }
            double localX = (x * phiX + z * phiZ) / forward / fovScale;
            double localY = (x * thetaX + y * thetaY + z * thetaZ) / forward / fovScale;
            double screenX = localX * rollCos + localY * rollSin;
            double screenY = -localX * rollSin + localY * rollCos;
            double ndcX = screenX / aspect;
            double ndcY = -screenY;
            double shadowRadius = 5.2 / cameraDistance / fovScale;
            if (ndcX * ndcX + ndcY * ndcY <= shadowRadius * shadowRadius
                    || Math.abs(ndcX) > 1.0 || Math.abs(ndcY) > 1.0) {
                continue;
            }
            projected[count++] = (float) ndcX;
            projected[count++] = (float) ndcY;
        }
        return Arrays.copyOf(projected, count);
    }

    private static void updatePhysicsReadout(JLabel label, KerrRayTracer tracer) {
        KerrMetric metric = new KerrMetric(tracer.spin());
        label.setText(String.format("<html>Outer horizon = %.4f M<br>"
                        + "Prograde ISCO = %.4f M<br>Retrograde ISCO = %.4f M<br>"
                        + "Observer inclination = %.1f deg<br>Frame dragging at 6M = %.5f / M</html>",
                metric.outerHorizon(), metric.iscoRadius(true), metric.iscoRadius(false),
                Math.toDegrees(tracer.inclination()),
                metric.frameDraggingAngularVelocity(6.0, Math.PI / 2.0)));
    }

    private static void applyCameraPreset(KerrRayTracer tracer, JSlider inclination,
                                          JSlider distance, int inclinationDegrees,
                                          boolean resetAzimuth) {
        inclination.setValue(inclinationDegrees);
        if (resetAzimuth) {
            tracer.setAzimuth(0.0f);
            distance.setValue(42);
        }
    }

    private static void installCameraControls(GLJPanel viewport, KerrRayTracer tracer, Inspector inspector) {
        MouseAdapter cameraControls = new MouseAdapter() {
            private int previousX;
            private int previousY;

            @Override
            public void mousePressed(MouseEvent event) {
                previousX = event.getX();
                previousY = event.getY();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                int dx = event.getX() - previousX;
                int dy = event.getY() - previousY;
                previousX = event.getX();
                previousY = event.getY();
                tracer.orbit(-dx * 0.006f, dy * 0.006f);
                int degrees = Math.max(2, Math.min(88, Math.round((float) Math.toDegrees(tracer.inclination()))));
                inspector.inclination().setValue(degrees);
            }
        };
        viewport.addMouseListener(cameraControls);
        viewport.addMouseMotionListener(cameraControls);
        viewport.addMouseWheelListener((MouseWheelEvent event) -> {
            int nextDistance = Math.max(18, Math.min(60,
                    Math.round(tracer.cameraDistance() + event.getWheelRotation() * 1.5f)));
            inspector.distance().setValue(nextDistance);
        });
    }

    private static void runGraphicsSmokeTest(JFrame frame, KerrRayTracer rayTracer, Inspector inspector,
                                             AtomicReference<KerrParticleSystem> particleSystem,
                                             AtomicBoolean contextInitialized,
                                             AtomicBoolean firstFrameValidated) {
        Timer timer = new Timer(500, event -> {
            try {
                if (!contextInitialized.get() || !firstFrameValidated.get()) {
                    throw new IllegalStateException("OpenGL shader context or first frame was not validated");
                }
                inspector.spin().setValue(70);
                if (Math.abs(rayTracer.spin() - 0.70f) > 1e-6
                        || !inspector.spinLabel().getText().endsWith("0.70")) {
                    throw new IllegalStateException("Spin control did not update to 0.70");
                }
                System.out.println("Graphics smoke test: Kerr shader active; spin changed to a* = 0.70");
                inspector.spin().setValue(95);
                if (Math.abs(rayTracer.spin() - 0.95f) > 1e-6) {
                    throw new IllegalStateException("Spin control did not restore to 0.95");
                }
                System.out.println("Graphics smoke test: spin restored to a* = 0.95");
                inspector.particleCount().setSelectedItem(500);
                if (particleSystem.get().particleCount() != 500
                        || particleSystem.get().positions().size() != 500) {
                    throw new IllegalStateException("Particle population did not change to 500");
                }
                inspector.particlesPaused().setSelected(true);
                inspector.resetParticles().doClick();
                if (!inspector.particlesPaused().isSelected()
                        || particleSystem.get().particleCount() != 500) {
                    throw new IllegalStateException("Particle pause/reset controls did not remain active");
                }
                inspector.particleCount().setSelectedItem(100);
                System.out.println("Graphics smoke test: particle count, pause, and reset controls passed");
                inspector.edgeOn().doClick();
                if (Math.abs(Math.toDegrees(rayTracer.inclination()) - 88.0) > 0.1) {
                    throw new IllegalStateException("Edge-on camera preset did not update inclination");
                }
                inspector.resetCamera().doClick();
                if (Math.abs(Math.toDegrees(rayTracer.inclination()) - 80.0) > 0.1
                        || Math.abs(rayTracer.cameraDistance() - 42.0) > 0.1
                        || Math.abs(rayTracer.azimuth()) > 1e-6) {
                    throw new IllegalStateException("Reset camera preset did not restore the default view");
                }
                inspector.presentationMode().setSelectedItem("Scientific");
                if (rayTracer.isCinematic()) {
                    throw new IllegalStateException("Scientific presentation mode did not disable cinematic tone mapping");
                }
                inspector.presentationMode().setSelectedItem("Cinematic");
                if (!rayTracer.isCinematic()) {
                    throw new IllegalStateException("Cinematic presentation mode did not enable tone mapping");
                }
                System.out.println("Graphics smoke test: camera presets and presentation modes passed");
                frame.dispose();
            } catch (RuntimeException exception) {
                System.err.println("Graphics smoke test failed: " + exception.getMessage());
                frame.dispose();
                System.exit(1);
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static JSlider slider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value);
        slider.setOpaque(false);
        slider.setForeground(TEXT);
        return slider;
    }

    private static JLabel createLabel() {
        JLabel label = new JLabel();
        label.setForeground(TEXT);
        return label;
    }

    private static void showGraphicsError(RuntimeException exception) {
        String details = exception.getMessage() == null ? exception.toString() : exception.getMessage();
        JOptionPane.showMessageDialog(null, details,
                "Gargantua graphics initialization failed", JOptionPane.ERROR_MESSAGE);
    }

    private record Inspector(JPanel panel, JSlider spin, JLabel spinLabel,
                             JSlider inclination, JLabel inclinationLabel,
                             JSlider distance, JLabel distanceLabel,
                             JComboBox<Integer> particleCount, JCheckBox particlesPaused,
                             JSlider particleSpeed, JLabel particleStatus, JButton resetParticles,
                             JButton faceOn, JButton edgeOn, JButton elevated, JButton resetCamera,
                             JComboBox<String> presentationMode) {}
}
