import React, { useRef, useState } from 'react';
import { getWorkspaceFiles } from '../utils/virtualFs';

export const CommandApp: React.FC = () => {
  const [lines, setLines] = useState<string[]>([
    'Oriket Desktop Command Shell [Version 1.0.0]',
    '(c) Oriket Systems. Type "help" for available commands.',
    '',
  ]);
  const [input, setInput] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  const runCommand = (raw: string) => {
    const cmd = raw.trim();
    const lower = cmd.toLowerCase();
    const output: string[] = [`C:\\Users\\Oriket> ${cmd}`];

    if (!cmd) {
      setLines((prev) => [...prev, ...output]);
      return;
    }

    if (lower === 'help') {
      output.push(
        'HELP     Show available commands',
        'DIR      List workspace folders and files on C:',
        'CLS      Clear screen',
        'VER      Show OS version',
        'DATE     Show current date',
        'TIME     Show current time',
        'WHOAMI   Show current user',
        'ECHO     Print text (e.g. echo hello)',
        'CALC     Quick calculation (e.g. calc 12+8)'
      );
    } else if (lower === 'cls') {
      setLines([]);
      return;
    } else if (lower === 'ver') {
      output.push('Oriket Desktop Web [Version 1.0.0.26000]');
    } else if (lower === 'date') {
      output.push(`Current date: ${new Date().toLocaleDateString('en-US')}`);
    } else if (lower === 'time') {
      output.push(`Current time: ${new Date().toLocaleTimeString('en-US')}`);
    } else if (lower === 'whoami') {
      output.push('oriket\\desktop-user');
    } else if (lower === 'dir') {
      const files = getWorkspaceFiles();
      output.push(' Volume in drive C is ORIKET_SYS');
      output.push(' Directory of C:\\');
      files.forEach((f) => {
        const tag = f.isDirectory ? '<DIR>' : `${f.sizeBytes} B`;
        output.push(`  ${tag.padEnd(10)} ${f.parentPath}\\${f.name}`);
      });
    } else if (lower.startsWith('echo ')) {
      output.push(cmd.slice(5));
    } else if (lower.startsWith('calc ')) {
      const expr = cmd.slice(5).replace(/\s+/g, '');
      if (/^[0-9+\-*/().]+$/.test(expr)) {
        try {
          // Simple arithmetic using Function restricted to validated chars
          const fn = new Function(`return (${expr});`) as () => number;
          const v = fn();
          output.push(`= ${v}`);
        } catch {
          output.push('Calculation error');
        }
      } else {
        output.push('Invalid characters in expression');
      }
    } else {
      output.push(`'${cmd}' is not recognized as an internal or external command.`);
    }

    setLines((prev) => [...prev, ...output, '']);
    setTimeout(() => endRef.current?.scrollIntoView({ behavior: 'smooth' }), 20);
  };

  return (
    <div
      className="flex-1 min-h-0 flex flex-col bg-[#0C0F14] text-[#D8E2F0] font-mono text-xs rounded-lg p-3 overflow-hidden"
      dir="ltr"
    >
      <div className="flex-1 min-h-0 overflow-y-auto whitespace-pre-wrap leading-relaxed">
        {lines.map((l, i) => (
          <div key={i}>{l}</div>
        ))}
        <div ref={endRef} />
      </div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          runCommand(input);
          setInput('');
        }}
        className="flex items-center gap-2 pt-2 border-t border-white/10"
      >
        <span className="text-emerald-400 shrink-0">C:\Users\Oriket&gt;</span>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Enter command..."
          className="flex-1 bg-transparent text-white focus:outline-none"
          autoFocus
        />
      </form>
    </div>
  );
};
