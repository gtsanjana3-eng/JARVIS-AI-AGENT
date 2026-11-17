package com.jarvis.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes simple documents in several formats from lightweight Markdown
 * ("# Heading", "## Sub-heading", "- bullet", plain paragraphs).
 *
 * The .docx writer builds a real Office Open XML package with java.util.zip,
 * so Microsoft Word opens it as a normal formatted document — no libraries.
 */
public final class DocumentWriter {

    /** One block of a document. */
    public record Block(Kind kind, String text) {
        public enum Kind { TITLE, HEADING, BULLET, PARAGRAPH }
    }

    private DocumentWriter() {
    }

    /** Parses lightweight Markdown into blocks. */
    public static List<Block> parse(String title, String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            blocks.add(new Block(Block.Kind.TITLE, title.trim()));
        }
        StringBuilder para = new StringBuilder();
        for (String raw : markdown.replace("\r", "").split("\n")) {
            String line = raw.strip();
            boolean special = line.isEmpty() || line.startsWith("#") || line.matches("^[-*•]\\s+.*");
            if (special && para.length() > 0) {
                blocks.add(new Block(Block.Kind.PARAGRAPH, para.toString().trim()));
                para.setLength(0);
            }
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                String h = line.replaceFirst("^#+\\s*", "");
                if (!(blocks.size() == 1 && blocks.get(0).text().equalsIgnoreCase(h))) {
                    blocks.add(new Block(Block.Kind.HEADING, h));
                }
            } else if (line.matches("^[-*•]\\s+.*")) {
                blocks.add(new Block(Block.Kind.BULLET, line.replaceFirst("^[-*•]\\s+", "")));
            } else {
                para.append(line).append(' ');
            }
        }
        if (para.length() > 0) {
            blocks.add(new Block(Block.Kind.PARAGRAPH, para.toString().trim()));
        }
        return blocks;
    }

    public static void write(Path file, String format, List<Block> blocks) throws IOException {
        Files.createDirectories(file.getParent());
        switch (format) {
            case "docx" -> writeDocx(file, blocks);
            case "html" -> Files.writeString(file, html(blocks), StandardCharsets.UTF_8);
            case "md" -> Files.writeString(file, markdown(blocks), StandardCharsets.UTF_8);
            default -> Files.writeString(file, text(blocks), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ plain text / markdown / html

    static String text(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            switch (b.kind()) {
                case TITLE -> sb.append(b.text().toUpperCase()).append("\n").append("=".repeat(Math.min(60, b.text().length()))).append("\n\n");
                case HEADING -> sb.append(b.text()).append("\n").append("-".repeat(Math.min(60, b.text().length()))).append("\n");
                case BULLET -> sb.append("  • ").append(b.text()).append("\n");
                case PARAGRAPH -> sb.append(b.text()).append("\n\n");
            }
        }
        return sb.toString().replace("\n", System.lineSeparator());
    }

    static String markdown(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            switch (b.kind()) {
                case TITLE -> sb.append("# ").append(b.text()).append("\n\n");
                case HEADING -> sb.append("\n## ").append(b.text()).append("\n\n");
                case BULLET -> sb.append("- ").append(b.text()).append("\n");
                case PARAGRAPH -> sb.append(b.text()).append("\n\n");
            }
        }
        return sb.toString();
    }

    static String html(List<Block> blocks) {
        StringBuilder sb = new StringBuilder("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(blocks.isEmpty() ? "Document" : esc(blocks.get(0).text()))
                .append("</title><style>body{font-family:Segoe UI,Arial,sans-serif;max-width:760px;margin:40px auto;"
                        + "padding:0 20px;line-height:1.6;color:#1b2a35}h1{color:#0a6c8a}h2{color:#0a6c8a;margin-top:1.6em}"
                        + "</style></head><body>\n");
        boolean inList = false;
        for (Block b : blocks) {
            if (b.kind() != Block.Kind.BULLET && inList) {
                sb.append("</ul>\n");
                inList = false;
            }
            switch (b.kind()) {
                case TITLE -> sb.append("<h1>").append(esc(b.text())).append("</h1>\n");
                case HEADING -> sb.append("<h2>").append(esc(b.text())).append("</h2>\n");
                case BULLET -> {
                    if (!inList) {
                        sb.append("<ul>\n");
                        inList = true;
                    }
                    sb.append("<li>").append(esc(b.text())).append("</li>\n");
                }
                case PARAGRAPH -> sb.append("<p>").append(esc(b.text())).append("</p>\n");
            }
        }
        if (inList) {
            sb.append("</ul>\n");
        }
        return sb.append("</body></html>\n").toString();
    }

    // ------------------------------------------------------------------ .docx (Office Open XML)

    static void writeDocx(Path file, List<Block> blocks) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Block b : blocks) {
            switch (b.kind()) {
                case TITLE -> body.append(paragraph(b.text(), 40, true, "0A6C8A", 240));
                case HEADING -> body.append(paragraph(b.text(), 28, true, "0A6C8A", 120));
                case BULLET -> body.append(paragraph("•  " + b.text(), 22, false, null, 60));
                case PARAGRAPH -> body.append(paragraph(b.text(), 22, false, null, 160));
            }
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1300\" w:right=\"1300\" w:bottom=\"1300\" w:left=\"1300\"/></w:sectPr>"
                + "</w:body></w:document>";
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>";
        try (OutputStream os = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(os)) {
            entry(zip, "[Content_Types].xml", contentTypes);
            entry(zip, "_rels/.rels", rels);
            entry(zip, "word/document.xml", document);
        }
    }

    private static String paragraph(String text, int halfPoints, boolean bold, String color, int spaceAfter) {
        return "<w:p><w:pPr><w:spacing w:after=\"" + spaceAfter + "\" w:line=\"300\" w:lineRule=\"auto\"/></w:pPr>"
                + "<w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/>" + (bold ? "<w:b/>" : "")
                + (color != null ? "<w:color w:val=\"" + color + "\"/>" : "")
                + "<w:sz w:val=\"" + halfPoints + "\"/></w:rPr><w:t xml:space=\"preserve\">" + esc(text)
                + "</w:t></w:r></w:p>";
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
