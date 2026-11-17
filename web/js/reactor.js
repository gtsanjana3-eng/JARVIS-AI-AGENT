// Holographic core: rotating rings, reactive orb, waveform and particle field.
// Everything is driven by one requestAnimationFrame loop so ring speeds can
// ease smoothly between states instead of jumping.

const SVG_NS = "http://www.w3.org/2000/svg";
const C = 250; // centre of the 500x500 viewBox

// Ring speed multiplier per real agent state: reasoning stages spin faster as work intensifies.
const STATE_SPEED = {
  IDLE: 1, LISTENING: 1.7, UNDERSTANDING: 2.4, PLANNING: 3.2, SELECTING_TOOLS: 3.6, EXECUTING: 4.6,
  AWAITING_APPROVAL: 0.5, SPEAKING: 2.1, COMPLETED: 1.3, FAILED: 0.4,
};

export const STATE_LABELS = {
  IDLE: "IDLE", LISTENING: "LISTENING", UNDERSTANDING: "UNDERSTANDING", PLANNING: "PLANNING",
  SELECTING_TOOLS: "SELECTING TOOLS", EXECUTING: "EXECUTING", AWAITING_APPROVAL: "AWAITING APPROVAL",
  SPEAKING: "SPEAKING", COMPLETED: "COMPLETED", FAILED: "FAILED",
};

function el(name, attrs = {}, parent) {
  const node = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, v);
  if (parent) parent.appendChild(node);
  return node;
}

function circlePath(r) {
  return `M ${C} ${C - r} a ${r} ${r} 0 1 1 0 ${2 * r} a ${r} ${r} 0 1 1 0 ${-2 * r}`;
}

export class Reactor {
  constructor({ root, svg, wave, label }) {
    this.root = root;
    this.svg = svg;
    this.wave = wave;
    this.label = label;
    this.state = "IDLE";
    this.mult = 1;
    this.level = 0.1;
    this.micLevel = null;     // function returning 0..1 (real microphone)
    this.micSamples = null;   // function returning Uint8Array time-domain data
    this.rotors = [];
    this.last = performance.now();
    this.t = 0;
    this.orb = {
      halo: svg.querySelector(".orb-halo"),
      ring: svg.querySelector(".orb-ring"),
      core: svg.querySelector(".orb-core"),
    };
    this.build(svg.querySelector("#rings"));
    this.waveCtx = wave.getContext("2d");
    this.resize();
    window.addEventListener("resize", () => this.resize());
    requestAnimationFrame((now) => this.frame(now));
  }

  rotor(group, speed) {
    this.rotors.push({ group, speed, angle: Math.random() * 360 });
    return group;
  }

  build(host) {
    // outer static hairline + dotted ring
    el("circle", { cx: C, cy: C, r: 240, class: "r r-faint", "stroke-width": 1 }, host);
    const g1 = this.rotor(el("g", {}, host), 4);
    el("circle", { cx: C, cy: C, r: 231, class: "r r-mid", "stroke-width": 1.5, "stroke-dasharray": "2 7" }, g1);

    // tick ring
    const ticks = this.rotor(el("g", {}, host), -3);
    for (let i = 0; i < 120; i++) {
      const a = (i / 120) * Math.PI * 2;
      const long = i % 10 === 0;
      const r1 = long ? 208 : 213, r2 = 221;
      el("line", {
        x1: C + Math.cos(a) * r1, y1: C + Math.sin(a) * r1,
        x2: C + Math.cos(a) * r2, y2: C + Math.sin(a) * r2,
        class: `r r-tick ${long ? "r-strong" : "r-mid"}`,
      }, ticks);
    }

    // heavy arc segments
    const g2 = this.rotor(el("g", {}, host), 11);
    el("circle", { cx: C, cy: C, r: 198, class: "r r-strong", "stroke-width": 5, "stroke-dasharray": "190 36 70 36 300 28 46 60 120 44", "stroke-linecap": "round" }, g2);
    el("circle", { cx: C + 198, cy: C, r: 4, class: "r-dot" }, g2);
    el("circle", { cx: C - 198, cy: C, r: 3, class: "r-dot" }, g2);

    el("circle", { cx: C, cy: C, r: 186, class: "r r-faint r-blue", "stroke-width": 1 }, host);

    // rotating text ring
    const gText = this.rotor(el("g", {}, host), -6);
    el("path", { id: "textRing", d: circlePath(174), fill: "none" }, gText);
    const text = el("text", { class: "r-text" }, gText);
    const tp = el("textPath", { href: "#textRing", startOffset: "0" }, text);
    tp.textContent = "J.A.R.V.I.S  ◆  NEURAL CORE ONLINE  ◆  JAVA RUNTIME  ◆  VOICE INTERFACE  ◆  MEMORY BANK SYNCED  ◆  PROTOCOLS LOADED  ◆  ";

    const g3 = this.rotor(el("g", {}, host), -15);
    el("circle", { cx: C, cy: C, r: 158, class: "r r-mid r-blue", "stroke-width": 2.5, "stroke-dasharray": "130 26 10 26 60 20", "stroke-linecap": "round" }, g3);
    el("circle", { cx: C, cy: C - 158, r: 3.5, class: "r-dot" }, g3);

    // arc-reactor style trapezoid segments
    const g4 = this.rotor(el("g", {}, host), 7);
    const n = 12, rin = 118, rout = 140, gap = 0.07;
    for (let i = 0; i < n; i++) {
      const a0 = (i / n) * Math.PI * 2 + gap, a1 = ((i + 1) / n) * Math.PI * 2 - gap;
      const p = (r, a) => `${(C + Math.cos(a) * r).toFixed(2)} ${(C + Math.sin(a) * r).toFixed(2)}`;
      el("path", {
        d: `M ${p(rin, a0)} L ${p(rout, a0)} A ${rout} ${rout} 0 0 1 ${p(rout, a1)} L ${p(rin, a1)} A ${rin} ${rin} 0 0 0 ${p(rin, a0)} Z`,
        class: "r-seg",
      }, g4);
    }

    const g5 = this.rotor(el("g", {}, host), -26);
    el("circle", { cx: C, cy: C, r: 106, class: "r r-strong", "stroke-width": 1.2, "stroke-dasharray": "3 5" }, g5);

    // fast orbiting particle
    const g6 = this.rotor(el("g", {}, host), 38);
    el("circle", { cx: C, cy: C - 186, r: 2.5, class: "r-dot" }, g6);
    el("circle", { cx: C, cy: C + 231, r: 2, class: "r-dot" }, g6);
  }

  setState(state) {
    this.state = state;
    this.label.textContent = STATE_LABELS[state] || state;
  }

  /** Connect a real microphone analyser (or null to disconnect). */
  setMic(levelFn, samplesFn) {
    this.micLevel = levelFn;
    this.micSamples = samplesFn;
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = this.wave.clientWidth || 500;
    const h = this.wave.clientHeight || 70;
    this.wave.width = w * dpr;
    this.wave.height = h * dpr;
    this.waveCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.waveW = w;
    this.waveH = h;
  }

  targetLevel(t) {
    switch (this.state) {
      case "LISTENING":
        if (this.micLevel) return Math.min(1, 0.12 + this.micLevel() * 2.4);
        return 0.3 + 0.12 * Math.sin(t * 5);
      case "UNDERSTANDING":
        return 0.3 + 0.15 * Math.sin(t * 6);
      case "PLANNING":
      case "SELECTING_TOOLS":
        return 0.38 + 0.22 * Math.sin(t * 9);
      case "EXECUTING":
        return 0.45 + 0.25 * Math.abs(Math.sin(t * 11));
      case "AWAITING_APPROVAL":
        return 0.22 + 0.18 * Math.sin(t * 2.2);
      case "COMPLETED":
        return 0.2 + 0.08 * Math.sin(t * 3);
      case "FAILED":
        return 0.06 + 0.06 * Math.random();
      case "SPEAKING": {
        // syllable-like envelope: speechSynthesis exposes no audio, so we model it
        const syll = Math.abs(Math.sin(t * 7.3) * Math.sin(t * 2.3 + 1.2));
        return 0.28 + 0.6 * syll + 0.08 * Math.random();
      }
      default:
        return 0.12 + 0.05 * Math.sin(t * 1.6);
    }
  }

  frame(now) {
    const dt = Math.min(0.05, (now - this.last) / 1000);
    this.last = now;
    this.t += dt;

    const target = STATE_SPEED[this.state] || 1;
    this.mult += (target - this.mult) * Math.min(1, dt * 2.2);

    for (const r of this.rotors) {
      r.angle = (r.angle + r.speed * this.mult * dt) % 360;
      r.group.setAttribute("transform", `rotate(${r.angle.toFixed(2)} ${C} ${C})`);
    }

    const lv = this.targetLevel(this.t);
    this.level += (lv - this.level) * Math.min(1, dt * (this.state === "SPEAKING" ? 14 : 6));
    const L = this.level;
    this.root.style.setProperty("--level", L.toFixed(3));
    this.orb.core.setAttribute("transform", `translate(${C} ${C}) scale(${(1 + L * 0.55).toFixed(3)}) translate(${-C} ${-C})`);
    this.orb.halo.setAttribute("transform", `translate(${C} ${C}) scale(${(0.9 + L * 0.7).toFixed(3)}) translate(${-C} ${-C})`);
    this.orb.ring.setAttribute("transform", `translate(${C} ${C}) scale(${(1 + L * 0.28).toFixed(3)}) translate(${-C} ${-C})`);
    this.orb.halo.style.opacity = (0.35 + L * 0.6).toFixed(3);

    this.drawWave();
    requestAnimationFrame((n) => this.frame(n));
  }

  drawWave() {
    const ctx = this.waveCtx, w = this.waveW, h = this.waveH, mid = h / 2;
    ctx.clearRect(0, 0, w, h);
    const accent = getComputedStyle(document.body).getPropertyValue("--accent-rgb").trim() || "0, 229, 255";

    const grad = ctx.createLinearGradient(0, 0, w, 0);
    grad.addColorStop(0, `rgba(${accent}, 0)`);
    grad.addColorStop(0.2, `rgba(${accent}, .9)`);
    grad.addColorStop(0.5, "rgba(255, 255, 255, .95)");
    grad.addColorStop(0.8, "rgba(22, 139, 255, .9)");
    grad.addColorStop(1, "rgba(22, 139, 255, 0)");

    ctx.lineWidth = 1.6;
    ctx.shadowColor = `rgba(${accent}, .9)`;
    ctx.shadowBlur = 10;

    const samples = this.state === "LISTENING" && this.micSamples ? this.micSamples() : null;
    if (samples) {
      ctx.strokeStyle = grad;
      ctx.beginPath();
      const step = samples.length / w;
      for (let x = 0; x <= w; x += 2) {
        const v = (samples[Math.floor(x * step)] - 128) / 128;
        const env = Math.sin((x / w) * Math.PI);
        const y = mid + v * mid * 2.2 * env;
        x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      ctx.stroke();
    }

    const amp = this.level * mid * 0.92;
    const layers = [
      { f: 1.0, s: 2.2, a: 1, alpha: samples ? 0.35 : 1 },
      { f: 1.7, s: -3.1, a: 0.6, alpha: 0.5 },
      { f: 2.6, s: 4.3, a: 0.35, alpha: 0.35 },
    ];
    for (const L of layers) {
      ctx.globalAlpha = L.alpha;
      ctx.strokeStyle = grad;
      ctx.beginPath();
      for (let x = 0; x <= w; x += 3) {
        const p = x / w;
        const env = Math.pow(Math.sin(p * Math.PI), 1.6);
        const y = mid + Math.sin(p * Math.PI * 6 * L.f + this.t * L.s) * amp * L.a * env
                      + Math.sin(p * Math.PI * 17 * L.f - this.t * L.s * 1.7) * amp * 0.25 * L.a * env;
        x === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
      }
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
    ctx.shadowBlur = 0;

    // baseline
    ctx.fillStyle = `rgba(${accent}, .18)`;
    ctx.fillRect(0, mid, w, 1);
  }
}

/** Subtle drifting particle field with proximity lines. */
export class Particles {
  constructor(canvas, speedSource) {
    this.c = canvas;
    this.ctx = canvas.getContext("2d");
    this.speed = speedSource;
    this.reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    this.resize();
    window.addEventListener("resize", () => this.resize());
    requestAnimationFrame(() => this.frame());
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.w = window.innerWidth;
    this.h = window.innerHeight;
    this.c.width = this.w * dpr;
    this.c.height = this.h * dpr;
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    const count = Math.min(110, Math.floor((this.w * this.h) / 15000));
    this.ps = Array.from({ length: count }, () => ({
      x: Math.random() * this.w,
      y: Math.random() * this.h,
      vx: (Math.random() - 0.5) * 0.18,
      vy: (Math.random() - 0.5) * 0.18 - 0.05,
      r: Math.random() * 1.5 + 0.4,
      a: Math.random() * 0.5 + 0.2,
    }));
  }

  frame() {
    const { ctx, w, h } = this;
    const k = this.reduced ? 0 : this.speed();
    ctx.clearRect(0, 0, w, h);
    for (const p of this.ps) {
      p.x += p.vx * k;
      p.y += p.vy * k;
      if (p.x < -5) p.x = w + 5; else if (p.x > w + 5) p.x = -5;
      if (p.y < -5) p.y = h + 5; else if (p.y > h + 5) p.y = -5;
    }
    ctx.lineWidth = 0.6;
    for (let i = 0; i < this.ps.length; i++) {
      const a = this.ps[i];
      for (let j = i + 1; j < this.ps.length; j++) {
        const b = this.ps[j];
        const dx = a.x - b.x, dy = a.y - b.y, d2 = dx * dx + dy * dy;
        if (d2 < 12100) {
          ctx.strokeStyle = `rgba(0, 229, 255, ${(1 - Math.sqrt(d2) / 110) * 0.09})`;
          ctx.beginPath();
          ctx.moveTo(a.x, a.y);
          ctx.lineTo(b.x, b.y);
          ctx.stroke();
        }
      }
    }
    for (const p of this.ps) {
      ctx.fillStyle = `rgba(150, 240, 255, ${p.a})`;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }
    requestAnimationFrame(() => this.frame());
  }
}
