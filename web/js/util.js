// Shared UI helpers.
export { escapeHtml } from "./md.js";
import { escapeHtml } from "./md.js";

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export const fmtTime = (ms) => new Date(ms).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
export const fmtDateTime = (ms) => new Date(ms).toLocaleString([], { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });

export function fmtBytes(b) {
  if (b >= 1024 ** 3) return (b / 1024 ** 3).toFixed(1) + " GB";
  if (b >= 1024 ** 2) return (b / 1024 ** 2).toFixed(1) + " MB";
  if (b >= 1024) return (b / 1024).toFixed(1) + " KB";
  return b + " B";
}

export function fmtDuration(ms) {
  const s = Math.floor(ms / 1000);
  const p = (n) => String(n).padStart(2, "0");
  return `${p(Math.floor(s / 3600))}:${p(Math.floor(s / 60) % 60)}:${p(s % 60)}`;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function storage(key, value) {
  try {
    if (value === undefined) return localStorage.getItem(key);
    localStorage.setItem(key, value);
  } catch { /* storage unavailable: the preference just isn't remembered */ }
  return null;
}

/** Shows a toast. `html` must already be escaped. */
export function toast(html, { error = false, cls = "", ms = 4200 } = {}) {
  const t = document.createElement("div");
  t.className = "toast" + (error ? " err" : "") + (cls ? " " + cls : "");
  t.innerHTML = html;
  $("#toasts").appendChild(t);
  setTimeout(() => {
    t.classList.add("out");
    setTimeout(() => t.remove(), 320);
  }, ms);
  return t;
}

export const toastText = (text, opts) => toast(escapeHtml(text), opts);

export function confirmDialog(title, text, okLabel = "Confirm") {
  const modal = $("#modal");
  $("#modalTitle").textContent = title;
  $("#modalText").textContent = text;
  $("#modalOk").textContent = okLabel;
  modal.hidden = false;
  $("#modalCancel").focus();
  return new Promise((resolve) => {
    const close = (v) => {
      modal.hidden = true;
      $("#modalOk").onclick = $("#modalCancel").onclick = modal.onclick = null;
      document.removeEventListener("keydown", onKey, true);
      resolve(v);
    };
    const onKey = (e) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        close(false);
      }
    };
    $("#modalOk").onclick = () => close(true);
    $("#modalCancel").onclick = () => close(false);
    modal.onclick = (e) => e.target === modal && close(false);
    document.addEventListener("keydown", onKey, true);
  });
}

/** An approval card with Approve / Deny buttons. Several copies (chat + activity) stay in sync. */
export function approvalCard(approval, riskDescription, onDecide) {
  const el = document.createElement("div");
  el.className = "approval";
  el.dataset.approvalId = approval.id;
  el.innerHTML = `
    <h5>Approval needed · ${escapeHtml(approval.risk.toLowerCase())}</h5>
    <p>${escapeHtml(approval.summary)}</p>
    <small>${escapeHtml((riskDescription || "This action needs your permission").replace(/\.?$/, "."))} You can also say “yes” or “no”.</small>
    <div class="row">
      <button class="btn btn-ok btn-sm" data-act="approve">Approve</button>
      <button class="btn btn-ghost btn-sm btn-danger" data-act="deny">Deny</button>
    </div>`;
  el.addEventListener("click", (e) => {
    const b = e.target.closest("[data-act]");
    if (b) onDecide(approval.id, b.dataset.act === "approve");
  });
  return el;
}

export function markApprovalDecided(approval) {
  const label = { APPROVED: "Approved", DENIED: "Denied", EXPIRED: "Cancelled" }[approval.status] || approval.status;
  $$(`.approval[data-approval-id="${approval.id}"]`).forEach((el) => {
    el.classList.add("decided");
    el.querySelector("h5").textContent = label;
    el.querySelector(".row")?.remove();
    el.querySelector("small")?.remove();
  });
}
