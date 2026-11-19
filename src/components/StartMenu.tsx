import React, { useMemo, useState } from 'react';
import { AppId } from '../types/desktop';
import { getIconPathForTitle } from '../utils/fluentIcons';
import { DESKTOP_SHORTCUTS } from '../utils/virtualFs';

interface StartMenuProps {
  onOpenApp: (appId: AppId, title: string, payload?: { url?: string; initialFolder?: string }) => void;
  onOpenExternalUrl: (url: string, title: string) => void;
  onCheckUpdates: () => void;
  onPickPdf: () => void;
  onShowPowerMenu: () => void;
  onClose: () => void;
}

export const StartMenu: React.FC<StartMenuProps> = ({
  onOpenApp,
  onOpenExternalUrl,
  onCheckUpdates,
  onPickPdf,
  onShowPowerMenu,
  onClose,
}) => {
  const [query, setQuery] = useState('');
  const [showAllApps, setShowAllApps] = useState(false);

  const pinnedItems = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = DESKTOP_SHORTCUTS.filter((s) =>
      q ? s.defaultLabel.toLowerCase().includes(q) || s.key.toLowerCase().includes(q) : true
    );
    return showAllApps ? list : list.slice(0, 12);
  }, [query, showAllApps]);

  const handleItemClick = (item: (typeof DESKTOP_SHORTCUTS)[number]) => {
    if (item.actionType === 'update') {
      onCheckUpdates();
    } else if (item.actionType === 'pick_pdf') {
      onPickPdf();
    } else if (item.actionType === 'url' && item.url) {
      onOpenExternalUrl(item.url, item.windowTitle || item.defaultLabel);
    } else if (item.appId) {
      onOpenApp(item.appId, item.defaultLabel);
    }
    onClose();
  };

  return (
    <div
      onClick={(e) => e.stopPropagation()}
      className="fixed bottom-[62px] left-1/2 -translate-x-1/2 z-[900] w-[min(94vw,640px)] rounded-2xl bg-[#F3F7FC]/95 backdrop-blur-xl border border-white/80 shadow-2xl p-5 flex flex-col gap-4 select-none"
      dir="rtl"
    >
      {/* Search Bar */}
      <div className="relative flex items-center">
        <img
          src="/icons/win11_search.png"
          alt=""
          className="w-4 h-4 absolute right-3.5 pointer-events-none opacity-75"
        />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="اكتب هنا للبحث في التطبيقات..."
          className="w-full h-10 pr-10 pl-4 rounded-full bg-white border border-slate-200/90 text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-[#0067C0] shadow-xs"
          autoFocus
        />
      </div>

      {/* Pinned Header */}
      <div className="flex items-center justify-between px-1">
        <span className="text-[13px] font-bold text-[#122036]">
          {showAllApps ? 'كل التطبيقات' : 'التطبيقات المثبّتة'}
        </span>
        <button
          type="button"
          onClick={() => setShowAllApps((v) => !v)}
          className="px-3 py-1 rounded-md bg-white/90 hover:bg-white border border-slate-200/80 text-[11px] font-semibold text-[#1F3A60] shadow-2xs cursor-pointer"
        >
          {showAllApps ? 'المثبّتة ‹' : 'كل التطبيقات ›'}
        </button>
      </div>

      {/* Pinned Grid */}
      <div className="grid grid-cols-4 sm:grid-cols-6 gap-2 max-h-[250px] overflow-y-auto pr-0.5">
        {pinnedItems.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => handleItemClick(item)}
            className="flex flex-col items-center justify-start gap-1.5 p-2 rounded-xl hover:bg-white/85 active:scale-95 transition cursor-pointer"
          >
            <img
              src={getIconPathForTitle(item.iconKey)}
              alt=""
              className="w-9 h-9 object-contain"
              draggable={false}
            />
            <span className="text-[11px] font-medium text-[#162742] text-center leading-tight line-clamp-2">
              {item.defaultLabel}
            </span>
          </button>
        ))}
        {pinnedItems.length === 0 && (
          <div className="col-span-full py-8 text-center text-xs text-slate-500">
            لا توجد نتائج مطابقة للبحث
          </div>
        )}
      </div>

      {/* Recommended */}
      {!showAllApps && (
        <div className="border-t border-slate-200/70 pt-3 flex flex-col gap-2">
          <div className="px-1 text-[12px] font-bold text-[#122036]">موصى به</div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => {
                onPickPdf();
                onClose();
              }}
              className="flex items-center gap-3 p-2 rounded-xl hover:bg-white/80 transition text-right cursor-pointer"
            >
              <img src="/icons/win11_pdf.png" alt="" className="w-8 h-8 object-contain" />
              <div className="flex flex-col">
                <span className="text-xs font-semibold text-[#122036]">فتح مستند PDF</span>
                <span className="text-[10px] text-slate-500">مشغل PDF السريع</span>
              </div>
            </button>
            <button
              type="button"
              onClick={() => {
                onOpenApp('paint', 'الرسام');
                onClose();
              }}
              className="flex items-center gap-3 p-2 rounded-xl hover:bg-white/80 transition text-right cursor-pointer"
            >
              <img src="/icons/win11_paint.png" alt="" className="w-8 h-8 object-contain" />
              <div className="flex flex-col">
                <span className="text-xs font-semibold text-[#122036]">برنامج الرسام</span>
                <span className="text-[10px] text-slate-500">رسم وتحرير الصور</span>
              </div>
            </button>
          </div>
        </div>
      )}

      {/* Footer */}
      <div className="border-t border-slate-200/80 pt-3 flex items-center justify-between px-1">
        <button
          type="button"
          onClick={() => {
            onOpenApp('about', 'حول Oriket Desktop');
            onClose();
          }}
          className="flex items-center gap-2.5 px-2.5 py-1.5 rounded-lg hover:bg-white/80 transition cursor-pointer"
        >
          <div className="w-7 h-7 rounded-full bg-[#0067C0] text-white flex items-center justify-center text-xs font-bold">
            O
          </div>
          <span className="text-xs font-bold text-[#122036]">Oriket Desktop</span>
        </button>

        <div className="flex items-center gap-1.5">
          <button
            type="button"
            onClick={() => {
              onOpenApp('settings', 'لوحة التحكم');
              onClose();
            }}
            title="الإعدادات"
            className="w-9 h-9 rounded-lg hover:bg-white/90 flex items-center justify-center transition cursor-pointer"
          >
            <img src="/icons/win11_settings.png" alt="" className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={() => {
              onClose();
              onShowPowerMenu();
            }}
            title="خيارات الطاقة"
            className="w-9 h-9 rounded-lg hover:bg-red-50 text-[#122036] hover:text-red-600 flex items-center justify-center text-base font-bold transition cursor-pointer"
          >
            ⏻
          </button>
        </div>
      </div>
    </div>
  );
};
