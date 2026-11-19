import React, { useMemo, useState } from 'react';
import { AppId, RecycledEntry, WindowPayload } from '../types/desktop';
import {
  deleteRecycledEntryPermanently,
  emptyRecycleBin,
  formatBytes,
  getRecycledIcon,
  getRecycledItems,
  isConfirmDeleteEnabled,
  restoreRecycledEntry,
  setConfirmDeleteEnabled,
} from '../utils/virtualFs';

interface RecycleBinAppProps {
  onOpenApp: (appId: AppId, title: string, payload?: WindowPayload) => void;
  onRecycleChanged: () => void;
  onNotify: (title: string, msg: string) => void;
}

export const RecycleBinApp: React.FC<RecycleBinAppProps> = ({
  onOpenApp,
  onRecycleChanged,
  onNotify,
}) => {
  const [tick, setTick] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<'large' | 'details'>('large');
  const [activeTab, setActiveTab] = useState<'manage' | 'home' | 'share' | 'view'>('manage');
  const [ribbonCollapsed, setRibbonCollapsed] = useState(false);
  const [groupExpanded, setGroupExpanded] = useState(true);
  const [showProperties, setShowProperties] = useState(false);
  const [showFileMenu, setShowFileMenu] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(() => isConfirmDeleteEnabled());

  const items = useMemo(() => {
    const all = getRecycledItems();
    const q = searchQuery.trim().toLowerCase();
    return q
      ? all.filter(
          (i) =>
            i.displayName.toLowerCase().includes(q) ||
            i.originalLocation.toLowerCase().includes(q)
        )
      : all;
  }, [tick, searchQuery]);

  const selectedEntry: RecycledEntry | null =
    items.find((i) => i.id === selectedId) || null;

  const refresh = () => {
    setTick((t) => t + 1);
    onRecycleChanged();
  };

  const handleEmpty = () => {
    if (items.length === 0) return;
    if (confirmDelete && !window.confirm(`حذف جميع العناصر (${items.length}) نهائيًا؟`)) {
      return;
    }
    emptyRecycleBin();
    setSelectedId(null);
    refresh();
    onNotify('سلة المحذوفات', 'تم إفراغ سلة المحذوفات بالكامل');
  };

  const handleRestoreAll = () => {
    if (items.length === 0) return;
    items.forEach((item) => restoreRecycledEntry(item.id));
    setSelectedId(null);
    refresh();
    onNotify('سلة المحذوفات', 'تمت استعادة جميع العناصر');
  };

  const handleRestoreSelected = () => {
    if (!selectedEntry) return;
    restoreRecycledEntry(selectedEntry.id);
    setSelectedId(null);
    refresh();
    onNotify('سلة المحذوفات', `تمت استعادة ${selectedEntry.displayName}`);
  };

  const handleDeleteSelected = () => {
    if (!selectedEntry) return;
    if (
      confirmDelete &&
      !window.confirm(`حذف "${selectedEntry.displayName}" نهائيًا؟`)
    ) {
      return;
    }
    deleteRecycledEntryPermanently(selectedEntry.id);
    setSelectedId(null);
    refresh();
    onNotify('سلة المحذوفات', `تم حذف ${selectedEntry.displayName} نهائيًا`);
  };

  const navNodes = [
    { label: 'Quick access', icon: '/icons/win11_explorer.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر') },
    { label: 'Desktop', icon: '/icons/win11_folder_desktop.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر', { initialFolder: 'Desktop' }) },
    { label: 'This PC', icon: '/icons/win11_thispc.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر') },
    { label: 'Documents', icon: '/icons/win11_folder_documents.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر', { initialFolder: 'Documents' }) },
    { label: 'Downloads', icon: '/icons/win11_folder_downloads.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر', { initialFolder: 'Downloads' }) },
    { label: 'Pictures', icon: '/icons/win11_folder_pictures.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر', { initialFolder: 'Pictures' }) },
    { label: 'Local Disk (C:)', icon: '/icons/win11_thispc.png', action: () => onOpenApp('my_computer', 'هذا الكمبيوتر') },
    { label: 'Recycle Bin', icon: items.length > 0 ? '/icons/win11_recyclebin_full.png' : '/icons/win11_recyclebin_empty.png', active: true, action: () => {} },
  ];

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-white text-[#1E1E1E] select-none relative" dir="ltr">
      {/* Contextual Top Header */}
      <div className="bg-[#F5F6F7] border-b border-[#E1E3E6] flex flex-col">
        <div className="flex items-end px-2 pt-1 gap-1 text-[11px]">
          <div className="relative">
            <button
              type="button"
              onClick={() => setShowFileMenu((v) => !v)}
              className="px-3 py-1 bg-[#1979CA] text-white font-semibold rounded-t cursor-pointer"
            >
              File
            </button>
            {showFileMenu && (
              <div className="absolute left-0 top-full z-50 w-48 bg-white border border-slate-200 shadow-xl py-1 text-xs">
                <button
                  type="button"
                  onClick={() => {
                    setShowFileMenu(false);
                    setShowProperties(true);
                  }}
                  className="w-full text-left px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
                >
                  Recycle Bin Properties
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowFileMenu(false);
                    handleEmpty();
                  }}
                  className="w-full text-left px-3 py-1.5 hover:bg-red-50 text-red-600 cursor-pointer"
                >
                  Empty Recycle Bin
                </button>
              </div>
            )}
          </div>
          {(['home', 'share', 'view'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setActiveTab(t)}
              className={`px-3 py-1 capitalize rounded-t cursor-pointer ${
                activeTab === t ? 'bg-white border-x border-t border-[#DADCE0] font-semibold' : 'hover:bg-black/5'
              }`}
            >
              {t}
            </button>
          ))}

          {/* Purple Contextual Tab */}
          <div className="ml-1 flex flex-col">
            <div className="bg-[#F8E0F9] text-[#7E1F7E] text-[9px] font-bold px-2.5 py-0.5 text-center border-t-2 border-[#A838AE]">
              Recycle Bin Tools
            </div>
            <button
              type="button"
              onClick={() => setActiveTab('manage')}
              className={`px-3 py-0.5 text-[11px] font-semibold cursor-pointer ${
                activeTab === 'manage'
                  ? 'bg-white text-[#7E1F7E] border-x border-[#DADCE0]'
                  : 'bg-[#F8E0F9]/50 text-[#7E1F7E]'
              }`}
            >
              Manage
            </button>
          </div>

          <div className="ml-auto flex items-center gap-1 pb-1">
            <button
              type="button"
              onClick={() => setRibbonCollapsed((c) => !c)}
              className="w-6 h-5 rounded hover:bg-black/10 text-xs cursor-pointer"
              title="Toggle Ribbon"
            >
              {ribbonCollapsed ? '⌄' : '⌃'}
            </button>
            <button
              type="button"
              onClick={() => setShowProperties(true)}
              className="w-6 h-5 rounded hover:bg-black/10 text-xs font-bold text-[#1979CA] cursor-pointer"
              title="Properties & Help"
            >
              ?
            </button>
          </div>
        </div>

        {/* Ribbon Strip */}
        {!ribbonCollapsed && (
          <div className="bg-white border-t border-[#DADCE0] px-3 py-1.5 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={handleEmpty}
              disabled={items.length === 0}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-[#E5F1FB] disabled:opacity-40 cursor-pointer"
            >
              <span className="text-lg leading-none">🗑</span>
              <span className="text-[10px]">Empty Recycle Bin</span>
            </button>
            <button
              type="button"
              onClick={() => setShowProperties(true)}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-[#E5F1FB] cursor-pointer"
            >
              <span className="text-lg leading-none">📋</span>
              <span className="text-[10px]">Properties</span>
            </button>
            <div className="h-8 w-px bg-slate-200" />
            <button
              type="button"
              onClick={handleRestoreAll}
              disabled={items.length === 0}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-[#E5F1FB] disabled:opacity-40 cursor-pointer"
            >
              <span className="text-lg leading-none text-emerald-600">↺</span>
              <span className="text-[10px]">Restore all items</span>
            </button>
            <button
              type="button"
              onClick={handleRestoreSelected}
              disabled={!selectedEntry}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-[#E5F1FB] disabled:opacity-40 cursor-pointer"
            >
              <span className="text-lg leading-none text-[#1979CA]">↩</span>
              <span className="text-[10px]">Restore selected</span>
            </button>
            <button
              type="button"
              onClick={handleDeleteSelected}
              disabled={!selectedEntry}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-red-50 disabled:opacity-40 text-red-700 cursor-pointer"
            >
              <span className="text-lg leading-none">✕</span>
              <span className="text-[10px]">Delete permanently</span>
            </button>
            <div className="h-8 w-px bg-slate-200" />
            <button
              type="button"
              onClick={() => setViewMode((v) => (v === 'large' ? 'details' : 'large'))}
              className="flex flex-col items-center gap-0.5 px-2.5 py-1 rounded hover:bg-[#E5F1FB] cursor-pointer"
            >
              <span className="text-lg leading-none">{viewMode === 'large' ? '☷' : '⊞'}</span>
              <span className="text-[10px]">{viewMode === 'large' ? 'Details view' : 'Large icons'}</span>
            </button>
          </div>
        )}
      </div>

      {/* Address & Search Bar */}
      <div className="flex flex-wrap items-center gap-2 px-2.5 py-1.5 bg-white border-b border-[#E5E5E5] text-xs">
        <div className="flex items-center gap-1 text-slate-500">
          <button
            type="button"
            onClick={() => onOpenApp('my_computer', 'هذا الكمبيوتر')}
            className="w-6 h-6 rounded hover:bg-slate-100 cursor-pointer"
            title="This PC"
          >
            ←
          </button>
          <button type="button" onClick={refresh} className="w-6 h-6 rounded hover:bg-slate-100 cursor-pointer" title="Refresh">
            ↻
          </button>
        </div>
        <div className="flex-1 min-w-[140px] h-7 px-2.5 border border-[#D4D4D4] flex items-center gap-1.5 text-xs bg-white">
          <img
            src={items.length > 0 ? '/icons/win11_recyclebin_full.png' : '/icons/win11_recyclebin_empty.png'}
            alt=""
            className="w-4 h-4"
          />
          <span className="text-slate-400">›</span>
          <span className="font-medium">Recycle Bin</span>
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search Recycle Bin"
          className="h-7 w-44 px-2.5 border border-[#D4D4D4] text-xs focus:outline-none focus:border-[#1979CA]"
        />
      </div>

      {/* Main Split Pane */}
      <div className="flex-1 min-h-0 flex">
        <aside className="hidden sm:flex w-44 shrink-0 border-r border-[#EAEAEA] p-1.5 flex-col gap-0.5 overflow-y-auto text-xs">
          {navNodes.map((n) => (
            <button
              key={n.label}
              type="button"
              onClick={n.action}
              className={`flex items-center gap-2 px-2 py-1.5 rounded text-left cursor-pointer ${
                n.active ? 'bg-[#CCE8FF] font-semibold' : 'hover:bg-slate-100'
              }`}
            >
              <img src={n.icon} alt="" className="w-4 h-4 shrink-0" />
              <span className="truncate">{n.label}</span>
            </button>
          ))}
        </aside>

        <div className="flex-1 min-h-0 overflow-y-auto p-3">
          <button
            type="button"
            onClick={() => setGroupExpanded((g) => !g)}
            className="flex items-center gap-2 text-xs font-semibold text-[#1D3B6C] mb-2 cursor-pointer"
          >
            <span>{groupExpanded ? '⌄' : '›'}</span>
            <span>Recycle Bin items ({items.length})</span>
            <span className="h-px w-36 bg-slate-200 inline-block" />
          </button>

          {groupExpanded && (
            <>
              {items.length === 0 ? (
                <div className="py-16 text-center text-xs text-slate-400">
                  This folder is empty.
                </div>
              ) : viewMode === 'large' ? (
                <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-2">
                  {items.map((item) => {
                    const isSel = selectedId === item.id;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => setSelectedId(item.id)}
                        onDoubleClick={() => {
                          setSelectedId(item.id);
                          setShowProperties(true);
                        }}
                        className={`flex flex-col items-center gap-1.5 p-2.5 rounded border text-center cursor-pointer ${
                          isSel
                            ? 'bg-[#CCE8FF] border-[#99D1FF]'
                            : 'border-transparent hover:bg-[#E5F3FF]'
                        }`}
                      >
                        <img
                          src={item.previewUrl || getRecycledIcon(item)}
                          alt=""
                          className="w-11 h-11 object-contain"
                        />
                        <span className="text-[11px] font-medium truncate w-full">
                          {item.displayName}
                        </span>
                        <span className="text-[10px] text-slate-400 truncate w-full">
                          {item.originalLocation}
                        </span>
                      </button>
                    );
                  })}
                </div>
              ) : (
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-slate-500">
                      <th className="py-1.5 px-2">Name</th>
                      <th className="py-1.5 px-2">Original Location</th>
                      <th className="py-1.5 px-2">Date Deleted</th>
                      <th className="py-1.5 px-2">Size</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item) => {
                      const isSel = selectedId === item.id;
                      return (
                        <tr
                          key={item.id}
                          onClick={() => setSelectedId(item.id)}
                          className={`cursor-pointer border-b border-slate-100 ${
                            isSel ? 'bg-[#CCE8FF]' : 'hover:bg-[#E5F3FF]'
                          }`}
                        >
                          <td className="py-1.5 px-2 flex items-center gap-2">
                            <img src={getRecycledIcon(item)} alt="" className="w-4 h-4" />
                            <span>{item.displayName}</span>
                          </td>
                          <td className="py-1.5 px-2 text-slate-600">{item.originalLocation}</td>
                          <td className="py-1.5 px-2 text-slate-500 font-mono-tabular">
                            {new Date(item.deletedAtMs).toLocaleString()}
                          </td>
                          <td className="py-1.5 px-2 font-mono-tabular">
                            {formatBytes(item.sizeBytes)}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      </div>

      {/* Bottom Status Bar */}
      <div className="h-6 px-3 bg-[#F5F6F7] border-t border-[#E1E3E6] flex items-center justify-between text-[11px] text-slate-600">
        <span>
          {items.length} item{items.length === 1 ? '' : 's'}
          {selectedEntry ? `  |  1 item selected (${formatBytes(selectedEntry.sizeBytes)})` : ''}
        </span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setViewMode('details')}
            className={`px-1.5 rounded ${viewMode === 'details' ? 'bg-slate-200' : ''} cursor-pointer`}
          >
            ☷
          </button>
          <button
            type="button"
            onClick={() => setViewMode('large')}
            className={`px-1.5 rounded ${viewMode === 'large' ? 'bg-slate-200' : ''} cursor-pointer`}
          >
            ⊞
          </button>
        </div>
      </div>

      {/* Properties Modal */}
      {showProperties && (
        <div
          onClick={() => setShowProperties(false)}
          className="fixed inset-0 z-[1400] bg-black/40 flex items-center justify-center p-4"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-sm bg-white rounded-xl border border-slate-200 shadow-2xl p-4 flex flex-col gap-3 text-xs"
            dir="rtl"
          >
            <div className="font-bold text-sm text-[#122036]">
              {selectedEntry ? `خصائص: ${selectedEntry.displayName}` : 'خصائص سلة المحذوفات'}
            </div>
            {selectedEntry ? (
              <div className="space-y-1 text-slate-600">
                <div>الموقع الأصلي: {selectedEntry.originalLocation}</div>
                <div>الحجم: {formatBytes(selectedEntry.sizeBytes)}</div>
                <div>تاريخ الحذف: {new Date(selectedEntry.deletedAtMs).toLocaleString()}</div>
              </div>
            ) : (
              <div className="space-y-2 text-slate-600">
                <div>عدد العناصر: {items.length}</div>
                <label className="flex items-center gap-2 cursor-pointer pt-1">
                  <input
                    type="checkbox"
                    checked={confirmDelete}
                    onChange={(e) => {
                      setConfirmDelete(e.target.checked);
                      setConfirmDeleteEnabled(e.target.checked);
                    }}
                  />
                  <span>عرض مربع حوار تأكيد الحذف النهائي</span>
                </label>
              </div>
            )}
            <button
              type="button"
              onClick={() => setShowProperties(false)}
              className="self-end px-4 py-1.5 rounded bg-[#0067C0] text-white font-semibold cursor-pointer"
            >
              موافق
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
