// Secondary views: Memory (tabs), Activity history, Tool registry, System.
import { api } from "./api.js";
import { $, $$, escapeHtml, fmtDateTime, fmtBytes, fmtDuration, toastText, confirmDialog } from "./util.js";
import { areaChart } from "./charts.js";

const ICON_X = `<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>`;
const ICON_OK = `<svg viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7"/></svg>`;
const ICON_GO = `<svg viewBox="0 0 24 24"><path d="M5 12h13M13 6l6 6-6 6"/></svg>`;

const TAB_INFO = {
  memories: "Long-term facts JARVIS uses in every conversation. Each one was approved by you. Ask “what do you remember about me?” or delete any fact here.",
  notes: "Notes saved by you or by JARVIS (say “take a note…”).",
  tasks: "Your to-do list. Ask “add a task…”, “what’s on my list?” or tick items off here.",
  reminders: "Due reminders pop up and are spoken aloud while JARVIS is open (missed ones fire on the next start). Ask “remind me in 20 minutes to…”.",
  conversations: "Every conversation is stored in SQLite. Open one to continue it.",
};

export class Views {
  constructor({ onOpenConversation, onOpenRun, getCurrentConversation, refreshConversations }) {
    this.onOpenConversation = onOpenConversation;
    this.onOpenRun = onOpenRun;
    this.getCurrentConversation = getCurrentConversation;
    this.refreshConversations = refreshConversations;
    this.tab = "memories";
    this.toolsLoaded = false;
    this.cpu = [];
    this.mem = [];

    $$("#memoryTabs .tab").forEach((b) => b.addEventListener("click", () => this.showTab(b.dataset.tab)));
    $("#recordList").addEventListener("click", (e) => this.onRecordClick(e));
    $("#noteForm").addEventListener("submit", (e) => this.addNote(e));
    $("#runsRefresh").addEventListener("click", () => this.loadRuns());
  }

  // ------------------------------------------------------------------ memory

  showTab(tab) {
    this.tab = tab;
    $$("#memoryTabs .tab").forEach((b) => b.classList.toggle("active", b.dataset.tab === tab));
    $("#memoryIntro").textContent = TAB_INFO[tab];
    $("#noteForm").hidden = tab !== "notes";
    this.loadTab();
  }

  async loadTab() {
    const tab = this.tab;
    const list = $("#recordList");
    let items;
    try {
      items = tab === "conversations" ? await api.conversations() : await api.list(tab);
    } catch (e) {
      list.innerHTML = `<li class="empty">${escapeHtml(e.message)}</li>`;
      return;
    }
    if (tab !== this.tab) return;
    const counter = { memories: "#cntMemories", notes: "#cntNotes", tasks: "#cntTasks", reminders: "#cntReminders", conversations: "#cntConversations" }[tab];
    if (tab === "tasks") items.sort((a, b) => (a.status === b.status ? b.id - a.id : a.status === "OPEN" ? -1 : 1));
    else if (tab === "reminders") items.sort((a, b) => (a.status === "PENDING") === (b.status === "PENDING") ? a.dueAt - b.dueAt : a.status === "PENDING" ? -1 : 1);
    else if (tab !== "conversations") items.reverse();
    $(counter).textContent = tab === "tasks" ? items.filter((t) => t.status === "OPEN").length
      : tab === "reminders" ? items.filter((r) => r.status === "PENDING").length : items.length;
    if (!items.length) {
      list.innerHTML = `<li class="empty"><strong>NOTHING HERE YET</strong>${escapeHtml(TAB_INFO[tab])}</li>`;
      return;
    }
    list.innerHTML = items.map((it) => this.recordHtml(tab, it)).join("");
  }

  recordHtml(tab, it) {
    const id = `<span class="rec-id">#${String(it.id).padStart(3, "0")}</span>`;
    const del = `<button class="icon-btn" data-del="${it.id}" title="Delete" aria-label="Delete">${ICON_X}</button>`;
    switch (tab) {
      case "memories":
        return `<li class="record" data-id="${it.id}">${id}<div><div class="rec-text"><span class="badge">${escapeHtml(it.category)}</span>${escapeHtml(it.content)}</div><span class="rec-meta">approved ${fmtDateTime(it.createdAt)}</span></div><div class="rec-actions">${del}</div></li>`;
      case "notes":
        return `<li class="record" data-id="${it.id}">${id}<div><div class="rec-text">${it.title ? `<b>${escapeHtml(it.title)}</b>\n` : ""}${escapeHtml(it.content)}</div><span class="rec-meta">${fmtDateTime(it.createdAt)}</span></div><div class="rec-actions">${del}</div></li>`;
      case "tasks":
        return `<li class="record ${it.status === "DONE" ? "done" : ""}" data-id="${it.id}">${id}<div><div class="rec-text"><span class="badge ${it.priority}">${it.priority.toLowerCase()}</span>${escapeHtml(it.title)}</div><span class="rec-meta">${it.dueDate ? "due " + escapeHtml(it.dueDate) + " · " : ""}${it.status === "DONE" ? "done " + fmtDateTime(it.completedAt) : "added " + fmtDateTime(it.createdAt)}</span></div><div class="rec-actions">${it.status === "OPEN" ? `<button class="icon-btn ok" data-done="${it.id}" title="Mark done" aria-label="Mark done">${ICON_OK}</button>` : ""}${del}</div></li>`;
      case "reminders":
        return `<li class="record ${it.status !== "PENDING" ? "done" : ""}" data-id="${it.id}">${id}<div><div class="rec-text"><span class="badge ${it.status}">${it.status.toLowerCase()}</span>${escapeHtml(it.text)}</div><span class="rec-meta">${fmtDateTime(it.dueAt)}</span></div><div class="rec-actions">${del}</div></li>`;
      case "conversations": {
        const current = this.getCurrentConversation() === it.id;
        return `<li class="record" data-id="${it.id}">${id}<div><div class="rec-text">${escapeHtml(it.title)}${current ? ' <span class="badge">open</span>' : ""}</div><span class="rec-meta">last active ${fmtDateTime(it.updatedAt)}</span></div><div class="rec-actions"><button class="icon-btn go" data-open="${it.id}" title="Open" aria-label="Open">${ICON_GO}</button>${del}</div></li>`;
      }
    }
    return "";
  }

  async onRecordClick(e) {
    const open = e.target.closest("[data-open]");
    if (open) return this.onOpenConversation(Number(open.dataset.open));
    const done = e.target.closest("[data-done]");
    if (done) {
      await api.completeTask(done.dataset.done).catch((err) => toastText(err.message, { error: true }));
      return this.loadTab();
    }
    const del = e.target.closest("[data-del]");
    if (!del) return;
    const what = { memories: "memory", notes: "note", tasks: "task", reminders: "reminder", conversations: "conversation" }[this.tab];
    if (!(await confirmDialog(`Delete ${what}`, `Permanently delete this ${what} from the database?`, "Delete"))) return;
    const li = del.closest(".record");
    li.classList.add("removing");
    try {
      if (this.tab === "conversations") {
        await api.deleteConversation(del.dataset.del);
        this.refreshConversations(Number(del.dataset.del));
      } else {
        await api.remove(this.tab, del.dataset.del);
      }
      setTimeout(() => this.loadTab(), 250);
    } catch (err) {
      li.classList.remove("removing");
      toastText(err.message, { error: true });
    }
  }

  async addNote(e) {
    e.preventDefault();
    const field = $("#noteInput");
    const content = field.value.trim();
    if (!content) return field.focus();
    try {
      const n = await api.addNote(content);
      field.value = "";
      toastText(`Note #${n.id} stored`);
      this.loadTab();
    } catch (err) {
      toastText(err.message, { error: true });
    }
  }

  updateCounts(counts) {
    if (!counts) return;
    $("#cntMemories").textContent = counts.memories;
    $("#cntNotes").textContent = counts.notes;
    $("#cntTasks").textContent = counts.tasks;
    $("#cntReminders").textContent = counts.reminders;
    $("#cntConversations").textContent = counts.conversations;
  }

  dataChanged(category) {
    const map = { memory: "memories", notes: "notes", tasks: "tasks", reminders: "reminders" };
    if (map[category] === this.tab) this.loadTab();
  }

  // ------------------------------------------------------------------ activity history

  async loadRuns() {
    const box = $("#runs");
    let runs;
    try {
      runs = await api.runs(40);
    } catch (e) {
      box.innerHTML = `<div class="empty">${escapeHtml(e.message)}</div>`;
      return;
    }
    if (!runs.length) {
      box.innerHTML = `<div class="empty"><strong>NO RUNS YET</strong>Ask JARVIS something — every request is recorded here.</div>`;
      return;
    }
    box.innerHTML = runs.map(({ run, steps }) => {
      const tools = steps.filter((s) => s.kind === "TOOL");
      const secs = run.finishedAt ? ((run.finishedAt - run.createdAt) / 1000).toFixed(1) + "s" : "";
      return `<article class="panel run-card">
        <div class="run-top"><span class="status ${run.status}">${run.status.toLowerCase().replace("_", " ")}</span>
          <span class="req">${escapeHtml(run.request)}</span><time>${fmtDateTime(run.createdAt)} ${secs ? "· " + secs : ""}</time>
          <button class="btn btn-ghost btn-sm" data-run="${run.id}">Inspect</button></div>
        <div class="run-body">
          <div>${run.plan ? `<div class="act-intent">${escapeHtml(run.plan.intent || "")}<span class="act-mode">${escapeHtml((run.plan.mode || "").toLowerCase())}</span></div>
            ${run.plan.steps?.length ? `<ol class="act-steps">${run.plan.steps.map((s) => `<li>${escapeHtml(s)}</li>`).join("")}</ol>` : ""}` : `<span class="rec-meta">${run.status === "OFFLINE" ? "offline mode (no AI plan)" : "no plan recorded"}</span>`}</div>
          <div class="tool-chips">${tools.length ? tools.map((t) => `<span class="tool-chip" title="${escapeHtml(t.output || "")}">${t.status === "SUCCESS" ? "✓" : t.status === "DENIED" ? "×" : "!"} ${escapeHtml(t.tool)}</span>`).join("") : '<span class="rec-meta">no tools used</span>'}</div>
        </div>
        ${run.result ? `<div class="run-result">${escapeHtml(run.result.length > 280 ? run.result.slice(0, 280) + "…" : run.result)}</div>` : ""}
        ${run.error ? `<div class="run-result" style="color:var(--danger)">${escapeHtml(run.error)}</div>` : ""}
      </article>`;
    }).join("");
    box.querySelectorAll("[data-run]").forEach((b) => b.addEventListener("click", () => {
      const entry = runs.find((r) => r.run.id === Number(b.dataset.run));
      this.onOpenRun(entry.run, entry.steps);
    }));
  }

  // ------------------------------------------------------------------ tools

  async loadTools() {
    if (this.toolsLoaded) return;
    const tools = await api.tools();
    this.toolsLoaded = true;
    $("#toolGrid").innerHTML = tools.map((t, i) => `
      <article class="panel tool" style="animation-delay:${i * 25}ms">
        <div class="tool-top"><span class="tool-cat">${escapeHtml(t.category)}</span><span class="risk ${t.risk}" title="${escapeHtml(t.riskDescription)}">${t.risk.toLowerCase().replace("_", " ")}</span></div>
        <h3>${escapeHtml(t.name)}</h3>
        <code class="cls">class <b>${escapeHtml(t.className)}</b> extends Tool</code>
        <p>${escapeHtml(t.description)}</p>
        <div class="params">${t.params.length ? t.params.map((p) => `<span class="param ${p.required ? "req" : ""}" title="${escapeHtml(p.description)}">${escapeHtml(p.name)}: ${p.type}${p.required ? "" : "?"}</span>`).join("") : '<span class="param">no input</span>'}</div>
        ${t.requiresApproval ? '<span class="approval-note">Requires your approval</span>' : ""}
      </article>`).join("");
  }

  // ------------------------------------------------------------------ system

  async loadDatabase() {
    try {
      const db = await api.database();
      $("#dbEngine").textContent = db.engine;
      $("#dbTables").innerHTML = db.tables.map((t) => `<tr><td>${escapeHtml(t.name)}</td><td>${t.rows}</td></tr>`).join("");
      $("#dbPath").textContent = `${db.file} · ${fmtBytes(db.bytes)}`;
    } catch { /* shown as offline elsewhere */ }
  }

  renderEngine(llm) {
    if (!llm) return;
    $("#engineState").textContent = llm.ready ? "ready" : llm.online ? "model missing" : "offline";
    const facts = `
      <div class="engine-grid">
        <div><span>Provider</span><strong>${escapeHtml(llm.provider)} (local, free)</strong></div>
        <div><span>Model</span><strong>${escapeHtml(llm.model)}</strong></div>
        <div><span>Status</span><strong style="color:${llm.ready ? "var(--ok)" : "var(--amber)"}">${escapeHtml(llm.detail)}</strong></div>
        <div><span>Installed models</span><strong>${llm.installedModels.length ? escapeHtml(llm.installedModels.join(", ")) : "—"}</strong></div>
      </div>`;
    let help = "";
    if (!llm.online) {
      help = `<ol class="setup">
        <li>Download and install <b>Ollama</b> (free) from <code>https://ollama.com/download</code>.</li>
        <li>Open a terminal and download the model: <code>ollama pull ${escapeHtml(llm.model)}</code> (about 2 GB).</li>
        <li>Keep Ollama running (it sits in the system tray). JARVIS connects automatically — no restart needed.</li>
      </ol>`;
    } else if (!llm.modelInstalled) {
      help = `<ol class="setup">
        <li>Open a terminal and run: <code>ollama pull ${escapeHtml(llm.model)}</code></li>
        <li>Or set <code>ollama.model</code> in <code>config/jarvis.properties</code> to an installed model and restart run.bat.</li>
      </ol>`;
    } else {
      help = `<p class="memory-intro">The reasoning engine runs entirely on this computer. To change the model, edit <code>ollama.model</code> in <code>config/jarvis.properties</code>.</p>`;
    }
    $("#engineBody").innerHTML = facts + help;
  }

  renderSystem(s) {
    this.cpu.push(s.cpu);
    this.mem.push(s.memory);
    if (this.cpu.length > 80) { this.cpu.shift(); this.mem.shift(); }
    $("#diagCpu").textContent = `${s.cpu.toFixed(1)}%`;
    $("#diagMem").textContent = `${s.memory.toFixed(1)}%`;
    areaChart($("#cpuChart"), this.cpu, "0, 229, 255");
    areaChart($("#memChart"), this.mem, "22, 139, 255");
    $("#sysInfo").innerHTML = [
      ["Operating system", s.os],
      ["Java runtime", s.java],
      ["Logical cores", s.cores],
      ["System memory", `${fmtBytes(s.memoryUsed)} / ${fmtBytes(s.memoryTotal)}`],
      ["JVM heap", `${fmtBytes(s.jvmUsed)} / ${fmtBytes(s.jvmMax)}`],
      ["JARVIS uptime", fmtDuration(s.uptime)],
      ["Registered tools", s.tools],
      ["Agent runs recorded", s.counts.runs],
    ].map(([k, v]) => `<div><dt>${k}</dt><dd>${escapeHtml(String(v))}</dd></div>`).join("");
  }
}
