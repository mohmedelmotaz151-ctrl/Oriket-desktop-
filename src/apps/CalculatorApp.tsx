import React, { useState } from 'react';

interface CalculatorAppProps {
  onNotify: (title: string, msg: string) => void;
}

function evaluateExpression(expr: string): string {
  const clean = expr.replace(/×/g, '*').replace(/÷/g, '/').replace(/\s+/g, '');
  if (!clean) return '0';
  if (!/^[0-9+\-*/().]+$/.test(clean)) return 'خطأ';
  try {
    // Safe arithmetic tokenizer + recursive descent parser (no eval)
    let pos = 0;
    const parseExpr = (): number => {
      let val = parseTerm();
      while (pos < clean.length && (clean[pos] === '+' || clean[pos] === '-')) {
        const op = clean[pos++];
        const right = parseTerm();
        val = op === '+' ? val + right : val - right;
      }
      return val;
    };
    const parseTerm = (): number => {
      let val = parseFactor();
      while (pos < clean.length && (clean[pos] === '*' || clean[pos] === '/')) {
        const op = clean[pos++];
        const right = parseFactor();
        if (op === '/') {
          if (right === 0) throw new Error('DivZero');
          val /= right;
        } else {
          val *= right;
        }
      }
      return val;
    };
    const parseFactor = (): number => {
      if (clean[pos] === '+') {
        pos++;
        return parseFactor();
      }
      if (clean[pos] === '-') {
        pos++;
        return -parseFactor();
      }
      if (clean[pos] === '(') {
        pos++;
        const v = parseExpr();
        if (clean[pos] === ')') pos++;
        return v;
      }
      const start = pos;
      while (pos < clean.length && /[0-9.]/.test(clean[pos])) pos++;
      if (start === pos) throw new Error('Syntax');
      return Number(clean.slice(start, pos));
    };
    const res = parseExpr();
    if (!Number.isFinite(res) || pos !== clean.length) return 'خطأ';
    return Number.isInteger(res) ? String(res) : String(Number(res.toFixed(8)));
  } catch {
    return 'خطأ';
  }
}

export const CalculatorApp: React.FC<CalculatorAppProps> = ({ onNotify }) => {
  const [expression, setExpression] = useState('');
  const [result, setResult] = useState('0');

  const keys = [
    ['C', '⌫', '(', ')'],
    ['7', '8', '9', '÷'],
    ['4', '5', '6', '×'],
    ['1', '2', '3', '-'],
    ['0', '.', '=', '+'],
  ];

  const handlePress = (key: string) => {
    if (key === 'C') {
      setExpression('');
      setResult('0');
      return;
    }
    if (key === '⌫') {
      const next = expression.slice(0, -1);
      setExpression(next);
      return;
    }
    if (key === '=') {
      const out = evaluateExpression(expression);
      setResult(out);
      if (out !== 'خطأ') setExpression(out);
      return;
    }
    setExpression((prev) => prev + key);
  };

  const copyResult = async () => {
    try {
      await navigator.clipboard.writeText(result);
      onNotify('الحاسبة', `تم نسخ الناتج (${result})`);
    } catch {
      onNotify('الحاسبة', 'تعذر النسخ التلقائي');
    }
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-2 bg-[#F8FAFD] rounded-lg select-none">
      <div className="bg-white border border-slate-200 rounded-xl p-3 flex flex-col items-end gap-1 shadow-2xs">
        <div className="text-xs text-slate-400 font-mono-tabular min-h-[18px] break-all" dir="ltr">
          {expression || ' '}
        </div>
        <div className="flex items-center justify-between w-full gap-2">
          <button
            type="button"
            onClick={copyResult}
            className="text-[11px] px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 cursor-pointer"
          >
            نسخ
          </button>
          <div className="text-2xl font-bold text-[#122036] font-mono-tabular truncate" dir="ltr">
            {result}
          </div>
        </div>
      </div>

      <div className="flex-1 grid grid-cols-4 gap-2">
        {keys.flat().map((key) => {
          const isEquals = key === '=';
          const isOp = ['÷', '×', '-', '+', 'C', '⌫', '(', ')'].includes(key);
          return (
            <button
              key={key}
              type="button"
              onClick={() => handlePress(key)}
              className={`rounded-xl text-base font-bold transition active:scale-95 cursor-pointer ${
                isEquals
                  ? 'bg-[#0067C0] hover:bg-[#0057A3] text-white shadow-xs'
                  : isOp
                  ? 'bg-[#E7EFFA] hover:bg-[#D8E6F8] text-[#143058]'
                  : 'bg-white hover:bg-slate-50 border border-slate-200 text-[#122036]'
              }`}
            >
              {key}
            </button>
          );
        })}
      </div>
    </div>
  );
};
