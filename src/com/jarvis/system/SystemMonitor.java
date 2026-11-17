package com.jarvis.system;

import java.lang.management.ManagementFactory;

/** Reads real CPU and memory figures from the operating system and the JVM. */
public class SystemMonitor {

    private final com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final long startedAt = System.currentTimeMillis();

    /** Whole-machine CPU load, 0–100. */
    public double cpuPercent() {
        double load = os.getCpuLoad();
        return load < 0 ? 0 : load * 100.0;
    }

    /** CPU used by the JARVIS Java process itself, 0–100. */
    public double processCpuPercent() {
        double load = os.getProcessCpuLoad();
        return load < 0 ? 0 : load * 100.0;
    }

    public long totalMemoryBytes() {
        return os.getTotalMemorySize();
    }

    public long usedMemoryBytes() {
        return os.getTotalMemorySize() - os.getFreeMemorySize();
    }

    public double memoryPercent() {
        long total = totalMemoryBytes();
        return total == 0 ? 0 : usedMemoryBytes() * 100.0 / total;
    }

    public long jvmUsedBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public long jvmMaxBytes() {
        return Runtime.getRuntime().maxMemory();
    }

    public int processors() {
        return os.getAvailableProcessors();
    }

    public String osName() {
        return os.getName() + " " + os.getVersion() + " (" + os.getArch() + ")";
    }

    public String javaVersion() {
        return System.getProperty("java.version") + " — " + System.getProperty("java.vendor");
    }

    public long uptimeMillis() {
        return System.currentTimeMillis() - startedAt;
    }

    public static String formatBytes(long bytes) {
        double gb = bytes / (1024.0 * 1024 * 1024);
        if (gb >= 1) {
            return String.format("%.1f GB", gb);
        }
        return String.format("%.0f MB", bytes / (1024.0 * 1024));
    }

    public static String formatDuration(long millis) {
        long s = millis / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }
}
