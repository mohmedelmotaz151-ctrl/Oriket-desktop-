# Oriket Desktop (Web Edition)

Windows 11-inspired desktop environment rewritten as a **React + TypeScript + Vite + Tailwind CSS** web application.

## Features
- **Boot & Salted SHA-256 Lock Screen**: Animated Windows-inspired boot sequence and local password protection.
- **Multi-Window Desktop Shell**: Draggable, resizable, snappable, minimizable, and maximizable windows with frosted-glass Taskbar, Start Menu, Quick Settings (with desktop display scaling & fullscreen toggle), Notification Center, Sleep, Restart, and Shutdown.
- **Built-in Browser (`متصفح الإنترنت`)**: Address bar, history navigation, bookmarks, fullscreen/maximize window support, and **viewport zoom controls (50%–150% + Fit)** so full pages display cleanly inside the browser window.
- **Productivity Suite**:
  - **Word (`Word / معالج النصوص`)**: Floating Word 365 ribbon, rich text formatting, Find & Replace, Read Mode, paper themes, TXT import/export.
  - **Excel (`Excel / الجداول`)**: Forest Green UI, formula engine (`=SUM`, `=AVERAGE`, `=MIN`, `=MAX`, `=COUNT`, arithmetic & cell refs), multi-sheet tabs, sorting/filtering, CSV import/export.
  - **Paint (`الرسام`)**: Pencil, Brush, Flood Fill, Eraser, Eyedropper, Text, Shapes, Selection/Crop, Rotate 90°, Flip Horizontal, Gridlines, PNG export.
  - **Notepad & Calculator & Command Shell (`المفكرة`, `الحاسبة`, `Command`)**.
- **Media & Files**:
  - **Windows Media Player 11 (`مشغل الموسيقى`)**: Dark Aero interface, animated canvas wave & spectrum bars, 3D Play orb, Library/Queue/Effects, built-in synthesized demo tracks, and local audio import.
  - **Photos (`الصور`) & Video Player (`مشغل الفيديو`) & File Viewer (`مشغل PDF`)**.
  - **This PC (`هذا الكمبيوتر`) & Recycle Bin (`سلة المحذوفات`)**: Persistent virtual filesystem (`C:`), file import/creation, contextual Recycle Bin ribbon, restore, and permanent delete.
- **Games & System**: Snake (14×14), Number Challenge (1–20), Task Manager, Control Panel / Settings (wallpapers, display scale, sound toggle).

## Development

```bash
npm install
npm run dev
```

Build for production:

```bash
npm run build
```
