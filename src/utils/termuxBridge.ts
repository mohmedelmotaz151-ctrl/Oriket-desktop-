import { registerPlugin } from '@capacitor/core';

export interface TermuxCommandResult {
  stdout: string;
  stderr: string;
  exitCode: number;
  errorCode?: number;
  errorMessage?: string;
}

interface TermuxBridgePlugin {
  checkInstalled(): Promise<{ installed: boolean }>;
  openTermux(): Promise<void>;
  runCommand(options: { command: string; workingDirectory?: string }): Promise<TermuxCommandResult>;
}

export const TermuxBridge = registerPlugin<TermuxBridgePlugin>('TermuxBridge');
