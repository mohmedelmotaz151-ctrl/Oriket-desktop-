import React, { useMemo, useState } from 'react';
import { AppId, WallpaperStyle } from '../types/desktop';

interface SettingsAppProps {
  wallpaperStyle: WallpaperStyle;
  onChangeWallpaper: (style: WallpaperStyle) => void;
  soundEnabled: boolean;
  onToggleSound: () => void;
  desktopScale: number;
  onChangeDesktopScale: (scale: number) => void;
  onOpenApp: (appId: AppId, title: string) => void;
  onNotify: (title: string, msg: string) => void;
}

export const SettingsApp: React.FC<SettingsAppProps> = ({
  wallpaperStyle,
  onChangeWallpaper,
  soundEnabled,
  onToggleSound,
  desktopScale,
  onChangeDesktopScale,
  onOpenApp,
  onNotify,
}) => {
  const [search, setSearch] = useState('');
  const [showPrivacyModal, setShowPrivacyModal] = useState(false);

  const wallpaperOptions: { style: WallpaperStyle; label: string; desc: string; swatch: string }[] = [
    { style: 0, label: 'أزرق Windows الكلاسيكي', desc: 'الأزرق الأيقوني لسطح المكتب', swatch: 'from-[#0B66D8] to-[#04235C]' },
    { style: 1, label: 'الوضع الداكن الأنيق', desc: 'خلفية داكنة مريحة للعين', swatch: 'from-[#0D1422] to-[#050810]' },
    { style: 2, label: 'الوضع الفاتح السماوي', desc: 'إضاءة نهارية ناعمة', swatch: 'from-[#A9CFF6] to-[#E6F2FF]' },
  ];

  const categories = useMemo(
    () => [
      {
        title: 'الشخصنة والخلفية',
        subtitle: 'تغيير خلفية سطح المكتب والمظهر',
        icon: '/icons/win11_photos.png',
        action: () => {},
      },
      {
        title: 'الشاشة والتكبير',
        subtitle: `نسبة عرض سطح المكتب: ${Math.round(desktopScale * 100)}%`,
        icon: '/icons/win11_thispc.png',
        action: () => {},
      },
      {
        title: 'الصوت والتنبيهات',
        subtitle: soundEnabled ? 'أصوات النظام مفعلة' : 'أصوات النظام صامتة',
        icon: '/icons/win11_music.png',
        action: onToggleSound,
      },
      {
        title: 'الخصوصية والأمان',
        subtitle: 'عرض سياسة الخصوصية وإدارة قفل الدخول',
        icon: '/icons/win11_protection.png',
        action: () => setShowPrivacyModal(true),
      },
      {
        title: 'التطبيقات وإدارة المهام',
        subtitle: 'مراقبة النوافذ المفتوحة',
        icon: '/icons/win11_taskmanager.png',
        action: () => onOpenApp('task_manager', 'إدارة المهام'),
      },
      {
        title: 'حول Oriket Desktop',
        subtitle: 'إصدار النظام والمواصفات',
        icon: '/icons/win11_settings.png',
        action: () => onOpenApp('about', 'حول Oriket Desktop'),
      },
    ],
    [desktopScale, soundEnabled, onToggleSound, onOpenApp]
  );

  const filteredCats = categories.filter((c) => {
    const q = search.trim().toLowerCase();
    return !q || c.title.toLowerCase().includes(q) || c.subtitle.toLowerCase().includes(q);
  });

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-4 p-3 bg-[#F6F9FD] rounded-lg overflow-y-auto" dir="rtl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <img src="/icons/win11_settings.png" alt="" className="w-7 h-7" />
          <div>
            <h2 className="text-sm font-bold text-[#122036]">لوحة التحكم والإعدادات</h2>
            <p className="text-[11px] text-slate-500">تخصيص النظام، الشاشة، الصوت، والخصوصية</p>
          </div>
        </div>
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="ابحث في الإعدادات..."
          className="h-8 w-52 px-3 rounded-lg bg-white border border-slate-200 text-xs focus:outline-none focus:border-[#0067C0]"
        />
      </div>

      {/* Wallpaper Picker Card */}
      <div className="bg-white border border-slate-200 rounded-xl p-3.5 flex flex-col gap-3">
        <div className="text-xs font-bold text-[#122036]">خلفيات سطح المكتب</div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
          {wallpaperOptions.map((w) => {
            const active = wallpaperStyle === w.style;
            return (
              <button
                key={w.style}
                type="button"
                onClick={() => {
                  onChangeWallpaper(w.style);
                  onNotify('الشخصنة', `تم تطبيق خلفية: ${w.label}`);
                }}
                className={`p-2.5 rounded-xl border text-right transition cursor-pointer flex flex-col gap-2 ${
                  active
                    ? 'border-[#0067C0] bg-[#EBF3FE] ring-1 ring-[#0067C0]'
                    : 'border-slate-200 hover:bg-slate-50'
                }`}
              >
                <div className={`h-14 w-full rounded-lg bg-gradient-to-br ${w.swatch}`} />
                <div className="text-xs font-bold text-[#122036]">{w.label}</div>
                <div className="text-[10px] text-slate-500">{w.desc}</div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Display Scale & Audio */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="bg-white border border-slate-200 rounded-xl p-3.5 flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-[#122036]">مقياس شاشة سطح المكتب</span>
            <span className="text-xs font-mono-tabular font-bold text-[#0067C0]">
              {Math.round(desktopScale * 100)}%
            </span>
          </div>
          <input
            type="range"
            min={75}
            max={125}
            step={5}
            value={Math.round(desktopScale * 100)}
            onChange={(e) => onChangeDesktopScale(Number(e.target.value) / 100)}
            className="accent-[#0067C0]"
          />
          <div className="flex justify-between text-[10px] text-slate-400">
            <span>75% (مساحة أوسع)</span>
            <button
              type="button"
              onClick={() => onChangeDesktopScale(1)}
              className="text-[#0067C0] hover:underline cursor-pointer"
            >
              الافتراضي 100%
            </button>
            <span>125% (خط أوضح)</span>
          </div>
        </div>

        <div className="bg-white border border-slate-200 rounded-xl p-3.5 flex flex-col justify-between gap-2">
          <div>
            <div className="text-xs font-bold text-[#122036]">أصوات النظام وحماية الدخول</div>
            <div className="text-[11px] text-slate-500 mt-0.5">
              التحكم في نغمات التشغيل والنقر، وسياسة الخصوصية
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={onToggleSound}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer ${
                soundEnabled
                  ? 'bg-[#0067C0] text-white'
                  : 'bg-slate-100 text-slate-700'
              }`}
            >
              {soundEnabled ? 'الصوت: مفعل 🔊' : 'الصوت: صامت 🔇'}
            </button>
            <button
              type="button"
              onClick={() => setShowPrivacyModal(true)}
              className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-semibold text-[#122036] cursor-pointer"
            >
              سياسة الخصوصية
            </button>
          </div>
        </div>
      </div>

      {/* Quick Settings Directory */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
        {filteredCats.map((cat) => (
          <button
            key={cat.title}
            type="button"
            onClick={cat.action}
            className="flex items-center gap-3 p-3 rounded-xl bg-white border border-slate-200 hover:border-[#0067C0]/50 hover:bg-[#FAFCFF] text-right transition cursor-pointer"
          >
            <img src={cat.icon} alt="" className="w-8 h-8 object-contain shrink-0" />
            <div className="min-w-0">
              <div className="text-xs font-bold text-[#122036]">{cat.title}</div>
              <div className="text-[11px] text-slate-500 truncate">{cat.subtitle}</div>
            </div>
          </button>
        ))}
      </div>

      {showPrivacyModal && (
        <div
          onClick={() => setShowPrivacyModal(false)}
          className="fixed inset-0 z-[1500] bg-black/45 flex items-center justify-center p-4"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-lg bg-white rounded-2xl border border-slate-200 shadow-2xl p-5 flex flex-col gap-3 max-h-[80vh] overflow-y-auto"
          >
            <div className="flex items-center justify-between border-b border-slate-200 pb-2">
              <h3 className="text-sm font-bold text-[#122036]">سياسة الخصوصية - Oriket Desktop</h3>
              <button
                type="button"
                onClick={() => setShowPrivacyModal(false)}
                className="text-slate-500 hover:text-slate-800 cursor-pointer"
              >
                ✕
              </button>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              يعمل Oriket Desktop محليًا بالكامل داخل المتصفح. يتم حفظ مستنداتك، ملفات الجداول، اللوحات، الإعدادات، وكلمة مرور القفل المشفرة في التخزين المحلي لجهازك فقط ولا يتم إرسال أي بيانات شخصية إلى خوادم خارجية.
            </p>
            <a
              href="/docs/privacy-policy.html"
              target="_blank"
              rel="noopener noreferrer"
              className="text-xs text-[#0067C0] hover:underline font-semibold"
            >
              فتح وثيقة سياسة الخصوصية الكاملة ↗
            </a>
            <button
              type="button"
              onClick={() => setShowPrivacyModal(false)}
              className="self-end px-4 py-1.5 rounded-lg bg-[#0067C0] text-white text-xs font-semibold cursor-pointer"
            >
              إغلاق
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
