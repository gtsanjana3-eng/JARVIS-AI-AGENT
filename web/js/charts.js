// Lightweight canvas charts for live telemetry (no libraries).

function prepare(canvas) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
  }
  const ctx = canvas.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  return { ctx, w, h };
}

function path(ctx, data, w, h, max, pad, capacity) {
  const step = (w - pad.l - pad.r) / Math.max(1, capacity - 1);
  const offset = capacity - data.length;
  const pts = data.map((v, i) => [pad.l + (i + offset) * step, pad.t + (h - pad.t - pad.b) * (1 - Math.min(v, max) / max)]);
  ctx.beginPath();
  pts.forEach(([x, y], i) => (i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)));
  return pts;
}

/** Tiny sparkline used inside metric cards. */
export function sparkline(canvas, data, rgb, capacity = 40) {
  if (!canvas.clientWidth || data.length < 2) return;
  const { ctx, w, h } = prepare(canvas);
  const pad = { l: 0, r: 0, t: 3, b: 1 };
  const pts = path(ctx, data, w, h, 100, pad, capacity);
  ctx.strokeStyle = `rgb(${rgb})`;
  ctx.lineWidth = 1.4;
  ctx.shadowColor = `rgb(${rgb})`;
  ctx.shadowBlur = 6;
  ctx.stroke();
  ctx.shadowBlur = 0;
  ctx.lineTo(pts[pts.length - 1][0], h);
  ctx.lineTo(pts[0][0], h);
  const g = ctx.createLinearGradient(0, 0, 0, h);
  g.addColorStop(0, `rgba(${rgb}, .28)`);
  g.addColorStop(1, `rgba(${rgb}, 0)`);
  ctx.fillStyle = g;
  ctx.fill();
}

/** Larger chart with grid and labels for the Diagnostics view. */
export function areaChart(canvas, data, rgb, capacity = 80) {
  if (!canvas.clientWidth) return;
  const { ctx, w, h } = prepare(canvas);
  const pad = { l: 34, r: 8, t: 10, b: 18 };
  ctx.font = "11px 'Share Tech Mono', monospace";
  ctx.fillStyle = "rgba(143, 180, 198, .7)";
  ctx.strokeStyle = "rgba(0, 229, 255, .08)";
  ctx.lineWidth = 1;
  for (let v = 0; v <= 100; v += 25) {
    const y = pad.t + (h - pad.t - pad.b) * (1 - v / 100);
    ctx.beginPath();
    ctx.moveTo(pad.l, y);
    ctx.lineTo(w - pad.r, y);
    ctx.stroke();
    ctx.fillText(String(v).padStart(3, " "), 4, y + 4);
  }
  ctx.fillText("now", w - pad.r - 22, h - 4);
  if (data.length < 2) return;
  const pts = path(ctx, data, w, h, 100, pad, capacity);
  ctx.strokeStyle = `rgb(${rgb})`;
  ctx.lineWidth = 2;
  ctx.shadowColor = `rgb(${rgb})`;
  ctx.shadowBlur = 10;
  ctx.stroke();
  ctx.shadowBlur = 0;
  ctx.lineTo(pts[pts.length - 1][0], h - pad.b);
  ctx.lineTo(pts[0][0], h - pad.b);
  const g = ctx.createLinearGradient(0, pad.t, 0, h - pad.b);
  g.addColorStop(0, `rgba(${rgb}, .3)`);
  g.addColorStop(1, `rgba(${rgb}, 0)`);
  ctx.fillStyle = g;
  ctx.fill();
  const [lx, ly] = pts[pts.length - 1];
  ctx.fillStyle = "#fff";
  ctx.beginPath();
  ctx.arc(lx, ly, 3, 0, Math.PI * 2);
  ctx.fill();
}
