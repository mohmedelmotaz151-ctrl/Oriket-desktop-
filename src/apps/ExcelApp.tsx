import React, { useEffect, useMemo, useRef, useState } from 'react';

interface ExcelAppProps {
  initialCsvText?: string;
  initialFileName?: string;
  registerHeaderActions?: (actions: {
    onSave: () => void;
    onUndo: () => void;
    onRedo: () => void;
  }) => void;
  onNotify: (title: string, msg: string) => void;
}

interface SheetData {
  name: string;
  cells: Record<string, string>; // key: "r,c"
  boldCells: Record<string, boolean>;
}

const COLS = 8; // A..H
const DEFAULT_ROWS = 18;
const KEY_WORKBOOK = 'orkeit_excel_v2';

function colName(c: number): string {
  return String.fromCharCode(65 + c);
}

function cellAddr(r: number, c: number): string {
  return `${colName(c)}${r + 1}`;
}

function parseAddress(ref: string): { r: number; c: number } | null {
  const m = ref.trim().toUpperCase().match(/^([A-Z])(\d+)$/);
  if (!m) return null;
  const c = m[1].charCodeAt(0) - 65;
  const r = Number(m[2]) - 1;
  if (c < 0 || c >= COLS || r < 0 || r >= 100) return null;
  return { r, c };
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let current: string[] = [];
  let field = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (ch === '"') {
        inQuotes = false;
      } else {
        field += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      current.push(field);
      field = '';
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      current.push(field);
      rows.push(current);
      current = [];
      field = '';
    } else {
      field += ch;
    }
  }
  if (field.length > 0 || current.length > 0) {
    current.push(field);
    rows.push(current);
  }
  return rows;
}

function createInitialSheets(csvText?: string): SheetData[] {
  if (csvText) {
    const parsed = parseCsv(csvText);
    const cells: Record<string, string> = {};
    parsed.forEach((row, r) => {
      row.slice(0, COLS).forEach((val, c) => {
        if (val) cells[`${r},${c}`] = val;
      });
    });
    return [{ name: 'Sheet1', cells, boldCells: {} }];
  }

  return [
    {
      name: 'Sheet1',
      cells: {
        '0,0': 'Product',
        '0,1': 'Category',
        '0,2': 'Q1',
        '0,3': 'Q2',
        '0,4': 'Total',
        '1,0': 'Laptop Pro',
        '1,1': 'Devices',
        '1,2': '4200',
        '1,3': '4850',
        '1,4': '=SUM(C2:D2)',
        '2,0': 'Monitor 4K',
        '2,1': 'Display',
        '2,2': '1850',
        '2,3': '2100',
        '2,4': '=SUM(C3:D3)',
        '3,0': 'Dock Station',
        '3,1': 'Accessories',
        '3,2': '640',
        '3,3': '790',
        '3,4': '=SUM(C4:D4)',
        '4,0': 'Grand Total',
        '4,2': '=SUM(C2:C4)',
        '4,3': '=SUM(D2:D4)',
        '4,4': '=SUM(E2:E4)',
      },
      boldCells: { '4,0': true, '4,4': true },
    },
    {
      name: 'Sheet2',
      cells: { '0,0': 'Task', '0,1': 'Status', '1,0': 'Review Q2', '1,1': 'Done' },
      boldCells: {},
    },
  ];
}

export const ExcelApp: React.FC<ExcelAppProps> = ({
  initialCsvText,
  initialFileName = 'Book1.csv',
  registerHeaderActions,
  onNotify,
}) => {
  const [sheets, setSheets] = useState<SheetData[]>(() => {
    if (initialCsvText) return createInitialSheets(initialCsvText);
    try {
      const raw = localStorage.getItem(KEY_WORKBOOK);
      if (raw) {
        const parsed = JSON.parse(raw) as SheetData[];
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch {
      // fallback
    }
    return createInitialSheets();
  });

  const [activeSheetIdx, setActiveSheetIdx] = useState(0);
  const [selected, setSelected] = useState<{ r: number; c: number }>({ r: 1, c: 4 });
  const [activeRibbon, setActiveRibbon] = useState<'File' | 'Home' | 'Formulas' | 'Data' | 'View'>('Data');
  const [formulaInput, setFormulaInput] = useState('');
  const [undoStack, setUndoStack] = useState<SheetData[][]>([]);
  const [redoStack, setRedoStack] = useState<SheetData[][]>([]);
  const [showGridlines, setShowGridlines] = useState(true);
  const [zoom, setZoom] = useState(1);
  const [filterCol, setFilterCol] = useState<number | null>(null);
  const [activeFilterValue, setActiveFilterValue] = useState<{ col: number; val: string } | null>(null);

  const csvInputRef = useRef<HTMLInputElement>(null);
  const sheet = sheets[activeSheetIdx] || sheets[0];

  useEffect(() => {
    try {
      localStorage.setItem(KEY_WORKBOOK, JSON.stringify(sheets));
    } catch {
      // ignore
    }
  }, [sheets]);

  const evalCell = (r: number, c: number, visited = new Set<string>()): string => {
    const key = `${r},${c}`;
    if (visited.has(key)) return '#CYCLE!';
    const raw = sheet.cells[key] ?? '';
    if (!raw.startsWith('=')) return raw;
    const nextVisited = new Set(visited);
    nextVisited.add(key);

    const expandRange = (a: string, b: string): number[] => {
      const p1 = parseAddress(a);
      const p2 = parseAddress(b);
      if (!p1 || !p2) return [];
      const nums: number[] = [];
      for (let rr = Math.min(p1.r, p2.r); rr <= Math.max(p1.r, p2.r); rr++) {
        for (let cc = Math.min(p1.c, p2.c); cc <= Math.max(p1.c, p2.c); cc++) {
          const val = Number(evalCell(rr, cc, nextVisited));
          if (!Number.isNaN(val)) nums.push(val);
        }
      }
      return nums;
    };

    try {
      let expr = raw.slice(1).trim();
      expr = expr.replace(
        /(SUM|AVERAGE|AVG|MIN|MAX|COUNT)\(([A-Z]\d+):([A-Z]\d+)\)/gi,
        (_, fn: string, a: string, b: string) => {
          const vals = expandRange(a, b);
          const upper = fn.toUpperCase();
          if (upper === 'COUNT') return String(vals.length);
          if (vals.length === 0) return '0';
          if (upper === 'SUM') return String(vals.reduce((x, y) => x + y, 0));
          if (upper === 'AVERAGE' || upper === 'AVG') {
            return String(vals.reduce((x, y) => x + y, 0) / vals.length);
          }
          if (upper === 'MIN') return String(Math.min(...vals));
          if (upper === 'MAX') return String(Math.max(...vals));
          return '0';
        }
      );

      expr = expr.replace(/\b([A-Z]\d+)\b/gi, (_, ref: string) => {
        const addr = parseAddress(ref);
        if (!addr) return '0';
        const v = Number(evalCell(addr.r, addr.c, nextVisited));
        return Number.isNaN(v) ? '0' : String(v);
      });

      if (!/^[0-9+\-*/().\s]+$/.test(expr)) return '#ERR';
      const fn = new Function(`return (${expr});`) as () => number;
      const out = fn();
      if (!Number.isFinite(out)) return '#DIV/0!';
      return Number.isInteger(out) ? String(out) : String(Number(out.toFixed(2)));
    } catch {
      return '#ERR';
    }
  };

  useEffect(() => {
    setFormulaInput(sheet.cells[`${selected.r},${selected.c}`] ?? '');
  }, [selected, activeSheetIdx, sheet.cells]);

  const commitWithUndo = (updater: (prev: SheetData[]) => SheetData[]) => {
    setUndoStack((u) => [...u.slice(-19), sheets]);
    setRedoStack([]);
    setSheets((prev) => updater(prev));
  };

  const setCellValue = (r: number, c: number, val: string) => {
    commitWithUndo((prev) =>
      prev.map((s, idx) => {
        if (idx !== activeSheetIdx) return s;
        const nextCells = { ...s.cells };
        if (val === '') delete nextCells[`${r},${c}`];
        else nextCells[`${r},${c}`] = val;
        return { ...s, cells: nextCells };
      })
    );
  };

  const handleExportCsv = () => {
    const lines: string[] = [];
    for (let r = 0; r < DEFAULT_ROWS; r++) {
      const rowVals: string[] = [];
      let hasData = false;
      for (let c = 0; c < COLS; c++) {
        const v = evalCell(r, c);
        if (v) hasData = true;
        rowVals.push(`"${v.replace(/"/g, '""')}"`);
      }
      if (hasData) lines.push(rowVals.join(','));
    }
    const bom = '\uFEFF';
    const blob = new Blob([bom + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = initialFileName.endsWith('.csv') ? initialFileName : `${sheet.name}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    onNotify('Excel', `تم تصدير ${sheet.name}.csv`);
  };

  const handleUndo = () => {
    if (undoStack.length === 0) return;
    const prev = undoStack[undoStack.length - 1];
    setRedoStack((r) => [...r, sheets]);
    setUndoStack((u) => u.slice(0, -1));
    setSheets(prev);
  };

  const handleRedo = () => {
    if (redoStack.length === 0) return;
    const next = redoStack[redoStack.length - 1];
    setUndoStack((u) => [...u, sheets]);
    setRedoStack((r) => r.slice(0, -1));
    setSheets(next);
  };

  useEffect(() => {
    registerHeaderActions?.({
      onSave: handleExportCsv,
      onUndo: handleUndo,
      onRedo: handleRedo,
    });
  });

  const handleImportCsv = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const parsed = parseCsv(String(reader.result || ''));
      const cells: Record<string, string> = {};
      parsed.forEach((row, r) => {
        row.slice(0, COLS).forEach((val, c) => {
          if (val) cells[`${r},${c}`] = val;
        });
      });
      commitWithUndo((prev) =>
        prev.map((s, idx) => (idx === activeSheetIdx ? { ...s, cells } : s))
      );
      onNotify('Excel', `تم استيراد ${file.name}`);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const sortColumn = (col: number, asc: boolean) => {
    const dataRows: string[][] = [];
    for (let r = 1; r < DEFAULT_ROWS; r++) {
      const row = Array.from({ length: COLS }, (_, c) => sheet.cells[`${r},${c}`] || '');
      if (row.some(Boolean)) dataRows.push(row);
    }
    dataRows.sort((a, b) => {
      const na = Number(a[col]);
      const nb = Number(b[col]);
      if (!Number.isNaN(na) && !Number.isNaN(nb) && a[col] !== '' && b[col] !== '') {
        return asc ? na - nb : nb - na;
      }
      return asc ? a[col].localeCompare(b[col]) : b[col].localeCompare(a[col]);
    });
    commitWithUndo((prev) =>
      prev.map((s, idx) => {
        if (idx !== activeSheetIdx) return s;
        const nextCells: Record<string, string> = {};
        for (let c = 0; c < COLS; c++) {
          if (s.cells[`0,${c}`]) nextCells[`0,${c}`] = s.cells[`0,${c}`];
        }
        dataRows.forEach((row, i) => {
          row.forEach((val, c) => {
            if (val) nextCells[`${i + 1},${c}`] = val;
          });
        });
        return { ...s, cells: nextCells };
      })
    );
    setFilterCol(null);
  };

  const columnStats = useMemo(() => {
    const nums: number[] = [];
    for (let r = 1; r < DEFAULT_ROWS; r++) {
      const v = Number(evalCell(r, selected.c));
      if (!Number.isNaN(v) && evalCell(r, selected.c) !== '') nums.push(v);
    }
    const sum = nums.reduce((a, b) => a + b, 0);
    const avg = nums.length ? sum / nums.length : 0;
    return { count: nums.length, sum, avg };
  }, [sheet, selected.c]);

  const distinctColValues = useMemo(() => {
    if (filterCol === null) return [];
    const set = new Set<string>();
    for (let r = 1; r < DEFAULT_ROWS; r++) {
      const v = evalCell(r, filterCol);
      if (v) set.add(v);
    }
    return Array.from(set);
  }, [filterCol, sheet]);

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-white text-[#1D2430] select-none relative" dir="ltr">
      <input
        ref={csvInputRef}
        type="file"
        accept=".csv,text/csv"
        className="hidden"
        onChange={handleImportCsv}
      />

      {/* Excel Ribbon Tabs */}
      <div className="bg-[#F3F6F4] border-b border-[#DCE2DF] px-2 pt-1 flex items-center gap-1 text-xs">
        {(['File', 'Home', 'Formulas', 'Data', 'View'] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveRibbon(tab)}
            className={`px-3 py-1 rounded-t font-medium cursor-pointer ${
              activeRibbon === tab
                ? 'bg-white text-[#217346] font-bold border-x border-t border-[#DCE2DF]'
                : 'text-slate-600 hover:bg-white/60'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* Ribbon Toolbar */}
      <div className="bg-white border-b border-[#E3E7E5] px-3 py-1.5 flex flex-wrap items-center gap-2 text-xs">
        {activeRibbon === 'File' && (
          <>
            <button type="button" onClick={() => csvInputRef.current?.click()} className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">Import CSV</button>
            <button type="button" onClick={handleExportCsv} className="px-2.5 py-1 rounded bg-[#217346] text-white font-semibold cursor-pointer">Export CSV</button>
            <button
              type="button"
              onClick={() =>
                commitWithUndo((prev) =>
                  prev.map((s, idx) => (idx === activeSheetIdx ? { ...s, cells: {} } : s))
                )
              }
              className="px-2.5 py-1 rounded bg-red-50 text-red-700 cursor-pointer"
            >
              Clear Sheet
            </button>
          </>
        )}
        {activeRibbon === 'Home' && (
          <>
            <button type="button" onClick={handleUndo} className="px-2 py-1 rounded hover:bg-slate-100 cursor-pointer">↶ Undo</button>
            <button type="button" onClick={handleRedo} className="px-2 py-1 rounded hover:bg-slate-100 cursor-pointer">↷ Redo</button>
            <button
              type="button"
              onClick={() =>
                setSheets((prev) =>
                  prev.map((s, idx) =>
                    idx !== activeSheetIdx
                      ? s
                      : {
                          ...s,
                          boldCells: {
                            ...s.boldCells,
                            [`${selected.r},${selected.c}`]: !s.boldCells[`${selected.r},${selected.c}`],
                          },
                        }
                  )
                )
              }
              className="px-2.5 py-1 rounded border border-slate-200 font-bold hover:bg-slate-100 cursor-pointer"
            >
              B
            </button>
          </>
        )}
        {activeRibbon === 'Formulas' && (
          <>
            {['=SUM(C2:C4)', '=AVERAGE(C2:C4)', '=MIN(C2:C4)', '=MAX(C2:C4)', '=COUNT(C2:C4)'].map((fn) => (
              <button
                key={fn}
                type="button"
                onClick={() => setCellValue(selected.r, selected.c, fn)}
                className="px-2.5 py-1 rounded bg-[#EDF5F0] text-[#185A36] font-mono text-[11px] hover:bg-[#DCECE2] cursor-pointer"
              >
                {fn}
              </button>
            ))}
          </>
        )}
        {activeRibbon === 'Data' && (
          <>
            <button type="button" onClick={() => sortColumn(selected.c, true)} className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">Sort A→Z</button>
            <button type="button" onClick={() => sortColumn(selected.c, false)} className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">Sort Z→A</button>
            <button type="button" onClick={() => setActiveFilterValue(null)} className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer">Clear Filter</button>
            <button type="button" onClick={() => csvInputRef.current?.click()} className="px-2.5 py-1 rounded bg-[#EDF5F0] text-[#185A36] font-semibold cursor-pointer">From CSV</button>
            <button type="button" onClick={handleExportCsv} className="px-2.5 py-1 rounded bg-[#217346] text-white font-semibold cursor-pointer">Export CSV</button>
          </>
        )}
        {activeRibbon === 'View' && (
          <button
            type="button"
            onClick={() => setShowGridlines((g) => !g)}
            className="px-2.5 py-1 rounded bg-slate-100 hover:bg-slate-200 cursor-pointer"
          >
            Gridlines: {showGridlines ? 'On' : 'Off'}
          </button>
        )}
      </div>

      {/* Formula Bar */}
      <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-[#F8FAF9] border-b border-[#E1E5E3] text-xs">
        <div className="w-12 h-7 rounded bg-white border border-slate-300 flex items-center justify-center font-mono-tabular font-bold text-[#217346]">
          {cellAddr(selected.r, selected.c)}
        </div>
        <button
          type="button"
          onClick={() => setFormulaInput(sheet.cells[`${selected.r},${selected.c}`] ?? '')}
          className="w-6 h-7 rounded hover:bg-slate-200 text-rose-600 font-bold cursor-pointer"
          title="Cancel"
        >
          ✕
        </button>
        <button
          type="button"
          onClick={() => setCellValue(selected.r, selected.c, formulaInput)}
          className="w-6 h-7 rounded hover:bg-slate-200 text-[#217346] font-bold cursor-pointer"
          title="Commit"
        >
          ✓
        </button>
        <span className="px-1.5 font-serif italic font-bold text-[#217346]">fx</span>
        <input
          type="text"
          value={formulaInput}
          onChange={(e) => setFormulaInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              setCellValue(selected.r, selected.c, formulaInput);
            }
          }}
          onBlur={() => {
            const current = sheet.cells[`${selected.r},${selected.c}`] ?? '';
            if (formulaInput !== current) setCellValue(selected.r, selected.c, formulaInput);
          }}
          placeholder="Enter value or =SUM(C2:C4)"
          className="flex-1 h-7 px-2.5 rounded bg-white border border-slate-300 font-mono text-xs focus:outline-none focus:border-[#217346]"
        />
      </div>

      {/* Spreadsheet Grid */}
      <div className="flex-1 min-h-0 overflow-auto bg-white">
        <table
          style={{ zoom: zoom }}
          className="border-collapse text-xs min-w-full"
        >
          <thead>
            <tr>
              <th className="w-10 bg-[#F1F3F2] border border-[#D4D9D6] text-[10px] text-slate-500 sticky top-0 left-0 z-20" />
              {Array.from({ length: COLS }).map((_, c) => (
                <th
                  key={c}
                  className={`min-w-[96px] h-7 border border-[#D4D9D6] font-semibold sticky top-0 z-10 ${
                    selected.c === c ? 'bg-[#D3E8DC] text-[#185A36]' : 'bg-[#F1F3F2] text-slate-600'
                  }`}
                >
                  {colName(c)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: DEFAULT_ROWS }).map((_, r) => {
              if (
                activeFilterValue &&
                r > 0 &&
                evalCell(r, activeFilterValue.col) !== activeFilterValue.val
              ) {
                return null;
              }
              const isTableHeader = r === 0;
              return (
                <tr key={r}>
                  <td
                    className={`w-10 text-center font-mono-tabular text-[11px] border border-[#D4D9D6] sticky left-0 z-10 ${
                      selected.r === r ? 'bg-[#D3E8DC] text-[#185A36] font-bold' : 'bg-[#F1F3F2] text-slate-500'
                    }`}
                  >
                    {r + 1}
                  </td>
                  {Array.from({ length: COLS }).map((__, c) => {
                    const isSel = selected.r === r && selected.c === c;
                    const displayVal = evalCell(r, c);
                    const isBold = isTableHeader || sheet.boldCells[`${r},${c}`];
                    return (
                      <td
                        key={c}
                        onClick={() => setSelected({ r, c })}
                        className={`relative h-7 px-1.5 ${
                          showGridlines ? 'border border-[#E1E5E3]' : 'border border-transparent'
                        } ${
                          isTableHeader
                            ? 'bg-[#4472C4] text-white font-bold'
                            : r % 2 === 1
                            ? 'bg-white'
                            : 'bg-[#E9F0F9]'
                        } ${isSel ? 'outline-2 outline-[#217346] -outline-offset-2 z-10' : ''}`}
                      >
                        <div className="flex items-center justify-between gap-1">
                          {isSel ? (
                            <input
                              type="text"
                              value={formulaInput}
                              onChange={(e) => setFormulaInput(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter') {
                                  setCellValue(r, c, formulaInput);
                                  setSelected({ r: Math.min(DEFAULT_ROWS - 1, r + 1), c });
                                }
                              }}
                              onBlur={() => {
                                const cur = sheet.cells[`${r},${c}`] ?? '';
                                if (formulaInput !== cur) setCellValue(r, c, formulaInput);
                              }}
                              className={`w-full bg-transparent focus:outline-none text-xs ${
                                isTableHeader ? 'text-white' : 'text-[#122036]'
                              } ${isBold ? 'font-bold' : ''}`}
                            />
                          ) : (
                            <span className={`truncate ${isBold ? 'font-bold' : ''}`}>
                              {displayVal}
                            </span>
                          )}
                          {isTableHeader && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                setFilterCol((prev) => (prev === c ? null : c));
                              }}
                              className="text-[10px] px-1 rounded bg-white/20 hover:bg-white/35 text-white cursor-pointer shrink-0"
                            >
                              ▾
                            </button>
                          )}
                        </div>
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Column Filter Popover */}
      {filterCol !== null && (
        <div className="absolute top-28 right-4 z-30 w-48 bg-white border border-slate-200 rounded-xl shadow-xl p-2 text-xs flex flex-col gap-1">
          <div className="font-bold text-[#122036] px-1 pb-1 border-b border-slate-100">
            Column {colName(filterCol)}
          </div>
          <button type="button" onClick={() => sortColumn(filterCol, true)} className="text-left px-2 py-1 rounded hover:bg-slate-100 cursor-pointer">Sort A → Z</button>
          <button type="button" onClick={() => sortColumn(filterCol, false)} className="text-left px-2 py-1 rounded hover:bg-slate-100 cursor-pointer">Sort Z → A</button>
          <button
            type="button"
            onClick={() => {
              setActiveFilterValue(null);
              setFilterCol(null);
            }}
            className="text-left px-2 py-1 rounded hover:bg-slate-100 text-[#217346] font-semibold cursor-pointer"
          >
            Show All
          </button>
          <div className="max-h-28 overflow-y-auto border-t border-slate-100 pt-1">
            {distinctColValues.map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => {
                  setActiveFilterValue({ col: filterCol, val: v });
                  setFilterCol(null);
                }}
                className="w-full text-left px-2 py-1 rounded hover:bg-[#EBF5EE] truncate cursor-pointer"
              >
                {v}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Sheet Tabs + Status Bar */}
      <div className="bg-[#F3F6F4] border-t border-[#DCE2DF] px-2.5 py-1 flex flex-wrap items-center justify-between gap-2 text-[11px]">
        <div className="flex items-center gap-1">
          {sheets.map((s, idx) => (
            <button
              key={s.name}
              type="button"
              onClick={() => setActiveSheetIdx(idx)}
              className={`px-3 py-0.5 rounded-t border text-xs cursor-pointer ${
                idx === activeSheetIdx
                  ? 'bg-white border-[#217346] text-[#217346] font-bold'
                  : 'bg-[#E6ECE8] border-transparent text-slate-600 hover:bg-white'
              }`}
            >
              {s.name}
            </button>
          ))}
          <button
            type="button"
            onClick={() => {
              const name = `Sheet${sheets.length + 1}`;
              setSheets((prev) => [...prev, { name, cells: {}, boldCells: {} }]);
              setActiveSheetIdx(sheets.length);
            }}
            className="w-6 h-6 rounded hover:bg-white font-bold text-[#217346] cursor-pointer"
            title="Add Sheet"
          >
            +
          </button>
        </div>

        <div className="flex items-center gap-3 text-slate-600 font-mono-tabular">
          <span>
            Ready • Col {colName(selected.c)}: Sum {columnStats.sum.toFixed(1)} | Avg{' '}
            {columnStats.avg.toFixed(1)} | Count {columnStats.count}
          </span>
          <div className="flex items-center gap-1">
            <input
              type="range"
              min={70}
              max={140}
              step={10}
              value={Math.round(zoom * 100)}
              onChange={(e) => setZoom(Number(e.target.value) / 100)}
              className="w-20 accent-[#217346]"
            />
            <span>{Math.round(zoom * 100)}%</span>
          </div>
        </div>
      </div>
    </div>
  );
};
