import React, { useEffect, useState } from 'react';
import { ScenicLockWallpaper } from './Wallpapers';

const KEY_HASH = 'orkeit_login_hash';
const KEY_SALT = 'orkeit_login_salt';

async function sha256Hex(password: string, salt: string): Promise<string> {
  const data = new TextEncoder().encode(`${salt}:${password}`);
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    return Array.from(new Uint8Array(hashBuffer))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }
  // Fallback deterministic digest if non-HTTPS context lacks crypto.subtle
  let h = 2166136261;
  for (let i = 0; i < data.length; i++) {
    h ^= data[i];
    h = Math.imul(h, 16777619);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

interface LoginScreenProps {
  onSuccess: () => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onSuccess }) => {
  const [hasPassword, setHasPassword] = useState<boolean>(() => {
    const h = localStorage.getItem(KEY_HASH);
    const s = localStorage.getItem(KEY_SALT);
    return Boolean(h && s);
  });
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [message, setMessage] = useState('');
  const [isSuccessMsg, setIsSuccessMsg] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setMessage('');
    setPassword('');
    setConfirm('');
  }, [hasPassword]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (busy) return;

    if (password.length < 6) {
      setIsSuccessMsg(false);
      setMessage('استخدم كلمة مرور من 6 أحرف أو أرقام على الأقل.');
      return;
    }

    if (!hasPassword) {
      if (password !== confirm) {
        setIsSuccessMsg(false);
        setMessage('كلمتا المرور غير متطابقتين.');
        return;
      }
      const salt =
        typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random()}`;
      const hash = await sha256Hex(password, salt);
      localStorage.setItem(KEY_SALT, salt);
      localStorage.setItem(KEY_HASH, hash);
    } else {
      const salt = localStorage.getItem(KEY_SALT) || '';
      const expected = localStorage.getItem(KEY_HASH) || '';
      const actual = await sha256Hex(password, salt);
      if (actual !== expected) {
        setIsSuccessMsg(false);
        setMessage('كلمة المرور غير صحيحة.');
        setPassword('');
        return;
      }
    }

    setBusy(true);
    setIsSuccessMsg(true);
    setMessage('جارٍ فتح سطح المكتب…');
    setTimeout(() => {
      onSuccess();
    }, 260);
  };

  const handleResetLocalPassword = () => {
    localStorage.removeItem(KEY_HASH);
    localStorage.removeItem(KEY_SALT);
    setHasPassword(false);
  };

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center overflow-hidden">
      <div className="absolute inset-0">
        <ScenicLockWallpaper />
      </div>

      <form
        onSubmit={handleSubmit}
        className="relative z-10 w-[min(420px,88vw)] flex flex-col items-center px-7 py-6"
      >
        <svg viewBox="0 0 108 108" className="w-20 h-20 rounded-xl shadow-lg">
          <rect width="108" height="108" rx="18" fill="#142B4A" />
          <path d="M16,25 L48,20 L48,51 L16,51 Z" fill="#FFFFFF" />
          <path d="M53,19 L92,13 L92,51 L53,51 Z" fill="#FFFFFF" />
          <path d="M16,56 L48,56 L48,87 L16,82 Z" fill="#FFFFFF" />
          <path d="M53,56 L92,56 L92,93 L53,88 Z" fill="#FFFFFF" />
        </svg>

        <h1 className="mt-3 text-2xl font-normal text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)]">
          {hasPassword ? 'SCANOR' : 'إعداد تسجيل الدخول'}
        </h1>

        <div className="my-2 text-5xl text-white drop-shadow-[0_2px_4px_rgba(0,0,0,0.85)] leading-none">
          ◯
        </div>

        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder={hasPassword ? 'PIN' : 'أنشئ كلمة مرور'}
          autoFocus
          dir="auto"
          className="w-full h-11 px-4 bg-white text-slate-800 placeholder-slate-500 border border-[#A0A0A0] text-sm focus:outline-none focus:border-[#0067C0]"
        />

        {!hasPassword && (
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            placeholder="أعد كتابة كلمة المرور"
            dir="auto"
            className="w-full h-11 mt-3 px-4 bg-white text-slate-800 placeholder-slate-500 border border-[#A0A0A0] text-sm focus:outline-none focus:border-[#0067C0]"
          />
        )}

        {message && (
          <p
            className={`mt-2.5 text-xs text-center font-medium drop-shadow ${
              isSuccessMsg ? 'text-[#BEEBCD]' : 'text-[#FFBEBE]'
            }`}
          >
            {message}
          </p>
        )}

        <button
          type="submit"
          disabled={busy}
          className="w-full h-11 mt-4 rounded-xl bg-[#0067C0] hover:bg-[#0058A6] active:scale-[0.99] text-white text-sm font-medium shadow-md transition disabled:opacity-60 cursor-pointer"
        >
          {hasPassword ? 'تسجيل الدخول' : 'حفظ كلمة المرور والمتابعة'}
        </button>

        <p className="mt-4 text-[11px] text-[#AEC4E0] text-center drop-shadow">
          كلمة المرور محفوظة على هذا الجهاز فقط
        </p>

        {hasPassword && (
          <button
            type="button"
            onClick={handleResetLocalPassword}
            className="mt-2 text-[11px] text-white/80 hover:text-white underline cursor-pointer"
          >
            إعادة تعيين كلمة المرور المحلية
          </button>
        )}
      </form>
    </div>
  );
};
