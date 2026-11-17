import { api, connectEvents } from "./api.js";
import { Reactor, Particles, STATE_LABELS } from "./reactor.js";
import { VoiceInput, VoiceOutput } from "./voice.js";
import { renderMarkdown, speakable } from "./md.js";
import { ActivityPanel } from "./activity.js";
import { Views } from "./views.js";
import { OopView } from "./oop.js";
import {
  $, $$, escapeHtml, fmtTime, sleep, storage, toast, toastText, confirmDialog, approvalCard, markApprovalDecided,
} from "./util.js";

const VIEW_TITLES = { core: "Core Interface", chat: "Conversation", memory: "Memory Bank", activity: "Agent Activity", tools: "Tool Registry", oop: "OOP Concepts", system: "System" };
const DEFAULT_DETAIL = {
  IDLE: "Awaiting your command", LISTENING: "Listening…", SPEAKING: "Speaking", COMPLETED: "Done",
  FAILED: "Something went wrong", AWAITING_APPROVAL: "Waiting for your decision",
};

const ui = {
  state: "IDLE",
  view: "core",
  conversationId: null,
  conversations: [],
  adoptNextConversation: false,
  runId: null,
  busy: false,
  pendingApproval: null,
  rendered: new Set(),
  online: false,
  llm: null,
  convMode: false,
  noSpeech: 0,
  interrupted: false,
  lastStatus: null,
};

// ================================================================== visuals

const reactor = new Reactor({ root: $("#reactor"), svg: $("#reactorSvg"), wave: $("#waveform"), label: $("#reactorState") });
new Particles($("#particles"), () => 0.6 + reactor.mult * 0.4);

/** Applies a state reported by the Java backend. */
function applyState(state, detail = "", runId = null) {
  ui.state = state;
  document.body.dataset.state = state;
  $("#statePillText").textContent = STATE_LABELS[state] || state;
  $("#stateDetail").textContent = detail || DEFAULT_DETAIL[state] || "";
  reactor.setState(state);
  activity.setState(state, runId);
  const live = $("#chat .msg.pending .live-detail");
  if (live) live.textContent = detail || STATE_LABELS[state] || "";
}

/** Reports a voice state (LISTENING / SPEAKING / IDLE) to the backend, which broadcasts it. */
function reportVoiceState(state) {
  api.setState(state).catch(() => {});
}

// ================================================================== activity + views

const activity = new ActivityPanel({ onDecide: decide });
const views = new Views({
  onOpenConversation: (id) => { showView("chat"); loadConversation(id); },
  onOpenRun: (run, steps) => { showView("activity"); activity.loadRun(run, steps); },
  getCurrentConversation: () => ui.conversationId,
  refreshConversations: (deletedId) => {
    if (deletedId === ui.conversationId) newConversation();
    loadConversations();
  },
});

const oopView = new OopView();

function showView(name) {
  if (!VIEW_TITLES[name]) return;
  ui.view = name;
  $$(".nav-item").forEach((b) => b.classList.toggle("active", b.dataset.view === name));
  $$(".view").forEach((v) => v.classList.toggle("active", v.dataset.view === name));
  $("#viewTitle").textContent = VIEW_TITLES[name];
  if (name === "memory") views.showTab(views.tab);
  if (name === "activity") views.loadRuns();
  if (name === "tools") views.loadTools().catch((e) => toastText(e.message, { error: true }));
  if (name === "oop") oopView.load();
  if (name === "system") { views.loadDatabase(); views.renderEngine(ui.llm); }
}
$$(".nav-item").forEach((b) => b.addEventListener("click", () => showView(b.dataset.view)));
document.addEventListener("click", (e) => {
  const go = e.target.closest("[data-goto]");
  if (go) showView(go.dataset.goto);
});

// ================================================================== chat

const chat = $("#chat");
const input = $("#input");

function chatEmpty() {
  chat.innerHTML = `<div class="empty"><strong>HOW CAN I HELP?</strong>Talk or type naturally — I'll plan the steps and use my tools. For example:
    <ul><li>“I have an exam next week.”</li><li>“Research the history of Java and save a summary to my notes.”</li>
    <li>“Remind me in 30 minutes to stretch.”</li><li>“Explain recursion with a Java example.”</li>
    <li>“Organise the files in my workspace.”</li></ul></div>`;
}

function addMessage(m, meta = {}) {
  if (m.id && ui.rendered.has(m.id)) return null;
  if (m.id) ui.rendered.add(m.id);
  chat.querySelector(".empty")?.remove();
  const isUser = m.role === "USER";
  const el = document.createElement("div");
  el.className = `msg ${isUser ? "user" : "jarvis"}${meta.status === "FAILED" ? " failed" : ""}`;
  const tags = [];
  if (meta.status === "OFFLINE") tags.push('<span class="msg-tag warn">offline mode</span>');
  if (meta.status === "FAILED") tags.push('<span class="msg-tag err">failed</span>');
  if (meta.status === "CANCELLED") tags.push('<span class="msg-tag warn">stopped</span>');
  if (meta.problems?.length) tags.push(`<span class="msg-tag warn" title="${escapeHtml(meta.problems.join("; "))}">${meta.problems.length} issue${meta.problems.length > 1 ? "s" : ""}</span>`);
  if (!isUser && m.runId) tags.push(`<button class="msg-link" data-run="${m.runId}">steps</button>`);
  el.innerHTML = `<div class="msg-meta"><b>${isUser ? "YOU" : "JARVIS"}</b><span>${fmtTime(m.createdAt || Date.now())}</span>${tags.join("")}</div>
    <div class="msg-body ${isUser ? "" : "md"}"></div>`;
  const body = $(".msg-body", el);
  if (isUser) body.textContent = m.text;
  else body.innerHTML = renderMarkdown(m.text);
  const pending = chat.querySelector(".msg.pending");
  chat.insertBefore(el, isUser ? null : pending);
  if (!isUser && meta.animate) {
    body.style.opacity = 0;
    body.animate([{ opacity: 0, transform: "translateY(4px)" }, { opacity: 1, transform: "none" }], { duration: 420, fill: "forwards" });
  }
  chat.scrollTop = chat.scrollHeight;
  return el;
}

function showPending(show) {
  chat.querySelector(".msg.pending")?.remove();
  if (!show) return;
  const el = document.createElement("div");
  el.className = "msg jarvis pending";
  el.innerHTML = `<div class="msg-meta"><b>JARVIS</b><span class="live-detail">${escapeHtml(STATE_LABELS[ui.state] || "")}</span></div>
    <div class="msg-body"><span class="thinking-dots"><i></i><i></i><i></i></span></div>`;
  chat.appendChild(el);
  chat.scrollTop = chat.scrollHeight;
}

chat.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-run]");
  if (!b) return;
  const runs = await api.runs(100).catch(() => []);
  const entry = runs.find((r) => r.run.id === Number(b.dataset.run));
  if (entry) { showView("activity"); activity.loadRun(entry.run, entry.steps); }
  else toastText("That run is no longer in the recent history.");
});

function setBusy(busy) {
  ui.busy = busy;
  const send = $("#sendBtn");
  send.classList.toggle("stop", busy);
  send.title = busy ? "Stop JARVIS" : "Send (Enter)";
  $("#newChatBtn").disabled = busy;
  showPending(busy);
}

// ---------------------------------------------------------------- conversations

async function loadConversations() {
  ui.conversations = await api.conversations().catch(() => []);
  renderConversationSelect();
}

function renderConversationSelect() {
  const sel = $("#conversationSelect");
  const opts = ui.conversations.map((c) => `<option value="${c.id}">${escapeHtml(c.title)}</option>`);
  if (ui.conversationId == null) opts.unshift('<option value="" selected>New conversation</option>');
  sel.innerHTML = opts.join("");
  if (ui.conversationId != null) sel.value = String(ui.conversationId);
}

$("#conversationSelect").addEventListener("change", (e) => {
  if (e.target.value) loadConversation(Number(e.target.value));
});

async function loadConversation(id) {
  if (ui.busy) return toastText("Wait for JARVIS to finish, or press Stop.");
  ui.conversationId = id;
  storage("jarvis.conversation", String(id));
  ui.rendered.clear();
  chat.innerHTML = "";
  const msgs = await api.messages(id).catch(() => []);
  if (!msgs.length) chatEmpty();
  msgs.forEach((m) => addMessage(m));
  renderConversationSelect();
}

function newConversation() {
  if (ui.busy) return;
  ui.conversationId = null;
  storage("jarvis.conversation", "");
  ui.rendered.clear();
  chatEmpty();
  renderConversationSelect();
  input.focus();
}
$("#newChatBtn").addEventListener("click", newConversation);

// ---------------------------------------------------------------- sending

const YES = /^(yes|yeah|yep|yup|sure|approve[d]?|go ahead|do it|confirm(ed)?|ok(ay)?|please do)\b[\s.!]*$/i;
const NO = /^(no|nope|deny|denied|don'?t|do not|cancel|stop|reject)\b[\s.!a-z]*$/i;

async function send(raw) {
  const text = raw.trim();
  if (!text) return;
  if (ui.pendingApproval && text.split(/\s+/).length <= 4) {
    if (YES.test(text)) { input.value = ""; return decide(ui.pendingApproval.id, true); }
    if (NO.test(text)) { input.value = ""; return decide(ui.pendingApproval.id, false); }
  }
  if (ui.busy) return toastText("JARVIS is still working. Press Stop to interrupt it.");
  voiceOut.cancel();
  ui.interrupted = false;
  input.value = "";
  autoGrow();
  ui.adoptNextConversation = ui.conversationId == null;
  try {
    const r = await api.chat(text, ui.conversationId);
    adoptConversation(r.conversationId);
    addMessage(r.userMessage);
    if (ui.runId !== r.runId) {
      ui.runId = r.runId;
      setBusy(true);
    }
  } catch (e) {
    ui.adoptNextConversation = false;
    input.value = text;
    toastText(e.message, { error: true });
  }
}

function adoptConversation(id) {
  if (ui.conversationId !== id) {
    ui.conversationId = id;
    storage("jarvis.conversation", String(id));
    loadConversations();
  }
  ui.adoptNextConversation = false;
}

async function decide(approvalId, approved) {
  try {
    await api.approve(approvalId, approved);
    ui.pendingApproval = null;
  } catch (e) {
    toastText(e.message, { error: true });
  }
}

$("#composer").addEventListener("submit", (e) => {
  e.preventDefault();
  if (ui.busy && !input.value.trim()) {
    api.cancel().catch((err) => toastText(err.message, { error: true }));
    return;
  }
  send(input.value);
});

input.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    $("#composer").requestSubmit();
  }
});
input.addEventListener("input", () => {
  if (voiceOut.speaking) interruptSpeech();
  autoGrow();
});
function autoGrow() {
  input.style.height = "auto";
  input.style.height = Math.min(160, input.scrollHeight) + "px";
}

// ================================================================== voice

const voiceOut = new VoiceOutput();
voiceOut.enabled = storage("jarvis.voice") !== "off";
$("#voiceToggle").setAttribute("aria-pressed", String(voiceOut.enabled));
const micBtns = [$("#micBtn"), $("#coreMic")];
const setMicActive = (on) => micBtns.forEach((b) => b.classList.toggle("active", on));

// ---------------------------------------------------------------- subtitles on the core screen
const subUser = $("#subUser");
const subJarvis = $("#subJarvis");

function showUserSubtitle(text) {
  subUser.textContent = text || "";
}

/** Shows what JARVIS says as a subtitle; long or formatted answers link to the Chat page. */
function showJarvisSubtitle(text, { full = text } = {}) {
  const plain = speakable(text).replace(/ The rest is on screen\.$/, "");
  const short = plain.length > 260 ? plain.slice(0, 257).replace(/\s+\S*$/, "") + "…" : plain;
  const needsChat = short !== plain || /```|\n\s*\|/.test(full || "");
  subJarvis.innerHTML = escapeHtml(short)
    + (needsChat ? ` <button class="msg-link" data-goto="chat">Full answer in Chat →</button>` : "");
  subJarvis.classList.remove("fresh");
  void subJarvis.offsetWidth;
  subJarvis.classList.add("fresh");
}

const voiceIn = new VoiceInput({
  onStart() {
    setMicActive(true);
    input.placeholder = "Listening… speak now";
    showUserSubtitle("");
    if (!ui.busy) reportVoiceState("LISTENING");
  },
  onInterim(text) {
    input.value = text;
    showUserSubtitle(text);
    autoGrow();
  },
  onFinal(text) {
    ui.noSpeech = 0;
    showUserSubtitle(text);
    if (/^(stop listening|goodbye|good bye|bye|that'?s all|end conversation)\b/i.test(text)) {
      input.value = "";
      setConvMode(false);
      showJarvisSubtitle("Understood. I'll stop listening. Tap the core when you need me.");
      speakReply("Understood. Tap the core when you need me.", { keepListening: false });
      return;
    }
    send(text);
  },
  onEnd(gotResult) {
    setMicActive(false);
    input.placeholder = "Ask JARVIS anything, or tell it what to do…";
    if (!gotResult) {
      if (input.value && !ui.busy) input.value = "";
      if (!ui.busy) reportVoiceState("IDLE");
      if (ui.convMode && !ui.interrupted) {
        if (++ui.noSpeech >= 2) {
          ui.noSpeech = 0;
          showJarvisSubtitle("I'm on standby. Tap the core or press Space when you need me.");
        } else {
          setTimeout(listenIfConversing, 500);
        }
      }
    }
  },
  onError(err) {
    const messages = {
      "not-allowed": "Microphone access was blocked. Allow it from the address bar to talk to JARVIS.",
      "service-not-allowed": "Microphone access was blocked by the browser.",
      "network": "Speech recognition needs an internet connection in this browser.",
      "audio-capture": "No microphone was found.",
    };
    if (err === "no-speech") return;
    toastText(messages[err] || `Voice error: ${err}`, { error: true });
    setConvMode(false);
  },
});
reactor.setMic(() => voiceIn.level(), () => voiceIn.samples());

if (!voiceIn.supported) {
  micBtns.forEach((b) => {
    b.classList.add("unsupported");
    b.title = "Voice input needs Chrome or Edge";
  });
  $("#voiceHint").textContent = "Voice input needs Chrome or Edge. Press “Type” to use the keyboard.";
}

function interruptSpeech() {
  ui.interrupted = true;
  voiceOut.cancel();
}

function startListening() {
  if (!voiceIn.supported) {
    toastText("Voice input isn't supported in this browser. Use Chrome or Edge — typing works everywhere.", { error: true });
    return false;
  }
  if (voiceOut.speaking) interruptSpeech();
  ui.interrupted = false;
  voiceIn.start();
  return true;
}

let greeted = false;

/** First tap of the session: JARVIS greets you by voice, then listens (like the films). */
async function greetThenListen() {
  greeted = true;
  const h = new Date().getHours();
  const line = `${h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening"}. ${
    ui.llm?.ready ? "All systems are online. How can I help?" : "My AI core is offline, so I'm in limited mode. How can I help?"}`;
  showJarvisSubtitle(line);
  await speakReply(line, { keepListening: false });
  startListening();
}

function toggleMic() {
  if (voiceIn.active || voiceIn.wanted) {
    ui.interrupted = true;
    voiceIn.stop();
    return;
  }
  if (!greeted && voiceIn.supported && voiceOut.enabled && !ui.busy && !ui.pendingApproval) {
    greetThenListen();
    return;
  }
  startListening();
}
$("#micBtn").addEventListener("click", toggleMic);
$("#coreMic").addEventListener("click", toggleMic);
$("#reactor").addEventListener("click", toggleMic);
$("#reactor").addEventListener("keydown", (e) => {
  if (e.key === "Enter") toggleMic();
});

/**
 * Hands-free: start listening again after JARVIS has replied. Works even when another app (Word, Notepad…)
 * is in front of the browser. Chrome sometimes refuses to restart the microphone the instant speech ends,
 * so wait a moment and retry once.
 */
function listenIfConversing(attempt = 0) {
  if (!ui.convMode || ui.busy || voiceIn.active || voiceIn.wanted || voiceOut.speaking || ui.interrupted) return;
  setTimeout(() => {
    if (!ui.convMode || ui.busy || voiceIn.active || voiceIn.wanted || voiceOut.speaking || ui.interrupted) return;
    startListening();
    setTimeout(() => {
      if (attempt < 1 && ui.convMode && !voiceIn.active && !voiceIn.wanted && !ui.busy && !voiceOut.speaking) {
        listenIfConversing(attempt + 1);
      }
    }, 1500);
  }, 450);
}

/** Hands-free mode: keep listening after every reply. Remembered between visits (on by default). */
function setConvMode(on, { listen = true } = {}) {
  ui.convMode = on;
  $("#convMode").checked = on;
  ui.noSpeech = 0;
  storage("jarvis.handsfree", on ? "on" : "off");
  if (on && listen) listenIfConversing();
}
$("#convMode").addEventListener("change", (e) => setConvMode(e.target.checked));
setConvMode(storage("jarvis.handsfree") !== "off", { listen: false });

// ---------------------------------------------------------------- typing on the core screen
$("#coreKeyboard").addEventListener("click", () => {
  const form = $("#coreForm");
  form.hidden = !form.hidden;
  $("#coreKeyboard").classList.toggle("on", !form.hidden);
  if (!form.hidden) $("#coreInput").focus();
});
$("#coreForm").addEventListener("submit", (e) => {
  e.preventDefault();
  const field = $("#coreInput");
  const text = field.value.trim();
  if (!text) return;
  field.value = "";
  showUserSubtitle(text);
  send(text);
});
$("#coreInput").addEventListener("input", () => {
  if (voiceOut.speaking) interruptSpeech();
});

$("#voiceToggle").addEventListener("click", (e) => {
  voiceOut.enabled = !voiceOut.enabled;
  e.currentTarget.setAttribute("aria-pressed", String(voiceOut.enabled));
  storage("jarvis.voice", voiceOut.enabled ? "on" : "off");
  if (!voiceOut.enabled) voiceOut.cancel();
  toastText(voiceOut.enabled ? "Spoken replies on" : "Spoken replies muted");
});

/** Speaks a reply, reporting SPEAKING/IDLE to the backend, then keeps the conversation going. */
async function speakReply(text, { keepListening = true } = {}) {
  // Speak even when another window is in front: JARVIS often just opened Word or Notepad on top of itself.
  const canSpeak = voiceOut.enabled && voiceOut.supported;
  ui.interrupted = false;
  if (canSpeak) {
    const spoken = speakable(text);
    await voiceOut.speak(spoken, { onStart: () => reportVoiceState("SPEAKING") });
    if (!voiceIn.active) reportVoiceState("IDLE");
  } else {
    await sleep(1400);
    if (!voiceIn.active && !ui.busy && (ui.state === "COMPLETED" || ui.state === "FAILED")) reportVoiceState("IDLE");
  }
  if (keepListening) listenIfConversing();
}

document.addEventListener("keydown", (e) => {
  const typing = e.target.matches("input, textarea, select");
  if (!$("#modal").hidden) return;
  if (e.key === "/" && !typing) {
    e.preventDefault();
    if (ui.view === "core") {
      $("#coreForm").hidden = false;
      $("#coreKeyboard").classList.add("on");
      $("#coreInput").focus();
    } else {
      showView("chat");
      input.focus();
    }
  } else if (e.code === "Space" && !typing && !e.target.closest("button, a, label, [role=button]")) {
    e.preventDefault();
    showView("core");
    toggleMic();
  } else if (e.key === "Escape") {
    if (voiceOut.speaking) interruptSpeech();
    if (voiceIn.active) { ui.interrupted = true; voiceIn.abort(); }
    if (ui.convMode) setConvMode(false);
    if (typing) e.target.blur();
  }
});

// ================================================================== live events from the agent

function onEvent(ev) {
  switch (ev.type) {
    case "state":
      applyState(ev.state, ev.detail, ev.runId);
      break;
    case "conversation":
      loadConversations();
      break;
    case "run_started": {
      const convId = ev.run.conversationId;
      if (ui.adoptNextConversation && ui.conversationId == null) adoptConversation(convId);
      if (convId === ui.conversationId) {
        addMessage(ev.message);
        ui.runId = ev.run.id;
        setBusy(true);
        showUserSubtitle(ev.message.text);
        subJarvis.textContent = "";
      }
      activity.reset(ev.run);
      break;
    }
    case "plan":
      activity.showPlan(ev.runId, ev.plan);
      break;
    case "tools_selected":
      activity.showTools(ev.runId, ev.tools);
      break;
    case "step":
      activity.upsertStep(ev.step);
      break;
    case "approval_required": {
      ui.pendingApproval = ev.approval;
      activity.showApproval(ev.approval, ev.riskDescription);
      if (ev.approval.runId === ui.runId) {
        setBusy(false); // the agent is paused: the user may answer, or send something else
        const wrap = document.createElement("div");
        wrap.className = "msg jarvis";
        wrap.innerHTML = `<div class="msg-meta"><b>JARVIS</b><span>${fmtTime(Date.now())}</span></div>`;
        wrap.appendChild(approvalCard(ev.approval, ev.riskDescription, decide));
        chat.appendChild(wrap);
        chat.scrollTop = chat.scrollHeight;
        const core = $("#coreApproval");
        core.innerHTML = "";
        core.appendChild(approvalCard(ev.approval, ev.riskDescription, decide));
        showJarvisSubtitle(`I need your approval: ${ev.approval.summary}. Say yes or no.`);
        speakReply(`I need your approval to ${ev.approval.summary}. Say yes or no.`);
      }
      break;
    }
    case "approval_decided":
      markApprovalDecided(ev.approval);
      setTimeout(() => {
        const card = $("#coreApproval .approval");
        if (card && card.dataset.approvalId === String(ev.approval.id)) $("#coreApproval").innerHTML = "";
      }, 1500);
      if (ui.pendingApproval?.id === ev.approval.id) ui.pendingApproval = null;
      if (ev.approval.status === "APPROVED" || ev.approval.status === "DENIED") {
        if (ev.approval.runId === ui.runId) setBusy(true);
      }
      break;
    case "run_finished": {
      const run = ev.run;
      activity.finish(run, ev.problems);
      if (run.id === ui.runId || run.conversationId === ui.conversationId) {
        setBusy(false);
        ui.pendingApproval = null;
        if (run.conversationId === ui.conversationId) {
          addMessage(ev.message, { status: run.status, problems: ev.problems, animate: true });
        }
        showJarvisSubtitle(ev.message.text);
        if (ev.speak) speakReply(ev.message.text);
      }
      loadConversations();
      break;
    }
    case "data_changed":
      views.dataChanged(ev.category);
      break;
    case "reminder": {
      const r = ev.reminder;
      toast(`<b>REMINDER${ev.late ? " (missed while closed)" : ""}</b>${escapeHtml(r.text)}`, { cls: "reminder", ms: 15000 });
      if (!ui.busy) {
        showJarvisSubtitle(`Reminder: ${r.text}`);
        speakReply(`Reminder: ${r.text}`);
      }
      break;
    }
  }
}

let lostToast = null;
connectEvents(onEvent, () => {
  if (lostToast) toastText("Live link to the Java core re-established.");
  lostToast = null;
  $("#linkDot").classList.add("on");
}, () => {
  $("#linkDot").classList.remove("on");
  if (ui.online && !lostToast) lostToast = toastText("Live link to the Java core lost — retrying…", { error: true, ms: 5000 });
});

// ================================================================== status polling

function renderEngineBadge(llm) {
  const badge = $("#engineBadge");
  badge.classList.toggle("ready", llm.ready);
  badge.classList.toggle("partial", llm.online && !llm.modelInstalled);
  $("#engineText").textContent = llm.ready ? `${llm.model} · ready` : llm.online ? "model not installed" : "AI offline · limited mode";
}

async function poll() {
  let s;
  try {
    s = await api.status();
  } catch {
    ui.online = false;
    return;
  }
  ui.online = true;
  ui.lastStatus = s;
  const engineChanged = !ui.llm || ui.llm.ready !== s.llm.ready || ui.llm.online !== s.llm.online;
  if (ui.llm && !ui.llm.ready && s.llm.ready) toastText(`AI engine ready: ${s.llm.model}`);
  ui.llm = s.llm;
  renderEngineBadge(s.llm);
  if (engineChanged && ui.view === "system") views.renderEngine(s.llm);
  views.updateCounts(s.counts);
  if (ui.view === "system") views.renderSystem(s);
}

// ================================================================== clock

function tickClock() {
  const d = new Date();
  $("#clock").textContent = d.toLocaleTimeString([], { hour12: false });
  $("#date").textContent = d.toLocaleDateString([], { weekday: "short", day: "2-digit", month: "short", year: "numeric" }).replace(/,/g, " ·");
}
tickClock();
setInterval(tickClock, 1000);

// ================================================================== boot

async function boot() {
  const log = $("#bootLog"), bar = $("#bootBar");
  log.innerHTML = "";
  $("#bootRetry").hidden = true;
  const line = (text) => {
    const li = document.createElement("li");
    li.textContent = text;
    log.appendChild(li);
    return li;
  };
  const step = async (text, pct, fn) => {
    const li = line(text);
    await sleep(220);
    try {
      const r = await fn?.();
      const note = typeof r === "object" && r ? r.note : r;
      li.classList.add(r && r.warn ? "warn" : "ok");
      if (note) li.textContent += ` — ${note}`;
      bar.style.width = pct + "%";
    } catch (err) {
      li.classList.add("fail");
      li.textContent += " — FAILED";
      throw err;
    }
  };

  let status;
  try {
    await step("Initialising holographic interface", 15);
    await step("Linking to Java core", 35, async () => {
      status = await api.status();
      return `${status.tools} tools registered`;
    });
    await step("Opening SQLite memory bank", 55, async () => {
      await loadConversations();
      return `${status.counts.conversations} conversations · ${status.counts.memories} memories`;
    });
    await step("Contacting AI engine", 75, () => status.llm.ready
      ? `${status.llm.provider} ${status.llm.model}`
      : { warn: true, note: status.llm.online ? "model not installed" : "offline — limited mode" });
    await step("Calibrating voice systems", 90, () => (voiceIn.supported ? "voice + text" : { warn: true, note: "text only (use Chrome/Edge for voice)" }));
    await step("All systems online", 100);
  } catch {
    line("Start the backend with run.bat, then retry.").classList.add("fail");
    $("#bootRetry").hidden = false;
    return;
  }

  const saved = Number(storage("jarvis.conversation"));
  if (saved && ui.conversations.some((c) => c.id === saved)) await loadConversation(saved);
  else newConversation();

  const pending = await api.pendingApproval().catch(() => null);
  if (pending && pending.id) {
    onEvent({ type: "approval_required", approval: pending, riskDescription: "" });
  }

  ui.llm = status.llm;
  renderEngineBadge(status.llm);
  applyState(status.state, status.stateDetail, status.activeRunId);
  if (status.agentWorking) setBusy(true);

  await sleep(300);
  $("#boot").classList.add("done");
  document.body.classList.add("ready");
  ui.online = true;
  $("#linkDot").classList.add("on");
  poll();
  setInterval(poll, 3000);
  input.focus();
  if (!status.llm.ready) {
    toast(`AI engine not ready — JARVIS is in <b>limited offline mode</b>. <button class="msg-link" data-goto="system">Setup steps</button>`, { ms: 9000 });
  }
}

$("#bootRetry").addEventListener("click", boot);
boot();
