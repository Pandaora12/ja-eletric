import { useState, useEffect, useRef, useCallback } from 'react';
import { readFile } from '@tauri-apps/plugin-fs';
import { open } from '@tauri-apps/plugin-dialog';
import type { Block, ImageContent } from '../../types/blocks';

interface Props {
  block: Block & { content: ImageContent };
  onChange: (content: Partial<ImageContent>) => void;
}

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'];

const MIME: Record<string, string> = {
  png: 'image/png', gif: 'image/gif',
  webp: 'image/webp', svg: 'image/svg+xml', bmp: 'image/bmp',
};

function getMime(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() ?? '';
  return MIME[ext] ?? 'image/jpeg';
}

// Snap para larguras fixas predefinidas
const WIDTH_SNAPS = [25, 33, 50, 66, 75, 100];

function snapWidth(pct: number): number {
  return WIDTH_SNAPS.reduce((prev, curr) =>
    Math.abs(curr - pct) < Math.abs(prev - pct) ? curr : prev,
  );
}

// ─── Componente principal ─────────────────────────────────────────────────────

export function ImageBlock({ block, onChange }: Props) {
  const { path, alt, width = 100 } = block.content;
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState(false);
  const containerRef              = useRef<HTMLDivElement>(null);

  // ── Carregamento da imagem ─────────────────────────────────────────────────

  useEffect(() => {
    if (!path) { setObjectUrl(null); setError(false); return; }

    let cancelled = false;
    setLoading(true);
    setError(false);

    readFile(path)
      .then((data) => {
        if (cancelled) return;
        const blob = new Blob([data], { type: getMime(path) });
        const url  = URL.createObjectURL(blob);
        setObjectUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return url; });
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) { setError(true); setLoading(false); }
      });

    return () => { cancelled = true; };
  }, [path]);

  useEffect(() => {
    return () => {
      setObjectUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return null; });
    };
  }, []);

  // ── Resize handle ──────────────────────────────────────────────────────────

  const handleResizeStart = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    const startX      = e.clientX;
    const parentEl    = containerRef.current?.closest('[data-block-container]') as HTMLElement | null;
    const parentWidth = parentEl?.offsetWidth ?? containerRef.current?.parentElement?.offsetWidth ?? 800;
    const startWidthPx = (width / 100) * parentWidth;

    const onMove = (ev: MouseEvent) => {
      const delta     = ev.clientX - startX;
      const newPx     = Math.max(80, startWidthPx + delta);
      const newPct    = Math.min(100, (newPx / parentWidth) * 100);
      const snapped   = snapWidth(newPct);
      onChange({ width: snapped });
    };

    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }, [width, onChange]);

  // ── Picker ────────────────────────────────────────────────────────────────

  const pickImage = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'Imagem', extensions: IMAGE_EXTENSIONS }],
    });
    if (typeof selected === 'string') onChange({ path: selected, alt });
  };

  // ── Estado vazio ──────────────────────────────────────────────────────────

  if (!path) {
    return (
      <button
        onClick={pickImage}
        className="w-full flex flex-col items-center justify-center gap-2 py-10 border-2 border-dashed border-zinc-200 dark:border-zinc-700 rounded-lg text-zinc-400 dark:text-zinc-600 hover:border-zinc-400 dark:hover:border-zinc-500 hover:text-zinc-500 dark:hover:text-zinc-400 transition-colors"
      >
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
        <span className="text-xs font-medium">Clique para adicionar uma foto</span>
        <span className="text-[10px]">JPG, PNG, WEBP, SVG</span>
      </button>
    );
  }

  if (loading) {
    return (
      <div className="w-full flex items-center justify-center py-10 rounded-lg bg-zinc-50 dark:bg-zinc-800/40">
        <span className="text-xs text-zinc-400 dark:text-zinc-500">Carregando imagem...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="w-full flex flex-col items-center gap-2 py-8 rounded-lg bg-red-50/60 dark:bg-red-950/20 border border-red-200/60 dark:border-red-900/30">
        <p className="text-xs text-red-600 dark:text-red-400 font-medium">Imagem não encontrada</p>
        <p className="text-[10px] text-zinc-400 truncate max-w-xs">{path}</p>
        <button
          onClick={pickImage}
          className="text-xs px-3 py-1.5 rounded border border-zinc-300 dark:border-zinc-600 text-zinc-600 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-700 transition-colors mt-1"
        >
          Escolher outra imagem
        </button>
      </div>
    );
  }

  // ── Imagem carregada ──────────────────────────────────────────────────────

  return (
    <div ref={containerRef} className="group/img relative inline-block" style={{ width: `${width}%` }}>
      {objectUrl && (
        <img
          src={objectUrl}
          alt={alt}
          className="w-full rounded-lg object-contain max-h-[480px] block"
          draggable={false}
        />
      )}

      {/* Overlay de ações */}
      <div className="absolute inset-0 flex items-end justify-end p-3 opacity-0 group-hover/img:opacity-100 transition-opacity pointer-events-none">
        <div className="flex gap-2 pointer-events-auto">
          <input
            type="text"
            value={alt}
            onChange={(e) => onChange({ path, alt: e.target.value, width })}
            placeholder="Legenda..."
            className="text-xs px-2 py-1 bg-black/60 text-white placeholder:text-white/50 rounded outline-none backdrop-blur-sm w-32"
            onClick={(e) => e.stopPropagation()}
          />
          <button
            onClick={pickImage}
            className="text-xs px-2.5 py-1 bg-black/60 text-white rounded hover:bg-black/80 transition-colors backdrop-blur-sm whitespace-nowrap"
          >
            Trocar foto
          </button>
        </div>
      </div>

      {/* Indicador de largura */}
      <div className="absolute top-1 left-1 opacity-0 group-hover/img:opacity-100 transition-opacity">
        <span className="text-[9px] px-1.5 py-0.5 bg-black/60 text-white rounded backdrop-blur-sm">
          {width}%
        </span>
      </div>

      {/* Handle de resize direita */}
      <div
        onMouseDown={handleResizeStart}
        className="absolute right-0 top-0 bottom-0 w-3 flex items-center justify-center opacity-0 group-hover/img:opacity-100 transition-opacity cursor-ew-resize"
        title="Arrastar para redimensionar"
      >
        <div className="h-12 w-1 bg-white/70 rounded-full shadow" />
      </div>
    </div>
  );
}
