import React from 'react';

export const AboutApp: React.FC = () => {
  return (
    <div className="flex-1 min-h-0 flex flex-col items-center justify-center text-center p-6 bg-white rounded-lg gap-4" dir="rtl">
      <div className="w-16 h-16 rounded-2xl bg-[#EBF3FE] border border-[#C7DCF9] flex items-center justify-center">
        <img src="/icons/win11_thispc.png" alt="" className="w-10 h-10 object-contain" />
      </div>
      <h2 className="text-lg font-extrabold text-[#122036]">Oriket Desktop</h2>
      <p className="text-sm text-slate-600 max-w-md leading-relaxed">
        بيئة سطح مكتب مستوحاة من Windows 11 مع نوافذ متعددة المهام، مستكشف ملفات، متصفح ويب، وبرامج إنتاجية ووسائط متكاملة.
      </p>
      <div className="text-xs text-slate-400 font-mono-tabular">
        Build 26000 • React Web Edition v1.0.0
      </div>
    </div>
  );
};
