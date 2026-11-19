import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createDemoTrackWavUrl } from '../utils/sound';
import { getWorkspaceFiles } from '../utils/virtualFs';

interface Wmp11AppProps {
  initialTrack?: { name: string; url: string };
  onToggleMaximize?: () => void;
  onNotify: (title: string, msg: string) => void;
}

interface AudioTrack {
  id: string;
  title: string;
  artist: string;
  url: string;
  durationSec?: number;
}

function formatSec(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export const Wmp11App: React.FC<Wmp11AppProps> = ({
  initialTrack,
  onToggleMaximize,
  onNotify,
}) => {
  const [tracks, setTracks] = useState<AudioTrack[]>(() =>
    initialTrack
      ? [{ id: 'init', title: initialTrack.name, artist: 'Local Audio', url: initialTrack.url }]
      : []
  );
  const [currentIndex, setCurrentIndex] = useState<number>(initialTrack ? 0 : -1);
  const [activeTab, setActiveTab] = useState<'now_playing' | 'library' | 'queue' | 'effects'>('now_playing');
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(0.85);
  const [muted, setMuted] = useState(false);
  const [shuffle, setShuffle] = useState(false);
  const [repeat, setRepeat] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [hudVisible, setHudVisible] = useState(true);

  const audioRef = useRef<HTMLAudioElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const currentTrack = currentIndex >= 0 ? tracks[currentIndex] : null;

  // Animated WMP 11 Silk Wave + 28 Spectrum Bars Canvas
  useEffect(() => {
    if (activeTab !== 'now_playing') return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let raf = 0;
    let phase = 0;

    const render = () => {
      const w = (canvas.width = canvas.clientWidth || 640);
      const h = (canvas.height = canvas.clientHeight || 300);
      ctx.clearRect(0, 0, w, h);

      const bg = ctx.createRadialGradient(w * 0.5, h * 0.45, 20, w * 0.5, h * 0.5, Math.max(w, h) * 0.7);
      bg.addColorStop(0, '#0D2246');
      bg.addColorStop(0.55, '#071124');
      bg.addColorStop(1, '#040812');
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, w, h);

      const speedMul = isPlaying ? 0.045 : 0.012;
      phase += speedMul;

      const waveConfigs = [
        { amp: isPlaying ? 34 : 12, freq: 0.012, color: 'rgba(84, 210, 255, 0.42)', offset: 0 },
        { amp: isPlaying ? 26 : 9, freq: 0.017, color: 'rgba(56, 152, 255, 0.35)', offset: 1.4 },
        { amp: isPlaying ? 19 : 7, freq: 0.023, color: 'rgba(130, 232, 255, 0.28)', offset: 2.8 },
      ];

      waveConfigs.forEach((cfg) => {
        ctx.beginPath();
        for (let x = 0; x <= w; x += 6) {
          const y =
            h * 0.48 +
            Math.sin(x * cfg.freq + phase + cfg.offset) * cfg.amp +
            Math.cos(x * cfg.freq * 0.6 - phase * 0.7) * (cfg.amp * 0.4);
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.strokeStyle = cfg.color;
        ctx.lineWidth = 2.4;
        ctx.stroke();
      });

      // 28 bottom-center neon cyan spectrum bars
      if (isPlaying) {
        const bars = 28;
        const barW = 5;
        const gap = 4;
        const totalW = bars * (barW + gap);
        const startX = (w - totalW) / 2;
        for (let i = 0; i < bars; i++) {
          const bh =
            8 +
            Math.abs(Math.sin(phase * 1.9 + i * 0.45) * Math.cos(phase * 1.1 - i * 0.25)) * 38;
          ctx.fillStyle = 'rgba(72, 208, 255, 0.72)';
          ctx.fillRect(startX + i * (barW + gap), h - 18 - bh, barW, bh);
        }
      }

      raf = requestAnimationFrame(render);
    };

    raf = requestAnimationFrame(render);
    return () => cancelAnimationFrame(raf);
  }, [activeTab, isPlaying]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.volume = muted ? 0 : volume;
    audio.playbackRate = speed;
  }, [volume, muted, speed]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !currentTrack) return;
    audio.src = currentTrack.url;
    audio.playbackRate = speed;
    audio
      .play()
      .then(() => setIsPlaying(true))
      .catch(() => setIsPlaying(false));
  }, [currentTrack?.id]);

  const loadDemoAndWorkspaceTracks = () => {
    const wsAudio = getWorkspaceFiles()
      .filter((f) => !f.isDirectory && (f.mimeType.startsWith('audio/') || /\.(mp3|wav|ogg|m4a)$/i.test(f.name)) && f.content)
      .map<AudioTrack>((f) => ({
        id: f.id,
        title: f.name,
        artist: 'C:\\Music',
        url: f.content!,
      }));

    const demos: AudioTrack[] = [
      { id: 'demo_1', title: 'Aero Glass Horizon', artist: 'Oriket Sound Lab', url: createDemoTrackWavUrl(220, 329.63, 20) },
      { id: 'demo_2', title: 'Midnight Aurora Synth', artist: 'Oriket Sound Lab', url: createDemoTrackWavUrl(261.63, 392.0, 20) },
      { id: 'demo_3', title: 'Cobalt Pulse Ambient', artist: 'Oriket Sound Lab', url: createDemoTrackWavUrl(196.0, 293.66, 20) },
    ];

    const merged = [...wsAudio, ...demos];
    setTracks(merged);
    if (merged.length > 0 && currentIndex === -1) {
      setCurrentIndex(0);
    }
    onNotify('مشغل الموسيقى', `تم تحميل ${merged.length} مقاطع صوتية`);
  };

  const handleOpenAudioFiles = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;
    const added: AudioTrack[] = files.map((file, i) => ({
      id: `aud_${Date.now()}_${i}`,
      title: file.name.replace(/\.[^.]+$/, ''),
      artist: 'ملف محلي',
      url: URL.createObjectURL(file),
    }));
    const startIdx = tracks.length;
    setTracks((prev) => [...prev, ...added]);
    setCurrentIndex(startIdx);
    onNotify('مشغل الموسيقى', `تمت إضافة ${added.length} مقطع صوتي`);
    e.target.value = '';
  };

  const playNext = () => {
    if (tracks.length === 0) return;
    if (shuffle && tracks.length > 1) {
      setCurrentIndex(Math.floor(Math.random() * tracks.length));
    } else {
      setCurrentIndex((i) => (i + 1) % tracks.length);
    }
  };

  const playPrev = () => {
    if (tracks.length === 0) return;
    setCurrentIndex((i) => (i - 1 + tracks.length) % tracks.length);
  };

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (!currentTrack) {
      if (tracks.length > 0) setCurrentIndex(0);
      else loadDemoAndWorkspaceTracks();
      return;
    }
    if (isPlaying) {
      audio.pause();
      setIsPlaying(false);
    } else {
      audio.play().then(() => setIsPlaying(true)).catch(() => {});
    }
  };

  const stopPlay = () => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.pause();
    audio.currentTime = 0;
    setIsPlaying(false);
    setCurrentTime(0);
  };

  const tabHistory = useMemo(() => ['now_playing', 'library', 'queue', 'effects'] as const, []);

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-[#060C18] text-[#EBF4FF] select-none overflow-hidden" dir="ltr">
      <audio
        ref={audioRef}
        onTimeUpdate={(e) => setCurrentTime(e.currentTarget.currentTime)}
        onLoadedMetadata={(e) => setDuration(e.currentTarget.duration || 0)}
        onEnded={() => {
          if (repeat && audioRef.current) {
            audioRef.current.currentTime = 0;
            audioRef.current.play().catch(() => {});
          } else {
            playNext();
          }
        }}
      />

      <input
        ref={fileInputRef}
        type="file"
        accept="audio/*"
        multiple
        className="hidden"
        onChange={handleOpenAudioFiles}
      />

      {/* Top WMP11 Aero Glass Toolbar */}
      <div className="h-8 px-2.5 bg-gradient-to-b from-[#1D2B44] to-[#0E1626] border-b border-white/10 flex items-center gap-2 text-xs">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => {
              const idx = tabHistory.indexOf(activeTab);
              setActiveTab(tabHistory[(idx - 1 + tabHistory.length) % tabHistory.length]);
            }}
            className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center cursor-pointer"
          >
            ◀
          </button>
          <button
            type="button"
            onClick={() => {
              const idx = tabHistory.indexOf(activeTab);
              setActiveTab(tabHistory[(idx + 1) % tabHistory.length]);
            }}
            className="w-6 h-6 rounded-full bg-white/10 hover:bg-white/20 flex items-center justify-center cursor-pointer"
          >
            ▶
          </button>
        </div>

        <div className="flex items-center gap-1 ml-1">
          {[
            { id: 'now_playing', label: 'Now Playing ▾' },
            { id: 'library', label: 'Library' },
            { id: 'queue', label: 'Queue' },
            { id: 'effects', label: 'Effects' },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setActiveTab(t.id as typeof activeTab)}
              className={`px-2.5 py-1 rounded-md text-[11px] font-semibold transition cursor-pointer ${
                activeTab === t.id
                  ? 'bg-[#2C72D6] text-white shadow-xs'
                  : 'text-slate-300 hover:bg-white/10'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          title="فتح ملف صوتي"
          className="ml-auto px-2 py-1 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
        >
          📂
        </button>
        <span className="text-[11px] font-bold tracking-[0.2em] text-cyan-300/90 hidden sm:inline">
          ORIKET
        </span>
      </div>

      {/* Main Content Stage */}
      <div className="relative flex-1 min-h-0 flex flex-col">
        {activeTab === 'now_playing' && (
          <div
            onClick={() => setHudVisible((v) => !v)}
            className="relative flex-1 min-h-0 cursor-pointer overflow-hidden"
          >
            <canvas ref={canvasRef} className="w-full h-full block" />
            {hudVisible && (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-4 pointer-events-none">
                <div className="text-sm sm:text-base font-bold text-white drop-shadow">
                  {currentTrack ? currentTrack.title : 'Windows Media Player 11'}
                </div>
                <div className="text-xs text-cyan-200/80">
                  {currentTrack ? currentTrack.artist : 'اختر مقاطع صوتية أو حمّل موسيقى تجريبية'}
                </div>
                {tracks.length === 0 && (
                  <div className="flex flex-wrap items-center justify-center gap-2 mt-2 pointer-events-auto" dir="rtl">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        loadDemoAndWorkspaceTracks();
                      }}
                      className="px-4 py-2 rounded-full bg-[#1C6DD0] hover:bg-[#2480EB] text-white text-xs font-bold shadow-lg cursor-pointer"
                    >
                      ♪ تحميل موسيقى الجهاز
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        fileInputRef.current?.click();
                      }}
                      className="px-4 py-2 rounded-full bg-white/15 hover:bg-white/25 text-white text-xs font-semibold cursor-pointer"
                    >
                      📂 فتح ملف صوتي
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {(activeTab === 'library' || activeTab === 'queue') && (
          <div className="flex-1 min-h-0 overflow-y-auto p-3 flex flex-col gap-2" dir="rtl">
            <div className="flex items-center justify-between pb-2 border-b border-white/10">
              <span className="text-xs font-bold text-cyan-200">
                {activeTab === 'library' ? 'مكتبة الصوتيات' : 'قائمة الانتظار'} ({tracks.length})
              </span>
              <div className="flex items-center gap-1.5">
                <button
                  type="button"
                  onClick={loadDemoAndWorkspaceTracks}
                  className="px-2.5 py-1 rounded bg-[#1C6DD0] text-white text-[11px] font-semibold cursor-pointer"
                >
                  تحميل الموسيقى
                </button>
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="px-2.5 py-1 rounded bg-white/10 text-white text-[11px] cursor-pointer"
                >
                  + إضافة ملف
                </button>
              </div>
            </div>
            {tracks.length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-400">
                المكتبة فارغة حاليًا
              </div>
            ) : (
              tracks.map((tr, idx) => (
                <button
                  key={tr.id}
                  type="button"
                  onClick={() => {
                    setCurrentIndex(idx);
                    setActiveTab('now_playing');
                  }}
                  className={`flex items-center justify-between px-3 py-2 rounded-lg text-right text-xs transition cursor-pointer ${
                    idx === currentIndex
                      ? 'bg-[#1D5DB5]/60 border border-cyan-400/50 text-white'
                      : 'bg-white/5 hover:bg-white/10 text-slate-200'
                  }`}
                >
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="text-cyan-400 font-mono-tabular">{idx + 1}.</span>
                    <span className="truncate font-semibold">{tr.title}</span>
                  </div>
                  <span className="text-[11px] text-slate-400">{tr.artist}</span>
                </button>
              ))
            )}
          </div>
        )}

        {activeTab === 'effects' && (
          <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-4 p-4" dir="rtl">
            <div className="text-sm font-bold text-cyan-200">سرعة التشغيل والمؤثرات الصوتية</div>
            <div className="flex items-center gap-2">
              {[0.75, 1.0, 1.25, 1.5].map((sp) => (
                <button
                  key={sp}
                  type="button"
                  onClick={() => setSpeed(sp)}
                  className={`px-3.5 py-1.5 rounded-full text-xs font-mono-tabular font-bold cursor-pointer ${
                    speed === sp ? 'bg-cyan-500 text-slate-950' : 'bg-white/10 text-white hover:bg-white/20'
                  }`}
                >
                  {sp}x
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Seekbar */}
      <div className="px-3 py-1 bg-[#091224] flex items-center gap-2">
        <input
          type="range"
          min={0}
          max={duration || 100}
          value={Math.min(currentTime, duration || 100)}
          onChange={(e) => {
            const val = Number(e.target.value);
            setCurrentTime(val);
            if (audioRef.current) audioRef.current.currentTime = val;
          }}
          className="w-full h-1 accent-cyan-400 cursor-pointer"
        />
      </div>

      {/* Bottom Aero Glass Transport Pod */}
      <div className="px-3 py-2 bg-gradient-to-b from-[#17253D] to-[#0B1220] border-t border-white/10 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0 max-w-[180px]">
          <div className="w-8 h-8 rounded bg-[#0E284D] border border-cyan-400/40 flex items-center justify-center text-cyan-300 text-xs shrink-0">
            ♪
          </div>
          <div className="min-w-0">
            <div className="text-[11px] font-bold truncate">
              {currentTrack ? currentTrack.title : 'Ready'}
            </div>
            <div className="text-[10px] text-cyan-200/75 font-mono-tabular">
              {formatSec(currentTime)} / {formatSec(duration)}
            </div>
          </div>
        </div>

        {/* Center Glossy Transport Controls */}
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setShuffle((s) => !s)}
            className={`w-7 h-7 rounded-full text-xs cursor-pointer ${
              shuffle ? 'bg-cyan-500/30 text-cyan-300' : 'bg-white/10 text-slate-300 hover:bg-white/20'
            }`}
            title="Shuffle"
          >
            ⇄
          </button>
          <button
            type="button"
            onClick={() => setRepeat((r) => !r)}
            className={`w-7 h-7 rounded-full text-xs cursor-pointer ${
              repeat ? 'bg-cyan-500/30 text-cyan-300' : 'bg-white/10 text-slate-300 hover:bg-white/20'
            }`}
            title="Repeat"
          >
            ↻
          </button>
          <button
            type="button"
            onClick={stopPlay}
            className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
            title="Stop"
          >
            ■
          </button>
          <button
            type="button"
            onClick={playPrev}
            className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 text-[10px] cursor-pointer"
            title="Previous"
          >
            |◀◀
          </button>
          {/* 3D Cobalt Glossy Play Orb */}
          <button
            type="button"
            onClick={togglePlay}
            className="w-10 h-10 rounded-full bg-gradient-to-b from-[#49A5FF] via-[#1B67D8] to-[#0C357D] border border-cyan-300/70 shadow-lg flex items-center justify-center text-white text-sm font-bold hover:brightness-110 active:scale-95 transition cursor-pointer"
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? '❚❚' : '▶'}
          </button>
          <button
            type="button"
            onClick={playNext}
            className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 text-[10px] cursor-pointer"
            title="Next"
          >
            ▶▶|
          </button>
        </div>

        {/* Volume & View Switch */}
        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => setMuted((m) => !m)}
            className="w-7 h-7 rounded-full bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
          >
            {muted ? '🔇' : '🔊'}
          </button>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={muted ? 0 : volume}
            onChange={(e) => {
              setMuted(false);
              setVolume(Number(e.target.value));
            }}
            className="w-16 h-1 accent-cyan-400 cursor-pointer"
          />
          <button
            type="button"
            onClick={() => setActiveTab((t) => (t === 'library' ? 'now_playing' : 'library'))}
            className="w-7 h-7 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
            title="Switch to Library"
          >
            ▤
          </button>
          {onToggleMaximize && (
            <button
              type="button"
              onClick={onToggleMaximize}
              className="w-7 h-7 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
              title="Maximize"
            >
              ⛶
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
