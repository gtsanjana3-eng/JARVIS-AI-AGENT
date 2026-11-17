package com.jarvis.tools.builtin;

import com.jarvis.ai.ChatMessage;
import com.jarvis.ai.LlmException;
import com.jarvis.ai.LlmReply;
import com.jarvis.tools.RiskLevel;
import com.jarvis.tools.Tool;
import com.jarvis.tools.ToolArgs;
import com.jarvis.tools.ToolContext;
import com.jarvis.tools.ToolException;
import com.jarvis.tools.ToolParam;
import com.jarvis.tools.ToolResult;
import com.jarvis.util.DocumentWriter;

import java.nio.file.Path;
import java.util.List;

import static com.jarvis.tools.ToolParam.Type.STRING;

/** Tools that produce documents: a report writer and an AI writer. */
public final class ReportTools {

    private ReportTools() {
    }

    static String slug(String s) {
        String slug = s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "document" : slug.length() > 50 ? slug.substring(0, 50) : slug;
    }

    public static class CreateReport extends Tool {
        public CreateReport() {
            super("create_report", "files",
                    "Save a formatted document (Word .docx, .txt, .md or web page .html) in workspace/reports. "
                            + "Content uses '# Heading', '- bullet' and plain paragraphs.",
                    RiskLevel.SAFE,
                    ToolParam.required("title", STRING, "Document title"),
                    ToolParam.required("content", STRING, "Document body"),
                    ToolParam.choice("format", false, "File type (default docx)", "docx", "txt", "md", "html"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            String format = args.string("format", "docx");
            List<DocumentWriter.Block> blocks = DocumentWriter.parse(args.string("title"), args.string("content"));
            if (blocks.size() < 2) {
                throw new ToolException("The document has no content.");
            }
            Path dir = ctx.workspace().resolve("reports");
            Path file = FileTools.uniqueTarget(dir.resolve(slug(args.string("title")) + "." + format));
            DocumentWriter.write(file, format, blocks);
            String rel = ctx.workspace().display(file);
            return ToolResult.ok("Saved " + rel + " (" + blocks.size() + " sections). PATH=" + rel.substring("workspace/".length()));
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Save \"" + args.string("title") + "\" as ." + args.string("format", "docx");
        }
    }

    public static class WriteWithAi extends Tool {
        public WriteWithAi() {
            super("write_with_ai", "files",
                    "Have the AI write a text (letter, essay, notes, summary, story, speech, application) about a topic.",
                    RiskLevel.SAFE,
                    ToolParam.required("kind", STRING, "What to write, e.g. 'leave letter', 'essay', 'notes'"),
                    ToolParam.required("topic", STRING, "What it is about, with any details the user gave"));
        }

        @Override
        protected ToolResult execute(ToolArgs args, ToolContext ctx) throws Exception {
            if (ctx.llm() == null || !ctx.llm().status().ready()) {
                throw new ToolException("The AI engine is offline, so I can't write text right now.");
            }
            String kind = args.string("kind");
            String topic = args.string("topic");
            List<ChatMessage> msgs = List.of(
                    ChatMessage.system("You are a skilled writer. Write exactly what is asked, ready to use. Use '# ' for the "
                            + "title and '## ' for section headings when the text has sections, '- ' for bullet points. "
                            + "No preamble, no commentary, no Markdown bold. Keep it under 350 words."),
                    ChatMessage.user("Write a " + kind + " about: " + topic));
            try {
                LlmReply r = ctx.llm().chat(msgs, List.of(), null, null);
                String text = r.content().trim();
                if (text.length() < 40) {
                    throw new ToolException("The AI returned too little text.");
                }
                return ToolResult.ok(text);
            } catch (LlmException e) {
                throw new ToolException(e.getMessage());
            }
        }

        @Override
        public String describeCall(ToolArgs args) {
            return "Write a " + args.string("kind") + " about " + args.string("topic");
        }
    }
}
