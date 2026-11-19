import React, { useMemo, useRef, useState } from 'react';
import { AppId, VirtualFileEntry, WindowPayload } from '../types/desktop';
import { getIconForFileName } from '../utils/fluentIcons';
import {
  formatBytes,
  getWorkspaceFiles,
  moveFileToRecycleBin,
  saveWorkspaceFiles,
} from '../utils/virtualFs';

interface FileExplorerAppProps {
  initialFolder?: string | null;
  onOpenApp: (appId: AppId, title: string, payload?: WindowPayload) => void;
  onNotify: (title: string, msg: string) => void;
  onRecycleChanged: () => void;
}

export const FileExplorerApp: React.FC<FileExplorerAppProps> = ({
  initialFolder,
  onOpenApp,
  onNotify,
  onRecycleChanged,
}) => {
  const [currentPath, setCurrentPath] = useState<string>(
    initialFolder ? `C:\\${initialFolder}` : 'C:'
  );
  const [searchQuery, setSearchQuery] = useState('');
  const [tick, setTick] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [dialogMode, setDialogMode] = useState<'new_folder' | 'new_txt' | 'rename' | null>(null);
  const [dialogInput, setDialogInput] = useState('');
  const importInputRef = useRef<HTMLInputElement>(null);

  const allFiles = useMemo(() => getWorkspaceFiles(), [tick]);

  const displayedItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return allFiles
      .filter((f) => {
        if (q) return f.name.toLowerCase().includes(q);
        return f.parentPath === currentPath;
      })
      .sort((a, b) => {
        if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
        return a.name.localeCompare(b.name);
      });
  }, [allFiles, currentPath, searchQuery]);

  const selectedItem = displayedItems.find((f) => f.id === selectedId) || null;

  const openEntry = (entry: VirtualFileEntry) => {
    if (entry.isDirectory) {
      setCurrentPath(`${entry.parentPath}\\${entry.name}`);
      setSelectedId(null);
      setSearchQuery('');
      return;
    }
    const lower = entry.name.toLowerCase();
    if (lower.endsWith('.csv') || lower.endsWith('.xls') || lower.endsWith('.xlsx')) {
      onOpenApp('excel', entry.name, {
        fileId: entry.id,
        externalFile: {
          name: entry.name,
          mime: entry.mimeType,
          textContent: entry.content,
        },
      });
    } else if (lower.endsWith('.doc') || lower.endsWith('.docx')) {
      onOpenApp('word', entry.name, {
        fileId: entry.id,
        externalFile: {
          name: entry.name,
          mime: entry.mimeType,
          textContent: entry.content,
        },
      });
    } else if (entry.mimeType.startsWith('image/') || /\.(png|jpg|jpeg|webp)$/i.test(lower)) {
      onOpenApp('file_viewer', entry.name, {
        externalFile: { name: entry.name, mime: entry.mimeType, dataUrl: entry.content },
      });
    } else if (entry.mimeType.startsWith('video/') || /\.(mp4|webm)$/i.test(lower)) {
      onOpenApp('video_player', entry.name, {
        externalFile: { name: entry.name, mime: entry.mimeType, dataUrl: entry.content },
      });
    } else if (entry.mimeType.startsWith('audio/') || /\.(mp3|wav|ogg)$/i.test(lower)) {
      onOpenApp('music_player', 'مشغل الموسيقى', {
        externalFile: { name: entry.name, mime: entry.mimeType, dataUrl: entry.content },
      });
    } else if (lower.endsWith('.pdf')) {
      onOpenApp('file_viewer', entry.name, {
        externalFile: { name: entry.name, mime: 'application/pdf', dataUrl: entry.content },
      });
    } else {
      onOpenApp('notepad', entry.name, {
        externalFile: { name: entry.name, mime: 'text/plain', textContent: entry.content || '' },
      });
    }
  };

  const handleImportFiles = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(e.target.files || []);
    if (picked.length === 0) return;
    picked.forEach((file) => {
      const isText =
        file.type.startsWith('text/') || /\.(txt|csv|json|md)$/i.test(file.name);
      const reader = new FileReader();
      reader.onload = () => {
        const files = getWorkspaceFiles();
        files.push({
          id: `imp_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
          name: file.name,
          parentPath: currentPath,
          isDirectory: false,
          sizeBytes: file.size,
          mimeType: file.type || 'application/octet-stream',
          content: String(reader.result || ''),
          updatedAt: Date.now(),
        });
        saveWorkspaceFiles(files);
        setTick((t) => t + 1);
      };
      if (isText) reader.readAsText(file);
      else reader.readAsDataURL(file);
    });
    onNotify('مستكشف الملفات', `تم استيراد ${picked.length} ملف إلى ${currentPath}`);
    e.target.value = '';
  };

  const handleConfirmDialog = (e: React.FormEvent) => {
    e.preventDefault();
    const val = dialogInput.trim();
    if (!val) return;
    const files = getWorkspaceFiles();

    if (dialogMode === 'new_folder') {
      files.push({
        id: `dir_${Date.now()}`,
        name: val,
        parentPath: currentPath,
        isDirectory: true,
        sizeBytes: 0,
        mimeType: 'inode/directory',
        updatedAt: Date.now(),
      });
      saveWorkspaceFiles(files);
      onNotify('مستكشف الملفات', `تم إنشاء المجلد: ${val}`);
    } else if (dialogMode === 'new_txt') {
      const name = val.endsWith('.txt') ? val : `${val}.txt`;
      files.push({
        id: `txt_${Date.now()}`,
        name,
        parentPath: currentPath,
        isDirectory: false,
        sizeBytes: 0,
        mimeType: 'text/plain',
        content: '',
        updatedAt: Date.now(),
      });
      saveWorkspaceFiles(files);
      onNotify('مستكشف الملفات', `تم إنشاء الملف: ${name}`);
    } else if (dialogMode === 'rename' && selectedItem) {
      const updated = files.map((f) => (f.id === selectedItem.id ? { ...f, name: val } : f));
      saveWorkspaceFiles(updated);
      onNotify('مستكشف الملفات', `تمت إعادة التسمية إلى ${val}`);
    }

    setDialogMode(null);
    setDialogInput('');
    setTick((t) => t + 1);
  };

  const goUp = () => {
    if (currentPath === 'C:') return;
    const idx = currentPath.lastIndexOf('\\');
    setCurrentPath(idx > 0 ? currentPath.slice(0, idx) : 'C:');
    setSelectedId(null);
  };

  const quickLocations = [
    { label: 'القرص المحلي (C:)', path: 'C:', icon: '/icons/win11_thispc.png' },
    { label: 'المستندات', path: 'C:\\Documents', icon: '/icons/win11_folder_documents.png' },
    { label: 'التنزيلات', path: 'C:\\Downloads', icon: '/icons/win11_folder_downloads.png' },
    { label: 'الصور', path: 'C:\\Pictures', icon: '/icons/win11_folder_pictures.png' },
    { label: 'الموسيقى', path: 'C:\\Music', icon: '/icons/win11_folder_music.png' },
    { label: 'الفيديو', path: 'C:\\Videos', icon: '/icons/win11_folder_videos.png' },
    { label: 'سطح المكتب', path: 'C:\\Desktop', icon: '/icons/win11_folder_desktop.png' },
  ];

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-white rounded-lg border border-slate-200 overflow-hidden" dir="rtl">
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-1.5 px-2.5 py-2 bg-[#F6F9FD] border-b border-slate-200">
        <button
          type="button"
          onClick={goUp}
          disabled={currentPath === 'C:'}
          className="px-2.5 py-1 rounded bg-white border border-slate-200 hover:bg-slate-50 disabled:opacity-40 text-xs font-semibold cursor-pointer"
        >
          ↑ للأعلى
        </button>
        <div className="flex-1 min-w-[140px] h-7 px-3 rounded bg-white border border-slate-200 flex items-center text-xs font-mono text-slate-700" dir="ltr">
          {currentPath}
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="بحث في الملفات..."
          className="h-7 w-36 px-2.5 rounded bg-white border border-slate-200 text-xs focus:outline-none focus:border-[#0067C0]"
        />
        <button
          type="button"
          onClick={() => {
            setDialogMode('new_folder');
            setDialogInput('مجلد جديد');
          }}
          className="px-2.5 py-1 rounded bg-white border border-slate-200 hover:bg-slate-50 text-xs font-semibold cursor-pointer"
        >
          + مجلد
        </button>
        <button
          type="button"
          onClick={() => {
            setDialogMode('new_txt');
            setDialogInput('ملف نصي');
          }}
          className="px-2.5 py-1 rounded bg-white border border-slate-200 hover:bg-slate-50 text-xs font-semibold cursor-pointer"
        >
          + نص
        </button>
        <button
          type="button"
          onClick={() => importInputRef.current?.click()}
          className="px-2.5 py-1 rounded bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-semibold cursor-pointer"
        >
          استيراد ملف
        </button>
        {selectedItem && (
          <>
            <button
              type="button"
              onClick={() => {
                setDialogMode('rename');
                setDialogInput(selectedItem.name);
              }}
              className="px-2.5 py-1 rounded bg-amber-50 border border-amber-200 text-amber-800 text-xs font-semibold cursor-pointer"
            >
              تسمية
            </button>
            <button
              type="button"
              onClick={() => {
                if (moveFileToRecycleBin(selectedItem.id)) {
                  onRecycleChanged();
                  setSelectedId(null);
                  setTick((t) => t + 1);
                  onNotify('سلة المحذوفات', `تم نقل "${selectedItem.name}" إلى سلة المحذوفات`);
                }
              }}
              className="px-2.5 py-1 rounded bg-red-50 border border-red-200 text-red-700 text-xs font-semibold cursor-pointer"
            >
              حذف
            </button>
          </>
        )}
      </div>

      <input
        ref={importInputRef}
        type="file"
        multiple
        className="hidden"
        onChange={handleImportFiles}
      />

      {/* Split Sidebar + Content */}
      <div className="flex-1 min-h-0 flex">
        <aside className="hidden sm:flex w-44 shrink-0 border-l border-slate-200 bg-[#FAFCFF] p-2 flex-col gap-1 overflow-y-auto">
          {quickLocations.map((loc) => (
            <button
              key={loc.path}
              type="button"
              onClick={() => {
                setCurrentPath(loc.path);
                setSelectedId(null);
                setSearchQuery('');
              }}
              className={`flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs font-medium text-right transition cursor-pointer ${
                currentPath === loc.path
                  ? 'bg-[#E5F1FB] text-[#0067C0] font-bold'
                  : 'hover:bg-slate-100 text-slate-700'
              }`}
            >
              <img src={loc.icon} alt="" className="w-4 h-4 shrink-0" />
              <span className="truncate">{loc.label}</span>
            </button>
          ))}
        </aside>

        <div className="flex-1 min-h-0 overflow-y-auto p-3">
          {displayedItems.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-slate-400 text-xs py-12">
              هذا المجلد فارغ
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
              {displayedItems.map((entry) => {
                const isSel = selectedId === entry.id;
                return (
                  <button
                    key={entry.id}
                    type="button"
                    onClick={() => setSelectedId(entry.id)}
                    onDoubleClick={() => openEntry(entry)}
                    className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border text-center transition cursor-pointer ${
                      isSel
                        ? 'bg-[#E5F1FB] border-[#0067C0]'
                        : 'bg-white border-slate-200/70 hover:bg-slate-50'
                    }`}
                  >
                    <img
                      src={getIconForFileName(entry.name, entry.isDirectory)}
                      alt=""
                      className="w-10 h-10 object-contain"
                    />
                    <span className="text-xs font-semibold text-[#122036] truncate w-full">
                      {entry.name}
                    </span>
                    <span className="text-[10px] text-slate-400 font-mono-tabular">
                      {entry.isDirectory ? 'مجلد' : formatBytes(entry.sizeBytes)}
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Footer status bar */}
      <div className="h-7 px-3 bg-[#F6F9FD] border-t border-slate-200 flex items-center justify-between text-[11px] text-slate-500">
        <span>{displayedItems.length} عنصر</span>
        <span>انقر مرتين لفتح الملف أو المجلد</span>
      </div>

      {dialogMode && (
        <div
          onClick={() => setDialogMode(null)}
          className="fixed inset-0 z-[1400] bg-black/40 flex items-center justify-center p-4"
        >
          <form
            onClick={(e) => e.stopPropagation()}
            onSubmit={handleConfirmDialog}
            className="w-full max-w-xs bg-white rounded-xl shadow-2xl p-4 flex flex-col gap-3"
          >
            <div className="text-xs font-bold text-[#122036]">
              {dialogMode === 'new_folder'
                ? 'إنشاء مجلد جديد'
                : dialogMode === 'new_txt'
                ? 'إنشاء ملف نصي جديد'
                : 'إعادة التسمية'}
            </div>
            <input
              type="text"
              value={dialogInput}
              onChange={(e) => setDialogInput(e.target.value)}
              className="h-9 px-3 rounded-lg border border-slate-300 text-xs focus:outline-none focus:border-[#0067C0]"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setDialogMode(null)}
                className="px-3 py-1.5 rounded-lg text-xs text-slate-600 hover:bg-slate-100 cursor-pointer"
              >
                إلغاء
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 rounded-lg bg-[#0067C0] text-white text-xs font-semibold cursor-pointer"
              >
                تأكيد
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
