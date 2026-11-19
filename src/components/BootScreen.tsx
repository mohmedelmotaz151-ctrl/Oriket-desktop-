import React, { useEffect, useState } from 'react';
import { playDesktopSound } from '../utils/sound';

interface BootScreenProps {
  soundEnabled: boolean;
  onComplete: () => void;
}

export const BootScreen: React.FC<BootScreenProps> = ({ soundEnabled, onComplete }) => {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const t0 = setTimeout(() => setVisible(true), 40);
    playDesktopSound('boot', soundEnabled);
    const t1 = setTimeout(() => {
      onComplete();
    }, 1850);
    return () => {
      clearTimeout(t0);
      clearTimeout(t1);
    };
  }, [onComplete, soundEnabled]);

  return (
    <div
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#05122A] text-white select-none"
      onClick={onComplete}
    >
      <div
        className={`flex flex-col items-center transition-opacity duration-700 ${
          visible ? 'opacity-100' : 'opacity-0'
        }`}
      >
        {/* Windows Launcher Logo matching ic_windows_launcher.xml */}
        <svg viewBox="0 0 108 108" className="w-28 h-28 rounded-2xl shadow-2xl">
          <rect width="108" height="108" rx="18" fill="#142B4A" />
          <path d="M16,25 L48,20 L48,51 L16,51 Z" fill="#FFFFFF" />
          <path d="M53,19 L92,13 L92,51 L53,51 Z" fill="#FFFFFF" />
          <path d="M16,56 L48,56 L48,87 L16,82 Z" fill="#FFFFFF" />
          <path d="M53,56 L92,56 L92,93 L53,88 Z" fill="#FFFFFF" />
        </svg>

        <h1 className="mt-5 text-3xl font-normal tracking-tight text-white">Windows</h1>
        <p className="mt-1 text-sm text-[#BED3EF]">Oriket Desktop</p>

        <div className="mt-9 w-9 h-9 rounded-full border-3 border-white/25 border-t-white animate-spin" />
      </div>
    </div>
  );
};
