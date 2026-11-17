package com.jarvis.tools.builtin;

import com.jarvis.system.SystemMonitor;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.MathEvaluator;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.jarvis.tools.ToolParam.Type.STRING;

/** General-purpose tools: calculator, clock, and computer status. */
public final class UtilityTools {

    private UtilityTools() {
    }

    public static class Calculator extends Tool {
        public Calculator() {
            super("calculator", "math",
                    "Evaluate an arithmetic expression exactly. Supports + - * / % ^, brackets, sqrt(), abs(), "
                            + "sin/cos/tan (degrees), log(), ln(), pi, e. Always use this instead of mental math.",
                    RiskLevel.SAFE,
                    ToolParam.required("expression", STRING, "The expression, e.g. (25 + 15) * 3 or sqrt(144)"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws ToolException {
            String expr = args.string("expression");
            try {
                return ToolResult.ok(expr + " = " + MathEvaluator.format(MathEvaluator.evaluate(expr)));
            } catch (ArithmeticException | IllegalArgumentException e) {
                throw new ToolException("Cannot evaluate '" + expr + "': " + e.getMessage());
            }
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Calculate " + args.string("expression");
        }
    }

    public static class DateTime extends Tool {
        public DateTime() {
            super("get_datetime", "time", "Get the current local date, time and day of the week.", RiskLevel.SAFE);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            ZonedDateTime now = ZonedDateTime.now(ctx.zone());
            return ToolResult.ok(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, h:mm a"))
                    + " (" + ctx.zone() + ")");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Read the clock";
        }
    }

    public static class SystemStatus extends Tool {
        public SystemStatus() {
            super("system_status", "system",
                    "Get live CPU load, memory usage, processor count and uptime of this computer.", RiskLevel.SAFE);
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) {
            SystemMonitor m = ctx.monitor();
            return ToolResult.ok(String.format(
                    "CPU load %.0f%% across %d logical cores; memory %.0f%% used (%s of %s); JARVIS uptime %s; OS %s.",
                    m.cpuPercent(), m.processors(), m.memoryPercent(),
                    SystemMonitor.formatBytes(m.usedMemoryBytes()), SystemMonitor.formatBytes(m.totalMemoryBytes()),
                    SystemMonitor.formatDuration(m.uptimeMillis()), m.osName()));
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Read CPU and memory usage";
        }
    }
}
