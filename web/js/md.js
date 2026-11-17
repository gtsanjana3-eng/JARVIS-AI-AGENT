// Small, safe Markdown renderer for JARVIS replies.
// Everything is HTML-escaped first, so model output can never inject markup.

export function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function inline(s) {
  return s
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/(^|[\s(])\*([^*\s][^*]*)\*/g, "$1<em>$2</em>");
}

function table(lines) {
  const rows = lines.map((l) => l.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim()));
  const head = rows[0];
  const body = rows.slice(2);
  return "<table><thead><tr>" + head.map((c) => `<th>${inline(c)}</th>`).join("") + "</tr></thead><tbody>"
    + body.map((r) => "<tr>" + r.map((c) => `<td>${inline(c)}</td>`).join("") + "</tr>").join("")
    + "</tbody></table>";
}

export function renderMarkdown(text) {
  const src = escapeHtml(text).replace(/\r\n/g, "\n");
  const out = [];
  const lines = src.split("\n");
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (/^```/.test(line)) {
      const code = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i])) code.push(lines[i++]);
      i++;
      out.push(`<pre><code>${code.join("\n")}</code></pre>`);
      continue;
    }
    if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:-]+\|[\s|:-]*$/.test(lines[i + 1])) {
      const t = [];
      while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) t.push(lines[i++]);
      out.push(table(t));
      continue;
    }
    const h = line.match(/^#{1,6}\s+(.*)/);
    if (h) {
      out.push(`<h4>${inline(h[1])}</h4>`);
      i++;
      continue;
    }
    if (/^\s*[-*•]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^\s*[-*•]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*[-*•]\s+/, ""));
      out.push("<ul>" + items.map((x) => `<li>${inline(x)}</li>`).join("") + "</ul>");
      continue;
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      const items = [];
      while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*\d+[.)]\s+/, ""));
      out.push("<ol>" + items.map((x) => `<li>${inline(x)}</li>`).join("") + "</ol>");
      continue;
    }
    if (!line.trim()) {
      i++;
      continue;
    }
    const para = [];
    while (i < lines.length && lines[i].trim() && !/^(```|#{1,6}\s|\s*[-*•]\s|\s*\d+[.)]\s|\s*\|)/.test(lines[i])) {
      para.push(lines[i++]);
    }
    if (!para.length) para.push(lines[i++]);
    out.push(`<p>${inline(para.join("<br>"))}</p>`);
  }
  return out.join("");
}

/** Plain text for speech: no code blocks, tables or Markdown symbols. */
export function speakable(text) {
  let t = String(text ?? "")
    .replace(/```[\s\S]*?```/g, " I've put the code on screen. ")
    .replace(/^\s*\|.*\|\s*$/gm, "")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/[*_#>]/g, "")
    .replace(/^\s*[-•]\s+/gm, "")
    .replace(/https?:\/\/\S+/g, "the link")
    .replace(/\n{2,}/g, ". ")
    .replace(/\s+/g, " ")
    .trim();
  if (t.length > 650) {
    const cut = t.slice(0, 650);
    t = cut.slice(0, Math.max(cut.lastIndexOf(". "), 300) + 1) + " The rest is on screen.";
  }
  return t;
}
