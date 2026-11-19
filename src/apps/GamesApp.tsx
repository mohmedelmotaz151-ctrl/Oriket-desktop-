import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';

interface GamesAppProps {
  onNotify: (title: string, msg: string) => void;
}

type InstrumentId =
  | 'acoustic'
  | 'grand'
  | 'electric'
  | 'bright'
  | 'harpsichord'
  | 'organ';

interface InstrumentConfig {
  id: InstrumentId;
  label: string;
  harmonics: number[];
  wave: OscillatorType;
  decaySec: number;
  attackSec: number;
}

interface PianoKeyDef {
  midi: number;
  note: string;
  octave: number;
  label: string;
  isBlack: boolean;
  freq: number;
  whiteIndex: number; // index among white keys (for black keys, left adjacent white key index)
}

interface RecordedNote {
  midi: number;
  timeMs: number;
  durationMs: number;
}

interface SavedRecording {
  id: string;
  name: string;
  instrument: InstrumentId;
  createdAt: number;
  notes: RecordedNote[];
}

const INSTRUMENTS: InstrumentConfig[] = [
  { id: 'acoustic', label: 'Acoustic Piano', harmonics: [1, 0.45, 0.18, 0.08], wave: 'triangle', decaySec: 1.5, attackSec: 0.008 },
  { id: 'grand', label: 'Grand Concert', harmonics: [1, 0.55, 0.25, 0.12], wave: 'triangle', decaySec: 2.1, attackSec: 0.01 },
  { id: 'electric', label: 'Electric Piano', harmonics: [1, 0.25, 0.05], wave: 'sine', decaySec: 1.8, attackSec: 0.006 },
  { id: 'bright', label: 'Bright Piano', harmonics: [1, 0.65, 0.35, 0.2], wave: 'sawtooth', decaySec: 1.3, attackSec: 0.005 },
  { id: 'harpsichord', label: 'Harpsichord', harmonics: [1, 0.7, 0.5, 0.35], wave: 'sawtooth', decaySec: 0.9, attackSec: 0.003 },
  { id: 'organ', label: 'Warm Organ', harmonics: [1, 0.6, 0.4, 0.2], wave: 'sine', decaySec: 2.4, attackSec: 0.03 },
];

const OCTAVE_BADGE_COLORS: Record<number, string> = {
  1: '#C97B7B',
  2: '#F28B49',
  3: '#E0AC30',
  4: '#38A169',
  5: '#3182CE',
  6: '#805AD5',
};

const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];

function buildKeyboard(): PianoKeyDef[] {
  const keys: PianoKeyDef[] = [];
  let whiteIdx = -1;
  // C1 (MIDI 24) to C6 (MIDI 84)
  for (let midi = 24; midi <= 84; midi++) {
    const noteName = NOTE_NAMES[midi % 12];
    const octave = Math.floor(midi / 12) - 1;
    const isBlack = noteName.includes('#');
    if (!isBlack) whiteIdx++;
    const freq = 440 * Math.pow(2, (midi - 69) / 12);
    keys.push({
      midi,
      note: noteName,
      octave,
      label: `${noteName}${octave}`,
      isBlack,
      freq,
      whiteIndex: whiteIdx,
    });
  }
  return keys;
}

const ALL_KEYS = buildKeyboard();
const WHITE_KEYS = ALL_KEYS.filter((k) => !k.isBlack);
const BLACK_KEYS = ALL_KEYS.filter((k) => k.isBlack);

const KEY_RECORDINGS = 'orkeit_piano_recordings_v1';

interface DemoSong {
  id: string;
  title: string;
  notes: Array<{ label: string; delayMs: number; durMs: number }>;
}

const DEMO_SONGS: DemoSong[] = [
  {
    id: 'ode_to_joy',
    title: 'Ode to Joy — Beethoven',
    notes: [
      { label: 'E3', delayMs: 0, durMs: 360 },
      { label: 'E3', delayMs: 380, durMs: 360 },
      { label: 'F3', delayMs: 760, durMs: 360 },
      { label: 'G3', delayMs: 1140, durMs: 360 },
      { label: 'G3', delayMs: 1520, durMs: 360 },
      { label: 'F3', delayMs: 1900, durMs: 360 },
      { label: 'E3', delayMs: 2280, durMs: 360 },
      { label: 'D3', delayMs: 2660, durMs: 360 },
      { label: 'C3', delayMs: 3040, durMs: 360 },
      { label: 'C3', delayMs: 3420, durMs: 360 },
      { label: 'D3', delayMs: 3800, durMs: 360 },
      { label: 'E3', delayMs: 4180, durMs: 360 },
      { label: 'E3', delayMs: 4560, durMs: 540 },
      { label: 'D3', delayMs: 5140, durMs: 220 },
      { label: 'D3', delayMs: 5380, durMs: 650 },
    ],
  },
  {
    id: 'fur_elise',
    title: 'Für Elise — Beethoven',
    notes: [
      { label: 'E4', delayMs: 0, durMs: 220 },
      { label: 'D#4', delayMs: 230, durMs: 220 },
      { label: 'E4', delayMs: 460, durMs: 220 },
      { label: 'D#4', delayMs: 690, durMs: 220 },
      { label: 'E4', delayMs: 920, durMs: 220 },
      { label: 'B3', delayMs: 1150, durMs: 220 },
      { label: 'D4', delayMs: 1380, durMs: 220 },
      { label: 'C4', delayMs: 1610, durMs: 220 },
      { label: 'A3', delayMs: 1840, durMs: 520 },
      { label: 'C3', delayMs: 2420, durMs: 220 },
      { label: 'E3', delayMs: 2650, durMs: 220 },
      { label: 'A3', delayMs: 2880, durMs: 220 },
      { label: 'B3', delayMs: 3110, durMs: 520 },
    ],
  },
  {
    id: 'twinkle',
    title: 'Twinkle Twinkle Little Star',
    notes: [
      { label: 'C3', delayMs: 0, durMs: 350 },
      { label: 'C3', delayMs: 380, durMs: 350 },
      { label: 'G3', delayMs: 760, durMs: 350 },
      { label: 'G3', delayMs: 1140, durMs: 350 },
      { label: 'A3', delayMs: 1520, durMs: 350 },
      { label: 'A3', delayMs: 1900, durMs: 350 },
      { label: 'G3', delayMs: 2280, durMs: 680 },
      { label: 'F3', delayMs: 3020, durMs: 350 },
      { label: 'F3', delayMs: 3400, durMs: 350 },
      { label: 'E3', delayMs: 3780, durMs: 350 },
      { label: 'E3', delayMs: 4160, durMs: 350 },
      { label: 'D3', delayMs: 4540, durMs: 350 },
      { label: 'D3', delayMs: 4920, durMs: 350 },
      { label: 'C3', delayMs: 5300, durMs: 680 },
    ],
  },
];

export const GamesApp: React.FC<GamesAppProps> = ({ onNotify }) => {
  const [instrumentIdx, setInstrumentIdx] = useState(0);
  const [showInstrumentMenu, setShowInstrumentMenu] = useState(false);
  const [sustainOn, setSustainOn] = useState(true);
  const [visibleWhiteCount, setVisibleWhiteCount] = useState<10 | 14 | 21>(10);
  const [startWhiteIdx, setStartWhiteIdx] = useState(0); // 0 = C1..E2 (matches screenshot)
  const [activeMidis, setActiveMidis] = useState<Set<number>>(new Set());
  const [lastNoteLabel, setLastNoteLabel] = useState<string>('C1');

  const [isRecording, setIsRecording] = useState(false);
  const [isPlayingBack, setIsPlayingBack] = useState(false);
  const [activePanel, setActivePanel] = useState<'none' | 'songs' | 'recordings'>('none');
  const [recordings, setRecordings] = useState<SavedRecording[]>(() => {
    try {
      const raw = localStorage.getItem(KEY_RECORDINGS);
      return raw ? (JSON.parse(raw) as SavedRecording[]) : [];
    } catch {
      return [];
    }
  });

  const audioCtxRef = useRef<AudioContext | null>(null);
  const pointerDownRef = useRef(false);
  const recordStartRef = useRef<number>(0);
  const recordedBufferRef = useRef<RecordedNote[]>([]);
  const playbackTimersRef = useRef<number[]>([]);

  const instrument = INSTRUMENTS[instrumentIdx];

  const getAudioCtx = useCallback((): AudioContext | null => {
    if (typeof window === 'undefined') return null;
    if (!audioCtxRef.current) {
      const Ctx =
        window.AudioContext ||
        (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      if (!Ctx) return null;
      audioCtxRef.current = new Ctx();
    }
    if (audioCtxRef.current.state === 'suspended') {
      audioCtxRef.current.resume().catch(() => {});
    }
    return audioCtxRef.current;
  }, []);

  const stopPlaybackTimers = useCallback(() => {
    playbackTimersRef.current.forEach((id) => window.clearTimeout(id));
    playbackTimersRef.current = [];
    setIsPlayingBack(false);
  }, []);

  useEffect(() => {
    return () => stopPlaybackTimers();
  }, [stopPlaybackTimers]);

  const playKey = useCallback(
    (key: PianoKeyDef, durationMs = 450, recordIt = true) => {
      const ctx = getAudioCtx();
      setLastNoteLabel(key.label);
      setActiveMidis((prev) => {
        const next = new Set(prev);
        next.add(key.midi);
        return next;
      });
      window.setTimeout(() => {
        setActiveMidis((prev) => {
          const next = new Set(prev);
          next.delete(key.midi);
          return next;
        });
      }, Math.min(260, durationMs));

      if (recordIt && isRecording) {
        recordedBufferRef.current.push({
          midi: key.midi,
          timeMs: Math.max(0, Date.now() - recordStartRef.current),
          durationMs,
        });
      }

      if (!ctx) return;
      const now = ctx.currentTime;
      const cfg = INSTRUMENTS[instrumentIdx];
      const masterGain = ctx.createGain();
      const filter = ctx.createBiquadFilter();
      filter.type = 'lowpass';
      filter.frequency.setValueAtTime(
        cfg.id === 'electric' ? 2400 : cfg.id === 'bright' ? 5200 : 3800,
        now
      );

      const decay = sustainOn ? cfg.decaySec * 1.45 : cfg.decaySec * 0.55;
      masterGain.gain.setValueAtTime(0.001, now);
      masterGain.gain.linearRampToValueAtTime(0.28, now + cfg.attackSec);
      masterGain.gain.exponentialRampToValueAtTime(0.0008, now + decay);

      filter.connect(masterGain);
      masterGain.connect(ctx.destination);

      cfg.harmonics.forEach((amp, idx) => {
        const osc = ctx.createOscillator();
        const harmonicGain = ctx.createGain();
        osc.type = idx === 0 ? cfg.wave : 'sine';
        osc.frequency.setValueAtTime(key.freq * (idx + 1), now);
        harmonicGain.gain.setValueAtTime(amp, now);
        osc.connect(harmonicGain);
        harmonicGain.connect(filter);
        osc.start(now);
        osc.stop(now + decay + 0.05);
      });
    },
    [getAudioCtx, instrumentIdx, isRecording, sustainOn]
  );

  const visibleWhites = useMemo(
    () => WHITE_KEYS.slice(startWhiteIdx, startWhiteIdx + visibleWhiteCount),
    [startWhiteIdx, visibleWhiteCount]
  );

  const visibleBlacks = useMemo(() => {
    const endIdx = startWhiteIdx + visibleWhiteCount - 1;
    return BLACK_KEYS.filter((b) => b.whiteIndex >= startWhiteIdx && b.whiteIndex < endIdx);
  }, [startWhiteIdx, visibleWhiteCount]);

  // Computer keyboard mapping onto visible keys
  useEffect(() => {
    const whiteChars = ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';', "'"];
    const blackMap: Record<string, number> = { w: 0, e: 1, t: 3, y: 4, u: 5, o: 7, p: 8 };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.repeat) return;
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === 'INPUT' || tag === 'TEXTAREA') return;
      const k = e.key.toLowerCase();
      const wIdx = whiteChars.indexOf(k);
      if (wIdx >= 0 && visibleWhites[wIdx]) {
        playKey(visibleWhites[wIdx]);
        return;
      }
      if (k in blackMap) {
        const targetWhiteGlobal = startWhiteIdx + blackMap[k];
        const black = BLACK_KEYS.find((b) => b.whiteIndex === targetWhiteGlobal);
        if (black) playKey(black);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [visibleWhites, startWhiteIdx, playKey]);

  const shiftViewport = (delta: number) => {
    const maxStart = Math.max(0, WHITE_KEYS.length - visibleWhiteCount);
    setStartWhiteIdx((prev) => Math.max(0, Math.min(maxStart, prev + delta)));
  };

  const toggleRecording = () => {
    if (!isRecording) {
      recordedBufferRef.current = [];
      recordStartRef.current = Date.now();
      setIsRecording(true);
      onNotify('البيانو', 'بدأ تسجيل المقطوعة الموسيقية...');
    } else {
      setIsRecording(false);
      const captured = recordedBufferRef.current;
      if (captured.length > 0) {
        const rec: SavedRecording = {
          id: `rec_${Date.now()}`,
          name: `تسجيل #${recordings.length + 1} (${captured.length} نغمة)`,
          instrument: instrument.id,
          createdAt: Date.now(),
          notes: captured,
        };
        const next = [rec, ...recordings].slice(0, 20);
        setRecordings(next);
        localStorage.setItem(KEY_RECORDINGS, JSON.stringify(next));
        onNotify('البيانو', `تم حفظ التسجيل (${captured.length} نغمة)`);
      } else {
        onNotify('البيانو', 'لم يتم عزف نغمات أثناء التسجيل');
      }
    }
  };

  const playNoteSequence = (notes: RecordedNote[], title: string) => {
    stopPlaybackTimers();
    if (notes.length === 0) return;
    setIsPlayingBack(true);
    setActivePanel('none');
    onNotify('البيانو', `تشغيل: ${title}`);

    let maxEnd = 0;
    notes.forEach((n) => {
      const key = ALL_KEYS.find((k) => k.midi === n.midi);
      if (!key) return;
      const t = window.setTimeout(() => {
        // Ensure played note stays in view if off-screen
        if (
          key.whiteIndex < startWhiteIdx ||
          key.whiteIndex >= startWhiteIdx + visibleWhiteCount
        ) {
          const maxStart = Math.max(0, WHITE_KEYS.length - visibleWhiteCount);
          setStartWhiteIdx(Math.max(0, Math.min(maxStart, key.whiteIndex - 2)));
        }
        playKey(key, n.durationMs, false);
      }, n.timeMs);
      playbackTimersRef.current.push(t);
      maxEnd = Math.max(maxEnd, n.timeMs + n.durationMs);
    });

    const endTimer = window.setTimeout(() => {
      setIsPlayingBack(false);
    }, maxEnd + 200);
    playbackTimersRef.current.push(endTimer);
  };

  const playDemoSong = (song: DemoSong) => {
    const seq: RecordedNote[] = song.notes
      .map((n) => {
        const found = ALL_KEYS.find((k) => k.label === n.label);
        return found ? { midi: found.midi, timeMs: n.delayMs, durationMs: n.durMs } : null;
      })
      .filter((x): x is RecordedNote => x !== null);
    playNoteSequence(seq, song.title);
  };

  const handlePrimaryPlayButton = () => {
    if (isPlayingBack) {
      stopPlaybackTimers();
      return;
    }
    if (recordings.length > 0) {
      playNoteSequence(recordings[0].notes, recordings[0].name);
    } else {
      playDemoSong(DEMO_SONGS[0]);
    }
  };

  const cycleSpan = () => {
    const next: 10 | 14 | 21 = visibleWhiteCount === 10 ? 14 : visibleWhiteCount === 14 ? 21 : 10;
    setVisibleWhiteCount(next);
    const maxStart = Math.max(0, WHITE_KEYS.length - next);
    setStartWhiteIdx((s) => Math.min(s, maxStart));
  };

  return (
    <div
      onPointerUp={() => {
        pointerDownRef.current = false;
      }}
      onPointerLeave={() => {
        pointerDownRef.current = false;
      }}
      className="flex-1 min-h-0 flex flex-col bg-[#080A0F] select-none overflow-hidden rounded-b-lg relative"
      dir="ltr"
    >
      {/* Top Studio Black Bar matching Piano Profesional */}
      <div className="h-[64px] shrink-0 bg-black px-3 flex items-center gap-2.5 border-b border-white/10 relative z-30">
        {/* White Rounded Acoustic Piano Selector */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowInstrumentMenu((v) => !v)}
            className="h-11 min-w-[165px] sm:min-w-[195px] px-4 rounded-xl bg-white hover:bg-slate-50 shadow-inner flex items-center justify-center text-[#C83232] font-semibold text-sm transition cursor-pointer whitespace-nowrap"
          >
            {instrument.label}
          </button>

          {showInstrumentMenu && (
            <div className="absolute left-0 top-full mt-1.5 z-50 w-52 bg-[#141821] border border-white/15 rounded-xl shadow-2xl py-1 text-xs text-white">
              {INSTRUMENTS.map((inst, idx) => (
                <button
                  key={inst.id}
                  type="button"
                  onClick={() => {
                    setInstrumentIdx(idx);
                    setShowInstrumentMenu(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 hover:bg-white/10 flex items-center justify-between cursor-pointer ${
                    idx === instrumentIdx ? 'text-[#F87171] font-bold bg-white/5' : 'text-slate-200'
                  }`}
                >
                  <span>{inst.label}</span>
                  {idx === instrumentIdx && <span>✓</span>}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Metallic Brushed Sustain / Audio Button */}
        <button
          type="button"
          onClick={() => setSustainOn((s) => !s)}
          title={sustainOn ? 'Sustain Pedal: ON' : 'Sustain Pedal: OFF'}
          className={`h-11 w-[74px] rounded-xl border border-white/40 shadow-md flex items-center justify-center transition cursor-pointer ${
            sustainOn
              ? 'bg-gradient-to-b from-[#FFFFFF] via-[#E1E4EA] to-[#AEB4BF] text-[#3B4252]'
              : 'bg-gradient-to-b from-[#9CA3AF] to-[#6B7280] text-slate-900 opacity-75'
          }`}
        >
          <span className="text-xl leading-none font-bold">♫</span>
        </button>

        {/* 5 Circular Light-Grey Tool Buttons */}
        <div className="flex items-center gap-2 sm:gap-2.5 overflow-x-auto">
          <button
            type="button"
            onClick={cycleSpan}
            title={`Visible keys: ${visibleWhiteCount}`}
            className="w-11 h-11 rounded-full bg-[#CCCCCC] hover:bg-[#E2E2E2] active:scale-95 transition flex items-center justify-center text-black shadow cursor-pointer shrink-0"
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5 fill-current">
              <path d="M3 5h18v14H3V5zm2 2v10h2.5v-5h2v5h2.5v-5h2v5h2.5v-5h2v5H19V7H5z" />
            </svg>
          </button>

          <button
            type="button"
            onClick={toggleRecording}
            title={isRecording ? 'Stop Recording' : 'Record Performance'}
            className={`w-11 h-11 rounded-full transition active:scale-95 flex items-center justify-center shadow cursor-pointer shrink-0 ${
              isRecording ? 'bg-red-950 ring-2 ring-red-500' : 'bg-[#CCCCCC] hover:bg-[#E2E2E2]'
            }`}
          >
            <span
              className={`w-5 h-5 rounded-full ${
                isRecording ? 'bg-red-500 animate-pulse' : 'bg-[#D91E18]'
              }`}
            />
          </button>

          <button
            type="button"
            onClick={handlePrimaryPlayButton}
            title={isPlayingBack ? 'Stop Playback' : 'Play Melody / Recording'}
            className={`w-11 h-11 rounded-full transition active:scale-95 flex items-center justify-center text-black shadow cursor-pointer shrink-0 ${
              isPlayingBack ? 'bg-emerald-300' : 'bg-[#CCCCCC] hover:bg-[#E2E2E2]'
            }`}
          >
            {isPlayingBack ? (
              <span className="w-3.5 h-3.5 bg-black rounded-[2px]" />
            ) : (
              <svg viewBox="0 0 24 24" className="w-5 h-5 fill-current ml-0.5">
                <path d="M8 5v14l11-7z" />
              </svg>
            )}
          </button>

          <button
            type="button"
            onClick={() => setActivePanel((p) => (p === 'songs' ? 'none' : 'songs'))}
            title="Built-in Piano Melodies"
            className={`w-11 h-11 rounded-full transition active:scale-95 flex items-center justify-center text-black shadow cursor-pointer shrink-0 ${
              activePanel === 'songs' ? 'bg-white ring-2 ring-[#3182CE]' : 'bg-[#CCCCCC] hover:bg-[#E2E2E2]'
            }`}
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5 fill-current">
              <path d="M6 3h9l5 5v13H6V3zm8 1.5V9h4.5L14 4.5zM8 11h8v1.5H8V11zm0 3h8v1.5H8V14zm0 3h6v1.5H8V17z" />
            </svg>
          </button>

          <button
            type="button"
            onClick={() => setActivePanel((p) => (p === 'recordings' ? 'none' : 'recordings'))}
            title="Saved Recordings"
            className={`w-11 h-11 rounded-full transition active:scale-95 flex items-center justify-center text-black shadow cursor-pointer shrink-0 ${
              activePanel === 'recordings' ? 'bg-white ring-2 ring-[#3182CE]' : 'bg-[#CCCCCC] hover:bg-[#E2E2E2]'
            }`}
          >
            <svg viewBox="0 0 24 24" className="w-5 h-5 fill-current">
              <path d="M4 6h2v2H4V6zm4 0h12v2H8V6zm-4 5h2v2H4v-2zm4 0h12v2H8v-2zm-4 5h2v2H4v-2zm4 0h12v2H8v-2z" />
            </svg>
          </button>
        </div>

        <div className="ml-auto hidden md:flex items-center gap-3 text-xs text-slate-300 font-mono-tabular">
          <span>{lastNoteLabel}</span>
          <span className="text-slate-500">·</span>
          <span>{visibleWhiteCount} Keys</span>
        </div>
      </div>

      {/* Dark Brushed Felt Mini-Piano Overview Navigation Strip */}
      <div className="h-12 shrink-0 bg-gradient-to-b from-[#1A202C] via-[#12161F] to-[#0B0E14] border-b border-black px-2 flex items-center justify-between gap-2">
        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={() => shiftViewport(-1)}
            disabled={startWhiteIdx === 0}
            title="Shift Left 1 Key"
            className="w-7 h-8 rounded hover:bg-white/10 disabled:opacity-30 text-white text-sm flex items-center justify-center cursor-pointer"
          >
            ◀
          </button>
          <button
            type="button"
            onClick={() => shiftViewport(-7)}
            disabled={startWhiteIdx === 0}
            title="Shift Left 1 Octave"
            className="w-8 h-8 rounded hover:bg-white/10 disabled:opacity-30 text-white text-sm flex items-center justify-center cursor-pointer"
          >
            ◀◀
          </button>
        </div>

        {/* Interactive Full 5-Octave Mini Keyboard Overview */}
        <div className="relative h-8 flex-1 max-w-[440px] bg-[#0B0D12] border border-white/15 rounded-xs overflow-hidden flex">
          {WHITE_KEYS.map((wk, idx) => {
            const inRange = idx >= startWhiteIdx && idx < startWhiteIdx + visibleWhiteCount;
            const hasSharp = wk.note !== 'E' && wk.note !== 'B' && idx < WHITE_KEYS.length - 1;
            return (
              <button
                key={wk.midi}
                type="button"
                onClick={() => {
                  const maxStart = Math.max(0, WHITE_KEYS.length - visibleWhiteCount);
                  setStartWhiteIdx(Math.max(0, Math.min(maxStart, idx - Math.floor(visibleWhiteCount / 2))));
                }}
                className={`relative flex-1 h-full border-r border-slate-500/50 transition-colors cursor-pointer ${
                  inRange ? 'bg-white' : 'bg-[#6F747D] hover:bg-[#8E949E]'
                }`}
                title={`Jump to ${wk.label}`}
              >
                {hasSharp && (
                  <span
                    className={`absolute top-0 right-[-28%] w-[56%] h-[58%] z-10 pointer-events-none ${
                      inRange ? 'bg-black' : 'bg-[#1D2026]'
                    }`}
                  />
                )}
              </button>
            );
          })}
        </div>

        <div className="flex items-center gap-1 shrink-0">
          <button
            type="button"
            onClick={() => shiftViewport(7)}
            disabled={startWhiteIdx >= WHITE_KEYS.length - visibleWhiteCount}
            title="Shift Right 1 Octave"
            className="w-8 h-8 rounded hover:bg-white/10 disabled:opacity-30 text-white text-sm flex items-center justify-center cursor-pointer"
          >
            ▶▶
          </button>
          <button
            type="button"
            onClick={() => shiftViewport(1)}
            disabled={startWhiteIdx >= WHITE_KEYS.length - visibleWhiteCount}
            title="Shift Right 1 Key"
            className="w-7 h-8 rounded hover:bg-white/10 disabled:opacity-30 text-white text-sm flex items-center justify-center cursor-pointer"
          >
            ▶
          </button>
        </div>
      </div>

      {/* Main Realistic Ivory & Ebony Piano Keyboard Stage */}
      <div className="relative flex-1 min-h-0 bg-[#1B1D22] px-1 pt-0.5 pb-1.5 flex overflow-hidden">
        <div className="relative flex-1 flex h-full w-full">
          {/* White Keys */}
          {visibleWhites.map((wk) => {
            const isPressed = activeMidis.has(wk.midi);
            const badgeColor = OCTAVE_BADGE_COLORS[wk.octave] || '#C97B7B';
            return (
              <div
                key={wk.midi}
                onPointerDown={(e) => {
                  e.preventDefault();
                  pointerDownRef.current = true;
                  playKey(wk);
                }}
                onPointerEnter={() => {
                  if (pointerDownRef.current) playKey(wk);
                }}
                className={`relative flex-1 h-full mx-[1.5px] rounded-b-[8px] border border-[#9FA5B0] flex flex-col justify-end items-center pb-3 transition-transform duration-75 cursor-pointer touch-none ${
                  isPressed
                    ? 'bg-gradient-to-b from-[#DFE4EC] via-[#EEF2F7] to-[#D1D8E3] translate-y-[2px] shadow-inner'
                    : 'bg-gradient-to-b from-[#ECEEEF] via-[#FAFAFC] to-[#E5E7EC] hover:brightness-[1.02] shadow-[inset_0_-8px_0_#C2C6CE,0_2px_4px_rgba(0,0,0,0.45)]'
                }`}
              >
                <span
                  style={{ backgroundColor: badgeColor }}
                  className="px-1.5 py-0.5 min-w-[24px] text-center rounded-[3px] text-[10px] font-bold text-[#14171D] shadow-xs font-mono-tabular"
                >
                  {wk.label}
                </span>
              </div>
            );
          })}

          {/* Black Sharp Keys Overlaid Accurately */}
          {visibleBlacks.map((bk) => {
            const localWhiteIdx = bk.whiteIndex - startWhiteIdx;
            const leftPct = ((localWhiteIdx + 1) / visibleWhiteCount) * 100;
            const widthPct = (0.6 / visibleWhiteCount) * 100;
            const isPressed = activeMidis.has(bk.midi);
            return (
              <div
                key={bk.midi}
                onPointerDown={(e) => {
                  e.stopPropagation();
                  e.preventDefault();
                  pointerDownRef.current = true;
                  playKey(bk);
                }}
                onPointerEnter={() => {
                  if (pointerDownRef.current) playKey(bk);
                }}
                style={{
                  left: `calc(${leftPct}% - ${widthPct / 2}%)`,
                  width: `${widthPct}%`,
                }}
                title={bk.label}
                className={`absolute top-0 h-[61%] z-20 rounded-b-[6px] border-x border-b border-black flex flex-col justify-end items-center pb-2 transition-transform duration-75 cursor-pointer touch-none ${
                  isPressed
                    ? 'bg-gradient-to-b from-[#2B303A] via-[#1B1E25] to-[#0D0F14] translate-y-[2px]'
                    : 'bg-gradient-to-b from-[#17191E] via-[#2C313A] to-[#0E1015] shadow-[inset_0_-6px_0_#3A404D,0_5px_10px_rgba(0,0,0,0.75)]'
                }`}
              >
                <div className="w-[62%] h-[82%] rounded-b-[4px] border border-white/15 bg-gradient-to-b from-white/10 to-transparent pointer-events-none" />
              </div>
            );
          })}
        </div>

        {/* Songbook Drawer Overlay */}
        {activePanel === 'songs' && (
          <div
            onClick={(e) => e.stopPropagation()}
            className="absolute top-2 right-2 z-40 w-72 bg-[#131720]/95 backdrop-blur-md border border-white/20 rounded-xl shadow-2xl p-3 text-white flex flex-col gap-2"
            dir="rtl"
          >
            <div className="flex items-center justify-between border-b border-white/15 pb-1.5">
              <span className="text-xs font-bold">مقطوعات جاهزة للتشغيل</span>
              <button
                type="button"
                onClick={() => setActivePanel('none')}
                className="text-slate-400 hover:text-white text-xs cursor-pointer"
              >
                ✕
              </button>
            </div>
            {DEMO_SONGS.map((song) => (
              <button
                key={song.id}
                type="button"
                onClick={() => playDemoSong(song)}
                className="flex items-center justify-between px-3 py-2 rounded-lg bg-white/5 hover:bg-white/15 text-xs text-left transition cursor-pointer"
                dir="ltr"
              >
                <span className="font-semibold truncate">{song.title}</span>
                <span className="text-emerald-400 font-bold">▶</span>
              </button>
            ))}
          </div>
        )}

        {/* Saved Recordings Drawer Overlay */}
        {activePanel === 'recordings' && (
          <div
            onClick={(e) => e.stopPropagation()}
            className="absolute top-2 right-2 z-40 w-72 bg-[#131720]/95 backdrop-blur-md border border-white/20 rounded-xl shadow-2xl p-3 text-white flex flex-col gap-2"
            dir="rtl"
          >
            <div className="flex items-center justify-between border-b border-white/15 pb-1.5">
              <span className="text-xs font-bold">التسجيلات المحفوظة ({recordings.length})</span>
              <button
                type="button"
                onClick={() => setActivePanel('none')}
                className="text-slate-400 hover:text-white text-xs cursor-pointer"
              >
                ✕
              </button>
            </div>
            {recordings.length === 0 ? (
              <div className="py-6 text-center text-xs text-slate-400">
                اضغط زر التسجيل الأحمر ثم اعزف لحفظ مقطوعتك هنا
              </div>
            ) : (
              <div className="max-h-56 overflow-y-auto flex flex-col gap-1.5">
                {recordings.map((rec) => (
                  <div
                    key={rec.id}
                    className="flex items-center justify-between gap-2 px-2.5 py-2 rounded-lg bg-white/5 text-xs"
                  >
                    <button
                      type="button"
                      onClick={() => playNoteSequence(rec.notes, rec.name)}
                      className="flex-1 text-right hover:text-emerald-400 font-semibold truncate cursor-pointer"
                    >
                      ▶ {rec.name}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        const next = recordings.filter((r) => r.id !== rec.id);
                        setRecordings(next);
                        localStorage.setItem(KEY_RECORDINGS, JSON.stringify(next));
                      }}
                      className="text-rose-400 hover:text-rose-300 px-1 cursor-pointer"
                      title="حذف"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
