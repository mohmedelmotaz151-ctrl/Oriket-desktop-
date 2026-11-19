import React from 'react';
import { DesktopNotice } from '../types/desktop';

interface NotificationCenterPanelProps {
  notices: DesktopNotice[];
  onClearAll: () => void;
  onOpenSettings: () => void;
  onClose: () => void;
}

export const NotificationCenterPanel: React.FC<NotificationCenterPanelProps> = ({
  notices,
  onClearAll,
  onOpenSettings,
  onClose,
}) => {
  return (
    <div
      onClick={(e) => e.stopPropagation()}
      className="fixed bottom-[62px] right-3 z-[900] w-[min(92vw,340px)] max-h-[70vh] rounded-2xl bg-[#F4F7FC]/95 backdrop-blur-xl border border-white/85 shadow-2xl p-4 flex flex-col gap-3 select-none"
      dir="rtl"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <img src="/icons/win11_bell.png" alt="" className="w-5 h-5" />
          <span className="text-sm font-bold text-[#122036]">مركز الإشعارات</span>
        </div>
        <button
          type="button"
          onClick={onClearAll}
          className="text-[11px] font-semibold text-[#0067C0] hover:underline cursor-pointer"
        >
          مسح الكل
        </button>
      </div>

      <div className="flex-1 min-h-[120px] max-h-[300px] overflow-y-auto flex flex-col gap-2">
        {notices.length === 0 ? (
          <div className="py-10 text-center text-xs text-slate-500">
            لا توجد إشعارات جديدة حاليًا
          </div>
        ) : (
          notices.map((n) => (
            <div
              key={n.id}
              className="bg-white/95 border border-slate-200/80 rounded-xl p-2.5 flex flex-col gap-1 shadow-2xs"
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-[#122036]">{n.title}</span>
                <span className="text-[10px] text-slate-400 font-mono-tabular">
                  {new Date(n.time).toLocaleTimeString('en-US', {
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </span>
              </div>
              <p className="text-[11px] text-slate-600 leading-relaxed">{n.message}</p>
            </div>
          ))
        )}
      </div>

      <div className="border-t border-slate-200/80 pt-2.5 flex items-center justify-between">
        <button
          type="button"
          onClick={() => {
            onOpenSettings();
            onClose();
          }}
          className="text-[11px] font-semibold text-[#1F3A60] hover:text-[#0067C0] cursor-pointer"
        >
          إعدادات الإشعارات ⚙
        </button>
        <button
          type="button"
          onClick={onClose}
          className="text-[11px] text-slate-500 hover:text-slate-800 cursor-pointer"
        >
          إغلاق
        </button>
      </div>
    </div>
  );
};
