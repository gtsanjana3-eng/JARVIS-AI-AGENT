// Live "Agent Activity" panel: shows the real pipeline stage, the plan,
// each tool call with its input/output, approvals, and the final status.
import { $, $$, escapeHtml, fmtTime, approvalCard } from "./util.js";

const STAGES = ["UNDERSTANDING", "PLANNING", "SELECTING_TOOLS", "EXECUTING", "AWAITING_APPROVAL", "DONE"];

function shortArgs(input) {
  if (!input) return "";
  try {
    const o = JSON.parse(input);
    const parts = Object.entries(o).map(([k, v]) => `${k}=${typeof v === "string" ? JSON.stringify(v.length > 40 ? v.slice(0, 40) + "…" : v) : v}`);
    return parts.join(" ");
  } catch {
    return input.slice(0, 60);
  }
}

function prettyJson(s) {
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch {
    return s ?? "";
  }
}

export class ActivityPanel {
  constructor({ onDecide, onOpenRun }) {
    this.box = $("#activity");
    this.pipeline = $("#pipeline");
    this.runLabel = $("#activityRun");
    this.onDecide = onDecide;
    this.onOpenRun = onOpenRun;
    this.runId = null;
    this.visited = new Set();
  }

  /** Starts showing a new run. */
  reset(run) {
    this.runId = run.id;
    this.visited.clear();
    this.runLabel.textContent = `run #${run.id}`;
    this.box.innerHTML = `
      <div class="act-request"><small>REQUEST · ${fmtTime(run.createdAt)}</small>${escapeHtml(run.request)}</div>
      <div class="act-block" data-part="plan" hidden><h6>Plan</h6><div></div></div>
      <div class="act-block" data-part="tools" hidden><h6>Tools selected</h6><div class="tool-chips"></div></div>
      <div class="act-block" data-part="steps"><h6>Execution</h6><ol class="steps"></ol></div>
      <div data-part="approval"></div>
      <div data-part="result"></div>`;
    this.renderPipeline(null);
  }

  /** Highlights the pipeline according to the real backend state. */
  setState(state, runId) {
    if (runId != null && runId !== this.runId) return;
    const idx = { COMPLETED: 5, FAILED: 5 }[state] ?? STAGES.indexOf(state);
    if (idx < 0) {
      if (state === "IDLE" || state === "LISTENING" || state === "SPEAKING") this.renderPipeline(null, true);
      return;
    }
    this.visited.add(STAGES[idx]);
    this.renderPipeline(STAGES[idx], false, state === "FAILED");
  }

  renderPipeline(active, keep = false, failed = false) {
    $$("li", this.pipeline).forEach((li) => {
      const stage = li.dataset.stage;
      li.classList.toggle("active", stage === active && !failed);
      li.classList.toggle("failed", failed && stage === "DONE");
      li.classList.toggle("done", (this.visited.has(stage) && stage !== active) || (stage === "DONE" && active === "DONE" && !failed));
      if (!keep && !active) li.classList.remove("done", "active", "failed");
    });
  }

  part(name) {
    return this.box.querySelector(`[data-part="${name}"]`);
  }

  showPlan(runId, plan) {
    if (runId !== this.runId || !this.part("plan")) return;
    const p = this.part("plan");
    p.hidden = false;
    const mode = { ANSWER: "direct answer", CLARIFY: "ask a question", USE_TOOLS: "use tools" }[plan.mode] || plan.mode;
    p.querySelector("div").innerHTML = `
      <div class="act-intent">${escapeHtml(plan.intent || "—")}<span class="act-mode">${mode}</span></div>
      ${plan.steps?.length ? `<ol class="act-steps">${plan.steps.map((s) => `<li>${escapeHtml(s)}</li>`).join("")}</ol>` : ""}`;
  }

  showTools(runId, names) {
    if (runId !== this.runId || !this.part("tools")) return;
    const t = this.part("tools");
    t.hidden = false;
    t.querySelector(".tool-chips").innerHTML = names.map((n) => `<span class="tool-chip">${escapeHtml(n)}</span>`).join("");
  }

  upsertStep(step) {
    if (step.runId !== this.runId || !this.part("steps")) return;
    const list = this.part("steps").querySelector(".steps");
    let el = list.querySelector(`[data-step="${step.id}"]`);
    if (!el) {
      el = document.createElement("li");
      el.dataset.step = step.id;
      list.appendChild(el);
    }
    const title = step.kind === "TOOL" ? `${step.tool} ${shortArgs(step.input)}`
      : step.kind === "PLAN" ? "planner — build plan"
      : step.kind === "OFFLINE" ? "offline skill engine"
      : "error";
    const wasOpen = el.querySelector("details")?.open;
    el.innerHTML = `
      <details class="step ${step.status}" ${wasOpen || step.status === "ERROR" ? "open" : ""}>
        <summary><span class="st-icon"></span><span class="st-name" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
          <span class="st-time">${step.durationMs != null ? (step.durationMs / 1000).toFixed(1) + "s" : step.status === "AWAITING_APPROVAL" ? "waiting" : "…"}</span></summary>
        <pre>${step.input ? `<b>input</b>\n${escapeHtml(prettyJson(step.input))}\n` : ""}${step.output != null ? `<b>${step.status === "SUCCESS" ? "result" : "outcome"}</b>\n${escapeHtml(step.kind === "PLAN" ? prettyJson(step.output) : step.output)}` : "<b>running…</b>"}</pre>
      </details>`;
    this.box.scrollTop = this.box.scrollHeight;
  }

  showApproval(approval, riskDescription) {
    if (approval.runId !== this.runId || !this.part("approval")) return;
    const slot = this.part("approval");
    slot.innerHTML = "";
    slot.appendChild(approvalCard(approval, riskDescription, this.onDecide));
    this.box.scrollTop = this.box.scrollHeight;
  }

  finish(run, problems = []) {
    if (run.id !== this.runId || !this.part("result")) return;
    const label = { COMPLETED: "Completed", FAILED: "Failed", CANCELLED: "Stopped", OFFLINE: "Answered in offline mode" }[run.status] || run.status;
    const secs = run.finishedAt ? ((run.finishedAt - run.createdAt) / 1000).toFixed(1) + "s" : "";
    this.part("result").innerHTML = `<div class="act-result ${run.status}">${label} · ${secs}${problems.length ? " · " + escapeHtml(problems.join("; ")) : ""}${run.status === "FAILED" && run.error ? "<br>" + escapeHtml(run.error) : ""}</div>`;
    this.runLabel.textContent = `run #${run.id} · ${label.toLowerCase()}`;
    this.visited.add("DONE");
    this.renderPipeline("DONE", true, run.status === "FAILED");
    this.box.scrollTop = this.box.scrollHeight;
  }

  /** Shows a past run (from the history) in the panel. */
  loadRun(run, steps) {
    this.reset(run);
    if (run.plan) {
      this.showPlan(run.id, run.plan);
    }
    steps.forEach((s) => this.upsertStep(s));
    if (run.status !== "RUNNING" && run.status !== "AWAITING_APPROVAL") {
      ["UNDERSTANDING", "PLANNING"].forEach((s) => this.visited.add(s));
      if (steps.some((s) => s.kind === "TOOL")) ["SELECTING_TOOLS", "EXECUTING"].forEach((s) => this.visited.add(s));
      this.finish(run);
    }
  }
}
