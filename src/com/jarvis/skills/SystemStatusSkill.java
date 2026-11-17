package com.jarvis.skills;

import com.jarvis.system.SystemMonitor;

public class SystemStatusSkill extends Skill {

    public SystemStatusSkill() {
        super("Diagnostics", "System", "Runs a live diagnostic of CPU, memory and uptime.", "System status");
    }

    @Override
    public boolean canHandle(String c) {
        return containsAny(c, "system", "status", "diagnostic", "cpu", "memory usage", "ram", "uptime");
    }

    @Override
    public SkillResult execute(String c, String original, SkillContext ctx) {
        SystemMonitor m = ctx.monitor();
        String reply = String.format(
                "Diagnostics complete. CPU load is at %.0f percent across %d cores. Memory usage is %.0f percent, "
                        + "%s of %s. My core has been online for %s.",
                m.cpuPercent(), m.processors(), m.memoryPercent(),
                SystemMonitor.formatBytes(m.usedMemoryBytes()), SystemMonitor.formatBytes(m.totalMemoryBytes()),
                SystemMonitor.formatDuration(m.uptimeMillis()));
        return SkillResult.withAction(reply, "SHOW_VIEW", "diagnostics");
    }
}
