import { useState, useEffect } from 'react';
import { readFile } from '@tauri-apps/plugin-fs';
import { useSettingsStore } from '../../stores/settingsStore';
import type { Block, CompanyHeaderContent } from '../../types/blocks';

interface Props {
  block: Block & { content: CompanyHeaderContent };
}

const MIME: Record<string, string> = {
  png: 'image/png', svg: 'image/svg+xml',
  gif: 'image/gif', webp: 'image/webp', bmp: 'image/bmp',
};

export function CompanyHeaderBlock({ block: _ }: Props) {
  const companyName  = useSettingsStore((s) => s.companyName);
  const primaryColor = useSettingsStore((s) => s.primaryColor);
  const logoPath     = useSettingsStore((s) => s.logoPath);

  const [logoUrl, setLogoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!logoPath) { setLogoUrl(null); return; }
    let cancelled = false;

    readFile(logoPath)
      .then((data) => {
        if (cancelled) return;
        const ext = logoPath.split('.').pop()?.toLowerCase() ?? 'png';
        const blob = new Blob([data], { type: MIME[ext] ?? 'image/jpeg' });
        const url = URL.createObjectURL(blob);
        setLogoUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return url; });
      })
      .catch(() => { if (!cancelled) setLogoUrl(null); });

    return () => { cancelled = true; };
  }, [logoPath]);

  useEffect(() => {
    return () => setLogoUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return null; });
  }, []);

  const color = primaryColor || '#0ea5e9';

  if (!companyName && !logoPath) {
    return (
      <div className="py-3 px-3 border-l-2 border-zinc-200 dark:border-zinc-700 text-zinc-400 dark:text-zinc-600 text-xs italic">
        Configure suas informações em{' '}
        <span className="not-italic font-medium text-zinc-500 dark:text-zinc-500">⚙ Configurações</span>
        {' '}para personalizar este cabeçalho.
      </div>
    );
  }

  return (
    <div
      className="flex items-center justify-between pb-4 border-b-2"
      style={{ borderColor: color }}
    >
      {/* Logo à esquerda */}
      <div className="flex-shrink-0">
        {logoUrl ? (
          <img src={logoUrl} alt={companyName} className="h-14 w-auto object-contain" />
        ) : (
          <div className="h-12 w-12 rounded bg-zinc-100 dark:bg-zinc-800 flex items-center justify-center text-[9px] font-semibold uppercase tracking-widest text-zinc-400 dark:text-zinc-600">
            Logo
          </div>
        )}
      </div>

      {/* Nome da empresa à direita */}
      <div className="text-right">
        <p className="text-xl font-bold tracking-tight leading-tight" style={{ color }}>
          {companyName}
        </p>
      </div>
    </div>
  );
}
