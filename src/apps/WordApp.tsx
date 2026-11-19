import React, { useEffect, useRef, useState } from 'react';

interface WordAppProps {
  initialTitle?: string;
  initialText?: string;
  onNotify: (title: string, msg: string) => void;
}

const KEY_WORD_TITLE = 'orkeit_word_title_v1';
const KEY_WORD_HTML = 'orkeit_word_html_v1';

const RIBBON_TABS = [
  'File',
  'Home',
  'Insert',
  'Draw',
  'Design',
  'Layout',
  'References',
  'Mailings',
  'Review',
  'View',
  'Help',
] as const;

export const WordApp: React.FC<WordAppProps> = ({
  initialTitle,
  initialText,
  onNotify,
}) => {
  const [docTitle, setDocTitle] = useState<string>(
    () => initialTitle || localStorage.getItem(KEY_WORD_TITLE) || 'Document1'
  );
  const [activeTab, setActiveTab] = useState<(typeof RIBBON_TABS)[number]>('Home');
  const [saveState, setSaveState] = useState<'Saved' | 'Saving...'>('Saved');
  const [searchTerm, setSearchTerm] = useState('');
  const [replaceTerm, setReplaceTerm] = useState('');
  const [showFindModal, setShowFindModal] = useState(false);
  const [showFileMenu, setShowFileMenu] = useState(false);
  const [paperBg, setPaperBg] = useState<'#FFFFFF' | '#FCF7EC' | '#F0F6FF'>('#FFFFFF');
  const [narrowMargins, setNarrowMargins] = useState(false);
  const [readMode, setReadMode] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [fontSize, setFontSize] = useState(15);
  const [fontFamily, setFontFamily] = useState('Calibri, Cairo, sans-serif');
  const [showMarks, setShowMarks] = useState(false);
  const [wordStats, setWordStats] = useState({ words: 0, chars: 0 });

  const editorRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const updateStats = () => {
    const text = editorRef.current?.innerText || '';
    const trimmed = text.trim();
    const words = trimmed ? trimmed.split(/\s+/).length : 0;
    setWordStats({ words, chars: text.length });
  };

  useEffect(() => {
    const el = editorRef.current;
    if (!el) return;
    if (initialText !== undefined) {
      el.innerText = initialText;
    } else {
      const saved = localStorage.getItem(KEY_WORD_HTML);
      if (saved) {
        el.innerHTML = saved;
      } else {
        el.innerHTML =
          '<h1 style="font-size:22px;font-weight:700;color:#185ABD;margin-bottom:8px;">مستند Oriket Word</h1><p>ابدأ بكتابة المستند الخاص بك هنا. يدعم المحرر التنسيق الكامل، الخطوط، الألوان، الجداول، والحفظ السريع.</p>';
      }
    }
    updateStats();
  }, []);

  const persistDoc = () => {
    setSaveState('Saving...');
    localStorage.setItem(KEY_WORD_TITLE, docTitle);
    if (editorRef.current) {
      localStorage.setItem(KEY_WORD_HTML, editorRef.current.innerHTML);
    }
    updateStats();
    window.setTimeout(() => setSaveState('Saved'), 250);
  };

  const exec = (cmd: string, value?: string) => {
    editorRef.current?.focus();
    document.execCommand(cmd, false, value);
    persistDoc();
  };

  const insertHtmlAtCursor = (html: string) => {
    editorRef.current?.focus();
    document.execCommand('insertHTML', false, html);
    persistDoc();
  };

  const handleDownloadTxt = () => {
    const content = editorRef.current?.innerText || '';
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${docTitle || 'Document1'}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    onNotify('Word', `تم تصدير ${docTitle}.txt`);
  };

  const handleOpenTxt = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (editorRef.current) {
        editorRef.current.innerText = String(reader.result || '');
      }
      setDocTitle(file.name.replace(/\.[^.]+$/, ''));
      persistDoc();
      onNotify('Word', `تم فتح ${file.name}`);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleSearchNext = () => {
    if (!searchTerm.trim() || !editorRef.current) return;
    const text = editorRef.current.innerText;
    if (!text.toLowerCase().includes(searchTerm.toLowerCase())) {
      onNotify('Word', `لم يتم العثور على "${searchTerm}"`);
    } else {
      onNotify('Word', `تم العثور على "${searchTerm}" داخل المستند`);
    }
  };

  const handleReplaceAll = () => {
    if (!searchTerm || !editorRef.current) return;
    const html = editorRef.current.innerHTML;
    const escaped = searchTerm.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    editorRef.current.innerHTML = html.replace(new RegExp(escaped, 'g'), replaceTerm);
    persistDoc();
    onNotify('Word', 'تم تنفيذ الاستبدال بنجاح');
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-[#ECF0F6] text-[#1C2430] select-none" dir="ltr">
      <input
        ref={fileInputRef}
        type="file"
        accept=".txt,.md,.html"
        className="hidden"
        onChange={handleOpenTxt}
      />

      {/* Top Word 365 Header */}
      <div className="bg-[#F3F5F8] px-3 pt-1.5 pb-1 flex flex-wrap items-center gap-2 border-b border-[#E1E6EE]">
        <img src="/icons/win11_word.png" alt="" className="w-5 h-5" />
        <input
          type="text"
          value={docTitle}
          onChange={(e) => {
            setDocTitle(e.target.value);
            localStorage.setItem(KEY_WORD_TITLE, e.target.value);
          }}
          className="h-7 px-2 rounded bg-transparent hover:bg-white focus:bg-white border border-transparent focus:border-[#185ABD] text-xs font-bold text-[#122036] w-36 focus:outline-none"
        />
        <span className="text-[11px] text-slate-500">{saveState}</span>

        <div className="ml-auto flex items-center gap-2">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              handleSearchNext();
            }}
            className="flex items-center"
          >
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search in document..."
              className="h-7 w-40 px-3 rounded-full bg-white border border-[#D4DBE6] text-xs focus:outline-none focus:border-[#185ABD]"
            />
          </form>
          <button
            type="button"
            onClick={handleDownloadTxt}
            className="h-7 px-2.5 rounded-md bg-[#185ABD] hover:bg-[#134A9C] text-white text-[11px] font-semibold cursor-pointer"
          >
            Export TXT
          </button>
        </div>
      </div>

      {/* Ribbon Tabs */}
      {!readMode && (
        <div className="bg-[#F3F5F8] px-3 pt-1 flex items-center gap-1 overflow-x-auto text-xs">
          {RIBBON_TABS.map((tab) => (
            <div key={tab} className="relative">
              <button
                type="button"
                onClick={() => {
                  if (tab === 'File') setShowFileMenu((v) => !v);
                  else {
                    setShowFileMenu(false);
                    setActiveTab(tab);
                  }
                }}
                className={`px-2.5 py-1 rounded-t-md font-medium whitespace-nowrap cursor-pointer ${
                  tab === 'File'
                    ? 'text-[#185ABD] font-bold hover:bg-white/70'
                    : activeTab === tab
                    ? 'bg-white text-[#185ABD] font-bold shadow-2xs'
                    : 'text-slate-600 hover:bg-white/60'
                }`}
              >
                {tab}
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Floating White Ribbon Card */}
      {!readMode && (
        <div className="mx-2 mt-1 mb-2 bg-white rounded-xl border border-[#DCE2EC] shadow-xs px-3 py-2 flex flex-wrap items-center gap-2 text-xs relative">
          {showFileMenu && (
            <div className="absolute left-0 top-0 z-40 w-48 bg-white border border-slate-200 rounded-xl shadow-xl py-1.5 text-xs">
              <button
                type="button"
                onClick={() => {
                  if (editorRef.current) editorRef.current.innerHTML = '<p><br/></p>';
                  setDocTitle('Document1');
                  persistDoc();
                  setShowFileMenu(false);
                }}
                className="w-full text-left px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                New Document
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowFileMenu(false);
                  fileInputRef.current?.click();
                }}
                className="w-full text-left px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                Open TXT...
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowFileMenu(false);
                  handleDownloadTxt();
                }}
                className="w-full text-left px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                Save / Download TXT
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowFileMenu(false);
                  setShowFindModal(true);
                }}
                className="w-full text-left px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
              >
                Find & Replace
              </button>
            </div>
          )}

          {activeTab === 'Home' && (
            <>
              <div className="flex items-center gap-1 pr-2 border-r border-slate-200">
                <button type="button" onClick={() => exec('undo')} className="px-2 py-1 rounded hover:bg-slate-100 cursor-pointer" title="Undo">↶</button>
                <button type="button" onClick={() => exec('redo')} className="px-2 py-1 rounded hover:bg-slate-100 cursor-pointer" title="Redo">↷</button>
              </div>
              <div className="flex items-center gap-1 pr-2 border-r border-slate-200">
                <select
                  value={fontFamily}
                  onChange={(e) => {
                    setFontFamily(e.target.value);
                    exec('fontName', e.target.value);
                  }}
                  className="h-7 px-2 rounded border border-slate-200 bg-[#F9FAFC] text-xs"
                >
                  <option value="Calibri, Cairo, sans-serif">Calibri</option>
                  <option value="Georgia, serif">Georgia</option>
                  <option value="JetBrains Mono, monospace">Monospace</option>
                </select>
                <button
                  type="button"
                  onClick={() => setFontSize((s) => Math.max(10, s - 1))}
                  className="w-7 h-7 rounded border border-slate-200 hover:bg-slate-100 cursor-pointer"
                >
                  Aˇ
                </button>
                <span className="w-7 text-center font-mono-tabular">{fontSize}</span>
                <button
                  type="button"
                  onClick={() => setFontSize((s) => Math.min(32, s + 1))}
                  className="w-7 h-7 rounded border border-slate-200 hover:bg-slate-100 cursor-pointer"
                >
                  A^
                </button>
              </div>
              <div className="flex items-center gap-1 pr-2 border-r border-slate-200">
                <button type="button" onClick={() => exec('bold')} className="w-7 h-7 rounded hover:bg-slate-100 font-bold cursor-pointer">B</button>
                <button type="button" onClick={() => exec('italic')} className="w-7 h-7 rounded hover:bg-slate-100 italic cursor-pointer">I</button>
                <button type="button" onClick={() => exec('underline')} className="w-7 h-7 rounded hover:bg-slate-100 underline cursor-pointer">U</button>
                <button type="button" onClick={() => exec('strikeThrough')} className="w-7 h-7 rounded hover:bg-slate-100 line-through cursor-pointer">ab</button>
                <button type="button" onClick={() => exec('subscript')} className="w-7 h-7 rounded hover:bg-slate-100 cursor-pointer">x₂</button>
                <button type="button" onClick={() => exec('superscript')} className="w-7 h-7 rounded hover:bg-slate-100 cursor-pointer">x²</button>
                <input
                  type="color"
                  title="Text Color"
                  onChange={(e) => exec('foreColor', e.target.value)}
                  className="w-6 h-6 rounded border border-slate-200 cursor-pointer"
                />
                <input
                  type="color"
                  defaultValue="#FFF59D"
                  title="Highlight Color"
                  onChange={(e) => exec('hiliteColor', e.target.value)}
                  className="w-6 h-6 rounded border border-slate-200 cursor-pointer"
                />
              </div>
              <div className="flex items-center gap-1 pr-2 border-r border-slate-200">
                <button type="button" onClick={() => exec('insertUnorderedList')} className="px-2 h-7 rounded hover:bg-slate-100 cursor-pointer">• List</button>
                <button type="button" onClick={() => exec('insertOrderedList')} className="px-2 h-7 rounded hover:bg-slate-100 cursor-pointer">1. List</button>
                <button type="button" onClick={() => exec('justifyLeft')} className="px-2 h-7 rounded hover:bg-slate-100 cursor-pointer">⫷</button>
                <button type="button" onClick={() => exec('justifyCenter')} className="px-2 h-7 rounded hover:bg-slate-100 cursor-pointer">≣</button>
                <button type="button" onClick={() => exec('justifyRight')} className="px-2 h-7 rounded hover:bg-slate-100 cursor-pointer">⫸</button>
                <button
                  type="button"
                  onClick={() => setShowMarks((m) => !m)}
                  className={`w-7 h-7 rounded cursor-pointer ${showMarks ? 'bg-[#E5EFFB] text-[#185ABD]' : 'hover:bg-slate-100'}`}
                  title="Show formatting marks"
                >
                  ¶
                </button>
              </div>
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => exec('formatBlock', 'P')} className="px-2.5 py-1 rounded border border-slate-200 hover:bg-[#EBF2FC] text-[11px] cursor-pointer">Normal</button>
                <button type="button" onClick={() => exec('formatBlock', 'H2')} className="px-2.5 py-1 rounded border border-slate-200 hover:bg-[#EBF2FC] text-[11px] font-bold text-[#185ABD] cursor-pointer">Heading 1</button>
                <button type="button" onClick={() => exec('formatBlock', 'H1')} className="px-2.5 py-1 rounded border border-slate-200 hover:bg-[#EBF2FC] text-[11px] font-extrabold cursor-pointer">Title</button>
              </div>
            </>
          )}

          {activeTab === 'Insert' && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => insertHtmlAtCursor(`<span> ${new Date().toLocaleDateString()} </span>`)}
                className="px-3 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Insert Date
              </button>
              <button
                type="button"
                onClick={() =>
                  insertHtmlAtCursor(
                    '<table style="width:100%;border-collapse:collapse;margin:8px 0;"><tr><th style="border:1px solid #cbd5e1;padding:6px;background:#eef3fa;">Column 1</th><th style="border:1px solid #cbd5e1;padding:6px;background:#eef3fa;">Column 2</th></tr><tr><td style="border:1px solid #cbd5e1;padding:6px;">Item</td><td style="border:1px solid #cbd5e1;padding:6px;">Value</td></tr></table>'
                  )
                }
                className="px-3 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Insert Table
              </button>
              <button
                type="button"
                onClick={() => insertHtmlAtCursor('<hr style="margin:12px 0;border-color:#cbd5e1;" />')}
                className="px-3 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Horizontal Line
              </button>
            </div>
          )}

          {(activeTab === 'Layout' || activeTab === 'Design') && (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-slate-500">Paper Color:</span>
              <button type="button" onClick={() => setPaperBg('#FFFFFF')} className="px-2.5 py-1 rounded border border-slate-200 bg-white cursor-pointer">White</button>
              <button type="button" onClick={() => setPaperBg('#FCF7EC')} className="px-2.5 py-1 rounded border border-slate-200 bg-[#FCF7EC] cursor-pointer">Warm</button>
              <button type="button" onClick={() => setPaperBg('#F0F6FF')} className="px-2.5 py-1 rounded border border-slate-200 bg-[#F0F6FF] cursor-pointer">Soft Blue</button>
              <button
                type="button"
                onClick={() => setNarrowMargins((n) => !n)}
                className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Margins: {narrowMargins ? 'Narrow' : 'Standard'}
              </button>
            </div>
          )}

          {(activeTab === 'Review' || activeTab === 'View' || activeTab === 'Help' || activeTab === 'Draw' || activeTab === 'References' || activeTab === 'Mailings') && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setShowFindModal(true)}
                className="px-3 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Find & Replace
              </button>
              <button
                type="button"
                onClick={() => setReadMode(true)}
                className="px-3 py-1 rounded bg-[#EBF3FE] text-[#185ABD] font-semibold cursor-pointer"
              >
                Read Mode
              </button>
              <button
                type="button"
                onClick={() =>
                  onNotify('Word Count', `${wordStats.words} words • ${wordStats.chars} characters`)
                }
                className="px-3 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
              >
                Word Count
              </button>
            </div>
          )}
        </div>
      )}

      {readMode && (
        <div className="bg-[#185ABD] text-white px-3 py-1 flex items-center justify-between text-xs">
          <span>Read Mode Active</span>
          <button
            type="button"
            onClick={() => setReadMode(false)}
            className="px-2.5 py-0.5 rounded bg-white/20 hover:bg-white/30 cursor-pointer"
          >
            Exit Read Mode
          </button>
        </div>
      )}

      {/* Document Canvas Stage */}
      <div className="flex-1 min-h-0 overflow-auto p-4 sm:p-6 flex justify-center">
        <div
          style={{
            backgroundColor: paperBg,
            transform: `scale(${zoom})`,
            transformOrigin: 'top center',
            fontFamily,
            fontSize: `${fontSize}px`,
            padding: narrowMargins ? '20px' : '40px',
          }}
          className="w-full max-w-[740px] min-h-[520px] shadow-md border border-[#D4DBE6] rounded-xs transition-all"
        >
          <div
            ref={editorRef}
            contentEditable={!readMode}
            suppressContentEditableWarning
            onInput={persistDoc}
            dir="auto"
            className={`min-h-[440px] focus:outline-none leading-relaxed ${
              showMarks ? 'underline decoration-dotted decoration-slate-300' : ''
            }`}
          />
        </div>
      </div>

      {/* Bottom Word Status Bar */}
      <div className="h-7 px-3 bg-[#F3F5F8] border-t border-[#DCE2EC] flex items-center justify-between text-[11px] text-slate-600">
        <span>
          Page 1 of 1  |  {wordStats.words} words  |  {wordStats.chars} chars
        </span>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => setZoom((z) => Math.max(0.6, +(z - 0.1).toFixed(2)))} className="px-1.5 hover:bg-slate-200 rounded cursor-pointer">−</button>
          <input
            type="range"
            min={60}
            max={180}
            step={10}
            value={Math.round(zoom * 100)}
            onChange={(e) => setZoom(Number(e.target.value) / 100)}
            className="w-24 accent-[#185ABD]"
          />
          <button type="button" onClick={() => setZoom((z) => Math.min(1.8, +(z + 0.1).toFixed(2)))} className="px-1.5 hover:bg-slate-200 rounded cursor-pointer">+</button>
          <span className="font-mono-tabular w-10 text-right">{Math.round(zoom * 100)}%</span>
        </div>
      </div>

      {showFindModal && (
        <div
          onClick={() => setShowFindModal(false)}
          className="fixed inset-0 z-[1400] bg-black/40 flex items-center justify-center p-4"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-xs bg-white rounded-xl shadow-2xl p-4 flex flex-col gap-2.5 text-xs"
          >
            <div className="font-bold text-[#122036]">Find & Replace</div>
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Find text..."
              className="h-8 px-2.5 rounded border border-slate-300"
            />
            <input
              type="text"
              value={replaceTerm}
              onChange={(e) => setReplaceTerm(e.target.value)}
              placeholder="Replace with..."
              className="h-8 px-2.5 rounded border border-slate-300"
            />
            <div className="flex justify-end gap-2 pt-1">
              <button type="button" onClick={() => setShowFindModal(false)} className="px-3 py-1.5 rounded text-slate-600 hover:bg-slate-100 cursor-pointer">Close</button>
              <button type="button" onClick={handleReplaceAll} className="px-3 py-1.5 rounded bg-[#185ABD] text-white font-semibold cursor-pointer">Replace All</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
