import React, { useEffect, useRef, useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { TermuxBridge } from '../utils/termuxBridge';
import { getWorkspaceFiles } from '../utils/virtualFs';

const LegacyCommandApp: React.FC = () => {
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


const TermuxTerminal: React.FC = () => {
  const [lines, setLines] = useState<string[]>([
    'Oriket Linux Terminal [Android / Termux]',
    'Checking Termux connection...',
    '',
  ]);
  const [input, setInput] = useState('');
  const [installed, setInstalled] = useState<boolean | null>(null);
  const [running, setRunning] = useState(false);
  const [cwd, setCwd] = useState('~/');
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    TermuxBridge.checkInstalled().then(({ installed: found }) => {
      setInstalled(found);
      setLines((prev) => [...prev, found
        ? 'Termux detected. Type "setup" for one-time permission instructions.'
        : 'Termux is not installed. Install it from the official F-Droid page.', '']);
    }).catch(() => {
      setInstalled(false);
      setLines((prev) => [...prev, 'Could not detect Termux. Type "setup" for help.', '']);
    });
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines, running]);

  const append = (values: string[]) => setLines((prev) => [...prev, ...values, '']);

  const showSetup = () => append([
    'ONE-TIME SETUP',
    '1. Install Termux from F-Droid: https://f-droid.org/packages/com.termux/',
    '2. Open Termux once and wait for setup to finish.',
    '3. In Termux, edit: nano ~/.termux/termux.properties',
    '4. Add this line, save, then restart Termux: allow-external-apps=true',
    '5. Android Settings > Apps > Oriket Desktop > Permissions > Additional permissions.',
    '6. Allow "Run commands in Termux environment".',
    '7. In Termux run: pkg update && pkg install nmap',
    'Guide: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent',
  ]);

  const execute = async (command: string, shown = command) => {
    if (installed !== true) {
      append(['Termux is not ready. Type "setup" for instructions.']);
      return;
    }
    setRunning(true);
    append(['$ ' + cwd + ' ' + shown]);
    try {
      const result = await TermuxBridge.runCommand({ command, workingDirectory: cwd });
      const stdout = result.stdout || '';
      const cwdMatch = stdout.match(/\\n__ORKIT_CWD__(.+)\\s*$/);
      const exitMatch = stdout.match(/\\n__ORKIT_EXIT__(\\d+)\\n/);
      const clean = stdout.replace(/\\n__ORKIT_EXIT__\\d+\\n__ORKIT_CWD__.+\\s*$/, '').trimEnd();
      if (cwdMatch && cwdMatch[1]) setCwd(cwdMatch[1].trim());
      const output = [];
      if (clean) output.push(...clean.split('\\n'));
      if (result.stderr) output.push(...result.stderr.trimEnd().split('\\n'));
      if (result.errorMessage) output.push('Termux error: ' + result.errorMessage);
      output.push('[exit code: ' + (exitMatch ? Number(exitMatch[1]) : result.exitCode) + ']');
      append(output);
    } catch (error) {
      append([
        error instanceof Error ? error.message : 'Command failed.',
        'Check allow-external-apps=true and Oriket permission. Type "setup".',
      ]);
    } finally {
      setRunning(false);
    }
  };

  const run = async (raw: string) => {
    const command = raw.trim();
    if (!command || running) return;
    setInput('');
    if (command === 'clear' || command === 'cls') { setLines([]); return; }
    if (command === 'help') {
      append([
        'Linux commands run in the real Termux environment.',
        'Basic: pwd, whoami, ls, cd, date, uname -a',
        'Packages: pkg update && pkg upgrade',
        'Install Nmap: pkg install nmap',
        'Host discovery: nmap -sn 192.168.1.0/24',
        'Service scan: nmap -sV 192.168.1.1',
        'Special: setup, open-termux, install-nmap, clear',
        'Only scan networks and devices you own or are authorized to test.',
      ]);
      return;
    }
    if (command === 'setup') { showSetup(); return; }
    if (command === 'open-termux') {
      try { await TermuxBridge.openTermux(); append(['Opening Termux...']); }
      catch (error) { append([error instanceof Error ? error.message : 'Could not open Termux.']); }
      return;
    }
    if (command === 'install-nmap') { await execute('pkg update -y && pkg install nmap -y'); return; }
    if (command === 'pwd') { append([cwd.replace(/^~/, '/data/data/com.termux/files/home')]); return; }
    if (command === 'cd' || command.startsWith('cd ')) {
      const target = command.slice(2).trim() || '~';
      await execute('cd ' + target + '; pwd', command);
      return;
    }
    if (/(^|[;&|\\s])(rm\\s+-rf|mkfs|dd\\s+if=|wipefs|reboot|shutdown)(\\s|$)/i.test(command)) {
      if (!window.confirm('This command may delete data or change device state. Run it anyway?')) return;
    }
    if (/\\bnmap\\b/i.test(command)) {
      if (!window.confirm('Only scan devices or networks you own or are explicitly authorized to test. Continue?')) return;
    }
    await execute(command);
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-[#080D14] text-[#D8E2F0] font-mono text-xs rounded-lg overflow-hidden" dir="ltr">
      <div className="flex items-center justify-between gap-2 px-3 py-2 bg-[#111A27] border-b border-white/10">
        <div className="flex items-center gap-2 min-w-0">
          <span className="w-2 h-2 rounded-full bg-emerald-400 shrink-0" />
          <span className="font-semibold text-white truncate">ORIKET LINUX TERMINAL</span>
          <span className="text-[10px] text-slate-400">TERMUX</span>
        </div>
        <span className={'text-[10px] ' + (installed ? 'text-emerald-300' : 'text-amber-300')}>
          {installed ? 'CONNECTED' : installed === false ? 'SETUP REQUIRED' : 'CHECKING'}
        </span>
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto whitespace-pre-wrap break-words leading-relaxed p-3">
        {lines.map((line, index) => <div key={index}>{line}</div>)}
        {running && <div className="text-amber-300">Running command...</div>}
        <div ref={endRef} />
      </div>
      <div className="flex flex-wrap gap-2 px-3 py-2 border-t border-white/10 bg-[#101722]">
        <button type="button" onClick={showSetup} className="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600">Setup</button>
        <button type="button" onClick={() => run('install-nmap')} disabled={!installed || running} className="px-2 py-1 rounded bg-sky-800 hover:bg-sky-700 disabled:opacity-40">Install Nmap</button>
        <button type="button" onClick={() => run('nmap --version')} disabled={!installed || running} className="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 disabled:opacity-40">Nmap version</button>
        <button type="button" onClick={() => run('open-termux')} disabled={!installed} className="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 disabled:opacity-40">Open Termux</button>
      </div>
      <form onSubmit={(event) => { event.preventDefault(); void run(input); }} className="flex items-center gap-2 px-3 py-3 border-t border-white/10">
        <span className="text-emerald-400 shrink-0">$&nbsp;</span>
        <input type="text" value={input} onChange={(event) => setInput(event.target.value)} placeholder={installed ? 'Enter a Linux command...' : 'Type setup for instructions...'} disabled={running} className="flex-1 min-w-0 bg-transparent text-white focus:outline-none disabled:opacity-50" autoComplete="off" autoCapitalize="off" spellCheck={false} />
        <button type="submit" disabled={running || !input.trim()} className="px-3 py-1.5 rounded bg-[#B4232D] text-white font-semibold disabled:opacity-40">Run</button>
      </form>
      <div className="px-3 pb-2 text-[10px] text-slate-500">
        Commands execute with your Termux user permissions. Scan only authorized networks.
      </div>
    </div>
  );
};

export const CommandApp: React.FC = () =>
  Capacitor.isNativePlatform() ? <TermuxTerminal /> : <LegacyCommandApp />;
