package com.jarvis.tools.builtin;

import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;
import com.jarvis.tools.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.jarvis.tools.ToolParam.Type.BOOLEAN;
import static com.jarvis.tools.ToolParam.Type.STRING;

/**
 * Document and file-organisation tools. All paths are confined to the
 * workspace folder; deleting moves items to workspace/.trash so it can be undone.
 */
public final class FileTools {

    private static final Set<String> TEXT_EXT = Set.of("txt", "md", "csv", "json", "html", "css", "js", "java",
            "py", "c", "cpp", "h", "xml", "yaml", "yml", "log", "sql", "ini", "properties");
    private static final int MAX_READ_CHARS = 12_000;

    private FileTools() {
    }

    static String extension(Path p) {
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String name = target.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        for (int i = 2; ; i++) {
            Path p = target.resolveSibling(base + "-" + i + ext); // no spaces: paths are passed between steps
            if (!Files.exists(p)) {
                return p;
            }
        }
    }

    public static class ListFiles extends Tool {
        public ListFiles() {
            super("list_files", "files", "List files and folders inside the workspace folder.", RiskLevel.SAFE,
                    ToolParam.optional("path", STRING, "Sub-folder inside the workspace; omit for the top level"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path dir = ws.resolve(args.string("path", ""));
            if (!Files.isDirectory(dir)) {
                throw new ToolException(ws.display(dir) + " is not a folder.");
            }
            List<String> lines = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> !p.getFileName().toString().equals(".trash")).sorted().limit(200).forEach(p -> {
                    try {
                        lines.add(Files.isDirectory(p) ? "[folder] " + p.getFileName() + "/"
                                : p.getFileName() + "  (" + Files.size(p) / 1024 + " KB)");
                    } catch (IOException e) {
                        lines.add(p.getFileName().toString());
                    }
                });
            }
            if (lines.isEmpty()) {
                return ToolResult.ok(ws.display(dir) + " is empty.");
            }
            return ToolResult.ok(ws.display(dir) + " contains " + lines.size() + " item(s):\n" + String.join("\n", lines));
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "List files in workspace/" + args.string("path", "");
        }
    }

    public static class ReadFile extends Tool {
        public ReadFile() {
            super("read_file", "files", "Read a text document from the workspace (txt, md, csv, code...).",
                    RiskLevel.SAFE, ToolParam.required("path", STRING, "File path inside the workspace"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path f = ws.resolve(args.string("path"));
            if (!ws.isFile(f)) {
                throw new ToolException("File not found: " + ws.display(f));
            }
            if (!TEXT_EXT.contains(extension(f))) {
                throw new ToolException("Only text documents can be read (" + String.join(", ", TEXT_EXT) + ").");
            }
            String text = Files.readString(f, StandardCharsets.UTF_8);
            boolean cut = text.length() > MAX_READ_CHARS;
            return ToolResult.ok(ws.display(f) + (cut ? " (first " + MAX_READ_CHARS + " characters)" : "") + ":\n"
                    + (cut ? text.substring(0, MAX_READ_CHARS) : text));
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Read " + args.string("path");
        }
    }

    public static class WriteDocument extends Tool {
        public WriteDocument() {
            super("write_document", "files",
                    "Create a text document (e.g. notes.md, summary.txt, plan.md) in the workspace. "
                            + "Overwriting an existing file needs overwrite=true and the user's approval.",
                    RiskLevel.SAFE,
                    ToolParam.required("path", STRING, "File path inside the workspace, e.g. study/summary.md"),
                    ToolParam.required("content", STRING, "Full text of the document"),
                    ToolParam.optional("overwrite", BOOLEAN, "Replace the file if it already exists"));
        }

        @Override
        public RiskLevel riskFor(ToolArgs args, ToolContext ctx) {
            try {
                if (args.bool("overwrite", false) && Files.exists(ctx.workspace().resolve(args.string("path")))) {
                    return RiskLevel.DESTRUCTIVE;
                }
            } catch (ToolException ignored) {
                // invalid path: execute() will report it
            }
            return RiskLevel.SAFE;
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path f = ws.resolve(args.string("path"));
            if (!TEXT_EXT.contains(extension(f))) {
                throw new ToolException("Use a text file extension such as .md or .txt.");
            }
            boolean exists = Files.exists(f);
            if (exists && !args.bool("overwrite", false)) {
                throw new ToolException(ws.display(f) + " already exists. Choose another name or set overwrite=true.");
            }
            Files.createDirectories(f.getParent());
            Files.writeString(f, args.string("content"), StandardCharsets.UTF_8);
            return ToolResult.ok((exists ? "Overwrote " : "Created ") + ws.display(f) + " ("
                    + args.string("content").length() + " characters).");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return (args.bool("overwrite", false) ? "Write (overwrite) " : "Create ") + "workspace/" + args.string("path");
        }
    }

    public static class CreateFolder extends Tool {
        public CreateFolder() {
            super("create_folder", "files", "Create a folder inside the workspace.", RiskLevel.SAFE,
                    ToolParam.required("path", STRING, "Folder path inside the workspace"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Path d = ctx.workspace().resolve(args.string("path"));
            if (Files.exists(d)) {
                return ToolResult.ok(ctx.workspace().display(d) + " already exists.");
            }
            Files.createDirectories(d);
            return ToolResult.ok("Created folder " + ctx.workspace().display(d) + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Create folder workspace/" + args.string("path");
        }
    }

    public static class MoveFile extends Tool {
        public MoveFile() {
            super("move_file", "files", "Move or rename a file or folder inside the workspace.", RiskLevel.SENSITIVE,
                    ToolParam.required("from", STRING, "Current path"),
                    ToolParam.required("to", STRING, "New path, or an existing folder to move into"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path from = ws.resolve(args.string("from"));
            Path to = ws.resolve(args.string("to"));
            if (!Files.exists(from) || from.equals(ws.root())) {
                throw new ToolException("Nothing to move at " + ws.display(from) + ".");
            }
            if (Files.isDirectory(to)) {
                to = to.resolve(from.getFileName());
            }
            if (Files.exists(to)) {
                throw new ToolException(ws.display(to) + " already exists; nothing was moved.");
            }
            Files.createDirectories(to.getParent());
            Files.move(from, to);
            return ToolResult.ok("Moved " + ws.display(from) + " → " + ws.display(to) + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Move workspace/" + args.string("from") + " → workspace/" + args.string("to");
        }
    }

    public static class DeleteFile extends Tool {
        public DeleteFile() {
            super("delete_file", "files",
                    "Delete a file or folder in the workspace (it goes to workspace/.trash and can be recovered).",
                    RiskLevel.DESTRUCTIVE, ToolParam.required("path", STRING, "Path inside the workspace"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path p = ws.resolve(args.string("path"));
            if (!Files.exists(p) || p.equals(ws.root())) {
                throw new ToolException("Nothing to delete at " + ws.display(p) + ".");
            }
            Path trash = ws.root().resolve(".trash");
            Files.createDirectories(trash);
            Path target = uniqueTarget(trash.resolve(p.getFileName()));
            Files.move(p, target);
            return ToolResult.ok("Deleted " + ws.display(p) + " (recoverable from " + ws.display(target) + ").");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Delete workspace/" + args.string("path");
        }
    }

    public static class OrganizeFolder extends Tool {
        private static final Map<String, Set<String>> GROUPS = new LinkedHashMap<>();

        static {
            GROUPS.put("Documents", Set.of("pdf", "doc", "docx", "txt", "md", "rtf", "odt", "ppt", "pptx"));
            GROUPS.put("Spreadsheets", Set.of("xls", "xlsx", "csv", "ods"));
            GROUPS.put("Images", Set.of("png", "jpg", "jpeg", "gif", "bmp", "svg", "webp", "heic"));
            GROUPS.put("Audio", Set.of("mp3", "wav", "m4a", "aac", "flac", "ogg"));
            GROUPS.put("Video", Set.of("mp4", "mkv", "avi", "mov", "webm"));
            GROUPS.put("Code", Set.of("java", "py", "js", "ts", "html", "css", "c", "cpp", "h", "json", "xml", "sql"));
            GROUPS.put("Archives", Set.of("zip", "rar", "7z", "tar", "gz"));
        }

        public OrganizeFolder() {
            super("organize_folder", "files",
                    "Tidy a workspace folder by moving its files into sub-folders by type (Documents, Images, "
                            + "Code, Spreadsheets, Audio, Video, Archives, Other).",
                    RiskLevel.SENSITIVE,
                    ToolParam.optional("path", STRING, "Folder inside the workspace; omit for the top level"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            Workspace ws = ctx.workspace();
            Path dir = ws.resolve(args.string("path", ""));
            if (!Files.isDirectory(dir)) {
                throw new ToolException(ws.display(dir) + " is not a folder.");
            }
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(ws::isFile).toList();
            }
            if (files.isEmpty()) {
                return ToolResult.ok("There are no loose files to organise in " + ws.display(dir) + ".");
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Path f : files) {
                String group = "Other";
                for (Map.Entry<String, Set<String>> g : GROUPS.entrySet()) {
                    if (g.getValue().contains(extension(f))) {
                        group = g.getKey();
                        break;
                    }
                }
                Path targetDir = dir.resolve(group);
                Files.createDirectories(targetDir);
                Files.move(f, uniqueTarget(targetDir.resolve(f.getFileName())));
                counts.merge(group, 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder("Organised " + files.size() + " file(s) in " + ws.display(dir) + ": ");
            counts.forEach((g, n) -> sb.append(g).append(' ').append(n).append(", "));
            return ToolResult.ok(sb.substring(0, sb.length() - 2) + ".");
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Sort files in workspace/" + args.string("path", "") + " into type folders";
        }
    }
}
