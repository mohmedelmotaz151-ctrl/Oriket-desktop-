import React, { useEffect, useRef, useState } from 'react';
import { WindowEntry } from '../types/desktop';
import { getIconPathForTitle } from '../utils/fluentIcons';

interface WindowShellProps {
  win: WindowEntry;
  isActive: boolean;
  desktopBounds: { width: number; height: number };
  onFocus: () => void;
  onMinimize: () => void;
  onToggleMaximize: () => void;
  onClose: () => void;
  onMoveResize: (patch: Partial<Pick<WindowEntry, 'x' | 'y' | 'width' | 'height'>>) => void;
  children: React.ReactNode;
}

export const WindowShell: React.FC<WindowShellProps> = ({
  win,
  isActive,
  desktopBounds,
  onFocus,
  onMinimize,
  onToggleMaximize,
  onClose,
  onMoveResize,
  children,
}) => {
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number } | null>(null);
  const dragRef = useRef<{
    startX: number;
    startY: number;
    origX: number;
    origY: number;
  } | null>(null);
  const resizeRef = useRef<{
    startX: number;
    startY: number;
    origW: number;
    origH: number;
  } | null>(null);

  useEffect(() => {
    const closeMenu = () => setContextMenu(null);
    if (contextMenu) {
      window.addEventListener('click', closeMenu);
      return () => window.removeEventListener('click', closeMenu);
    }
  }, [contextMenu]);

  const isWmp = win.appId === 'music_player';
  const isExcel = win.appId === 'excel';
  const isWord = win.appId === 'word';
  const isPaint = win.appId === 'paint';
  const isRecycle = win.appId === 'recycle_bin';
  const isGames = win.appId === 'games';

  const outerStyle: React.CSSProperties = win.maximized
    ? {
        left: 0,
        top: 0,
        width: '100%',
        height: '100%',
        zIndex: win.zIndex,
        display: win.minimized ? 'none' : 'flex',
      }
    : {
        left: win.x,
        top: win.y,
        width: win.width,
        height: win.height,
        zIndex: win.zIndex,
        display: win.minimized ? 'none' : 'flex',
      };

  const startDrag = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('button')) return;
    onFocus();
    if (win.maximized) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      origX: win.x,
      origY: win.y,
    };
  };

  const onDragMove = (e: React.PointerEvent) => {
    if (!dragRef.current || win.maximized) return;
    const dx = e.clientX - dragRef.current.startX;
    const dy = e.clientY - dragRef.current.startY;
    const nextX = Math.max(-win.width + 120, Math.min(desktopBounds.width - 120, dragRef.current.origX + dx));
    const nextY = Math.max(0, Math.min(desktopBounds.height - 44, dragRef.current.origY + dy));
    onMoveResize({ x: nextX, y: nextY });
  };

  const endDrag = (e: React.PointerEvent) => {
    if (!dragRef.current) return;
    dragRef.current = null;
    if (!win.maximized && desktopBounds.width > 0) {
      if (e.clientX < 18) {
        onMoveResize({ x: 0, y: 0, width: Math.floor(desktopBounds.width / 2), height: desktopBounds.height });
      } else if (e.clientX > desktopBounds.width - 18) {
        onMoveResize({
          x: Math.floor(desktopBounds.width / 2),
          y: 0,
          width: Math.floor(desktopBounds.width / 2),
          height: desktopBounds.height,
        });
      } else if (e.clientY < 14) {
        onToggleMaximize();
      }
    }
  };

  const startResize = (e: React.PointerEvent) => {
    e.stopPropagation();
    onFocus();
    if (win.maximized) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    resizeRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      origW: win.width,
      origH: win.height,
    };
  };

  const onResizeMove = (e: React.PointerEvent) => {
    if (!resizeRef.current || win.maximized) return;
    const dw = e.clientX - resizeRef.current.startX;
    const dh = e.clientY - resizeRef.current.startY;
    const maxW = Math.max(300, desktopBounds.width - win.x);
    const maxH = Math.max(240, desktopBounds.height - win.y);
    onMoveResize({
      width: Math.max(300, Math.min(maxW, resizeRef.current.origW + dw)),
      height: Math.max(240, Math.min(maxH, resizeRef.current.origH + dh)),
    });
  };

  const endResize = () => {
    resizeRef.current = null;
  };

  const outerBorderClass = isWmp
    ? 'bg-[#070C16] border-[#3A4E6C]'
    : isExcel
    ? 'bg-white border-[#185A36]'
    : 'bg-white border-[#C6D2E2]';

  const titlebarClass = isWmp
    ? 'bg-gradient-to-b from-[#222E44] via-[#101826] to-[#141E30] text-[#EBF4FF]'
    : isExcel
    ? 'bg-[#217346] text-white'
    : isWord
    ? 'bg-[#F3F5F8] text-[#122036]'
    : isPaint
    ? 'bg-[#F3F5F9] text-[#122036]'
    : isRecycle || isGames
    ? 'bg-white text-[#122036]'
    : 'bg-[#F5F8FC] text-[#122036]';

  const bodyBgClass = isWmp
    ? 'bg-[#060C18]'
    : isGames
    ? 'bg-[#080A0F]'
    : isWord
    ? 'bg-[#ECF0F6]'
    : isPaint
    ? 'bg-[#E5EAF1]'
    : isExcel || isRecycle
    ? 'bg-white'
    : 'bg-[#FAFBFE] p-2';

  return (
    <div
      style={outerStyle}
      onPointerDown={onFocus}
      className={`absolute flex flex-col overflow-hidden border transition-shadow duration-150 ${
        win.maximized ? 'rounded-none' : 'rounded-[10px]'
      } ${outerBorderClass} ${isActive ? 'shadow-2xl' : 'shadow-lg opacity-[0.98]'}`}
    >
      {/* Titlebar */}
      <div
        onPointerDown={startDrag}
        onPointerMove={onDragMove}
        onPointerUp={endDrag}
        onDoubleClick={onToggleMaximize}
        onContextMenu={(e) => {
          e.preventDefault();
          setContextMenu({ x: e.clientX, y: e.clientY });
        }}
        className={`h-8 shrink-0 flex items-center px-2 select-none cursor-default ${titlebarClass}`}
      >
        <img
          src={getIconPathForTitle(win.title)}
          alt=""
          className="w-4 h-4 mr-2 shrink-0 object-contain"
          draggable={false}
        />

        {isExcel && win.customHeaderAction && (
          <div className="flex items-center gap-1 mr-2">
            <button
              type="button"
              onClick={win.customHeaderAction.onSave}
              title="حفظ وتصدير"
              className="w-6 h-6 rounded flex items-center justify-center bg-white/15 hover:bg-white/25 text-white text-xs cursor-pointer"
            >
              💾
            </button>
            <button
              type="button"
              onClick={win.customHeaderAction.onUndo}
              title="تراجع"
              className="w-6 h-6 rounded flex items-center justify-center bg-white/15 hover:bg-white/25 text-white text-xs cursor-pointer"
            >
              ↶
            </button>
            <button
              type="button"
              onClick={win.customHeaderAction.onRedo}
              title="إعادة"
              className="w-6 h-6 rounded flex items-center justify-center bg-white/15 hover:bg-white/25 text-white text-xs cursor-pointer"
            >
              ↷
            </button>
          </div>
        )}

        <span
          className={`flex-1 text-xs font-bold truncate pr-2 ${
            isExcel ? 'text-center' : ''
          }`}
        >
          {win.title}
        </span>

        <div className="flex items-center gap-0.5 shrink-0">
          <button
            type="button"
            onClick={onMinimize}
            title="تصغير"
            className={`w-8 h-7 rounded-md flex items-center justify-center text-xs transition cursor-pointer ${
              isWmp
                ? 'bg-white/10 hover:bg-white/20 text-[#DCE8F8]'
                : isExcel
                ? 'hover:bg-white/20 text-white'
                : 'hover:bg-slate-200/80 text-[#122036]'
            }`}
          >
            —
          </button>
          <button
            type="button"
            onClick={onToggleMaximize}
            title={win.maximized ? 'استعادة' : 'تكبير'}
            className={`w-9 h-8 rounded-md flex items-center justify-center text-xs transition cursor-pointer ${
              isWmp
                ? 'bg-white/10 hover:bg-white/20 text-[#DCE8F8]'
                : isExcel
                ? 'hover:bg-white/20 text-white'
                : 'hover:bg-slate-200/80 text-[#122036]'
            }`}
          >
            {win.maximized ? '❐' : '▢'}
          </button>
          <button
            type="button"
            onClick={onClose}
            title="إغلاق"
            className={`w-9 h-8 rounded-md flex items-center justify-center text-xs transition cursor-pointer ${
              isWmp
                ? 'bg-gradient-to-b from-[#DA5650] to-[#A22826] text-white hover:brightness-110'
                : isExcel
                ? 'hover:bg-[#E81123] text-white'
                : 'hover:bg-[#E81123] hover:text-white text-[#122036]'
            }`}
          >
            ✕
          </button>
        </div>
      </div>

      {/* Body */}
      <div className={`relative flex-1 min-h-0 flex flex-col overflow-hidden ${bodyBgClass}`}>
        {children}

        {/* Bottom-right resize grip */}
        {!win.maximized && (
          <div
            onPointerDown={startResize}
            onPointerMove={onResizeMove}
            onPointerUp={endResize}
            title="تغيير حجم النافذة"
            className="absolute bottom-0.5 right-0.5 z-30 w-5 h-5 rounded flex items-center justify-center text-[11px] text-[#697D9B] bg-[#EEF3FA]/85 hover:bg-[#DCE6F5] cursor-nwse-resize select-none"
          >
            ◢
          </div>
        )}
      </div>

      {contextMenu && (
        <div
          style={{ left: contextMenu.x, top: contextMenu.y }}
          className="fixed z-[9999] min-w-[140px] bg-white border border-slate-200 rounded-lg shadow-xl py-1 text-xs text-slate-800"
        >
          <button
            type="button"
            onClick={() => {
              onMinimize();
              setContextMenu(null);
            }}
            className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
          >
            تصغير
          </button>
          <button
            type="button"
            onClick={() => {
              onToggleMaximize();
              setContextMenu(null);
            }}
            className="w-full text-right px-3 py-1.5 hover:bg-slate-100 cursor-pointer"
          >
            {win.maximized ? 'استعادة' : 'تكبير'}
          </button>
          <button
            type="button"
            onClick={() => {
              onClose();
              setContextMenu(null);
            }}
            className="w-full text-right px-3 py-1.5 hover:bg-red-50 text-red-600 cursor-pointer"
          >
            إغلاق
          </button>
        </div>
      )}
    </div>
  );
};
