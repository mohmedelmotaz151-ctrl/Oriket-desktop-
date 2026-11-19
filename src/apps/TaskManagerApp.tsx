import React from 'react';
import { WindowEntry } from '../types/desktop';
import { getIconPathForTitle } from '../utils/fluentIcons';

interface TaskManagerAppProps {
  windows: WindowEntry[];
  onFocusWindow: (id: number) => void;
  onCloseWindow: (id: number) => void;
}

export const TaskManagerApp: React.FC<TaskManagerAppProps> = ({
  windows,
  onFocusWindow,
  onCloseWindow,
}) => {
  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 bg-white rounded-lg p-3" dir="rtl">
      <div className="flex items-center justify-between border-b border-slate-200 pb-2">
        <div>
          <h3 className="text-sm font-bold text-[#122036]">النوافذ والعمليات النشطة</h3>
          <p className="text-[11px] text-slate-500">
            إجمالي النوافذ المفتوحة: {windows.length}
          </p>
        </div>
        <span className="px-2.5 py-1 rounded-md bg-[#EBF3FE] text-[#0067C0] text-xs font-bold font-mono-tabular">
          {windows.filter((w) => !w.minimized).length} نشط
        </span>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-2">
        {windows.map((w) => (
          <div
            key={w.id}
            className="flex items-center justify-between gap-2 p-2.5 rounded-xl border border-slate-200/90 bg-[#FAFCFF]"
          >
            <div className="flex items-center gap-2.5 min-w-0">
              <img src={getIconPathForTitle(w.title)} alt="" className="w-6 h-6 shrink-0" />
              <div className="min-w-0">
                <div className="text-xs font-bold text-[#122036] truncate">{w.title}</div>
                <div className="text-[10px] text-slate-500">
                  الحالة: {w.minimized ? 'مصغر' : w.maximized ? 'ملء الشاشة' : 'مفتوح'} • PID #{w.id}
                </div>
              </div>
            </div>

            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                onClick={() => onFocusWindow(w.id)}
                className="px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-[11px] font-semibold text-slate-700 cursor-pointer"
              >
                تركيز
              </button>
              <button
                type="button"
                onClick={() => onCloseWindow(w.id)}
                className="px-2.5 py-1 rounded-md bg-red-50 hover:bg-red-100 text-[11px] font-semibold text-red-600 cursor-pointer"
              >
                إنهاء المهمة
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
