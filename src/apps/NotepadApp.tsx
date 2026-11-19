import React, { useEffect, useRef, useState } from 'react';

interface NotepadAppProps {
  initialText?: string;
  initialFileName?: string;
  onNotify: (title: string, msg: string) => void;
}

const KEY_DRAFT = 'orkeit_notepad_draft';

export const NotepadApp: React.FC<NotepadAppProps> = ({
  initialText,
  initialFileName = 'note.txt',
  onNotify,
}) => {
  const [text, setText] = useState<string>(() => {
    if (initialText !== undefined) return initialText;
    return localStorage.getItem(KEY_DRAFT) || '';
  });
  const [fileName, setFileName] = useState(initialFileName);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    localStorage.setItem(KEY_DRAFT, text);
  }, [text]);

  const handleOpenFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      setText(String(reader.result || ''));
      setFileName(file.name);
      onNotify('المفكرة', `تم فتح ${file.name}`);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleDownload = () => {
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName.endsWith('.txt') ? fileName : `${fileName}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    onNotify('المفكرة', 'تم تنزيل الملف النصي');
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-2 bg-white rounded-lg p-2" dir="rtl">
      <div className="flex flex-wrap items-center gap-1.5 border-b border-slate-200 pb-2">
        <button
          type="button"
          onClick={() => {
            setText('');
            setFileName('note.txt');
          }}
          className="px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-semibold text-slate-700 cursor-pointer"
        >
          جديد
        </button>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          className="px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-semibold text-slate-700 cursor-pointer"
        >
          فتح ملف
        </button>
        <button
          type="button"
          onClick={handleDownload}
          className="px-2.5 py-1 rounded-md bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-semibold cursor-pointer"
        >
          حفظ TXT
        </button>
        <button
          type="button"
          onClick={() => {
            localStorage.setItem(KEY_DRAFT, text);
            onNotify('المفكرة', 'تم حفظ المسودة محليًا');
          }}
          className="px-2.5 py-1 rounded-md bg-emerald-50 hover:bg-emerald-100 text-emerald-700 text-xs font-semibold cursor-pointer"
        >
          حفظ المسودة
        </button>
        <span className="mr-auto text-[11px] text-slate-500">
          {text.length} حرف
        </span>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept=".txt,.md,.csv,.json,.log,text/*"
        className="hidden"
        onChange={handleOpenFile}
      />

      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="اكتب ملاحظاتك هنا..."
        className="flex-1 min-h-0 w-full resize-none rounded-lg border border-slate-200 p-3 text-sm text-slate-800 focus:outline-none focus:border-[#0067C0] leading-relaxed"
      />
    </div>
  );
};
