import { useState, useEffect } from 'react';
import { useDocumentStore } from '../../stores/documentStore';
import { useAppStore } from '../../stores/appStore';
import { useSettingsStore } from '../../stores/settingsStore';

// ─── Relógio dinâmico ────────────────────────────────────────────────────────

function LiveClock() {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 10_000);
    return () => clearInterval(id);
  }, []);

  const date = now.toLocaleDateString('pt-BR', { day: 'numeric', month: 'long' });
  const time = now.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

  return (
    <span className="text-xs font-medium tabular-nums text-teal-600 dark:text-teal-400">
      {date}, {time}
    </span>
  );
}

// ─── Ícone de engrenagem ─────────────────────────────────────────────────────

function GearIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  );
}

// ─── Navbar ──────────────────────────────────────────────────────────────────

export function Navbar() {
  const openModal      = useDocumentStore((s) => s.openModal);
  const toggleSettings = useAppStore((s) => s.toggleSettings);
  const userName       = useSettingsStore((s) => s.userName);

  return (
    <header className="h-12 shrink-0 flex items-center justify-between px-6 bg-white dark:bg-zinc-900 border-b border-zinc-200 dark:border-zinc-800 z-10">

      {/* Esquerda: nome do usuário + relógio */}
      <div className="flex items-center gap-3">
        {userName && (
          <span className="text-xs font-medium text-zinc-400 dark:text-zinc-500">
            {userName}
          </span>
        )}
        <LiveClock />
      </div>

      {/* Direita: engrenagem + novo documento */}
      <div className="flex items-center gap-2">
        <button
          onClick={toggleSettings}
          title="Configurações"
          className="w-8 h-8 flex items-center justify-center rounded text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
        >
          <GearIcon />
        </button>
        <button
          onClick={() => openModal({ type: 'create-doc', data: null })}
          className="flex items-center gap-1.5 px-4 py-2 text-xs font-semibold bg-zinc-900 dark:bg-zinc-100 text-white dark:text-zinc-900 rounded hover:bg-zinc-700 dark:hover:bg-zinc-300 transition-colors tracking-widest uppercase"
        >
          + Novo Documento
        </button>
      </div>
    </header>
  );
}
