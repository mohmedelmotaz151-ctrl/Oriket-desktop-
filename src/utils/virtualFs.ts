import { RecycledEntry, ShortcutDefinition, VirtualFileEntry } from '../types/desktop';
import { getIconForFileName, getIconPathForTitle } from './fluentIcons';

const KEY_FILES = 'orkeit_workspace_files_v1';
const KEY_RECYCLE_ITEMS = 'orkeit_recycle_bin_v1';
const KEY_HIDDEN_SHORTCUTS = 'orkeit_hidden_shortcuts';
const KEY_SHORTCUT_ORDER = 'orkeit_shortcut_order';
const KEY_CONFIRM_DELETE = 'orkeit_recycle_confirm_delete';

export const WORKSPACE_FOLDERS = [
  'Desktop',
  'Documents',
  'Downloads',
  'Music',
  'Pictures',
  'Videos',
  'Projects',
] as const;

export const DESKTOP_SHORTCUTS: ShortcutDefinition[] = [
  { key: 'متصفح الإنترنت', defaultLabel: 'متصفح الإنترنت', iconKey: 'متصفح الإنترنت', actionType: 'app', appId: 'browser' },
  {
    key: 'زيارات الدفاع المدني',
    defaultLabel: 'زيارات الدفاع المدني',
    iconKey: 'زيارات الدفاع المدني',
    actionType: 'url',
    appId: 'browser',
    url: 'https://orkeitstm.vercel.app',
    windowTitle: 'زيارات الدفاع المدني',
  },
  { key: 'هذا الكمبيوتر', defaultLabel: 'هذا الكمبيوتر', iconKey: 'هذا الكمبيوتر', actionType: 'app', appId: 'my_computer' },
  { key: 'مشغل PDF', defaultLabel: 'مشغل PDF', iconKey: 'مشغل PDF', actionType: 'pick_pdf', appId: 'file_viewer' },
  { key: 'مشغل الفيديو', defaultLabel: 'مشغل الفيديو', iconKey: 'مشغل الفيديو', actionType: 'app', appId: 'video_player' },
  { key: 'الرسام', defaultLabel: 'الرسام', iconKey: 'الرسام', actionType: 'app', appId: 'paint' },
  { key: 'الصور', defaultLabel: 'الصور', iconKey: 'الصور', actionType: 'app', appId: 'photos' },
  { key: 'مشغل الموسيقى', defaultLabel: 'مشغل الموسيقى', iconKey: 'مشغل الموسيقى', actionType: 'app', appId: 'music_player' },
  {
    key: 'YouTube',
    defaultLabel: 'YouTube',
    iconKey: 'YouTube',
    actionType: 'url',
    appId: 'browser',
    url: 'https://www.youtube.com',
    windowTitle: 'YouTube',
  },
  {
    key: 'YouTube Music',
    defaultLabel: 'YouTube Music',
    iconKey: 'YouTube Music',
    actionType: 'url',
    appId: 'browser',
    url: 'https://music.youtube.com',
    windowTitle: 'YouTube Music',
  },
  { key: 'Word / معالج النصوص', defaultLabel: 'Word / معالج النصوص', iconKey: 'Word / معالج النصوص', actionType: 'app', appId: 'word' },
  { key: 'Excel / الجداول', defaultLabel: 'Excel / الجداول', iconKey: 'Excel / الجداول', actionType: 'app', appId: 'excel' },
  { key: 'لوحة التحكم', defaultLabel: 'لوحة التحكم', iconKey: 'لوحة التحكم', actionType: 'app', appId: 'settings' },
  { key: 'إدارة المهام', defaultLabel: 'إدارة المهام', iconKey: 'إدارة المهام', actionType: 'app', appId: 'task_manager' },
  { key: 'Command', defaultLabel: 'Command', iconKey: 'Command', actionType: 'app', appId: 'command' },
  { key: 'ألعاب Windows', defaultLabel: 'ألعاب Windows', iconKey: 'ألعاب Windows', actionType: 'app', appId: 'games' },
  { key: 'الحاسبة', defaultLabel: 'الحاسبة', iconKey: 'الحاسبة', actionType: 'app', appId: 'calculator' },
  { key: 'المفكرة', defaultLabel: 'المفكرة', iconKey: 'المفكرة', actionType: 'app', appId: 'notepad' },
  { key: 'تحديث التطبيق', defaultLabel: 'تحديث التطبيق', iconKey: 'تحديث التطبيق', actionType: 'update' },
  { key: 'سلة المحذوفات', defaultLabel: 'سلة المحذوفات', iconKey: 'سلة المحذوفات', actionType: 'app', appId: 'recycle_bin' },
];

function seedInitialFiles(): VirtualFileEntry[] {
  const now = Date.now();
  const folderEntries: VirtualFileEntry[] = WORKSPACE_FOLDERS.map((folder, idx) => ({
    id: `dir_${folder}`,
    name: folder,
    parentPath: 'C:',
    isDirectory: true,
    sizeBytes: 0,
    mimeType: 'inode/directory',
    updatedAt: now - idx * 60000,
  }));

  const sampleFiles: VirtualFileEntry[] = [
    {
      id: 'file_welcome_txt',
      name: 'Welcome-Oriket.txt',
      parentPath: 'C:\\Documents',
      isDirectory: false,
      sizeBytes: 312,
      mimeType: 'text/plain',
      content: 'مرحبًا بك في Oriket Desktop!\n\nيمكنك إنشاء مجلدات وملفات نصية أو استيراد ملفات من جهازك وحفظها في القرص المحلي (C:).',
      updatedAt: now - 120000,
    },
    {
      id: 'file_sample_csv',
      name: 'Sales-Report.csv',
      parentPath: 'C:\\Documents',
      isDirectory: false,
      sizeBytes: 248,
      mimeType: 'text/csv',
      content: 'Item,Category,Quantity,UnitPrice,Total\nLaptop,Hardware,4,3200,=C2*D2\nMonitor,Display,7,950,=C3*D3\nKeyboard,Accessories,12,140,=C4*D4',
      updatedAt: now - 180000,
    },
    {
      id: 'file_sample_pic',
      name: 'Sample-Bloom.jpg',
      parentPath: 'C:\\Pictures',
      isDirectory: false,
      sizeBytes: 42800,
      mimeType: 'image/jpeg',
      content: '/assets/aistudio/Microsoft-Word-design_4fc2c2c7.jpg',
      updatedAt: now - 240000,
    },
  ];

  return [...folderEntries, ...sampleFiles];
}

export function getWorkspaceFiles(): VirtualFileEntry[] {
  try {
    const raw = localStorage.getItem(KEY_FILES);
    if (!raw) {
      const seeded = seedInitialFiles();
      localStorage.setItem(KEY_FILES, JSON.stringify(seeded));
      return seeded;
    }
    const parsed = JSON.parse(raw) as VirtualFileEntry[];
    return Array.isArray(parsed) ? parsed : seedInitialFiles();
  } catch {
    return seedInitialFiles();
  }
}

export function saveWorkspaceFiles(files: VirtualFileEntry[]): void {
  try {
    localStorage.setItem(KEY_FILES, JSON.stringify(files));
  } catch {
    // Ignore storage quota errors
  }
}

export function getHiddenShortcuts(): string[] {
  try {
    const raw = localStorage.getItem(KEY_HIDDEN_SHORTCUTS);
    return raw ? (JSON.parse(raw) as string[]) : [];
  } catch {
    return [];
  }
}

export function setHiddenShortcuts(keys: string[]): void {
  localStorage.setItem(KEY_HIDDEN_SHORTCUTS, JSON.stringify(keys));
}

export function getShortcutLabel(key: string): string {
  return localStorage.getItem(`orkeit_shortcut_label_${key}`) || key;
}

export function setShortcutLabel(key: string, label: string): void {
  localStorage.setItem(`orkeit_shortcut_label_${key}`, label);
}

export function getShortcutOrder(): string[] {
  try {
    const raw = localStorage.getItem(KEY_SHORTCUT_ORDER);
    return raw ? raw.split('|').filter(Boolean) : [];
  } catch {
    return [];
  }
}

export function setShortcutOrder(order: string[]): void {
  localStorage.setItem(KEY_SHORTCUT_ORDER, order.join('|'));
}

export function isConfirmDeleteEnabled(): boolean {
  const val = localStorage.getItem(KEY_CONFIRM_DELETE);
  return val === null ? true : val === 'true';
}

export function setConfirmDeleteEnabled(enabled: boolean): void {
  localStorage.setItem(KEY_CONFIRM_DELETE, String(enabled));
}

export function getRecycledItems(): RecycledEntry[] {
  try {
    const raw = localStorage.getItem(KEY_RECYCLE_ITEMS);
    const items: RecycledEntry[] = raw ? (JSON.parse(raw) as RecycledEntry[]) : [];
    return items.sort((a, b) => b.deletedAtMs - a.deletedAtMs);
  } catch {
    return [];
  }
}

function saveRecycledItems(items: RecycledEntry[]): void {
  localStorage.setItem(KEY_RECYCLE_ITEMS, JSON.stringify(items));
}

export function moveShortcutToRecycleBin(shortcutKey: string): void {
  const hidden = getHiddenShortcuts();
  if (!hidden.includes(shortcutKey)) {
    setHiddenShortcuts([...hidden, shortcutKey]);
  }
  const items = getRecycledItems().filter((i) => i.id !== `shortcut:${shortcutKey}`);
  const displayName = getShortcutLabel(shortcutKey);
  items.unshift({
    id: `shortcut:${shortcutKey}`,
    isShortcut: true,
    shortcutKey,
    displayName,
    originalLocation: 'Desktop',
    deletedAtMs: Date.now(),
    sizeBytes: 1024,
    iconTitle: shortcutKey,
  });
  saveRecycledItems(items);
}

export function moveFileToRecycleBin(fileId: string): boolean {
  const files = getWorkspaceFiles();
  const target = files.find((f) => f.id === fileId);
  if (!target) return false;

  const toRemoveIds = new Set<string>([target.id]);
  if (target.isDirectory) {
    const prefix = `${target.parentPath}\\${target.name}`;
    for (const f of files) {
      if (f.parentPath === prefix || f.parentPath.startsWith(`${prefix}\\`)) {
        toRemoveIds.add(f.id);
      }
    }
  }
  saveWorkspaceFiles(files.filter((f) => !toRemoveIds.has(f.id)));

  const items = getRecycledItems();
  const relLoc = target.parentPath === 'C:' ? 'Local Disk (C:)' : target.parentPath;
  items.unshift({
    id: `file:${target.id}_${Date.now()}`,
    isShortcut: false,
    displayName: target.name,
    originalLocation: relLoc,
    deletedAtMs: Date.now(),
    sizeBytes: target.sizeBytes || (target.isDirectory ? 4096 : (target.content?.length || 512)),
    iconTitle: target.name,
    fileEntry: target,
    previewUrl:
      !target.isDirectory && target.mimeType.startsWith('image/') && target.content
        ? target.content
        : undefined,
  });
  saveRecycledItems(items);
  return true;
}

export function restoreRecycledEntry(entryId: string): boolean {
  const items = getRecycledItems();
  const target = items.find((i) => i.id === entryId);
  if (!target) return false;

  if (target.isShortcut && target.shortcutKey) {
    const hidden = getHiddenShortcuts().filter((k) => k !== target.shortcutKey);
    setHiddenShortcuts(hidden);
  } else if (target.fileEntry) {
    const files = getWorkspaceFiles();
    let restored = { ...target.fileEntry };
    const exists = files.some(
      (f) => f.parentPath === restored.parentPath && f.name === restored.name
    );
    if (exists) {
      const dot = restored.name.lastIndexOf('.');
      const suffix = `_restored_${Date.now() % 10000}`;
      restored = {
        ...restored,
        id: `file_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        name: dot > 0 ? `${restored.name.slice(0, dot)}${suffix}${restored.name.slice(dot)}` : `${restored.name}${suffix}`,
      };
    }
    saveWorkspaceFiles([...files, restored]);
  }

  saveRecycledItems(items.filter((i) => i.id !== entryId));
  return true;
}

export function deleteRecycledEntryPermanently(entryId: string): boolean {
  const items = getRecycledItems();
  const target = items.find((i) => i.id === entryId);
  if (!target) return false;
  if (target.isShortcut && target.shortcutKey) {
    const hidden = getHiddenShortcuts().filter((k) => k !== target.shortcutKey);
    setHiddenShortcuts(hidden);
  }
  saveRecycledItems(items.filter((i) => i.id !== entryId));
  return true;
}

export function emptyRecycleBin(): void {
  setHiddenShortcuts([]);
  saveRecycledItems([]);
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}

export function getRecycledIcon(entry: RecycledEntry): string {
  if (entry.isShortcut) return getIconPathForTitle(entry.iconTitle);
  return getIconForFileName(entry.displayName, entry.fileEntry?.isDirectory);
}
