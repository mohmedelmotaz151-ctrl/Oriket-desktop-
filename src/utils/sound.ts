export type DesktopSoundKind = 'boot' | 'start' | 'sleep' | 'shutdown' | 'restart';

let sharedAudioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctx) return null;
  if (!sharedAudioCtx) {
    try {
      sharedAudioCtx = new Ctx();
    } catch {
      return null;
    }
  }
  if (sharedAudioCtx.state === 'suspended') {
    sharedAudioCtx.resume().catch(() => {});
  }
  return sharedAudioCtx;
}

export function playDesktopSound(kind: DesktopSoundKind, enabled = true): void {
  if (!enabled) return;
  const ctx = getAudioContext();
  if (!ctx) return;

  const sequence: Array<{ delay: number; freq: number; duration: number }> = (() => {
    switch (kind) {
      case 'boot':
        return [
          { delay: 0, freq: 523.25, duration: 0.14 },
          { delay: 0.16, freq: 659.25, duration: 0.14 },
          { delay: 0.34, freq: 783.99, duration: 0.24 },
        ];
      case 'start':
        return [
          { delay: 0, freq: 587.33, duration: 0.1 },
          { delay: 0.11, freq: 880, duration: 0.16 },
        ];
      case 'sleep':
        return [
          { delay: 0, freq: 659.25, duration: 0.14 },
          { delay: 0.18, freq: 523.25, duration: 0.14 },
          { delay: 0.36, freq: 392.0, duration: 0.22 },
        ];
      case 'shutdown':
        return [
          { delay: 0, freq: 783.99, duration: 0.13 },
          { delay: 0.16, freq: 659.25, duration: 0.13 },
          { delay: 0.34, freq: 440.0, duration: 0.24 },
        ];
      default:
        return [
          { delay: 0, freq: 523.25, duration: 0.12 },
          { delay: 0.15, freq: 659.25, duration: 0.14 },
          { delay: 0.3, freq: 587.33, duration: 0.18 },
        ];
    }
  })();

  const now = ctx.currentTime;
  for (const note of sequence) {
    try {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(note.freq, now + note.delay);
      gain.gain.setValueAtTime(0.001, now + note.delay);
      gain.gain.exponentialRampToValueAtTime(0.08, now + note.delay + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, now + note.delay + note.duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(now + note.delay);
      osc.stop(now + note.delay + note.duration + 0.02);
    } catch {
      // Ignore Web Audio autoplay blocks cleanly
    }
  }
}

export function createDemoTrackWavUrl(baseFreq: number, chordFreq: number, durationSec = 18): string {
  const sampleRate = 22050;
  const numSamples = sampleRate * durationSec;
  const buffer = new ArrayBuffer(44 + numSamples * 2);
  const view = new DataView(buffer);

  const writeStr = (offset: number, str: string) => {
    for (let i = 0; i < str.length; i++) {
      view.setUint8(offset + i, str.charCodeAt(i));
    }
  };

  writeStr(0, 'RIFF');
  view.setUint32(4, 36 + numSamples * 2, true);
  writeStr(8, 'WAVE');
  writeStr(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeStr(36, 'data');
  view.setUint32(40, numSamples * 2, true);

  for (let i = 0; i < numSamples; i++) {
    const t = i / sampleRate;
    const envelope = Math.min(1, t * 2) * Math.min(1, (durationSec - t) * 1.5);
    const pulse = 0.65 + 0.35 * Math.sin(2 * Math.PI * 1.5 * t);
    const sample =
      (0.32 * Math.sin(2 * Math.PI * baseFreq * t) +
        0.22 * Math.sin(2 * Math.PI * chordFreq * t) +
        0.15 * Math.sin(2 * Math.PI * (baseFreq * 1.5) * t)) *
      envelope *
      pulse;
    view.setInt16(44 + i * 2, Math.max(-1, Math.min(1, sample)) * 16000, true);
  }

  const blob = new Blob([buffer], { type: 'audio/wav' });
  return URL.createObjectURL(blob);
}
