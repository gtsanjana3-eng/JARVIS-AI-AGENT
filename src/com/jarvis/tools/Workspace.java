package com.jarvis.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * The sandbox folder for all file tools. Every path the AI supplies is
 * resolved inside this folder; anything that tries to escape it
 * ("../", absolute paths, links) is rejected.
 */
public class Workspace {

    private final Path root;

    public Workspace(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public Path root() {
        return root;
    }

    /** Resolves a user/AI supplied path safely inside the workspace. */
    public Path resolve(String relative) throws ToolException {
        String rel = relative == null ? "" : relative.trim().replace('\\', '/');
        if (rel.isEmpty() || rel.equals(".") || rel.equals("/")) {
            return root;
        }
        if (rel.startsWith("workspace/")) {
            rel = rel.substring("workspace/".length());
        }
        Path p = Path.of(rel);
        if (p.isAbsolute() || rel.matches("^[A-Za-z]:.*")) {
            throw new ToolException("Only paths inside the workspace folder are allowed (got '" + relative + "').");
        }
        Path resolved = root.resolve(p).normalize();
        if (!resolved.startsWith(root)) {
            throw new ToolException("That path leaves the workspace folder, which is not allowed.");
        }
        // Refuse to follow links that could point outside the sandbox.
        for (Path cur = resolved; cur != null && cur.startsWith(root) && !cur.equals(root); cur = cur.getParent()) {
            if (Files.isSymbolicLink(cur)) {
                throw new ToolException("Links are not allowed inside the workspace.");
            }
        }
        return resolved;
    }

    /** Path shown to the user, relative to the workspace root. */
    public String display(Path p) {
        Path rel = root.relativize(p.toAbsolutePath().normalize());
        String s = rel.toString().replace('\\', '/');
        return s.isEmpty() ? "workspace/" : "workspace/" + s;
    }

    public boolean isFile(Path p) {
        return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
    }
}
