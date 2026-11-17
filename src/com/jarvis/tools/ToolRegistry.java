package com.jarvis.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds every tool the agent can use. New capabilities are added by
 * registering another {@link Tool} subclass — nothing else changes.
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("Duplicate tool name: " + tool.getName());
        }
        tools.put(tool.getName(), tool);
        return this;
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(name == null ? null : tools.get(name.trim()));
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    public int size() {
        return tools.size();
    }

    /** Compact list "name — description" used by the planner to choose tools. */
    public String catalog() {
        StringBuilder sb = new StringBuilder();
        for (Tool t : tools.values()) {
            sb.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Dynamic tool selection: given the tools the planner chose, return them plus
     * the other tools in the same categories (e.g. choosing save_note also offers
     * search_notes), so the model has what it needs without seeing every tool.
     */
    public List<Tool> selectFor(Collection<String> chosen) {
        Set<String> categories = new LinkedHashSet<>();
        for (String name : chosen) {
            get(name).ifPresent(t -> categories.add(t.getCategory()));
        }
        if (categories.isEmpty()) {
            return new ArrayList<>(tools.values());
        }
        List<Tool> out = new ArrayList<>();
        for (Tool t : tools.values()) {
            if (categories.contains(t.getCategory())) {
                out.add(t);
            }
        }
        return out;
    }
}
