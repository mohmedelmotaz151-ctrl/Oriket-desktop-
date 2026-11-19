import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AboutApp } from './apps/AboutApp';
import { BrowserApp } from './apps/BrowserApp';
import { CalculatorApp } from './apps/CalculatorApp';
import { CommandApp } from './apps/CommandApp';
import { ExcelApp } from './apps/ExcelApp';
import { FileExplorerApp } from './apps/FileExplorerApp';
import { FileViewerApp } from './apps/FileViewerApp';
import { GamesApp } from './apps/GamesApp';
import { NotepadApp } from './apps/NotepadApp';
import { PaintApp } from './apps/PaintApp';
import { PhotosApp } from './apps/PhotosApp';
import { RecycleBinApp } from './apps/RecycleBinApp';
import { SettingsApp } from './apps/SettingsApp';
import { TaskManagerApp } from './apps/TaskManagerApp';
import { VideoPlayerApp } from './apps/VideoPlayerApp';
import { Wmp11App } from './apps/Wmp11App';
import { WordApp } from './apps/WordApp';
import { BootScreen } from './components/BootScreen';
import { LoginScreen } from './components/LoginScreen';
import { NotificationCenterPanel } from './components/NotificationCenterPanel';
import { PowerMenuModal, ShutdownScreen, SleepOverlay } from './components/PowerMenuModal';
import { QuickSettingsPanel } from './components/QuickSettingsPanel';
import { StartMenu } from './components/StartMenu';
import { WindowsWallpaper } from './components/Wallpapers';
import { WindowShell } from './components/WindowShell';
import {
  AppId,
  DesktopNotice,
  ShortcutDefinition,
  WallpaperStyle,
  WindowEntry,
  WindowPayload,
} from './types/desktop';
import { getIconPathForTitle } from './utils/fluentIcons';
import { DesktopSoundKind, playDesktopSound } from './utils/sound';
import {
  DESKTOP_SHORTCUTS,
  getHiddenShortcuts,
  getRecycledItems,
  getShortcutLabel,
  getShortcutOrder,
  moveShortcutToRecycleBin,
  setHiddenShortcuts,
  setShortcutLabel,
  setShortcutOrder,
} from './utils/virtualFs';

type SystemPhase = 'boot' | 'login' | 'desktop' | 'sleep' | 'shutdown';

const KEY_WALLPAPER = 'orkeit_wallpaper_style';
const KEY_SOUND = 'orkeit_sound_enabled';
const KEY_SCALE = 'orkeit_desktop_scale';

export const App: React.FC = () => {
  const [phase, setPhase] = useState<SystemPhase>('boot');
  const [wallpaperStyle, setWallpaperStyle] = useState<WallpaperStyle>(() => {
    const v = Number(localStorage.getItem(KEY_WALLPAPER) || '0');
    return v === 1 || v === 2 ? v : 0;
  });
  const [soundEnabled, setSoundEnabled] = useState<boolean>(() => {
    const v = localStorage.getItem(KEY_SOUND);
    return v === null ? true : v === 'true';
  });
  const [desktopScale, setDesktopScale] = useState<number>(() => {
    const v = Number(localStorage.getItem(KEY_SCALE) || '1');
    return Number.isFinite(v) && v >= 0.75 && v <= 1.25 ? v : 1;
  });
  const [isBrowserFullscreen, setIsBrowserFullscreen] = useState(false);

  const [windows, setWindows] = useState<WindowEntry[]>([]);
  const [activeWindowId, setActiveWindowId] = useState<number | null>(null);
  const nextIdRef = useRef(1);
  const topZRef = useRef(20);

  const [showStart, setShowStart] = useState(false);
  const [showQuickSettings, setShowQuickSettings] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [showPowerModal, setShowPowerModal] = useState(false);

  const [notices, setNotices] = useState<DesktopNotice[]>([
    {
      id: 'welcome',
      title: 'مرحبًا بك في Oriket Desktop',
      message: 'تم تشغيل بيئة سطح المكتب بنجاح مع دعم تعدد النوافذ.',
      time: Date.now(),
    },
  ]);

  const [selectedShortcutKey, setSelectedShortcutKey] = useState<string | null>(null);
  const [shortcutTick, setShortcutTick] = useState(0);
  const [recycleHasItems, setRecycleHasItems] = useState(() => getRecycledItems().length > 0);
  const [desktopContextMenu, setDesktopContextMenu] = useState<{
    x: number;
    y: number;
    shortcut?: ShortcutDefinition;
  } | null>(null);
  const [renameShortcutModal, setRenameShortcutModal] = useState<{
    key: string;
    value: string;
  } | null>(null);
  const [propertiesShortcut, setPropertiesShortcut] = useState<ShortcutDefinition | null>(null);

  const [clock, setClock] = useState(() => new Date());
  const [desktopBounds, setDesktopBounds] = useState({ width: 1200, height: 700 });
  const stageRef = useRef<HTMLDivElement>(null);
  const pdfPickerRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const id = window.setInterval(() => setClock(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    const measure = () => {
      if (stageRef.current) {
        setDesktopBounds({
          width: stageRef.current.clientWidth,
          height: stageRef.current.clientHeight,
        });
      }
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [phase, desktopScale]);

  const notify = useCallback((title: string, message: string) => {
    setNotices((prev) => [
      { id: `n_${Date.now()}_${Math.random().toString(36).slice(2, 5)}`, title, message, time: Date.now() },
      ...prev.slice(0, 29),
    ]);
  }, []);

  const playSoundSafe = useCallback(
    (kind: DesktopSoundKind) => {
      playDesktopSound(kind, soundEnabled);
    },
    [soundEnabled]
  );

  const refreshRecycle = useCallback(() => {
    setRecycleHasItems(getRecycledItems().length > 0);
    setShortcutTick((t) => t + 1);
  }, []);

  const orderedShortcuts = useMemo(() => {
    const hidden = new Set(getHiddenShortcuts());
    const savedOrder = getShortcutOrder();
    const map = new Map(DESKTOP_SHORTCUTS.map((s) => [s.key, s]));
    const result: ShortcutDefinition[] = [];
    savedOrder.forEach((k) => {
      const item = map.get(k);
      if (item && !hidden.has(k)) {
        result.push(item);
        map.delete(k);
      }
    });
    DESKTOP_SHORTCUTS.forEach((s) => {
      if (map.has(s.key) && !hidden.has(s.key)) result.push(s);
    });
    return result;
  }, [shortcutTick]);

  const openWindow = useCallback(
    (appId: AppId, title: string, payload?: WindowPayload) => {
      playSoundSafe('start');
      setShowStart(false);
      setShowQuickSettings(false);
      setShowNotifications(false);

      const id = nextIdRef.current++;
      const z = ++topZRef.current;
      const bw = Math.max(640, desktopBounds.width);
      const bh = Math.max(420, desktopBounds.height);

      const isWideApp =
        appId === 'browser' ||
        appId === 'word' ||
        appId === 'excel' ||
        appId === 'paint' ||
        appId === 'games' ||
        appId === 'my_computer' ||
        appId === 'recycle_bin';

      const w = Math.min(bw - 24, isWideApp ? Math.max(760, Math.floor(bw * 0.78)) : 560);
      const h = Math.min(bh - 20, isWideApp ? Math.max(480, Math.floor(bh * 0.84)) : 460);
      const offset = ((id - 1) % 6) * 26;
      const x = Math.max(12, Math.min(bw - w - 12, Math.floor((bw - w) / 2) + offset - 30));
      const y = Math.max(10, Math.min(bh - h - 10, Math.floor((bh - h) / 2) + offset - 20));

      const shouldStartMax = appId === 'browser' && bw < 900;

      const entry: WindowEntry = {
        id,
        appId,
        title,
        x,
        y,
        width: w,
        height: h,
        minimized: false,
        maximized: shouldStartMax,
        zIndex: z,
        payload,
      };
      setWindows((prev) => [...prev, entry]);
      setActiveWindowId(id);
    },
    [desktopBounds, playSoundSafe]
  );

  const focusWindow = useCallback((id: number) => {
    const z = ++topZRef.current;
    setWindows((prev) =>
      prev.map((w) => (w.id === id ? { ...w, minimized: false, zIndex: z } : w))
    );
    setActiveWindowId(id);
  }, []);

  const closeWindow = useCallback((id: number) => {
    setWindows((prev) => prev.filter((w) => w.id !== id));
    setActiveWindowId((cur) => (cur === id ? null : cur));
  }, []);

  const toggleMaximizeWindow = useCallback((id: number) => {
    setWindows((prev) =>
      prev.map((w) => (w.id === id ? { ...w, maximized: !w.maximized, minimized: false } : w))
    );
  }, []);

  const minimizeWindow = useCallback((id: number) => {
    setWindows((prev) => prev.map((w) => (w.id === id ? { ...w, minimized: true } : w)));
    setActiveWindowId((cur) => (cur === id ? null : cur));
  }, []);

  const moveResizeWindow = useCallback(
    (id: number, patch: Partial<Pick<WindowEntry, 'x' | 'y' | 'width' | 'height'>>) => {
      setWindows((prev) => prev.map((w) => (w.id === id ? { ...w, ...patch } : w)));
    },
    []
  );

  const registerWindowHeaderActions = useCallback(
    (id: number, actions: NonNullable<WindowEntry['customHeaderAction']>) => {
      setWindows((prev) =>
        prev.map((w) => {
          if (w.id !== id) return w;
          if (w.customHeaderAction) return w;
          return { ...w, customHeaderAction: actions };
        })
      );
    },
    []
  );

  const checkForUpdates = useCallback(async () => {
    notify('تحديث التطبيق', 'جاري التحقق من أحدث إصدار عبر GitHub...');
    try {
      const res = await fetch(
        'https://api.github.com/repos/mohmedelmotaz151-ctrl/Oriket-desktop-/releases/latest'
      );
      if (!res.ok) throw new Error('No release');
      const json = (await res.json()) as { tag_name?: string; name?: string };
      notify('تحديث التطبيق', `أحدث إصدار متاح: ${json.tag_name || json.name || '1.0.0'}`);
    } catch {
      notify('تحديث التطبيق', 'أنت تستخدم أحدث إصدار من Oriket Desktop Web (1.0.0)');
    }
  }, [notify]);

  const handleShortcutLaunch = useCallback(
    (s: ShortcutDefinition) => {
      if (s.actionType === 'update') {
        checkForUpdates();
      } else if (s.actionType === 'pick_pdf') {
        pdfPickerRef.current?.click();
      } else if (s.actionType === 'url' && s.url) {
        openWindow('browser', s.windowTitle || s.defaultLabel, { url: s.url });
      } else if (s.appId) {
        openWindow(s.appId, getShortcutLabel(s.key));
      }
    },
    [checkForUpdates, openWindow]
  );

  // Global Keyboard Shortcuts
  useEffect(() => {
    if (phase !== 'desktop') return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.ctrlKey && e.shiftKey && e.key.toLowerCase() === 'escape') {
        e.preventDefault();
        openWindow('task_manager', 'إدارة المهام');
      } else if (e.key === 'F5') {
        e.preventDefault();
        refreshRecycle();
        notify('سطح المكتب', 'تم تحديث سطح المكتب');
      } else if (e.key === 'Escape') {
        setShowStart(false);
        setShowQuickSettings(false);
        setShowNotifications(false);
        setDesktopContextMenu(null);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [phase, openWindow, refreshRecycle, notify]);

  const toggleBrowserFullscreen = async () => {
    try {
      if (!document.fullscreenElement) {
        await document.documentElement.requestFullscreen();
        setIsBrowserFullscreen(true);
      } else {
        await document.exitFullscreen();
        setIsBrowserFullscreen(false);
      }
    } catch {
      // ignore if blocked
    }
  };

  const changeWallpaper = (style: WallpaperStyle) => {
    setWallpaperStyle(style);
    localStorage.setItem(KEY_WALLPAPER, String(style));
  };

  const toggleSound = () => {
    setSoundEnabled((prev) => {
      const next = !prev;
      localStorage.setItem(KEY_SOUND, String(next));
      return next;
    });
  };

  const changeDesktopScale = (next: number) => {
    setDesktopScale(next);
    localStorage.setItem(KEY_SCALE, String(next));
  };

  const moveShortcut = (key: string, delta: -1 | 1) => {
    const keys = orderedShortcuts.map((s) => s.key);
    const idx = keys.indexOf(key);
    if (idx < 0) return;
    const target = idx + delta;
    if (target < 0 || target >= keys.length) return;
    const copy = [...keys];
    const [removed] = copy.splice(idx, 1);
    copy.splice(target, 0, removed);
    setShortcutOrder(copy);
    setShortcutTick((t) => t + 1);
  };

  if (phase === 'boot') {
    return (
      <BootScreen
        soundEnabled={soundEnabled}
        onComplete={() => setPhase('login')}
      />
    );
  }

  if (phase === 'login') {
    return (
      <LoginScreen
        onSuccess={() => {
          playSoundSafe('start');
          setPhase('desktop');
        }}
      />
    );
  }

  if (phase === 'sleep') {
    return <SleepOverlay onWake={() => setPhase('desktop')} />;
  }

  if (phase === 'shutdown') {
    return <ShutdownScreen onPowerOn={() => setPhase('boot')} />;
  }

  const timeString = clock.toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });
  const dateString = clock.toLocaleDateString('ar-SA', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  });

  return (
    <div
      style={{
        zoom: desktopScale,
      }}
      onClick={() => {
        setShowStart(false);
        setShowQuickSettings(false);
        setShowNotifications(false);
        setDesktopContextMenu(null);
      }}
      className="relative w-screen h-screen overflow-hidden flex flex-col select-none bg-[#041432]"
    >
      <WindowsWallpaper styleIndex={wallpaperStyle} />

      <input
        ref={pdfPickerRef}
        type="file"
        accept="application/pdf,.pdf"
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (!file) return;
          const url = URL.createObjectURL(file);
          openWindow('file_viewer', file.name, {
            externalFile: { name: file.name, mime: 'application/pdf', dataUrl: url },
          });
          e.target.value = '';
        }}
      />

      {/* Desktop Workspace Stage */}
      <div
        ref={stageRef}
        onContextMenu={(e) => {
          e.preventDefault();
          setDesktopContextMenu({ x: e.clientX, y: e.clientY });
        }}
        className="relative flex-1 min-h-0 overflow-hidden"
      >
        {/* Vertical-first Windows Desktop Icon Grid */}
        <div className="absolute inset-0 p-3 grid grid-flow-col auto-cols-[84px] grid-rows-[repeat(auto-fill,88px)] gap-1.5 content-start justify-start pointer-events-auto">
          {orderedShortcuts.map((s) => {
            const label = getShortcutLabel(s.key);
            const isSelected = selectedShortcutKey === s.key;
            const iconSrc =
              s.appId === 'recycle_bin'
                ? recycleHasItems
                  ? '/icons/win11_recyclebin_full.png'
                  : '/icons/win11_recyclebin_empty.png'
                : getIconPathForTitle(s.iconKey);

            return (
              <button
                key={s.key}
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedShortcutKey(s.key);
                }}
                onDoubleClick={(e) => {
                  e.stopPropagation();
                  handleShortcutLaunch(s);
                }}
                onContextMenu={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  setSelectedShortcutKey(s.key);
                  setDesktopContextMenu({ x: e.clientX, y: e.clientY, shortcut: s });
                }}
                className={`w-[82px] h-[84px] rounded-lg px-1 py-1.5 flex flex-col items-center justify-start gap-1 text-center transition cursor-pointer ${
                  isSelected
                    ? 'bg-white/25 border border-white/55 backdrop-blur-xs'
                    : 'hover:bg-white/15 border border-transparent'
                }`}
              >
                <img
                  src={iconSrc}
                  alt=""
                  className="w-10 h-10 object-contain drop-shadow"
                  draggable={false}
                />
                <span
                  className={`text-[11px] leading-tight line-clamp-2 drop-shadow ${
                    wallpaperStyle === 2 ? 'text-[#0E1E38] font-semibold' : 'text-white font-medium'
                  }`}
                >
                  {label}
                </span>
              </button>
            );
          })}
        </div>

        {/* Open Multi-Window Stack */}
        {windows.map((win) => (
          <WindowShell
            key={win.id}
            win={win}
            isActive={win.id === activeWindowId}
            desktopBounds={desktopBounds}
            onFocus={() => focusWindow(win.id)}
            onMinimize={() => minimizeWindow(win.id)}
            onToggleMaximize={() => toggleMaximizeWindow(win.id)}
            onClose={() => closeWindow(win.id)}
            onMoveResize={(patch) => moveResizeWindow(win.id, patch)}
          >
            {win.appId === 'browser' && (
              <BrowserApp
                initialUrl={win.payload?.url}
                isMaximized={win.maximized}
                onToggleMaximize={() => toggleMaximizeWindow(win.id)}
                onNotify={notify}
              />
            )}
            {win.appId === 'my_computer' && (
              <FileExplorerApp
                initialFolder={win.payload?.initialFolder}
                onOpenApp={openWindow}
                onNotify={notify}
                onRecycleChanged={refreshRecycle}
              />
            )}
            {win.appId === 'recycle_bin' && (
              <RecycleBinApp
                onOpenApp={openWindow}
                onRecycleChanged={refreshRecycle}
                onNotify={notify}
              />
            )}
            {win.appId === 'word' && (
              <WordApp
                initialTitle={win.payload?.externalFile?.name}
                initialText={win.payload?.externalFile?.textContent}
                onNotify={notify}
              />
            )}
            {win.appId === 'excel' && (
              <ExcelApp
                initialCsvText={win.payload?.externalFile?.textContent}
                initialFileName={win.payload?.externalFile?.name}
                registerHeaderActions={(actions) => registerWindowHeaderActions(win.id, actions)}
                onNotify={notify}
              />
            )}
            {win.appId === 'paint' && <PaintApp onNotify={notify} />}
            {win.appId === 'music_player' && (
              <Wmp11App
                initialTrack={
                  win.payload?.externalFile?.dataUrl
                    ? {
                        name: win.payload.externalFile.name,
                        url: win.payload.externalFile.dataUrl,
                      }
                    : undefined
                }
                onToggleMaximize={() => toggleMaximizeWindow(win.id)}
                onNotify={notify}
              />
            )}
            {win.appId === 'photos' && <PhotosApp onNotify={notify} />}
            {win.appId === 'video_player' && (
              <VideoPlayerApp
                initialName={win.payload?.externalFile?.name}
                initialUrl={win.payload?.externalFile?.dataUrl}
                onNotify={notify}
              />
            )}
            {(win.appId === 'file_viewer' || win.appId === 'pdf_viewer') && (
              <FileViewerApp
                initialName={win.payload?.externalFile?.name}
                initialMime={win.payload?.externalFile?.mime}
                initialDataUrl={win.payload?.externalFile?.dataUrl}
                initialText={win.payload?.externalFile?.textContent}
                onNotify={notify}
              />
            )}
            {win.appId === 'calculator' && <CalculatorApp onNotify={notify} />}
            {win.appId === 'notepad' && (
              <NotepadApp
                initialText={win.payload?.externalFile?.textContent}
                initialFileName={win.payload?.externalFile?.name}
                onNotify={notify}
              />
            )}
            {win.appId === 'settings' && (
              <SettingsApp
                wallpaperStyle={wallpaperStyle}
                onChangeWallpaper={changeWallpaper}
                soundEnabled={soundEnabled}
                onToggleSound={toggleSound}
                desktopScale={desktopScale}
                onChangeDesktopScale={changeDesktopScale}
                onOpenApp={openWindow}
                onNotify={notify}
              />
            )}
            {win.appId === 'task_manager' && (
              <TaskManagerApp
                windows={windows}
                onFocusWindow={focusWindow}
                onCloseWindow={closeWindow}
              />
            )}
            {win.appId === 'command' && <CommandApp />}
            {win.appId === 'games' && <GamesApp onNotify={notify} />}
            {win.appId === 'about' && <AboutApp />}
          </WindowShell>
        ))}
      </div>

      {/* Windows 11 Frosted Glass Taskbar */}
      <footer
        onClick={(e) => e.stopPropagation()}
        className="relative z-[800] h-12 shrink-0 bg-[#EFF4F9]/88 backdrop-blur-xl border-t border-white/80 px-2.5 flex items-center justify-between gap-2"
      >
        {/* Left/Right Tray */}
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => {
              setShowNotifications((v) => !v);
              setShowStart(false);
              setShowQuickSettings(false);
            }}
            className="px-2 py-1 rounded-lg hover:bg-white/75 flex items-center gap-2 text-right cursor-pointer"
            title="التاريخ والإشعارات"
          >
            <div className="flex flex-col items-end leading-tight">
              <span className="text-[11px] font-bold text-[#122036] font-mono-tabular">
                {timeString}
              </span>
              <span className="text-[10px] text-slate-600">{dateString}</span>
            </div>
            <div className="relative">
              <img src="/icons/win11_bell.png" alt="" className="w-4 h-4" />
              {notices.length > 0 && (
                <span className="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-[#0067C0]" />
              )}
            </div>
          </button>

          <button
            type="button"
            onClick={() => {
              setShowQuickSettings((v) => !v);
              setShowStart(false);
              setShowNotifications(false);
            }}
            className="px-2 py-1.5 rounded-lg hover:bg-white/75 flex items-center gap-1.5 cursor-pointer"
            title="الإعدادات السريعة وتكبير الشاشة"
          >
            <img src="/icons/win11_wifi.png" alt="" className="w-4 h-4" />
            <img src="/icons/win11_music.png" alt="" className="w-4 h-4" />
            <img src="/icons/win11_battery.png" alt="" className="w-4 h-4" />
          </button>
        </div>

        {/* Center Start + Search + Open Window Pills */}
        <div className="flex-1 flex items-center justify-center gap-1.5 min-w-0 overflow-x-auto">
          <button
            type="button"
            onClick={() => {
              playSoundSafe('start');
              setShowStart((v) => !v);
              setShowQuickSettings(false);
              setShowNotifications(false);
            }}
            className={`w-9 h-9 rounded-lg flex items-center justify-center transition cursor-pointer shrink-0 ${
              showStart ? 'bg-white shadow-xs' : 'hover:bg-white/70'
            }`}
            title="ابدأ"
          >
            <img src="/icons/win11_start.png" alt="Start" className="w-6 h-6 object-contain" />
          </button>

          <button
            type="button"
            onClick={() => {
              setShowStart(true);
              setShowQuickSettings(false);
              setShowNotifications(false);
            }}
            className="w-9 h-9 rounded-lg hover:bg-white/70 flex items-center justify-center transition cursor-pointer shrink-0"
            title="بحث"
          >
            <img src="/icons/win11_search.png" alt="Search" className="w-5 h-5 object-contain" />
          </button>

          {/* Pinned quick apps when closed */}
          {(['browser', 'my_computer', 'word', 'excel', 'paint', 'music_player'] as AppId[]).map(
            (appId) => {
              const labelMap: Record<string, string> = {
                browser: 'متصفح الإنترنت',
                my_computer: 'هذا الكمبيوتر',
                word: 'Word / معالج النصوص',
                excel: 'Excel / الجداول',
                paint: 'الرسام',
                music_player: 'مشغل الموسيقى',
              };
              const hasOpen = windows.some((w) => w.appId === appId);
              if (hasOpen) return null;
              return (
                <button
                  key={appId}
                  type="button"
                  onClick={() => openWindow(appId, labelMap[appId])}
                  className="w-9 h-9 rounded-lg hover:bg-white/70 hidden sm:flex items-center justify-center transition cursor-pointer shrink-0"
                  title={labelMap[appId]}
                >
                  <img
                    src={getIconPathForTitle(labelMap[appId])}
                    alt=""
                    className="w-5 h-5 object-contain"
                  />
                </button>
              );
            }
          )}

          {/* Active / Open Windows */}
          {windows.map((w) => {
            const isAct = w.id === activeWindowId && !w.minimized;
            return (
              <button
                key={w.id}
                type="button"
                onClick={() => {
                  if (isAct) minimizeWindow(w.id);
                  else focusWindow(w.id);
                }}
                className={`relative h-9 px-2.5 rounded-lg flex items-center gap-1.5 text-xs font-semibold max-w-[140px] transition cursor-pointer shrink-0 ${
                  isAct
                    ? 'bg-white/95 text-[#122036] shadow-2xs border border-slate-200/70'
                    : 'bg-white/45 hover:bg-white/75 text-slate-700'
                }`}
              >
                <img src={getIconPathForTitle(w.title)} alt="" className="w-4 h-4 shrink-0" />
                <span className="truncate hidden md:inline">{w.title}</span>
                <span
                  className={`absolute bottom-0.5 left-1/2 -translate-x-1/2 h-[3px] rounded-full transition-all ${
                    isAct ? 'w-5 bg-[#0067C0]' : 'w-2 bg-slate-400'
                  }`}
                />
              </button>
            );
          })}
        </div>

        {/* Right spacer / Desktop Scale Quick Indicator */}
        <div className="hidden md:flex items-center gap-1.5 text-[11px] text-slate-600">
          <button
            type="button"
            onClick={() => setPhase('login')}
            className="px-2 py-1 rounded-md hover:bg-white/75 cursor-pointer"
            title="قفل الشاشة"
          >
            🔒
          </button>
        </div>
      </footer>

      {/* Popovers */}
      {showStart && (
        <StartMenu
          onOpenApp={(appId, title, payload) => openWindow(appId, title, payload)}
          onOpenExternalUrl={(url, title) => openWindow('browser', title, { url })}
          onCheckUpdates={checkForUpdates}
          onPickPdf={() => pdfPickerRef.current?.click()}
          onShowPowerMenu={() => setShowPowerModal(true)}
          onClose={() => setShowStart(false)}
        />
      )}

      {showQuickSettings && (
        <QuickSettingsPanel
          soundEnabled={soundEnabled}
          onToggleSound={toggleSound}
          desktopScale={desktopScale}
          onChangeDesktopScale={changeDesktopScale}
          isBrowserFullscreen={isBrowserFullscreen}
          onToggleBrowserFullscreen={toggleBrowserFullscreen}
          onOpenSettings={(id = 'settings', t = 'لوحة التحكم') => openWindow(id, t)}
          onNotify={notify}
          onClose={() => setShowQuickSettings(false)}
        />
      )}

      {showNotifications && (
        <NotificationCenterPanel
          notices={notices}
          onClearAll={() => setNotices([])}
          onOpenSettings={() => openWindow('settings', 'لوحة التحكم')}
          onClose={() => setShowNotifications(false)}
        />
      )}

      {showPowerModal && (
        <PowerMenuModal
          onSleep={() => {
            setShowPowerModal(false);
            setPhase('sleep');
          }}
          onRestart={() => {
            setShowPowerModal(false);
            setWindows([]);
            setPhase('boot');
          }}
          onShutdown={() => {
            setShowPowerModal(false);
            setWindows([]);
            setPhase('shutdown');
          }}
          onClose={() => setShowPowerModal(false)}
        />
      )}

      {/* Desktop / Shortcut Context Menu */}
      {desktopContextMenu && (
        <div
          onClick={(e) => e.stopPropagation()}
          style={{
            left: Math.min(desktopContextMenu.x, window.innerWidth - 190),
            top: Math.min(desktopContextMenu.y, window.innerHeight - 240),
          }}
          className="fixed z-[1300] w-44 bg-white border border-slate-200 rounded-xl shadow-2xl py-1.5 text-xs text-slate-800"
          dir="rtl"
        >
          {desktopContextMenu.shortcut ? (
            <>
              <button
                type="button"
                onClick={() => {
                  handleShortcutLaunch(desktopContextMenu.shortcut!);
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 font-bold cursor-pointer"
              >
                فتح
              </button>
              <button
                type="button"
                onClick={() => {
                  moveShortcut(desktopContextMenu.shortcut!.key, -1);
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                نقل للأعلى
              </button>
              <button
                type="button"
                onClick={() => {
                  moveShortcut(desktopContextMenu.shortcut!.key, 1);
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                نقل للأسفل
              </button>
              <button
                type="button"
                onClick={() => {
                  const key = desktopContextMenu.shortcut!.key;
                  setRenameShortcutModal({ key, value: getShortcutLabel(key) });
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                إعادة تسمية
              </button>
              <button
                type="button"
                onClick={() => {
                  setPropertiesShortcut(desktopContextMenu.shortcut!);
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                خصائص
              </button>
              {desktopContextMenu.shortcut.appId !== 'recycle_bin' && (
                <button
                  type="button"
                  onClick={() => {
                    moveShortcutToRecycleBin(desktopContextMenu.shortcut!.key);
                    refreshRecycle();
                    notify('سلة المحذوفات', `تم نقل "${getShortcutLabel(desktopContextMenu.shortcut!.key)}" إلى السلة`);
                    setDesktopContextMenu(null);
                  }}
                  className="w-full text-right px-3 py-1.5 hover:bg-red-50 text-red-600 cursor-pointer"
                >
                  حذف إلى السلة
                </button>
              )}
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => {
                  refreshRecycle();
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                تحديث (F5)
              </button>
              <button
                type="button"
                onClick={() => {
                  changeWallpaper(((wallpaperStyle + 1) % 3) as WallpaperStyle);
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                تغيير الخلفية
              </button>
              <button
                type="button"
                onClick={() => {
                  setHiddenShortcuts([]);
                  refreshRecycle();
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                إظهار كل الأيقونات
              </button>
              <button
                type="button"
                onClick={() => {
                  openWindow('settings', 'لوحة التحكم');
                  setDesktopContextMenu(null);
                }}
                className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                الإعدادات
              </button>
            </>
          )}
        </div>
      )}

      {/* Rename Shortcut Modal */}
      {renameShortcutModal && (
        <div
          onClick={() => setRenameShortcutModal(null)}
          className="fixed inset-0 z-[1500] bg-black/45 flex items-center justify-center p-4"
          dir="rtl"
        >
          <form
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault();
              if (renameShortcutModal.value.trim()) {
                setShortcutLabel(renameShortcutModal.key, renameShortcutModal.value.trim());
                setShortcutTick((t) => t + 1);
              }
              setRenameShortcutModal(null);
            }}
            className="w-full max-w-xs bg-white rounded-xl shadow-2xl p-4 flex flex-col gap-3"
          >
            <div className="text-xs font-bold text-[#122036]">إعادة تسمية الاختصار</div>
            <input
              type="text"
              value={renameShortcutModal.value}
              onChange={(e) =>
                setRenameShortcutModal({ ...renameShortcutModal, value: e.target.value })
              }
              className="h-9 px-3 rounded-lg border border-slate-300 text-xs"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setRenameShortcutModal(null)} className="px-3 py-1.5 text-xs text-slate-600 cursor-pointer">إلغاء</button>
              <button type="submit" className="px-4 py-1.5 rounded-lg bg-[#0067C0] text-white text-xs font-semibold cursor-pointer">حفظ</button>
            </div>
          </form>
        </div>
      )}

      {/* Properties Shortcut Modal */}
      {propertiesShortcut && (
        <div
          onClick={() => setPropertiesShortcut(null)}
          className="fixed inset-0 z-[1500] bg-black/45 flex items-center justify-center p-4"
          dir="rtl"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-xs bg-white rounded-xl shadow-2xl p-4 flex flex-col gap-2.5 text-xs"
          >
            <div className="font-bold text-sm text-[#122036]">خصائص الاختصار</div>
            <div>الاسم: {getShortcutLabel(propertiesShortcut.key)}</div>
            <div>النوع: اختصار تطبيق سطح المكتب</div>
            {propertiesShortcut.url && <div className="truncate" dir="ltr">URL: {propertiesShortcut.url}</div>}
            <button
              type="button"
              onClick={() => setPropertiesShortcut(null)}
              className="self-end px-4 py-1.5 rounded-lg bg-[#0067C0] text-white font-semibold cursor-pointer"
            >
              موافق
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
