import {
  readTextFile,
  writeTextFile,
  mkdir,
  exists,
  BaseDirectory,
} from '@tauri-apps/plugin-fs';
import type { Document } from '../types/document';

// Arquivo salvo em: %APPDATA%\com.user.ja-eletric\documents.json (Windows)
//                   ~/Library/Application Support/com.user.ja-eletric/documents.json (macOS)
const FILE = 'documents.json';
const BASE = BaseDirectory.AppData;

export interface PersistedState {
  documents: Record<string, Document>;
  activeDocumentId: string | null;
}

export async function loadState(): Promise<PersistedState | null> {
  try {
    const fileExists = await exists(FILE, { baseDir: BASE });
    if (!fileExists) return null;

    const raw = await readTextFile(FILE, { baseDir: BASE });
    return JSON.parse(raw) as PersistedState;
  } catch (err) {
    console.error('[storage] loadState failed:', err);
    return null;
  }
}

export async function saveState(state: PersistedState): Promise<void> {
  try {
    // Garante que o diretório AppData existe (Tauri cria na inicialização,
    // mas recursive:true é inofensivo caso já exista)
    await mkdir('', { baseDir: BASE, recursive: true }).catch(() => {});

    await writeTextFile(FILE, JSON.stringify(state, null, 2), { baseDir: BASE });
  } catch (err) {
    console.error('[storage] saveState failed:', err);
  }
}
