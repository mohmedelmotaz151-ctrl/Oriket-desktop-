import React, { useMemo, useRef, useState } from 'react';
import { getWorkspaceFiles, saveWorkspaceFiles } from '../utils/virtualFs';

interface PhotosAppProps {
  onNotify: (title: string, msg: string) => void;
}

interface PhotoItem {
  id: string;
  name: string;
  url: string;
  source: 'preset' | 'workspace' | 'upload';
}

const PRESET_PHOTOS: PhotoItem[] = [
  { id: 'preset_1', name: 'Word-Design.jpg', url: '/assets/aistudio/Microsoft-Word-design_4fc2c2c7.jpg', source: 'preset' },
  { id: 'preset_2', name: 'Paint-Flower.webp', url: '/assets/aistudio/MSFT-Colorful-flower-in-the-Paint-app_VP1-539x349_edce3aad.webp', source: 'preset' },
  { id: 'preset_3', name: 'CSV-Windows11.png', url: '/assets/aistudio/How-to-Open-CSV-File-in-Windows-11_5ab2f4c0.png', source: 'preset' },
  { id: 'preset_4', name: 'Recycle-Bin-Guide.png', url: '/assets/aistudio/recover-deleted-files-from-recycle-bin_2ec0e8ad.png', source: 'preset' },
  { id: 'preset_5', name: 'Desktop-Wallpaper-A.jpg', url: '/assets/aistudio/IMG_d0eac33a-d218-4b7e-b38c-44005f4eb5a2_8a20cd45.jpg', source: 'preset' },
  { id: 'preset_6', name: 'Desktop-Wallpaper-B.jpg', url: '/assets/aistudio/IMG_f6ad7ea7-d810-4ef5-bb2f-6e17bf2a3273_3f4ecb2f.jpg', source: 'preset' },
];

export const PhotosApp: React.FC<PhotosAppProps> = ({ onNotify }) => {
  const [refreshTick, setRefreshTick] = useState(0);
  const [uploaded, setUploaded] = useState<PhotoItem[]>([]);
  const [selected, setSelected] = useState<PhotoItem | null>(null);
  const [zoom, setZoom] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [squareCrop, setSquareCrop] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const photos = useMemo(() => {
    const ws = getWorkspaceFiles()
      .filter((f) => !f.isDirectory && f.mimeType.startsWith('image/') && f.content)
      .map<PhotoItem>((f) => ({
        id: f.id,
        name: f.name,
        url: f.content!,
        source: 'workspace',
      }));
    return [...uploaded, ...ws, ...PRESET_PHOTOS];
  }, [uploaded, refreshTick]);

  const handleUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    files.forEach((file) => {
      const reader = new FileReader();
      reader.onload = () => {
        const dataUrl = String(reader.result || '');
        setUploaded((prev) => [
          {
            id: `up_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
            name: file.name,
            url: dataUrl,
            source: 'upload',
          },
          ...prev,
        ]);
      };
      reader.readAsDataURL(file);
    });
    if (files.length > 0) {
      onNotify('الصور', `تمت إضافة ${files.length} صورة للمعرض`);
    }
    e.target.value = '';
  };

  const saveEditedCopy = () => {
    if (!selected) return;
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => {
      const rad = (rotation * Math.PI) / 180;
      const swap = rotation % 180 !== 0;
      let w = swap ? img.height : img.width;
      let h = swap ? img.width : img.height;
      if (squareCrop) {
        const side = Math.min(w, h);
        w = side;
        h = side;
      }
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.min(1200, w));
      canvas.height = Math.max(1, Math.min(1200, h));
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      ctx.translate(canvas.width / 2, canvas.height / 2);
      ctx.rotate(rad);
      const scale = Math.max(canvas.width / (swap ? img.height : img.width), canvas.height / (swap ? img.width : img.height));
      const dw = img.width * (squareCrop ? scale : Math.min(canvas.width / (swap ? img.height : img.width), canvas.height / (swap ? img.width : img.height)));
      const dh = img.height * (squareCrop ? scale : Math.min(canvas.width / (swap ? img.height : img.width), canvas.height / (swap ? img.width : img.height)));
      ctx.drawImage(img, -dw / 2, -dh / 2, dw, dh);
      const outUrl = canvas.toDataURL('image/png');
      const files = getWorkspaceFiles();
      const newName = `Edited_${selected.name.replace(/\.[^.]+$/, '')}_${Date.now() % 10000}.png`;
      saveWorkspaceFiles([
        ...files,
        {
          id: `pic_${Date.now()}`,
          name: newName,
          parentPath: 'C:\\Pictures',
          isDirectory: false,
          sizeBytes: outUrl.length,
          mimeType: 'image/png',
          content: outUrl,
          updatedAt: Date.now(),
        },
      ]);
      setRefreshTick((t) => t + 1);
      onNotify('الصور', `تم حفظ نسخة معدلة: ${newName}`);
    };
    img.src = selected.url;
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-2 bg-white rounded-lg p-2.5" dir="rtl">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-2">
        <div className="flex items-center gap-2">
          <img src="/icons/win11_photos.png" alt="" className="w-5 h-5" />
          <span className="text-xs font-bold text-[#122036]">
            {selected ? selected.name : `معرض الصور (${photos.length})`}
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          {selected && (
            <>
              <button
                type="button"
                onClick={() => setSelected(null)}
                className="px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-semibold cursor-pointer"
              >
                ← المعرض
              </button>
              <button
                type="button"
                onClick={() => setZoom((z) => Math.max(0.5, +(z - 0.2).toFixed(2)))}
                className="w-7 h-7 rounded bg-slate-100 hover:bg-slate-200 text-xs font-bold cursor-pointer"
              >
                −
              </button>
              <button
                type="button"
                onClick={() => setZoom((z) => Math.min(2.5, +(z + 0.2).toFixed(2)))}
                className="w-7 h-7 rounded bg-slate-100 hover:bg-slate-200 text-xs font-bold cursor-pointer"
              >
                +
              </button>
              <button
                type="button"
                onClick={() => setRotation((r) => (r + 90) % 360)}
                className="px-2.5 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-semibold cursor-pointer"
              >
                تدوير 90°
              </button>
              <button
                type="button"
                onClick={() => setSquareCrop((c) => !c)}
                className={`px-2.5 py-1 rounded-md text-xs font-semibold cursor-pointer ${
                  squareCrop ? 'bg-[#0067C0] text-white' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
                }`}
              >
                قص مربع 1:1
              </button>
              <button
                type="button"
                onClick={saveEditedCopy}
                className="px-2.5 py-1 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold cursor-pointer"
              >
                حفظ نسخة
              </button>
            </>
          )}
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="px-3 py-1 rounded-md bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-semibold cursor-pointer"
          >
            استيراد صور
          </button>
        </div>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        multiple
        className="hidden"
        onChange={handleUpload}
      />

      {selected ? (
        <div className="flex-1 min-h-0 bg-[#0F172A] rounded-xl flex items-center justify-center overflow-hidden p-4">
          <div
            className={`overflow-hidden flex items-center justify-center transition-all ${
              squareCrop ? 'w-64 h-64 rounded-xl border-2 border-cyan-400' : 'max-w-full max-h-full'
            }`}
          >
            <img
              src={selected.url}
              alt={selected.name}
              style={{
                transform: `scale(${zoom}) rotate(${rotation}deg)`,
              }}
              className="max-w-full max-h-[340px] object-contain transition-transform"
            />
          </div>
        </div>
      ) : (
        <div className="flex-1 min-h-0 overflow-y-auto grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5 p-1">
          {photos.map((photo) => (
            <button
              key={photo.id}
              type="button"
              onClick={() => {
                setSelected(photo);
                setZoom(1);
                setRotation(0);
                setSquareCrop(false);
              }}
              className="group relative aspect-[4/3] rounded-xl overflow-hidden border border-slate-200 bg-slate-100 hover:border-[#0067C0] transition cursor-pointer"
            >
              <img
                src={photo.url}
                alt={photo.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
              />
              <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/75 to-transparent px-2 py-1.5 text-[10px] text-white truncate text-left" dir="ltr">
                {photo.name}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};
