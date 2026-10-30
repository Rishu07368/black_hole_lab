package com.blackholelab.app;

import java.util.Locale;

final class GraphicsDiagnostics {
    enum Adapter {
        NVIDIA,
        INTEL,
        SOFTWARE,
        OTHER_HARDWARE,
        UNKNOWN
    }

    private GraphicsDiagnostics() {}

    static Adapter identify(String vendor, String renderer, boolean hardwareRasterizer) {
        String identity = ((vendor == null ? "" : vendor) + " "
                + (renderer == null ? "" : renderer)).toLowerCase(Locale.ROOT);
        if (!hardwareRasterizer || identity.matches(".*(llvmpipe|softpipe|software|basic render|gdi generic).*")) {
            return Adapter.SOFTWARE;
        }
        if (identity.contains("nvidia")) {
            return Adapter.NVIDIA;
        }
        if (identity.contains("intel")) {
            return Adapter.INTEL;
        }
        if (vendor == null && renderer == null) {
            return Adapter.UNKNOWN;
        }
        return Adapter.OTHER_HARDWARE;
    }

    static String status(Adapter adapter) {
        return switch (adapter) {
            case NVIDIA -> "NVIDIA GPU ACTIVE";
            case INTEL -> "INTEL GPU ACTIVE - verify NVIDIA preference";
            case SOFTWARE -> "SOFTWARE RASTERIZER ACTIVE - hardware acceleration unavailable";
            case OTHER_HARDWARE -> "HARDWARE GPU ACTIVE — not identified as NVIDIA";
            case UNKNOWN -> "GPU IDENTITY UNAVAILABLE";
        };
    }

    static Adapter identifyReport(String report) {
        if (report == null) {
            return Adapter.UNKNOWN;
        }
        String vendor = field(report, "Vendor: ");
        String renderer = field(report, "Renderer: ");
        boolean hardware = report.contains("Hardware rasterizer: true");
        return identify(vendor, renderer, hardware);
    }

    private static String field(String report, String prefix) {
        int start = report.indexOf(prefix);
        if (start < 0) {
            return null;
        }
        start += prefix.length();
        int end = report.indexOf(" | ", start);
        return end < 0 ? report.substring(start) : report.substring(start, end);
    }
}
