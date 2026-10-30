package com.blackholelab.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphicsDiagnosticsTest {
    @Test
    void identifiesLiveNvidiaRenderer() {
        assertEquals(GraphicsDiagnostics.Adapter.NVIDIA,
                GraphicsDiagnostics.identify("NVIDIA Corporation", "NVIDIA GeForce RTX 3050", true));
    }

    @Test
    void identifiesIntelAsActiveRatherThanAssumingNvidia() {
        assertEquals(GraphicsDiagnostics.Adapter.INTEL,
                GraphicsDiagnostics.identify("Intel", "Intel(R) UHD Graphics 770", true));
        assertEquals("INTEL GPU ACTIVE - verify NVIDIA preference",
                GraphicsDiagnostics.status(GraphicsDiagnostics.Adapter.INTEL));
    }

    @Test
    void identifiesSoftwareAndUnknownRenderers() {
        assertEquals(GraphicsDiagnostics.Adapter.SOFTWARE,
                GraphicsDiagnostics.identify("Microsoft", "GDI Generic", true));
        assertEquals(GraphicsDiagnostics.Adapter.SOFTWARE,
                GraphicsDiagnostics.identify("NVIDIA", "NVIDIA", false));
        assertEquals(GraphicsDiagnostics.Adapter.UNKNOWN,
                GraphicsDiagnostics.identify(null, null, true));
    }

    @Test
    void classifiesTheLiveContextReportWithoutAssumingTheInstalledAdapter() {
        assertEquals(GraphicsDiagnostics.Adapter.INTEL,
                GraphicsDiagnostics.identifyReport("Vendor: Intel | Renderer: Intel UHD | "
                        + "OpenGL: 4.6 | Hardware rasterizer: true"));
        assertEquals(GraphicsDiagnostics.Adapter.SOFTWARE,
                GraphicsDiagnostics.identifyReport("Vendor: Microsoft | Renderer: GDI Generic | "
                        + "Hardware rasterizer: false"));
    }
}
