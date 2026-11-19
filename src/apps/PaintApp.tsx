import React, { useEffect, useRef, useState } from 'react';

interface PaintAppProps {
  onNotify: (title: string, msg: string) => void;
}

type ToolType = 'pencil' | 'brush' | 'fill' | 'eraser' | 'eyedropper' | 'text' | 'shape' | 'select';
type ShapeType = 'line' | 'rect' | 'round_rect' | 'circle' | 'triangle' | 'arrow';

const PALETTE_ROW_1 = [
  '#000000', '#7F7F7F', '#880015', '#ED1C24', '#FF7F27',
  '#FFF200', '#22B14C', '#00A2E8', '#3F48CC', '#A349A4',
];
const PALETTE_ROW_2 = [
  '#FFFFFF', '#C3C3C3', '#B97A57', '#FFAEC9', '#FFC90E',
  '#EFE4B0', '#B5E61D', '#99D9EA', '#7092BE', '#C8BFE7',
];

function hexToRgba(hex: string): [number, number, number, number] {
  const clean = hex.replace('#', '');
  const n = parseInt(clean.length === 3 ? clean.replace(/(.)/g, '$1$1') : clean, 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255];
}

function floodFillCanvas(ctx: CanvasRenderingContext2D, startX: number, startY: number, fillHex: string) {
  const w = ctx.canvas.width;
  const h = ctx.canvas.height;
  const img = ctx.getImageData(0, 0, w, h);
  const data = img.data;
  const sx = Math.floor(startX);
  const sy = Math.floor(startY);
  if (sx < 0 || sy < 0 || sx >= w || sy >= h) return;

  const startIdx = (sy * w + sx) * 4;
  const sr = data[startIdx];
  const sg = data[startIdx + 1];
  const sb = data[startIdx + 2];
  const [fr, fg, fb, fa] = hexToRgba(fillHex);
  if (Math.abs(sr - fr) < 4 && Math.abs(sg - fg) < 4 && Math.abs(sb - fb) < 4) return;

  const tol = 18;
  const match = (idx: number) =>
    Math.abs(data[idx] - sr) <= tol &&
    Math.abs(data[idx + 1] - sg) <= tol &&
    Math.abs(data[idx + 2] - sb) <= tol;

  const stack = new Int32Array(w * h);
  const visited = new Uint8Array(w * h);
  let sp = 0;
  stack[sp++] = sy * w + sx;
  visited[sy * w + sx] = 1;

  while (sp > 0) {
    const p = stack[--sp];
    const px = p % w;
    const py = (p - px) / w;
    const idx = p * 4;
    data[idx] = fr;
    data[idx + 1] = fg;
    data[idx + 2] = fb;
    data[idx + 3] = fa;

    const neighbors = [
      px > 0 ? p - 1 : -1,
      px < w - 1 ? p + 1 : -1,
      py > 0 ? p - w : -1,
      py < h - 1 ? p + w : -1,
    ];
    for (const n of neighbors) {
      if (n >= 0 && !visited[n] && match(n * 4)) {
        visited[n] = 1;
        stack[sp++] = n;
      }
    }
  }
  ctx.putImageData(img, 0, 0);
}

export const PaintApp: React.FC<PaintAppProps> = ({ onNotify }) => {
  const [tool, setTool] = useState<ToolType>('brush');
  const [shapeType, setShapeType] = useState<ShapeType>('rect');
  const [fillShapes, setFillShapes] = useState(false);
  const [brushSize, setBrushSize] = useState(6);
  const [primaryColor, setPrimaryColor] = useState('#000000');
  const [secondaryColor, setSecondaryColor] = useState('#FFFFFF');
  const [activeSlot, setActiveSlot] = useState<1 | 2>(1);
  const [showGrid, setShowGrid] = useState(false);
  const [zoom, setZoom] = useState(1);
  const [cursorPos, setCursorPos] = useState({ x: 0, y: 0 });
  const [canvasSize, setCanvasSize] = useState({ w: 720, h: 440 });
  const [selectionRect, setSelectionRect] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const [textModal, setTextModal] = useState<{ x: number; y: number } | null>(null);
  const [textInput, setTextInput] = useState('');

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const undoRef = useRef<ImageData[]>([]);
  const redoRef = useRef<ImageData[]>([]);
  const drawingRef = useRef<{
    active: boolean;
    startX: number;
    startY: number;
    snapshot?: ImageData;
  }>({ active: false, startX: 0, startY: 0 });

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }, []);

  const pushUndo = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    undoRef.current = [...undoRef.current.slice(-14), ctx.getImageData(0, 0, canvas.width, canvas.height)];
    redoRef.current = [];
  };

  const handleUndo = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || undoRef.current.length === 0) return;
    redoRef.current.push(ctx.getImageData(0, 0, canvas.width, canvas.height));
    const prev = undoRef.current.pop()!;
    ctx.putImageData(prev, 0, 0);
  };

  const handleRedo = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || redoRef.current.length === 0) return;
    undoRef.current.push(ctx.getImageData(0, 0, canvas.width, canvas.height));
    const next = redoRef.current.pop()!;
    ctx.putImageData(next, 0, 0);
  };

  const getCoords = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    return {
      x: Math.max(0, Math.min(canvas.width, ((e.clientX - rect.left) / rect.width) * canvas.width)),
      y: Math.max(0, Math.min(canvas.height, ((e.clientY - rect.top) / rect.height) * canvas.height)),
    };
  };

  const drawShape = (
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number
  ) => {
    ctx.strokeStyle = primaryColor;
    ctx.fillStyle = secondaryColor;
    ctx.lineWidth = brushSize;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    const w = x2 - x1;
    const h = y2 - y1;
    ctx.beginPath();
    if (shapeType === 'line') {
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    } else if (shapeType === 'rect') {
      if (fillShapes) ctx.fillRect(x1, y1, w, h);
      ctx.strokeRect(x1, y1, w, h);
    } else if (shapeType === 'round_rect') {
      ctx.roundRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(w), Math.abs(h), 14);
      if (fillShapes) ctx.fill();
      ctx.stroke();
    } else if (shapeType === 'circle') {
      ctx.ellipse(x1 + w / 2, y1 + h / 2, Math.abs(w / 2), Math.abs(h / 2), 0, 0, Math.PI * 2);
      if (fillShapes) ctx.fill();
      ctx.stroke();
    } else if (shapeType === 'triangle') {
      ctx.moveTo(x1 + w / 2, y1);
      ctx.lineTo(x2, y2);
      ctx.lineTo(x1, y2);
      ctx.closePath();
      if (fillShapes) ctx.fill();
      ctx.stroke();
    } else if (shapeType === 'arrow') {
      const angle = Math.atan2(y2 - y1, x2 - x1);
      const headLen = 14 + brushSize;
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.lineTo(x2 - headLen * Math.cos(angle - Math.PI / 6), y2 - headLen * Math.sin(angle - Math.PI / 6));
      ctx.moveTo(x2, y2);
      ctx.lineTo(x2 - headLen * Math.cos(angle + Math.PI / 6), y2 - headLen * Math.sin(angle + Math.PI / 6));
      ctx.stroke();
    }
  };

  const onPointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    const { x, y } = getCoords(e);

    if (tool === 'eyedropper') {
      const px = ctx.getImageData(Math.floor(x), Math.floor(y), 1, 1).data;
      const hex = `#${[px[0], px[1], px[2]].map((v) => v.toString(16).padStart(2, '0')).join('')}`;
      if (activeSlot === 1) setPrimaryColor(hex);
      else setSecondaryColor(hex);
      return;
    }

    if (tool === 'fill') {
      pushUndo();
      floodFillCanvas(ctx, x, y, activeSlot === 1 ? primaryColor : secondaryColor);
      return;
    }

    if (tool === 'text') {
      setTextModal({ x, y });
      setTextInput('');
      return;
    }

    pushUndo();
    e.currentTarget.setPointerCapture(e.pointerId);
    drawingRef.current = {
      active: true,
      startX: x,
      startY: y,
      snapshot: ctx.getImageData(0, 0, canvas.width, canvas.height),
    };

    if (tool === 'pencil' || tool === 'brush' || tool === 'eraser') {
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.strokeStyle = tool === 'eraser' ? '#FFFFFF' : primaryColor;
      ctx.lineWidth = tool === 'pencil' ? Math.max(1, Math.min(4, brushSize)) : brushSize;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
    }
  };

  const onPointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    const { x, y } = getCoords(e);
    setCursorPos({ x: Math.round(x), y: Math.round(y) });
    if (!drawingRef.current.active) return;

    if (tool === 'pencil' || tool === 'brush' || tool === 'eraser') {
      ctx.lineTo(x, y);
      ctx.stroke();
    } else if (tool === 'shape' && drawingRef.current.snapshot) {
      ctx.putImageData(drawingRef.current.snapshot, 0, 0);
      drawShape(ctx, drawingRef.current.startX, drawingRef.current.startY, x, y);
    } else if (tool === 'select') {
      const sx = Math.min(drawingRef.current.startX, x);
      const sy = Math.min(drawingRef.current.startY, y);
      const sw = Math.abs(x - drawingRef.current.startX);
      const sh = Math.abs(y - drawingRef.current.startY);
      setSelectionRect({ x: sx, y: sy, w: sw, h: sh });
    }
  };

  const onPointerUp = () => {
    drawingRef.current.active = false;
  };

  const rotate90 = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    pushUndo();
    const tmp = document.createElement('canvas');
    tmp.width = canvas.height;
    tmp.height = canvas.width;
    const tctx = tmp.getContext('2d')!;
    tctx.translate(tmp.width / 2, tmp.height / 2);
    tctx.rotate(Math.PI / 2);
    tctx.drawImage(canvas, -canvas.width / 2, -canvas.height / 2);
    canvas.width = tmp.width;
    canvas.height = tmp.height;
    setCanvasSize({ w: tmp.width, h: tmp.height });
    ctx.drawImage(tmp, 0, 0);
  };

  const flipHorizontal = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    pushUndo();
    const tmp = document.createElement('canvas');
    tmp.width = canvas.width;
    tmp.height = canvas.height;
    tmp.getContext('2d')!.drawImage(canvas, 0, 0);
    ctx.save();
    ctx.translate(canvas.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(tmp, 0, 0);
    ctx.restore();
  };

  const cropToSelection = () => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx || !selectionRect || selectionRect.w < 4 || selectionRect.h < 4) return;
    pushUndo();
    const data = ctx.getImageData(selectionRect.x, selectionRect.y, selectionRect.w, selectionRect.h);
    canvas.width = Math.round(selectionRect.w);
    canvas.height = Math.round(selectionRect.h);
    setCanvasSize({ w: canvas.width, h: canvas.height });
    ctx.putImageData(data, 0, 0);
    setSelectionRect(null);
  };

  const savePng = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const url = canvas.toDataURL('image/png');
    const a = document.createElement('a');
    a.href = url;
    a.download = `Oriket_Paint_${Date.now()}.png`;
    a.click();
    onNotify('الرسام', 'تم حفظ اللوحة بصيغة PNG');
  };

  const openImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const img = new Image();
    img.onload = () => {
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (!canvas || !ctx) return;
      pushUndo();
      ctx.fillStyle = '#FFFFFF';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      const scale = Math.min(canvas.width / img.width, canvas.height / img.height);
      const dw = img.width * scale;
      const dh = img.height * scale;
      ctx.drawImage(img, (canvas.width - dw) / 2, (canvas.height - dh) / 2, dw, dh);
    };
    img.src = URL.createObjectURL(file);
    e.target.value = '';
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-[#E5EAF1] text-[#162338] select-none" dir="rtl">
      <input ref={fileInputRef} type="file" accept="image/*" className="hidden" onChange={openImage} />

      {/* Top Ribbon */}
      <div className="bg-[#F9FAFD] border-b border-[#DCE2EC] px-3 py-2 flex flex-wrap items-center gap-2 text-xs">
        <div className="flex items-center gap-1 border-l border-slate-200 pl-2">
          <button type="button" onClick={savePng} className="px-2.5 py-1 rounded bg-[#0067C0] text-white font-semibold cursor-pointer">حفظ PNG</button>
          <button type="button" onClick={() => fileInputRef.current?.click()} className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">فتح صورة</button>
          <button type="button" onClick={handleUndo} className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">↶</button>
          <button type="button" onClick={handleRedo} className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">↷</button>
          <button
            type="button"
            onClick={() => {
              const ctx = canvasRef.current?.getContext('2d');
              if (!ctx) return;
              pushUndo();
              ctx.fillStyle = '#FFFFFF';
              ctx.fillRect(0, 0, canvasSize.w, canvasSize.h);
            }}
            className="px-2.5 py-1 rounded bg-rose-50 text-rose-700 hover:bg-rose-100 cursor-pointer"
          >
            مسح الكل
          </button>
        </div>

        {/* Tools */}
        <div className="flex items-center gap-1 border-l border-slate-200 pl-2">
          {[
            { id: 'brush', label: 'فرشاة' },
            { id: 'pencil', label: 'قلم' },
            { id: 'fill', label: 'تعبئة' },
            { id: 'eraser', label: 'ممحاة' },
            { id: 'eyedropper', label: 'قطارة' },
            { id: 'text', label: 'نص' },
            { id: 'select', label: 'تحديد' },
          ].map((t) => (
            <button
              key={t.id}
              type="button"
              onClick={() => setTool(t.id as ToolType)}
              className={`px-2 py-1 rounded font-semibold cursor-pointer ${
                tool === t.id ? 'bg-[#0067C0] text-white' : 'bg-slate-100 hover:bg-slate-200'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Shapes */}
        <div className="flex items-center gap-1 border-l border-slate-200 pl-2">
          {([
            ['line', '╱'],
            ['rect', '▭'],
            ['round_rect', '▢'],
            ['circle', '◯'],
            ['triangle', '△'],
            ['arrow', '↗'],
          ] as [ShapeType, string][]).map(([s, glyph]) => (
            <button
              key={s}
              type="button"
              onClick={() => {
                setTool('shape');
                setShapeType(s);
              }}
              className={`w-7 h-7 rounded font-bold cursor-pointer ${
                tool === 'shape' && shapeType === s
                  ? 'bg-[#0067C0] text-white'
                  : 'bg-slate-100 hover:bg-slate-200'
              }`}
            >
              {glyph}
            </button>
          ))}
          <button
            type="button"
            onClick={() => setFillShapes((f) => !f)}
            className={`px-2 py-1 rounded cursor-pointer ${fillShapes ? 'bg-amber-100 text-amber-800' : 'bg-slate-100'}`}
          >
            تعبئة الشكل
          </button>
        </div>

        {/* Image ops */}
        <div className="flex items-center gap-1 border-l border-slate-200 pl-2">
          <button type="button" onClick={rotate90} className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">تدوير 90°</button>
          <button type="button" onClick={flipHorizontal} className="px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">قلب أفقي</button>
          {selectionRect && (
            <button type="button" onClick={cropToSelection} className="px-2 py-1 rounded bg-emerald-600 text-white cursor-pointer">قص التحديد</button>
          )}
          <button
            type="button"
            onClick={() => setShowGrid((g) => !g)}
            className={`px-2 py-1 rounded cursor-pointer ${showGrid ? 'bg-[#0067C0] text-white' : 'bg-slate-100'}`}
          >
            شبكة
          </button>
        </div>

        {/* Size & Colors */}
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-1">
            <span>الحجم: {brushSize}px</span>
            <input
              type="range"
              min={1}
              max={40}
              value={brushSize}
              onChange={(e) => setBrushSize(Number(e.target.value))}
              className="w-20 accent-[#0067C0]"
            />
          </label>
          <button
            type="button"
            onClick={() => setActiveSlot(1)}
            style={{ backgroundColor: primaryColor }}
            className={`w-7 h-7 rounded border-2 cursor-pointer ${activeSlot === 1 ? 'border-[#0067C0] scale-110' : 'border-slate-300'}`}
            title="لون 1"
          />
          <button
            type="button"
            onClick={() => setActiveSlot(2)}
            style={{ backgroundColor: secondaryColor }}
            className={`w-7 h-7 rounded border-2 cursor-pointer ${activeSlot === 2 ? 'border-[#0067C0] scale-110' : 'border-slate-300'}`}
            title="لون 2"
          />
          <input
            type="color"
            value={activeSlot === 1 ? primaryColor : secondaryColor}
            onChange={(e) =>
              activeSlot === 1 ? setPrimaryColor(e.target.value) : setSecondaryColor(e.target.value)
            }
            className="w-7 h-7 rounded cursor-pointer"
          />
          <div className="grid grid-cols-10 gap-0.5">
            {[...PALETTE_ROW_1, ...PALETTE_ROW_2].map((hex) => (
              <button
                key={hex}
                type="button"
                onClick={() => (activeSlot === 1 ? setPrimaryColor(hex) : setSecondaryColor(hex))}
                style={{ backgroundColor: hex }}
                className="w-4 h-4 rounded-[3px] border border-slate-300 cursor-pointer"
              />
            ))}
          </div>
        </div>
      </div>

      {/* Canvas Stage */}
      <div className="flex-1 min-h-0 overflow-auto p-4 flex items-center justify-center">
        <div
          style={{ transform: `scale(${zoom})`, transformOrigin: 'center' }}
          className="relative shadow-lg border border-slate-300 bg-white"
        >
          <canvas
            ref={canvasRef}
            width={canvasSize.w}
            height={canvasSize.h}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            className="block cursor-crosshair touch-none"
          />
          {showGrid && (
            <div
              className="absolute inset-0 pointer-events-none opacity-25"
              style={{
                backgroundImage:
                  'linear-gradient(to right, #94a3b8 1px, transparent 1px), linear-gradient(to bottom, #94a3b8 1px, transparent 1px)',
                backgroundSize: '20px 20px',
              }}
            />
          )}
          {selectionRect && (
            <div
              style={{
                left: selectionRect.x,
                top: selectionRect.y,
                width: selectionRect.w,
                height: selectionRect.h,
              }}
              className="absolute border-2 border-dashed border-[#0067C0] bg-[#0067C0]/10 pointer-events-none"
            />
          )}
        </div>
      </div>

      {/* Status Bar */}
      <div className="h-7 px-3 bg-[#F3F5F9] border-t border-[#DCE2EC] flex items-center justify-between text-[11px] text-slate-600" dir="ltr">
        <span className="font-mono-tabular">
          Cursor: {cursorPos.x}, {cursorPos.y} px  |  Canvas: {canvasSize.w} × {canvasSize.h} px
        </span>
        <div className="flex items-center gap-2">
          <input
            type="range"
            min={50}
            max={200}
            step={10}
            value={Math.round(zoom * 100)}
            onChange={(e) => setZoom(Number(e.target.value) / 100)}
            className="w-24 accent-[#0067C0]"
          />
          <span className="font-mono-tabular">{Math.round(zoom * 100)}%</span>
        </div>
      </div>

      {textModal && (
        <div
          onClick={() => setTextModal(null)}
          className="fixed inset-0 z-[1400] bg-black/40 flex items-center justify-center p-4"
        >
          <form
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault();
              const ctx = canvasRef.current?.getContext('2d');
              if (ctx && textInput.trim()) {
                pushUndo();
                ctx.fillStyle = primaryColor;
                ctx.font = `bold ${Math.max(16, brushSize * 3)}px Cairo, sans-serif`;
                ctx.fillText(textInput, textModal.x, textModal.y);
              }
              setTextModal(null);
            }}
            className="w-full max-w-xs bg-white rounded-xl shadow-2xl p-4 flex flex-col gap-3"
          >
            <div className="text-xs font-bold text-[#122036]">إضافة نص على اللوحة</div>
            <input
              type="text"
              value={textInput}
              onChange={(e) => setTextInput(e.target.value)}
              placeholder="اكتب النص..."
              className="h-9 px-3 rounded border border-slate-300 text-xs"
              autoFocus
            />
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setTextModal(null)} className="px-3 py-1.5 rounded text-xs text-slate-600 cursor-pointer">إلغاء</button>
              <button type="submit" className="px-4 py-1.5 rounded bg-[#0067C0] text-white text-xs font-semibold cursor-pointer">إدراج</button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
