import {
  readTextFile,
  writeTextFile,
  mkdir,
  exists,
  BaseDirectory,
} from '@tauri-apps/plugin-fs';

const FILE = 'settings.json';
const BASE = BaseDirectory.AppData;

export interface Settings {
  userName: string;
  companyName: string;
  primaryColor: string;
  logoPath: string;
  defaultSignatureName: string;
  defaultSignatureRole: string;
}

export const DEFAULT_SETTINGS: Settings = {
  userName: '',
  companyName: '',
  primaryColor: '#0ea5e9',
  logoPath: '',
  defaultSignatureName: '',
  defaultSignatureRole: '',
};

export async function loadSettings(): Promise<Settings | null> {
  try {
    const fileExists = await exists(FILE, { baseDir: BASE });
    if (!fileExists) return null;
    const raw = await readTextFile(FILE, { baseDir: BASE });
    return { ...DEFAULT_SETTINGS, ...JSON.parse(raw) } as Settings;
  } catch {
    return null;
  }
}

export async function saveSettings(settings: Settings): Promise<void> {
  try {
    await mkdir('', { baseDir: BASE, recursive: true }).catch(() => {});
    await writeTextFile(FILE, JSON.stringify(settings, null, 2), { baseDir: BASE });
  } catch (err) {
    console.error('[settingsStorage] save failed:', err);
  }
}
