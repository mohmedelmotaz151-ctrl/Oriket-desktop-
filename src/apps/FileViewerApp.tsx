import React, { useRef, useState } from 'react';

interface FileViewerAppProps {
  initialName?: string;
  initialMime?: string;
  initialDataUrl?: string;
  initialText?: string;
  onNotify: (title: string, msg: string) => void;
}

export const FileViewerApp: React.FC<FileViewerAppProps> = ({
  initialName = 'عارض الملفات',
  initialMime = '',
  initialDataUrl,
  initialText,
  onNotify,
}) => {
  const [fileName, setFileName] = useState(initialName);
  const [mime, setMime] = useState(initialMime);
  const [dataUrl, setDataUrl] = useState<string | undefined>(initialDataUrl);
  const [textContent, setTextContent] = useState<string | undefined>(initialText);
  const [zoom, setZoom] = useState(1);
  const inputRef = useRef<HTMLInputElement>(null);

  const handlePick = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    setMime(file.type || '');
    const lower = file.name.toLowerCase();
    if (
      file.type.startsWith('text/') ||
      lower.endsWith('.txt') ||
      lower.endsWith('.csv') ||
      lower.endsWith('.json') ||
      lower.endsWith('.md')
    ) {
      const reader = new FileReader();
      reader.onload = () => {
        setTextContent(String(reader.result || ''));
        setDataUrl(undefined);
      };
      reader.readAsText(file);
    } else {
      const url = URL.createObjectURL(file);
      setDataUrl(url);
      setTextContent(undefined);
    }
    onNotify('عارض الملفات', `تم فتح ${file.name}`);
    e.target.value = '';
  };

  const isPdf = mime.includes('pdf') || fileName.toLowerCase().endsWith('.pdf');
  const isImage =
    mime.startsWith('image/') ||
    /\.(png|jpg|jpeg|webp|gif|bmp)$/i.test(fileName);

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-2 bg-white rounded-lg p-2" dir="rtl">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-2">
        <div className="flex items-center gap-2 min-w-0">
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="px-3 py-1 rounded-md bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-semibold cursor-pointer"
          >
            فتح ملف من الجهاز
          </button>
          <span className="text-xs font-bold text-[#122036] truncate">{fileName}</span>
        </div>

        {(isImage || isPdf) && (
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setZoom((z) => Math.max(0.5, +(z - 0.15).toFixed(2)))}
              className="w-7 h-7 rounded bg-slate-100 hover:bg-slate-200 text-xs font-bold cursor-pointer"
            >
              −
            </button>
            <span className="text-xs font-mono-tabular px-1">{Math.round(zoom * 100)}%</span>
            <button
              type="button"
              onClick={() => setZoom((z) => Math.min(2.5, +(z + 0.15).toFixed(2)))}
              className="w-7 h-7 rounded bg-slate-100 hover:bg-slate-200 text-xs font-bold cursor-pointer"
            >
              +
            </button>
          </div>
        )}
      </div>

      <input
        ref={inputRef}
        type="file"
        accept=".pdf,image/*,text/*,.csv,.json,.md"
        className="hidden"
        onChange={handlePick}
      />

      <div className="flex-1 min-h-0 overflow-auto rounded-lg bg-[#F5F8FC] border border-slate-200 flex items-center justify-center p-3">
        {isPdf && dataUrl ? (
          <iframe
            src={dataUrl}
            title={fileName}
            style={{ transform: `scale(${zoom})`, transformOrigin: 'top center' }}
            className="w-full h-full min-h-[320px] border-0 rounded bg-white"
          />
        ) : isImage && dataUrl ? (
          <img
            src={dataUrl}
            alt={fileName}
            style={{ transform: `scale(${zoom})`, transformOrigin: 'center' }}
            className="max-w-full max-h-full object-contain rounded shadow-sm transition-transform"
          />
        ) : textContent !== undefined ? (
          <pre
            className="w-full h-full self-start text-xs text-slate-800 whitespace-pre-wrap font-mono leading-relaxed bg-white p-3 rounded-lg border border-slate-200 overflow-auto"
            dir="auto"
          >
            {textContent}
          </pre>
        ) : (
          <div className="flex flex-col items-center gap-3 text-center py-10">
            <img src="/icons/win11_pdf.png" alt="" className="w-12 h-12 opacity-85" />
            <div className="text-xs font-semibold text-slate-600">
              اختر ملف PDF أو صورة أو مستندًا نصيًا لعرضه هنا
            </div>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="px-4 py-2 rounded-lg bg-[#0067C0] text-white text-xs font-semibold cursor-pointer"
            >
              اختيار ملف
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
