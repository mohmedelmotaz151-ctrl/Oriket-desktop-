import React, { useEffect, useState } from 'react';
import { ScenicLockWallpaper } from './Wallpapers';

interface PowerMenuModalProps {
  onSleep: () => void;
  onRestart: () => void;
  onShutdown: () => void;
  onClose: () => void;
}

export const PowerMenuModal: React.FC<PowerMenuModalProps> = ({
  onSleep,
  onRestart,
  onShutdown,
  onClose,
}) => {
  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-[1200] bg-black/45 backdrop-blur-xs flex items-center justify-center p-4"
      dir="rtl"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[340px] rounded-2xl bg-[#F4F7FC] border border-white/80 shadow-2xl p-5 flex flex-col gap-3"
      >
        <div className="text-sm font-bold text-[#122036] text-center">خيارات الطاقة</div>
        <button
          type="button"
          onClick={onSleep}
          className="w-full py-2.5 px-4 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-xs font-semibold text-[#122036] flex items-center justify-between cursor-pointer"
        >
          <span>وضع السكون (Sleep)</span>
          <span>☾</span>
        </button>
        <button
          type="button"
          onClick={onRestart}
          className="w-full py-2.5 px-4 rounded-xl bg-white hover:bg-slate-50 border border-slate-200 text-xs font-semibold text-[#122036] flex items-center justify-between cursor-pointer"
        >
          <span>إعادة التشغيل (Restart)</span>
          <span>↻</span>
        </button>
        <button
          type="button"
          onClick={onShutdown}
          className="w-full py-2.5 px-4 rounded-xl bg-red-600 hover:bg-red-700 text-xs font-semibold text-white flex items-center justify-between cursor-pointer"
        >
          <span>إيقاف التشغيل (Shut down)</span>
          <span>⏻</span>
        </button>
        <button
          type="button"
          onClick={onClose}
          className="w-full py-2 rounded-xl text-xs text-slate-600 hover:bg-slate-200/70 cursor-pointer"
        >
          إلغاء
        </button>
      </div>
    </div>
  );
};

export const SleepOverlay: React.FC<{ onWake: () => void }> = ({ onWake }) => {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const timeStr = now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  const dateStr = now.toLocaleDateString('ar-SA', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  });

  return (
    <div
      onClick={onWake}
      className="fixed inset-0 z-[2000] cursor-pointer select-none flex flex-col items-center justify-center text-white"
    >
      <ScenicLockWallpaper />
      <div className="absolute inset-0 bg-[#040B1A]/35" />
      <div className="relative z-10 flex flex-col items-center gap-3 text-center px-4">
        <div className="text-6xl font-light tracking-tight font-mono-tabular drop-shadow-lg">
          {timeStr}
        </div>
        <div className="text-base font-medium text-white/90 drop-shadow">{dateStr}</div>
        <div className="mt-8 px-4 py-2 rounded-full bg-white/15 backdrop-blur-md border border-white/25 text-xs text-white/90">
          اضغط في أي مكان للاستيقاظ
        </div>
      </div>
    </div>
  );
};

export const ShutdownScreen: React.FC<{ onPowerOn: () => void }> = ({ onPowerOn }) => {
  return (
    <div className="fixed inset-0 z-[2000] bg-[#040811] text-white flex flex-col items-center justify-center gap-6 select-none p-6">
      <div className="text-sm text-white/75">تم إيقاف تشغيل Oriket Desktop</div>
      <button
        type="button"
        onClick={onPowerOn}
        className="w-16 h-16 rounded-full bg-[#0F2447] hover:bg-[#193B72] border border-[#3B76D1] flex items-center justify-center text-2xl shadow-xl transition cursor-pointer"
        title="تشغيل النظام"
      >
        ⏻
      </button>
      <span className="text-xs text-white/50">اضغط زر الطاقة لإعادة التشغيل</span>
    </div>
  );
};
