import React, { useMemo, useRef, useState } from 'react';

interface BrowserAppProps {
  initialUrl?: string;
  isMaximized?: boolean;
  onToggleMaximize?: () => void;
  onNotify: (title: string, msg: string) => void;
}

interface Bookmark {
  title: string;
  url: string;
}

const KEY_BOOKMARKS = 'orkeit_browser_bookmarks';
const DEFAULT_BOOKMARKS: Bookmark[] = [
  { title: 'زيارات الدفاع المدني', url: 'https://orkeitstm.vercel.app' },
  { title: 'ويكيبيديا العربية', url: 'https://ar.wikipedia.org' },
  { title: 'Google', url: 'https://www.google.com' },
];

export const BrowserApp: React.FC<BrowserAppProps> = ({
  initialUrl = 'https://orkeitstm.vercel.app',
  isMaximized = false,
  onToggleMaximize,
  onNotify,
}) => {
  const [history, setHistory] = useState<string[]>([initialUrl]);
  const [historyIndex, setHistoryIndex] = useState(0);
  const [addressInput, setAddressInput] = useState(initialUrl);
  const [reloadNonce, setReloadNonce] = useState(0);
  const [zoom, setZoom] = useState<number>(0.85);
  const [showBookmarks, setShowBookmarks] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [bookmarks, setBookmarks] = useState<Bookmark[]>(() => {
    try {
      const raw = localStorage.getItem(KEY_BOOKMARKS);
      return raw ? (JSON.parse(raw) as Bookmark[]) : DEFAULT_BOOKMARKS;
    } catch {
      return DEFAULT_BOOKMARKS;
    }
  });

  const browserRootRef = useRef<HTMLDivElement>(null);
  const currentUrl = history[historyIndex] || initialUrl;

  const normalizeUrl = (raw: string): string => {
    const trimmed = raw.trim();
    if (!trimmed) return 'https://www.google.com';
    if (/^https?:\/\//i.test(trimmed)) return trimmed;
    if (trimmed.includes('.') && !trimmed.includes(' ')) return `https://${trimmed}`;
    return `https://www.google.com/search?q=${encodeURIComponent(trimmed)}`;
  };

  const navigateTo = (raw: string) => {
    const next = normalizeUrl(raw);
    const updated = [...history.slice(0, historyIndex + 1), next];
    setHistory(updated);
    setHistoryIndex(updated.length - 1);
    setAddressInput(next);
  };

  const canBack = historyIndex > 0;
  const canForward = historyIndex < history.length - 1;

  const goBack = () => {
    if (!canBack) return;
    const nextIdx = historyIndex - 1;
    setHistoryIndex(nextIdx);
    setAddressInput(history[nextIdx]);
  };

  const goForward = () => {
    if (!canForward) return;
    const nextIdx = historyIndex + 1;
    setHistoryIndex(nextIdx);
    setAddressInput(history[nextIdx]);
  };

  const saveBookmark = () => {
    let title = currentUrl;
    try {
      title = new URL(currentUrl).hostname;
    } catch {
      // keep raw
    }
    const next = [
      { title, url: currentUrl },
      ...bookmarks.filter((b) => b.url !== currentUrl),
    ].slice(0, 15);
    setBookmarks(next);
    localStorage.setItem(KEY_BOOKMARKS, JSON.stringify(next));
    onNotify('المتصفح', `تم حفظ ${title} في المفضلة`);
  };

  const removeBookmark = (url: string) => {
    const next = bookmarks.filter((b) => b.url !== url);
    setBookmarks(next);
    localStorage.setItem(KEY_BOOKMARKS, JSON.stringify(next));
  };

  const toggleBrowserFullscreen = async () => {
    const el = browserRootRef.current;
    if (!el) return;
    try {
      if (!document.fullscreenElement) {
        await el.requestFullscreen();
        setIsFullscreen(true);
      } else {
        await document.exitFullscreen();
        setIsFullscreen(false);
      }
    } catch {
      onToggleMaximize?.();
    }
  };

  const iframeScaleStyle = useMemo<React.CSSProperties>(() => {
    const pct = `${(100 / zoom).toFixed(4)}%`;
    return {
      width: pct,
      height: pct,
      transform: `scale(${zoom})`,
      transformOrigin: 'top left',
    };
  }, [zoom]);

  return (
    <div ref={browserRootRef} className="flex-1 min-h-0 flex flex-col bg-[#F5F8FC] rounded-lg overflow-hidden border border-slate-200/80">
      {/* Navigation & Address Row */}
      <div className="flex flex-wrap items-center gap-1.5 px-2 py-1.5 bg-white border-b border-slate-200">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={goBack}
            disabled={!canBack}
            title="رجوع"
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 disabled:opacity-40 text-xs font-bold text-slate-700 cursor-pointer"
          >
            ←
          </button>
          <button
            type="button"
            onClick={goForward}
            disabled={!canForward}
            title="تقدم"
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 disabled:opacity-40 text-xs font-bold text-slate-700 cursor-pointer"
          >
            →
          </button>
          <button
            type="button"
            onClick={() => setReloadNonce((n) => n + 1)}
            title="إعادة تحميل"
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-bold text-slate-700 cursor-pointer"
          >
            ↻
          </button>
          <button
            type="button"
            onClick={() => navigateTo('https://orkeitstm.vercel.app')}
            title="الصفحة الرئيسية"
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-bold text-slate-700 cursor-pointer"
          >
            ⌂
          </button>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            navigateTo(addressInput);
          }}
          className="flex-1 min-w-[160px] flex items-center gap-1.5"
        >
          <input
            type="text"
            value={addressInput}
            onChange={(e) => setAddressInput(e.target.value)}
            placeholder="ابحث أو أدخل عنوان URL..."
            className="flex-1 h-7 px-3 rounded-full bg-[#F1F5FA] border border-slate-200 text-xs text-slate-800 focus:outline-none focus:border-[#0067C0] focus:bg-white"
            dir="ltr"
          />
          <button
            type="submit"
            className="h-7 px-3 rounded-md bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-semibold cursor-pointer"
          >
            اذهب
          </button>
        </form>

        {/* Display Zoom & Screen Maximize Controls */}
        <div className="flex items-center gap-1 bg-[#F1F5FA] border border-slate-200 rounded-lg px-1.5 py-0.5" dir="rtl">
          <button
            type="button"
            onClick={() => setZoom((z) => Math.max(0.5, +(z - 0.1).toFixed(2)))}
            title="تصغير شاشة العرض"
            className="w-6 h-6 rounded hover:bg-white text-slate-700 font-bold text-sm cursor-pointer"
          >
            −
          </button>
          <button
            type="button"
            onClick={() => setZoom(1)}
            title="إعادة ضبط الحجم إلى 100%"
            className="px-1.5 h-6 rounded hover:bg-white text-[11px] font-mono-tabular font-semibold text-[#0067C0] cursor-pointer"
          >
            {Math.round(zoom * 100)}%
          </button>
          <button
            type="button"
            onClick={() => setZoom((z) => Math.min(1.5, +(z + 0.1).toFixed(2)))}
            title="تكبير شاشة العرض"
            className="w-6 h-6 rounded hover:bg-white text-slate-700 font-bold text-sm cursor-pointer"
          >
            +
          </button>
          <button
            type="button"
            onClick={() => setZoom(0.75)}
            title="عرض كامل الصفحة (75%)"
            className={`px-1.5 h-6 rounded text-[10px] font-semibold cursor-pointer ${
              Math.abs(zoom - 0.75) < 0.02 ? 'bg-[#0067C0] text-white' : 'hover:bg-white text-slate-700'
            }`}
          >
            احتواء
          </button>
          {onToggleMaximize && (
            <button
              type="button"
              onClick={onToggleMaximize}
              title={isMaximized ? 'استعادة حجم النافذة' : 'تكبير نافذة المتصفح'}
              className="px-1.5 h-6 rounded hover:bg-white text-[11px] font-semibold text-slate-700 cursor-pointer"
            >
              {isMaximized ? '❐ تصغير' : '▢ تكبير'}
            </button>
          )}
          <button
            type="button"
            onClick={toggleBrowserFullscreen}
            title="ملء الشاشة الكاملة"
            className="px-1.5 h-6 rounded hover:bg-white text-[11px] font-semibold text-slate-700 cursor-pointer"
          >
            {isFullscreen ? '⤡' : '⛶'}
          </button>
        </div>

        {/* Bookmarks Controls */}
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={saveBookmark}
            title="إضافة للمفضلة"
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-amber-50 text-amber-500 text-sm cursor-pointer"
          >
            ★
          </button>
          <button
            type="button"
            onClick={() => setShowBookmarks((v) => !v)}
            title="المفضلة"
            className={`h-7 px-2 rounded-md text-[11px] font-semibold cursor-pointer ${
              showBookmarks ? 'bg-[#0067C0] text-white' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
            }`}
          >
            المفضلة
          </button>
        </div>
      </div>

      {/* Bookmarks Bar */}
      {showBookmarks && (
        <div className="bg-[#EEF3FA] border-b border-slate-200 px-2.5 py-1.5 flex flex-wrap items-center gap-1.5" dir="rtl">
          {bookmarks.map((b) => (
            <div
              key={b.url}
              className="flex items-center gap-1 bg-white border border-slate-200 rounded-md px-2 py-0.5 text-[11px]"
            >
              <button
                type="button"
                onClick={() => navigateTo(b.url)}
                className="text-[#0067C0] hover:underline font-medium cursor-pointer"
              >
                {b.title}
              </button>
              <button
                type="button"
                onClick={() => removeBookmark(b.url)}
                className="text-slate-400 hover:text-red-500 ml-1 cursor-pointer"
              >
                ×
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Sub-bar with external fallback note */}
      <div className="bg-[#F8FAFD] border-b border-slate-200/70 px-2.5 py-1 flex items-center justify-between text-[11px] text-slate-500" dir="rtl">
        <span className="truncate max-w-[70%]">
          نسبة العرض: <strong className="text-[#0067C0]">{Math.round(zoom * 100)}%</strong> • في حال منع الموقع العرض الداخلي، استخدم الفتح الخارجي
        </span>
        <a
          href={currentUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="text-[#0067C0] hover:underline font-semibold shrink-0"
        >
          فتح في تبويب جديد ↗
        </a>
      </div>

      {/* Scaled Viewport */}
      <div className="relative flex-1 min-h-0 w-full overflow-auto bg-white">
        <div className="w-full h-full overflow-hidden relative">
          <iframe
            key={`${currentUrl}-${reloadNonce}`}
            src={currentUrl}
            title="Oriket Web Browser"
            style={iframeScaleStyle}
            className="border-0 bg-white block"
            sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox allow-downloads"
          />
        </div>
      </div>
    </div>
  );
};
