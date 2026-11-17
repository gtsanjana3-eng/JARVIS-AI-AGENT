package com.jarvis.oop;

import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolRegistry;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.Json;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Proves the OOP concepts of this project LIVE, using Java reflection on the
 * classes that are actually running (nothing on the OOP page is hard-coded).
 *
 * <ul>
 * <li>{@link #overview()} — every class in the project, its kind, its parent and
 * interfaces, abstract methods and private fields.</li>
 * <li>{@link #polymorphismDemo()} — runs different tools through ONE {@code Tool}
 * reference type and the SAME method call.</li>
 * <li>{@link #encapsulationDemo()} — tries to give a tool bad input and to change
 * its private data, and shows how the class protects itself.</li>
 * </ul>
 */
public final class OopInspector {

    /** The parent types whose families are drawn as hierarchy trees on the OOP page. */
    private static final List<String> FAMILIES = List.of(
            "com.jarvis.tools.Tool", "com.jarvis.model.Entity", "com.jarvis.db.SqliteRepository",
            "com.jarvis.agent.workflow.Workflow", "com.jarvis.search.SearchProvider", "com.jarvis.ai.LlmClient",
            "com.jarvis.skills.Skill");

    private final ToolRegistry tools;
    private final ToolContext ctx;
    private List<Class<?>> classes;

    public OopInspector(ToolRegistry tools, ToolContext ctx) {
        this.tools = tools;
        this.ctx = ctx;
    }

    // ================================================================== overview

    public synchronized String overview() {
        List<Class<?>> all = classes();
        int abstracts = 0, interfaces = 0, enums = 0, records = 0, subclasses = 0, privateFields = 0, fields = 0;
        List<Map<String, Object>> abstractTypes = new ArrayList<>();
        for (Class<?> c : all) {
            if (c.isInterface()) {
                interfaces++;
            } else if (c.isEnum()) {
                enums++;
            } else if (c.isRecord()) {
                records++;
            } else if (Modifier.isAbstract(c.getModifiers())) {
                abstracts++;
            }
            if (c.getSuperclass() != null && isOurs(c.getSuperclass())) {
                subclasses++;
            }
            for (Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || c.isEnum() && f.isEnumConstant() || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                fields++;
                if (Modifier.isPrivate(f.getModifiers())) {
                    privateFields++;
                }
            }
            if ((c.isInterface() || Modifier.isAbstract(c.getModifiers())) && !c.isAnnotation()) {
                List<String> methods = new ArrayList<>();
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isAbstract(m.getModifiers()) && !m.isSynthetic()) {
                        methods.add(signature(m));
                    }
                }
                methods.sort(null);
                if (!methods.isEmpty()) {
                    abstractTypes.add(Map.of("name", nice(c), "kind", c.isInterface() ? "interface" : "abstract class",
                            "methods", methods, "implementations", childrenOf(c, all).size()));
                }
            }
        }
        abstractTypes.sort(Comparator.comparing(m -> -((Integer) m.get("implementations"))));

        List<Map<String, Object>> families = new ArrayList<>();
        for (String name : FAMILIES) {
            Class<?> parent = all.stream().filter(c -> c.getName().equals(name)).findFirst().orElse(null);
            if (parent != null) {
                families.add(family(parent, all));
            }
        }

        return Json.object()
                .putValue("stats", Map.of("classes", all.size(), "abstractClasses", abstracts, "interfaces", interfaces,
                        "enums", enums, "records", records, "subclasses", subclasses, "fields", fields,
                        "privateFields", privateFields))
                .putValue("abstraction", abstractTypes)
                .putValue("families", families)
                .putValue("encapsulation", encapsulationOf(Tool.class))
                .toJson();
    }

    private Map<String, Object> family(Class<?> parent, List<Class<?>> all) {
        List<Map<String, Object>> kids = new ArrayList<>();
        for (Class<?> c : childrenOf(parent, all)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", nice(c));
            m.put("kind", kind(c));
            List<String> grand = childrenOf(c, all).stream().map(OopInspector::nice).toList();
            m.put("children", grand);
            kids.add(m);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", nice(parent));
        f.put("kind", kind(parent));
        f.put("relation", parent.isInterface() ? "implements" : "extends");
        f.put("children", kids);
        return f;
    }

    /** Direct subclasses / implementers of {@code parent} among the project's classes. */
    private static List<Class<?>> childrenOf(Class<?> parent, List<Class<?>> all) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : all) {
            if (c == parent) {
                continue;
            }
            boolean direct = c.getSuperclass() == parent;
            for (Class<?> i : c.getInterfaces()) {
                direct |= i == parent;
            }
            if (direct) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparing(OopInspector::nice));
        return out;
    }

    private static Map<String, Object> encapsulationOf(Class<?> c) {
        List<String> hidden = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                hidden.add(Modifier.toString(f.getModifiers()) + " " + f.getType().getSimpleName() + " " + f.getName());
            }
        }
        List<String> exposed = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                exposed.add((Modifier.isFinal(m.getModifiers()) ? "final " : "") + signature(m));
            }
        }
        exposed.sort(null);
        return Map.of("className", nice(c), "fields", hidden, "publicMethods", exposed);
    }

    // ================================================================== live demos

    /** Three completely different tools, one reference type, one method call. */
    public String polymorphismDemo() {
        List<Map<String, Object>> out = new ArrayList<>();
        Object[][] calls = {
                { "calculator", Map.of("expression", "(25 * 4) + sqrt(144)") },
                { "get_datetime", Map.of() },
                { "system_status", Map.of() },
        };
        for (Object[] call : calls) {
            Tool tool = tools.get((String) call[0]).orElse(null); // the variable's type is always Tool
            if (tool == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) call[1];
            long start = System.nanoTime();
            ToolResult r;
            try {
                r = tool.run(tool.validate(raw), ctx); // SAME call — Java picks each class's own execute()
            } catch (ToolException e) {
                r = ToolResult.error(e.getMessage());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tool", tool.getName());
            m.put("declaredType", "Tool");
            m.put("actualClass", nice(tool.getClass()));
            m.put("input", raw.isEmpty() ? "(no input)" : Json.stringify(raw));
            m.put("success", r.success());
            m.put("output", r.output().length() > 300 ? r.output().substring(0, 300) + " …" : r.output());
            m.put("micros", (System.nanoTime() - start) / 1000);
            out.add(m);
        }
        return Json.object().putValue("results", out).toJson();
    }

    /** Tries to misuse a tool from outside; the class's own rules stop every attempt. */
    public String encapsulationDemo() {
        List<Map<String, Object>> out = new ArrayList<>();
        Tool calc = tools.get("calculator").orElseThrow();

        // 1. Missing required input → the class's own validate() refuses it.
        out.add(attempt("Ask the calculator to work with nothing to calculate",
                "The calculator checked its own rules first: it needs a sum to work on, so it refused.", () -> {
            calc.validate(Map.of());
            return "accepted (not protected!)";
        }));
        // 2. Wrong type → refused.
        out.add(attempt("Tell 'delete note' to delete note number \"abc\"",
                "Note numbers must be numbers. The tool rejected the wrong kind of input before touching anything.", () -> {
                    tools.get("delete_note").orElseThrow().validate(Map.of("id", "abc"));
                    return "accepted (not protected!)";
                }));
        // 3. Change the private name field from another class → Java itself refuses (private = hidden).
        out.add(attempt("Secretly rename the calculator to \"hacked\" from outside",
                "Its name is locked inside the tool (private). Java itself refused to let an outsider change it.", () -> {
            Field f = Tool.class.getDeclaredField("name");
            f.set(calc, "hacked");
            return "changed (not protected!)";
        }));
        // 4. Crash inside a tool → run() (final) turns it into a safe error result.
        out.add(attempt("Make the calculator crash by dividing 10 by the word \"hello\"",
                "The tool's built-in safety net caught the problem and gave a polite error instead of crashing JARVIS.", () -> {
            ToolResult r = calc.run(calc.validate(Map.of("expression", "10 / hello")), ctx);
            if (r.success()) {
                return "ran: " + r.output();
            }
            throw new IllegalStateException(r.output());
        }));
        return Json.object().putValue("attempts", out).put("nameStillIs", calc.getName()).toJson();
    }

    private interface Attempt {
        String run() throws Exception;
    }

    private static Map<String, Object> attempt(String what, String plainWhy, Attempt a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attempt", what);
        m.put("why", plainWhy);
        try {
            m.put("blocked", false);
            m.put("result", a.run());
        } catch (Exception e) {
            m.put("blocked", true);
            m.put("result", e instanceof IllegalStateException
                    ? "run() caught the crash and returned a safe error instead: " + e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return m;
    }

    // ================================================================== class scanning

    /** Every class compiled from this project, read from the folder the program is running from. */
    private List<Class<?>> classes() {
        if (classes != null) {
            return classes;
        }
        List<Class<?>> out = new ArrayList<>();
        try {
            Path root = Path.of(OopInspector.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            try (Stream<Path> files = Files.walk(root.resolve("com").resolve("jarvis"))) {
                for (Path p : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                    String name = root.relativize(p).toString().replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                    if (name.matches(".*\\$\\d.*") || name.contains("$$")) {
                        continue; // anonymous classes and lambdas
                    }
                    try {
                        Class<?> c = Class.forName(name, false, OopInspector.class.getClassLoader());
                        if (!c.isAnonymousClass() && !c.isLocalClass() && !c.isSynthetic()) {
                            out.add(c);
                        }
                    } catch (ClassNotFoundException | LinkageError e) {
                        // skip anything that can't be loaded
                    }
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            // running from a jar or unusual location: fall back to the registered tools
            tools.all().forEach(t -> out.add(t.getClass()));
            out.add(Tool.class);
        }
        out.sort(Comparator.comparing(Class::getName));
        classes = out;
        return out;
    }

    private static boolean isOurs(Class<?> c) {
        return c.getName().startsWith("com.jarvis.");
    }

    private static String kind(Class<?> c) {
        if (c.isInterface()) {
            return "interface";
        }
        if (c.isEnum()) {
            return "enum";
        }
        if (c.isRecord()) {
            return "record";
        }
        return Modifier.isAbstract(c.getModifiers()) ? "abstract class" : "class";
    }

    /** "UtilityTools.Calculator" instead of "com.jarvis.tools.builtin.UtilityTools$Calculator". */
    private static String nice(Class<?> c) {
        String n = c.getName();
        return n.substring(n.lastIndexOf('.') + 1).replace('$', '.');
    }

    private static String signature(Method m) {
        List<String> params = new ArrayList<>();
        for (Class<?> p : m.getParameterTypes()) {
            params.add(p.getSimpleName());
        }
        return m.getReturnType().getSimpleName() + " " + m.getName() + "(" + String.join(", ", params) + ")";
    }
}
