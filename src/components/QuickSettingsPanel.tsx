import React, { useState } from 'react';
import { AppId } from '../types/desktop';

interface QuickSettingsPanelProps {
  soundEnabled: boolean;
  onToggleSound: () => void;
  desktopScale: number;
  onChangeDesktopScale: (next: number) => void;
  isBrowserFullscreen: boolean;
  onToggleBrowserFullscreen: () => void;
  onOpenSettings: (appId?: AppId, title?: string) => void;
  onNotify: (title: string, msg: string) => void;
  onClose: () => void;
}

export const QuickSettingsPanel: React.FC<QuickSettingsPanelProps> = ({
  soundEnabled,
  onToggleSound,
  desktopScale,
  onChangeDesktopScale,
  isBrowserFullscreen,
  onToggleBrowserFullscreen,
  onOpenSettings,
  onNotify,
  onClose,
}) => {
  const [wifiOn, setWifiOn] = useState(() => (typeof navigator !== 'undefined' ? navigator.onLine : true));
  const [btOn, setBtOn] = useState(true);

  const tiles = [
    {
      id: 'wifi',
      label: wifiOn ? 'Wi-Fi متصل' : 'Wi-Fi متوقف',
      icon: '/icons/win11_wifi.png',
      active: wifiOn,
      onClick: () => {
        setWifiOn((v) => !v);
        onNotify('الشبكة', !wifiOn ? 'تم تفعيل الاتصال اللاسلكي' : 'تم إيقاف مؤشر الشبكة');
      },
    },
    {
      id: 'bt',
      label: btOn ? 'Bluetooth مفعل' : 'Bluetooth متوقف',
      icon: '/icons/win11_bluetooth.png',
      active: btOn,
      onClick: () => {
        setBtOn((v) => !v);
        onNotify('Bluetooth', !btOn ? 'تم تفعيل Bluetooth' : 'تم إيقاف Bluetooth');
      },
    },
    {
      id: 'sound',
      label: soundEnabled ? 'أصوات النظام: مفعلة' : 'أصوات النظام: صامت',
      icon: '/icons/win11_music.png',
      active: soundEnabled,
      onClick: onToggleSound,
    },
    {
      id: 'fullscreen',
      label: isBrowserFullscreen ? 'إنهاء ملء الشاشة' : 'تكبير ملء الشاشة',
      icon: '/icons/win11_thispc.png',
      active: isBrowserFullscreen,
      onClick: onToggleBrowserFullscreen,
    },
    {
      id: 'wallpaper',
      label: 'خلفية سطح المكتب',
      icon: '/icons/win11_photos.png',
      active: false,
      onClick: () => {
        onOpenSettings('settings', 'لوحة التحكم');
        onClose();
      },
    },
    {
      id: 'settings',
      label: 'إعدادات النظام',
      icon: '/icons/win11_settings.png',
      active: false,
      onClick: () => {
        onOpenSettings('settings', 'لوحة التحكم');
        onClose();
      },
    },
  ];

  return (
    <div
      onClick={(e) => e.stopPropagation()}
      className="fixed bottom-[62px] left-3 sm:left-auto sm:right-3 z-[900] w-[min(92vw,340px)] rounded-2xl bg-[#F4F7FC]/95 backdrop-blur-xl border border-white/85 shadow-2xl p-4 flex flex-col gap-3.5 select-none"
      dir="rtl"
    >
      <div className="grid grid-cols-2 gap-2">
        {tiles.map((tile) => (
          <button
            key={tile.id}
            type="button"
            onClick={tile.onClick}
            className={`flex items-center gap-2.5 p-2.5 rounded-xl border text-right transition cursor-pointer ${
              tile.active
                ? 'bg-[#0067C0] border-[#0059A8] text-white shadow-xs'
                : 'bg-white/90 hover:bg-white border-slate-200/80 text-[#122036]'
            }`}
          >
            <img src={tile.icon} alt="" className="w-5 h-5 object-contain shrink-0" />
            <span className="text-[11px] font-semibold leading-tight">{tile.label}</span>
          </button>
        ))}
      </div>

      {/* Display Scale / Zoom Slider */}
      <div className="bg-white/85 border border-slate-200/80 rounded-xl p-3 flex flex-col gap-2">
        <div className="flex items-center justify-between text-xs font-semibold text-[#122036]">
          <span>تكبير وتصغير شاشة العرض</span>
          <span className="font-mono-tabular text-[#0067C0]">{Math.round(desktopScale * 100)}%</span>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onChangeDesktopScale(Math.max(0.75, +(desktopScale - 0.1).toFixed(2)))}
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 text-sm font-bold text-[#122036] cursor-pointer"
          >
            −
          </button>
          <input
            type="range"
            min={75}
            max={125}
            step={5}
            value={Math.round(desktopScale * 100)}
            onChange={(e) => onChangeDesktopScale(Number(e.target.value) / 100)}
            className="flex-1 accent-[#0067C0]"
          />
          <button
            type="button"
            onClick={() => onChangeDesktopScale(Math.min(1.25, +(desktopScale + 0.1).toFixed(2)))}
            className="w-7 h-7 rounded-md bg-slate-100 hover:bg-slate-200 text-sm font-bold text-[#122036] cursor-pointer"
          >
            +
          </button>
        </div>
      </div>

      <div className="flex items-center justify-between text-[11px] text-slate-500 pt-1 border-t border-slate-200/70">
        <span>Oriket Desktop Web</span>
        <span>v1.0.0</span>
      </div>
    </div>
  );
};
