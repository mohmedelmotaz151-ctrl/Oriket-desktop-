export type AppId =
  | 'browser'
  | 'my_computer'
  | 'pdf_viewer'
  | 'video_player'
  | 'paint'
  | 'photos'
  | 'music_player'
  | 'word'
  | 'excel'
  | 'settings'
  | 'task_manager'
  | 'command'
  | 'games'
  | 'snake'
  | 'calculator'
  | 'notepad'
  | 'recycle_bin'
  | 'about'
  | 'file_viewer';

export type WallpaperStyle = 0 | 1 | 2; // 0: Windows Blue, 1: Dark, 2: Light

export interface WindowPayload {
  url?: string;
  initialFolder?: string | null;
  fileId?: string;
  externalFile?: {
    name: string;
    mime: string;
    dataUrl?: string;
    textContent?: string;
  };
}

export interface WindowEntry {
  id: number;
  appId: AppId;
  title: string;
  x: number;
  y: number;
  width: number;
  height: number;
  minimized: boolean;
  maximized: boolean;
  zIndex: number;
  payload?: WindowPayload;
  customHeaderAction?: {
    onSave?: () => void;
    onUndo?: () => void;
    onRedo?: () => void;
  };
}

export interface DesktopNotice {
  id: string;
  title: string;
  message: string;
  time: number;
}

export interface ShortcutDefinition {
  key: string;
  defaultLabel: string;
  iconKey: string;
  actionType: 'app' | 'url' | 'update' | 'pick_pdf';
  appId?: AppId;
  url?: string;
  windowTitle?: string;
}

export interface VirtualFileEntry {
  id: string;
  name: string;
  parentPath: string; // e.g., "C:\\Documents" or "C:"
  isDirectory: boolean;
  sizeBytes: number;
  mimeType: string;
  content?: string; // text content or dataURL
  updatedAt: number;
}

export interface RecycledEntry {
  id: string;
  isShortcut: boolean;
  shortcutKey?: string;
  displayName: string;
  originalLocation: string;
  deletedAtMs: number;
  sizeBytes: number;
  iconTitle: string;
  fileEntry?: VirtualFileEntry;
  previewUrl?: string;
}
