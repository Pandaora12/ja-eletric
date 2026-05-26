import { useState, useEffect } from 'react';
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

export function ImageBlock({ block, onChange }: Props) {
  const { path, alt } = block.content;
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!path) {
      setObjectUrl(null);
      setError(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(false);

    readFile(path)
      .then((data) => {
        if (cancelled) return;
        const blob = new Blob([data], { type: getMime(path) });
        const url = URL.createObjectURL(blob);
        setObjectUrl((prev) => {
          if (prev) URL.revokeObjectURL(prev);
          return url;
        });
        setLoading(false);
      })
      .catch(() => {
        if (!cancelled) { setError(true); setLoading(false); }
      });

    return () => { cancelled = true; };
  }, [path]);

  // Revoga o blob URL ao desmontar
  useEffect(() => {
    return () => {
      setObjectUrl((prev) => { if (prev) URL.revokeObjectURL(prev); return null; });
    };
  }, []);

  const pickImage = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'Imagem', extensions: IMAGE_EXTENSIONS }],
    });
    if (typeof selected === 'string') {
      onChange({ path: selected, alt });
    }
  };

  // Estado vazio — nenhuma imagem selecionada
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

  // Carregando
  if (loading) {
    return (
      <div className="w-full flex items-center justify-center py-10 rounded-lg bg-zinc-50 dark:bg-zinc-800/40">
        <span className="text-xs text-zinc-400 dark:text-zinc-500">Carregando imagem...</span>
      </div>
    );
  }

  // Erro ao carregar
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

  // Imagem carregada
  return (
    <div className="group/img relative">
      {objectUrl && (
        <img
          src={objectUrl}
          alt={alt}
          className="w-full rounded-lg object-contain max-h-[480px]"
        />
      )}

      {/* Overlay com ações — aparece no hover */}
      <div className="absolute inset-0 flex items-end justify-end p-3 opacity-0 group-hover/img:opacity-100 transition-opacity">
        <div className="flex gap-2">
          <input
            type="text"
            value={alt}
            onChange={(e) => onChange({ path, alt: e.target.value })}
            placeholder="Legenda da imagem..."
            className="text-xs px-2 py-1 bg-black/60 text-white placeholder:text-white/50 rounded outline-none backdrop-blur-sm w-40"
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
    </div>
  );
}
