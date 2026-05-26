import { useState } from 'react';
import { useDocumentStore } from '../../../stores/documentStore';

interface Props {
  data: { docId: string; currentTitle: string };
  onClose: () => void;
}

export function RenameDocModal({ data, onClose }: Props) {
  const updateDocumentTitle = useDocumentStore((s) => s.updateDocumentTitle);
  const [title, setTitle] = useState(data.currentTitle);

  const handleConfirm = () => {
    const trimmed = title.trim();
    if (!trimmed) return;
    updateDocumentTitle(data.docId, trimmed);
    onClose();
  };

  return (
    <div className="w-full max-w-sm mx-4 bg-zinc-900 border border-zinc-800 rounded shadow-2xl">
      {/* Header */}
      <div className="px-6 pt-6 pb-4">
        <p className="text-[11px] font-semibold uppercase tracking-widest text-zinc-500 mb-3">
          Renomear documento
        </p>
        <input
          type="text"
          value={title}
          autoFocus
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleConfirm();
          }}
          className="w-full px-3 py-2 text-sm bg-zinc-950 border border-zinc-700 rounded text-zinc-100 outline-none focus:border-zinc-500 transition-colors"
        />
      </div>

      {/* Divider */}
      <div className="border-t border-zinc-800 mx-6" />

      {/* Ações */}
      <div className="flex items-center justify-end gap-2 px-6 py-4">
        <button
          onClick={onClose}
          className="px-4 py-1.5 text-xs font-medium text-zinc-400 border border-zinc-700 rounded hover:bg-zinc-800 hover:text-zinc-200 transition-colors"
        >
          Cancelar
        </button>
        <button
          onClick={handleConfirm}
          disabled={!title.trim()}
          className="px-4 py-1.5 text-xs font-medium bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        >
          Renomear
        </button>
      </div>
    </div>
  );
}
