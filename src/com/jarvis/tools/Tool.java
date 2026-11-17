package com.jarvis.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One capability the agent can use: save a note, read a file, search Wikipedia...
 *
 * OOP: ABSTRACT CLASS + TEMPLATE METHOD + POLYMORPHISM.
 * The final {@link #run} method validates input and catches every error, then
 * calls the subclass's {@link #execute}. The agent holds tools as {@code Tool}
 * references and never needs to know the concrete class.
 */
public abstract class Tool {

    private final String name;
    private final String category;
    private final String description;
    private final RiskLevel risk;
    private final List<ToolParam> params;

    protected Tool(String name, String category, String description, RiskLevel risk, ToolParam... params) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.risk = risk;
        this.params = List.of(params);
    }

    /** The tool's actual work. Throw {@link ToolException} for expected failures. */
    protected abstract ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception;

    /** A one-line, human-readable description of what this call will do (shown on approval cards). */
    public String describeCall(ToolArgs args) {
        return name + " " + args.toJson();
    }

    /** Risk of this particular call. Override when risk depends on the input (e.g. overwriting a file). */
    public RiskLevel riskFor(ToolArgs args, ToolContext ctx) {
        return risk;
    }

    public final boolean requiresApproval(ToolArgs args, ToolContext ctx) {
        return riskFor(args, ctx).requiresApproval();
    }

    /** Runs the tool safely: any exception becomes an error result instead of crashing the agent. */
    public final ToolResult run(ToolArgs args, ToolContext ctx) {
        try {
            ToolResult r = execute(args, ctx);
            return r != null ? r : ToolResult.error("Tool returned no result");
        } catch (ToolException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception | LinkageError e) {
            // LinkageError covers failures while loading a tool's classes, so one broken tool can't stop the agent.
            return ToolResult.error("Unexpected " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Checks and converts raw arguments from the model: required fields present,
     * correct types (accepting "5" for 5), allowed values. Unknown keys are ignored.
     */
    public ToolArgs validate(Map<String, Object> raw) throws ToolException {
        Map<String, Object> clean = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (ToolParam p : params) {
            Object v = raw == null ? null : raw.get(p.name());
            if (v instanceof String s && s.isBlank()) {
                v = null;
            }
            if (v == null) {
                if (p.required()) {
                    problems.add("'" + p.name() + "' is required");
                }
                continue;
            }
            try {
                Object converted = convert(p, v);
                if (!p.allowed().isEmpty() && !p.allowed().contains(converted.toString().toLowerCase())) {
                    problems.add("'" + p.name() + "' must be one of " + p.allowed());
                    continue;
                }
                clean.put(p.name(), p.allowed().isEmpty() ? converted : converted.toString().toLowerCase());
            } catch (IllegalArgumentException e) {
                problems.add("'" + p.name() + "' must be a " + p.jsonType());
            }
        }
        if (!problems.isEmpty()) {
            throw new ToolException("Invalid input for " + name + ": " + String.join("; ", problems));
        }
        return new ToolArgs(clean);
    }

    private static Object convert(ToolParam p, Object v) {
        return switch (p.type()) {
            case STRING -> v instanceof String s ? s.trim() : String.valueOf(v);
            case INTEGER -> v instanceof Number n ? Long.valueOf(Math.round(n.doubleValue()))
                    : Long.valueOf(Long.parseLong(v.toString().trim().replaceAll("^#", "")));
            case NUMBER -> v instanceof Number n ? Double.valueOf(n.doubleValue())
                    : Double.valueOf(Double.parseDouble(v.toString().trim()));
            case BOOLEAN -> {
                if (v instanceof Boolean b) {
                    yield b;
                }
                String s = v.toString().trim().toLowerCase();
                if (s.equals("true") || s.equals("yes")) {
                    yield Boolean.TRUE;
                }
                if (s.equals("false") || s.equals("no")) {
                    yield Boolean.FALSE;
                }
                throw new IllegalArgumentException();
            }
        };
    }

    /** JSON-schema definition sent to the model so it knows how to call this tool. */
    public Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ToolParam p : params) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.jsonType());
            prop.put("description", p.description());
            if (!p.allowed().isEmpty()) {
                prop.put("enum", p.allowed());
            }
            props.put(p.name(), prop);
            if (p.required()) {
                required.add(p.name());
            }
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", props);
        parameters.put("required", required);
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        return Map.of("type", "function", "function", fn);
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public List<ToolParam> getParams() {
        return params;
    }
}
