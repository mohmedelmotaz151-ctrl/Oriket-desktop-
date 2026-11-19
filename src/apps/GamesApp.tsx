import React, { useEffect, useState } from 'react';

interface GamesAppProps {
  initialGame?: 'menu' | 'snake' | 'number';
  onNotify: (title: string, msg: string) => void;
}

type Point = { x: number; y: number };
type Dir = 'UP' | 'DOWN' | 'LEFT' | 'RIGHT';

const GRID_SIZE = 14;

export const GamesApp: React.FC<GamesAppProps> = ({ initialGame = 'menu', onNotify }) => {
  const [activeGame, setActiveGame] = useState<'menu' | 'snake' | 'number'>(initialGame);

  // Snake state
  const [snake, setSnake] = useState<Point[]>([
    { x: 6, y: 7 },
    { x: 5, y: 7 },
    { x: 4, y: 7 },
  ]);
  const [dir, setDir] = useState<Dir>('RIGHT');
  const [food, setFood] = useState<Point>({ x: 10, y: 7 });
  const [score, setScore] = useState(0);
  const [running, setRunning] = useState(true);
  const [gameOver, setGameOver] = useState(false);

  // Number Challenge state
  const [targetNum, setTargetNum] = useState(() => Math.floor(Math.random() * 20) + 1);
  const [guessInput, setGuessInput] = useState('');
  const [guessHint, setGuessHint] = useState('خمن رقمًا من 1 إلى 20');
  const [attempts, setAttempts] = useState(0);

  const resetSnake = () => {
    setSnake([
      { x: 6, y: 7 },
      { x: 5, y: 7 },
      { x: 4, y: 7 },
    ]);
    setDir('RIGHT');
    setFood({ x: 10, y: 7 });
    setScore(0);
    setGameOver(false);
    setRunning(true);
  };

  useEffect(() => {
    if (activeGame !== 'snake') return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowUp') setDir((d) => (d === 'DOWN' ? d : 'UP'));
      if (e.key === 'ArrowDown') setDir((d) => (d === 'UP' ? d : 'DOWN'));
      if (e.key === 'ArrowLeft') setDir((d) => (d === 'RIGHT' ? d : 'LEFT'));
      if (e.key === 'ArrowRight') setDir((d) => (d === 'LEFT' ? d : 'RIGHT'));
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [activeGame]);

  useEffect(() => {
    if (activeGame !== 'snake' || !running || gameOver) return;
    const timer = window.setInterval(() => {
      setSnake((prev) => {
        const head = prev[0];
        const next: Point = {
          x: (head.x + (dir === 'RIGHT' ? 1 : dir === 'LEFT' ? -1 : 0) + GRID_SIZE) % GRID_SIZE,
          y: (head.y + (dir === 'DOWN' ? 1 : dir === 'UP' ? -1 : 0) + GRID_SIZE) % GRID_SIZE,
        };
        if (prev.some((p) => p.x === next.x && p.y === next.y)) {
          setGameOver(true);
          setRunning(false);
          return prev;
        }
        const ate = next.x === food.x && next.y === food.y;
        const body = ate ? [next, ...prev] : [next, ...prev.slice(0, -1)];
        if (ate) {
          setScore((s) => s + 10);
          let fx = Math.floor(Math.random() * GRID_SIZE);
          let fy = Math.floor(Math.random() * GRID_SIZE);
          while (body.some((p) => p.x === fx && p.y === fy)) {
            fx = Math.floor(Math.random() * GRID_SIZE);
            fy = Math.floor(Math.random() * GRID_SIZE);
          }
          setFood({ x: fx, y: fy });
        }
        return body;
      });
    }, 170);
    return () => window.clearInterval(timer);
  }, [activeGame, running, gameOver, dir, food]);

  const submitGuess = (e: React.FormEvent) => {
    e.preventDefault();
    const num = Number(guessInput);
    if (!Number.isInteger(num) || num < 1 || num > 20) {
      setGuessHint('أدخل رقمًا صحيحًا بين 1 و 20');
      return;
    }
    const nextAttempts = attempts + 1;
    setAttempts(nextAttempts);
    if (num === targetNum) {
      setGuessHint(`أحسنت! الرقم الصحيح هو ${targetNum} خلال ${nextAttempts} محاولات 🎉`);
      onNotify('تحدي الأرقام', `فزت خلال ${nextAttempts} محاولات!`);
    } else if (num < targetNum) {
      setGuessHint('الرقم المطلوب أكبر ▲');
    } else {
      setGuessHint('الرقم المطلوب أصغر ▼');
    }
    setGuessInput('');
  };

  if (activeGame === 'menu') {
    return (
      <div className="flex-1 min-h-0 flex flex-col gap-3 p-3 bg-white rounded-lg" dir="rtl">
        <h3 className="text-sm font-bold text-[#122036]">ألعاب Windows</h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            type="button"
            onClick={() => {
              resetSnake();
              setActiveGame('snake');
            }}
            className="flex items-center gap-3 p-4 rounded-2xl border border-slate-200 bg-[#F7FAFF] hover:bg-[#EDF4FF] text-right transition cursor-pointer"
          >
            <img src="/icons/win11_games.png" alt="" className="w-11 h-11 object-contain" />
            <div>
              <div className="text-sm font-bold text-[#122036]">Snake Desktop</div>
              <div className="text-xs text-slate-500">لعبة الثعبان الكلاسيكية 14×14</div>
            </div>
          </button>
          <button
            type="button"
            onClick={() => setActiveGame('number')}
            className="flex items-center gap-3 p-4 rounded-2xl border border-slate-200 bg-[#F7FAFF] hover:bg-[#EDF4FF] text-right transition cursor-pointer"
          >
            <img src="/icons/win11_calculator.png" alt="" className="w-11 h-11 object-contain" />
            <div>
              <div className="text-sm font-bold text-[#122036]">تحدي الأرقام (1-20)</div>
              <div className="text-xs text-slate-500">تخمين الرقم السري بأقل محاولات</div>
            </div>
          </button>
        </div>
      </div>
    );
  }

  if (activeGame === 'number') {
    return (
      <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-4 p-4 bg-white rounded-lg" dir="rtl">
        <button
          type="button"
          onClick={() => setActiveGame('menu')}
          className="self-start px-3 py-1 rounded-md bg-slate-100 hover:bg-slate-200 text-xs font-semibold text-slate-700 cursor-pointer"
        >
          ← العودة للألعاب
        </button>
        <div className="text-base font-bold text-[#122036]">تحدي الأرقام 1 – 20</div>
        <div className="text-xs text-[#0067C0] font-semibold bg-[#EBF3FE] px-4 py-2 rounded-full">
          {guessHint}
        </div>
        <form onSubmit={submitGuess} className="flex items-center gap-2">
          <input
            type="number"
            min={1}
            max={20}
            value={guessInput}
            onChange={(e) => setGuessInput(e.target.value)}
            placeholder="1 - 20"
            className="w-24 h-10 px-3 rounded-xl border border-slate-300 text-center text-sm font-bold focus:outline-none focus:border-[#0067C0]"
          />
          <button
            type="submit"
            className="h-10 px-4 rounded-xl bg-[#0067C0] hover:bg-[#0057A3] text-white text-xs font-bold cursor-pointer"
          >
            تحقق
          </button>
        </form>
        <button
          type="button"
          onClick={() => {
            setTargetNum(Math.floor(Math.random() * 20) + 1);
            setAttempts(0);
            setGuessHint('تم اختيار رقم جديد من 1 إلى 20');
          }}
          className="text-xs text-slate-500 hover:text-[#0067C0] cursor-pointer"
        >
          بدء جولة جديدة ↻
        </button>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col items-center justify-between gap-2 p-2 bg-[#0D1626] text-white rounded-lg select-none" dir="rtl">
      <div className="w-full flex items-center justify-between px-2">
        <button
          type="button"
          onClick={() => setActiveGame('menu')}
          className="px-2.5 py-1 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
        >
          ← الألعاب
        </button>
        <span className="text-xs font-bold font-mono-tabular text-emerald-400">
          النقاط: {score}
        </span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setRunning((r) => !r)}
            className="px-2.5 py-1 rounded bg-white/10 hover:bg-white/20 text-xs cursor-pointer"
          >
            {running ? 'إيقاف' : 'استئناف'}
          </button>
          <button
            type="button"
            onClick={resetSnake}
            className="px-2.5 py-1 rounded bg-[#0067C0] hover:bg-[#0057A3] text-xs cursor-pointer"
          >
            إعادة
          </button>
        </div>
      </div>

      {/* 14x14 Snake Grid */}
      <div className="relative bg-[#070C16] border border-slate-700 rounded-lg p-1 grid grid-cols-14 gap-0.5 w-[252px] h-[252px] sm:w-[280px] sm:h-[280px]">
        {Array.from({ length: GRID_SIZE * GRID_SIZE }).map((_, idx) => {
          const x = idx % GRID_SIZE;
          const y = Math.floor(idx / GRID_SIZE);
          const isHead = snake[0].x === x && snake[0].y === y;
          const isBody = !isHead && snake.some((p) => p.x === x && p.y === y);
          const isFood = food.x === x && food.y === y;
          return (
            <div
              key={idx}
              className={`rounded-[3px] ${
                isHead
                  ? 'bg-emerald-400'
                  : isBody
                  ? 'bg-emerald-600'
                  : isFood
                  ? 'bg-rose-500 animate-pulse'
                  : 'bg-[#101A2E]'
              }`}
            />
          );
        })}

        {gameOver && (
          <div className="absolute inset-0 bg-black/75 flex flex-col items-center justify-center gap-2 rounded-lg">
            <span className="text-sm font-bold text-rose-400">انتهت اللعبة!</span>
            <span className="text-xs text-slate-300">النتيجة: {score}</span>
            <button
              type="button"
              onClick={resetSnake}
              className="px-4 py-1.5 rounded-lg bg-[#0067C0] text-white text-xs font-bold cursor-pointer"
            >
              العب مجددًا
            </button>
          </div>
        )}
      </div>

      {/* Touch D-Pad */}
      <div className="grid grid-cols-3 gap-1 w-32 pb-1" dir="ltr">
        <div />
        <button
          type="button"
          onClick={() => setDir((d) => (d === 'DOWN' ? d : 'UP'))}
          className="h-8 rounded bg-white/15 hover:bg-white/25 text-xs font-bold cursor-pointer"
        >
          ▲
        </button>
        <div />
        <button
          type="button"
          onClick={() => setDir((d) => (d === 'RIGHT' ? d : 'LEFT'))}
          className="h-8 rounded bg-white/15 hover:bg-white/25 text-xs font-bold cursor-pointer"
        >
          ◀
        </button>
        <button
          type="button"
          onClick={() => setDir((d) => (d === 'UP' ? d : 'DOWN'))}
          className="h-8 rounded bg-white/15 hover:bg-white/25 text-xs font-bold cursor-pointer"
        >
          ▼
        </button>
        <button
          type="button"
          onClick={() => setDir((d) => (d === 'LEFT' ? d : 'RIGHT'))}
          className="h-8 rounded bg-white/15 hover:bg-white/25 text-xs font-bold cursor-pointer"
        >
          ▶
        </button>
      </div>
    </div>
  );
};
