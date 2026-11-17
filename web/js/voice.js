// Browser voice I/O: speech recognition (input), microphone analyser (for the
// live waveform) and speech synthesis (output, interruptible).

const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;

export class VoiceInput {
  constructor(handlers) {
    this.h = handlers;
    this.supported = Boolean(Recognition);
    this.active = false;
    this.wanted = false;
    this.analyser = null;
    this.stream = null;
    this.buf = null;
  }

  start() {
    if (!this.supported || this.active || this.wanted) return;
    const rec = new Recognition();
    rec.lang = navigator.language || "en-US";
    rec.interimResults = true;
    rec.continuous = false;
    rec.maxAlternatives = 1;
    this.rec = rec;
    this.finalSent = false;

    rec.onstart = () => {
      this.active = true;
      this.h.onStart?.();
    };
    rec.onresult = (e) => {
      let interim = "", final = "";
      for (let i = e.resultIndex; i < e.results.length; i++) {
        const r = e.results[i];
        if (r.isFinal) final += r[0].transcript;
        else interim += r[0].transcript;
      }
      if (interim) this.h.onInterim?.(interim);
      if (final && !this.finalSent) {
        this.finalSent = true;
        this.h.onFinal?.(final.trim());
      }
    };
    rec.onerror = (e) => {
      if (e.error !== "aborted") this.h.onError?.(e.error);
    };
    rec.onend = () => {
      this.active = false;
      this.wanted = false;
      this.closeMic();
      this.h.onEnd?.(this.finalSent);
    };

    try {
      this.wanted = true;
      rec.start();
    } catch (err) {
      this.wanted = false;
      this.h.onError?.(err.message);
      return;
    }
    this.openMic();
  }

  stop() {
    this.rec?.stop();
  }

  abort() {
    this.rec?.abort();
  }

  async openMic() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!this.wanted) return this.closeMic(); // recognition already ended
      const ctx = this.audioCtx || (this.audioCtx = new AudioContext());
      if (ctx.state === "suspended") await ctx.resume();
      const src = ctx.createMediaStreamSource(this.stream);
      this.analyser = ctx.createAnalyser();
      this.analyser.fftSize = 1024;
      this.buf = new Uint8Array(this.analyser.fftSize);
      src.connect(this.analyser);
    } catch {
      this.analyser = null; // waveform falls back to a modelled signal
    }
  }

  closeMic() {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.analyser = null;
  }

  level() {
    if (!this.analyser) return 0;
    this.analyser.getByteTimeDomainData(this.buf);
    let sum = 0;
    for (const v of this.buf) {
      const d = (v - 128) / 128;
      sum += d * d;
    }
    return Math.sqrt(sum / this.buf.length);
  }

  samples() {
    if (!this.analyser) return null;
    this.analyser.getByteTimeDomainData(this.buf);
    return this.buf;
  }
}

const VOICE_PREFS = [
  "Microsoft Ryan Online", "Google UK English Male", "Microsoft George", "Daniel",
  "Microsoft Guy Online", "Microsoft David", "Google US English",
];

export class VoiceOutput {
  constructor() {
    this.supported = "speechSynthesis" in window;
    this.enabled = true;
    this.voice = null;
    this.speaking = false;
    this._finish = null;
    if (this.supported) {
      this.pickVoice();
      speechSynthesis.addEventListener?.("voiceschanged", () => this.pickVoice());
    }
  }

  pickVoice() {
    const voices = speechSynthesis.getVoices();
    if (!voices.length) return;
    for (const pref of VOICE_PREFS) {
      const v = voices.find((x) => x.name.includes(pref));
      if (v) return (this.voice = v);
    }
    this.voice = voices.find((v) => v.lang === "en-GB") || voices.find((v) => v.lang.startsWith("en")) || voices[0];
  }

  /**
   * Speaks text sentence by sentence (long single utterances get cut off in Chrome).
   * Resolves true when finished, false if disabled or interrupted.
   */
  speak(text, { onStart } = {}) {
    this.cancel();
    if (!this.supported || !this.enabled || !text) return Promise.resolve(false);
    const chunks = (text.match(/[^.!?]+[.!?]*\s*/g) || [text]).reduce((acc, s) => {
      const last = acc[acc.length - 1];
      if (last && last.length + s.length < 220) acc[acc.length - 1] = last + s;
      else acc.push(s);
      return acc;
    }, []);
    return new Promise((resolve) => {
      let started = false;
      let index = 0;
      const guard = setTimeout(() => done(true), 6000 + text.length * 110);
      const done = (ok) => {
        if (!this._finish) return;
        clearTimeout(guard);
        this._finish = null;
        this.speaking = false;
        resolve(ok);
      };
      this._finish = done;
      this.speaking = true;
      const next = () => {
        if (!this._finish) return;
        if (index >= chunks.length) return done(true);
        const u = new SpeechSynthesisUtterance(chunks[index++].trim());
        if (this.voice) u.voice = this.voice;
        u.rate = 1.03;
        u.pitch = 0.95;
        u.onstart = () => {
          if (!started) {
            started = true;
            onStart?.();
          }
        };
        u.onend = next;
        u.onerror = (e) => (e.error === "interrupted" || e.error === "canceled" ? done(false) : next());
        speechSynthesis.speak(u);
      };
      next();
    });
  }

  /** Stops speaking immediately (interruption). */
  cancel() {
    if (!this.supported) return;
    const finish = this._finish;
    this._finish = null;
    this.speaking = false;
    speechSynthesis.cancel();
    if (finish) {
      this._finish = finish;
      finish(false);
    }
  }
}
