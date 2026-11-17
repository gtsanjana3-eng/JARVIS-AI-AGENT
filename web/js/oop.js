// OOP page: how JARVIS uses the four OOP ideas, shown with pictures,
// filled with LIVE facts read from the running Java program (no code shown).
import { api } from "./api.js";
import { $, escapeHtml, toastText } from "./util.js";

/** "UtilityTools.Calculator" → "Calculator", "AgentRunRepository" → "Agent Run Repository". */
const friendly = (n) => n.split(".").pop().replace(/([a-z])([A-Z])/g, "$1 $2");

const FAMILY_INFO = {
  Tool: "every ability JARVIS has",
  Entity: "every record saved in the database",
  SqliteRepository: "the database helpers",
  Workflow: "multi-step jobs",
  SearchProvider: "web search engines",
  LlmClient: "the AI brain",
  Skill: "offline answers (no AI needed)",
};

const LOCKED = {
  name: ["🏷️", "Its name"],
  category: ["🗂️", "Its group"],
  description: ["📝", "What it does"],
  risk: ["⚠️", "Its safety level"],
  params: ["📥", "Inputs it needs"],
};

const ABILITY_ICONS = ["🧮", "🕒", "📝", "⏰", "✅", "🧠", "🌦️", "📰", "🏏", "📂", "🚀", "🔎"];

export class OopView {
  constructor() {
    this.loaded = false;
    this.data = null;
    $("#oopConcepts").addEventListener("click", (e) => {
      const b = e.target.closest("[data-demo]");
      if (b) this.runDemo(b.dataset.demo, b);
    });
    // The concept chips at the top jump to their card.
    document.querySelectorAll('.oop-tags a[href^="#oop-"]').forEach((a) => a.addEventListener("click", (e) => {
      e.preventDefault();
      document.querySelector(a.getAttribute("href"))?.scrollIntoView({ behavior: "smooth", block: "start" });
    }));
  }

  async load() {
    if (this.loaded) return;
    try {
      this.data = await api.oop();
      this.loaded = true;
      this.render();
    } catch (e) {
      toastText(e.message, { error: true });
    }
  }

  render() {
    const d = this.data;
    const s = d.stats;
    $("#oopStats").innerHTML = [
      [s.classes, "building blocks", "classes"],
      [s.abstractClasses, "blueprints", "abstract classes"],
      [s.interfaces, "contracts", "interfaces"],
      [s.subclasses, "children", "inherit from a parent"],
      [pctText(s), "data kept private", "locked inside objects"],
    ].map(([v, k, t]) => `<div class="oop-stat"><strong>${v}</strong><span>${k}</span><em>${t}</em></div>`).join("");

    $("#oopConcepts").innerHTML = [this.encapsulation(d), this.inheritance(d), this.polymorphism(), this.abstraction(d)].join("");

    $("#oopTree").innerHTML = d.families.map((f) => `
      <div class="oop-family">
        <div class="fam-root">👑 ${escapeHtml(friendly(f.name))}<small>${escapeHtml(FAMILY_INFO[f.name] || "")}</small><em>${f.children.length} ${f.children.length === 1 ? "child" : "children"}</em></div>
        <ul>${f.children.map((k) => `<li>${escapeHtml(friendly(k.name))}${k.children.length ? `<ul>${k.children.map((g) => `<li>${escapeHtml(friendly(g))}</li>`).join("")}</ul>` : ""}</li>`).join("")}</ul>
      </div>`).join("");
  }

  card(id, no, title, picture, jarvis, extra = "") {
    return `
      <article class="panel oop-card" id="oop-${id}">
        <header><span class="oop-no">${no}</span><h3>${title}</h3></header>
        <p class="oop-jarvis"><b>How JARVIS uses it:</b> ${jarvis}</p>
        <div class="oop-pic">${picture}</div>
        ${extra}
      </article>`;
  }

  // ------------------------------------------------------------------ 01 encapsulation

  encapsulation(d) {
    const locked = d.encapsulation.fields.map((f) => f.split(" ").pop())
      .map((n) => LOCKED[n] || ["🔒", n]).map(([i, t]) => `<div class="cap-item"><span>${i}</span>${t}<b>🔒</b></div>`).join("");
    const picture = `
      <div class="cap-diagram">
        <div class="cap-outside">
          <div class="actor">🧑<span>You</span></div>
          <div class="actor">🤖<span>JARVIS agent</span></div>
        </div>
        <div class="cap-arrows">
          <div class="cap-arrow ok">→ <span>“Do your job”</span></div>
          <div class="cap-arrow ok">→ <span>“What's your name?”</span></div>
          <div class="cap-arrow no">✕ <span>Change the inside directly</span></div>
        </div>
        <div class="capsule">
          <div class="cap-title">💊 A JARVIS tool (the capsule)</div>
          ${locked}
          <div class="cap-guard">🛡️ Safety check runs before every job</div>
        </div>
      </div>`;
    return this.card("encapsulation", "01", "Encapsulation",
      picture,
      `Every ability in JARVIS (calculator, notes, reminders, weather…) is a sealed capsule. Its name, safety level and the inputs it needs are <b>locked inside</b>. When you give a command, the agent can only ask the tool to do its job. The tool <b>checks the request itself</b> before doing anything, so bad input or a crash can never break JARVIS. Right now <b>${pctText(d.stats)}</b> of all data in JARVIS is kept private like this.`,
      `<div class="oop-live">
        <div class="oop-live-head"><span class="live-dot"></span>Live test on the real program</div>
        <p class="oop-note">Press the button and JARVIS will really try to misuse one of its own tools in 4 ways.</p>
        <button class="btn oop-demo" data-demo="encapsulation">Try to break a tool</button>
        <div class="oop-demo-out" id="demo-encapsulation"></div>
      </div>`);
  }

  // ------------------------------------------------------------------ 02 inheritance

  inheritance(d) {
    const tool = d.families.find((f) => f.name === "Tool");
    const kids = tool ? tool.children : [];
    const shown = kids.slice(0, 12).map((k, i) => `<div class="kid"><span>${ABILITY_ICONS[i % ABILITY_ICONS.length]}</span>${escapeHtml(friendly(k.name))}</div>`).join("");
    const picture = `
      <div class="inh-diagram">
        <div class="inh-parent">
          <div class="inh-title">👑 Parent: “Tool”</div>
          <div class="inh-gives">Gives every child:
            <span>🏷️ a name</span><span>⚠️ a safety level</span><span>🛡️ input checking</span><span>✋ “ask before risky actions”</span></div>
        </div>
        <div class="inh-lines"><i></i></div>
        <div class="inh-kids">${shown}${kids.length > 12 ? `<div class="kid more">+ ${kids.length - 12} more</div>` : ""}</div>
        <div class="inh-own">Each child only adds <b>its own skill</b>: the calculator adds maths, the weather tool adds forecasts…</div>
      </div>`;
    const others = d.families.filter((f) => f.name !== "Tool" && f.children.length)
      .map((f) => `<span class="fam-chip"><b>${f.children.length}</b> ${escapeHtml(FAMILY_INFO[f.name] || friendly(f.name))}</span>`).join("");
    return this.card("inheritance", "02", "Inheritance",
      picture,
      `JARVIS has one parent called <b>Tool</b>, and all <b>${kids.length} of its abilities are its children</b>. The parent holds what every ability needs: a name, a safety level, input checking and the “ask before risky actions” rule. These are written once, and all ${kids.length} children get them automatically. Each child only adds its own job. That's why a new ability can be added to JARVIS without rewriting the safety rules.`,
      `<div class="oop-live">
        <div class="oop-live-head"><span class="live-dot"></span>Other families found in the running program</div>
        <div class="fam-chips">${others}</div>
      </div>`);
  }

  // ------------------------------------------------------------------ 03 polymorphism

  polymorphism() {
    const box = (id, ico, name) => `
      <div class="poly-box" id="poly-${id}">
        <div class="poly-name"><span>${ico}</span>${name}</div>
        <div class="poly-result">waiting…</div>
      </div>`;
    const picture = `
      <div class="poly-diagram">
        <div class="poly-cmd">📣<b>Same command</b><span>“Do your job!”</span></div>
        <div class="poly-fan"><i></i><i></i><i></i></div>
        <div class="poly-boxes">
          ${box("calculator", "🧮", "Calculator")}
          ${box("get_datetime", "🕒", "Clock")}
          ${box("system_status", "💻", "System monitor")}
        </div>
      </div>`;
    return this.card("polymorphism", "03", "Polymorphism",
      picture,
      "When you speak to JARVIS, the agent picks the abilities it needs and gives <b>every one of them the very same command</b>: “do your job”. It never needs to know which ability it's talking to. The calculator does maths, the clock tells the time and the system monitor reads the CPU. Same command, different behaviour. That's how one agent can control all of JARVIS's abilities.",
      `<div class="oop-live">
        <div class="oop-live-head"><span class="live-dot"></span>Live test on the real program</div>
        <p class="oop-note">Press the button. JARVIS sends the same command to 3 different abilities, and the boxes above fill with their real answers.</p>
        <button class="btn oop-demo" data-demo="polymorphism">Send the same command to 3 tools</button>
      </div>`);
  }

  // ------------------------------------------------------------------ 04 abstraction

  abstraction(d) {
    const search = d.families.find((f) => f.name === "SearchProvider");
    const engines = search ? search.children.flatMap((c) => (c.children.length ? c.children : [c.name])) : [];
    const picture = `
      <div class="abs-diagram">
        <div class="abs-layer seen">
          <div class="abs-label">👀 What you see</div>
          <div class="abs-items"><span>🗣️ “Search for the Taj Mahal”</span><span>🧠 “Think about it”</span><span>💾 “Save this note”</span></div>
        </div>
        <div class="abs-curtain">hidden behind the curtain</div>
        <div class="abs-layer hidden">
          <div class="abs-label">⚙️ How it's really done (hidden)</div>
          <div class="abs-items">
            <span>🔎 ${engines.length ? engines.map((e) => escapeHtml(friendly(e).replace(" Search", ""))).join(" / ") : "search engine"}</span>
            <span>🦙 Ollama AI on this PC</span>
            <span>🗄️ SQLite database</span>
          </div>
        </div>
      </div>`;
    const blueprints = d.abstraction.slice(0, 6).map((a) => `<span class="fam-chip"><b>${escapeHtml(friendly(a.name))}</b> ${a.kind === "interface" ? "contract" : "blueprint"} → ${a.implementations} follow it</span>`).join("");
    return this.card("abstraction", "04", "Abstraction",
      picture,
      `Each part of JARVIS only knows <b>what</b> another part can do, not <b>how</b> it does it. The agent just says “search”, “think” or “save”. Which search engine answers, how the Ollama AI runs and how the SQLite database stores data all stay hidden. That's why JARVIS's search engine can be switched between ${engines.length || "different"} options without changing anything else.`,
      `<div class="oop-live">
        <div class="oop-live-head"><span class="live-dot"></span>Blueprints found in the running program</div>
        <div class="fam-chips">${blueprints}</div>
      </div>`);
  }

  // ------------------------------------------------------------------ live demos

  async runDemo(id, button) {
    button.disabled = true;
    try {
      if (id === "polymorphism") {
        document.querySelectorAll(".poly-result").forEach((r) => { r.textContent = "working…"; });
        const r = await api.oopPolymorphism();
        r.results.forEach((x, i) => {
          const el = document.querySelector(`#poly-${x.tool} .poly-result`);
          if (!el) return;
          setTimeout(() => {
            el.textContent = x.output.length > 90 ? x.output.slice(0, 90) + "…" : x.output;
            el.classList.toggle("bad", !x.success);
            el.parentElement.classList.add("lit");
          }, i * 350);
        });
      } else {
        const out = $("#demo-encapsulation");
        out.innerHTML = `<div class="oop-note">Trying…</div>`;
        const r = await api.oopEncapsulation();
        out.innerHTML = r.attempts.map((a, i) => `
          <div class="enc-row ${a.blocked ? "blocked" : "allowed"}" style="animation-delay:${i * 180}ms">
            <span class="enc-badge">${a.blocked ? "🛡️ BLOCKED" : "ALLOWED"}</span>
            <div><div class="enc-try">${escapeHtml(a.attempt)}</div><div class="enc-why" title="${escapeHtml(a.result)}">${escapeHtml(a.why)}</div></div>
          </div>`).join("") + `<p class="oop-note">✅ After all 4 attempts the calculator is still called “${escapeHtml(r.nameStillIs)}” and still works.</p>`;
      }
    } catch (e) {
      toastText(e.message, { error: true });
    } finally {
      button.disabled = false;
    }
  }
}

function pctText(s) {
  return s.fields ? `${Math.round((s.privateFields / s.fields) * 100)}%` : "most";
}
