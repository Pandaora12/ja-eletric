import { useState, useEffect } from 'react';
import { useDocumentStore } from '../../../stores/documentStore';

interface Props {
  data: { docId: string; docTitle: string };
  onClose: () => void;
}

export function DeleteDocModal({ data, onClose }: Props) {
  const deleteDocument = useDocumentStore((s) => s.deleteDocument);
  const [visible, setVisible] = useState(false);
  useEffect(() => { requestAnimationFrame(() => setVisible(true)); }, []);

  const handleConfirm = () => {
    deleteDocument(data.docId);
    onClose();
  };

  return (
    <div className={`w-full max-w-sm mx-4 bg-zinc-900 border border-zinc-800 rounded shadow-2xl transition-all duration-200 ease-out ${visible ? 'opacity-100 scale-100' : 'opacity-0 scale-95'}`}>
      {/* Header */}
      <div className="px-6 pt-6 pb-4">
        <p className="text-[11px] font-semibold uppercase tracking-widest text-zinc-500 mb-3">
          Excluir documento
        </p>
        <h2 className="text-sm font-medium text-zinc-100 leading-snug">
          Tem certeza que deseja excluir{' '}
          <span className="text-white font-semibold">"{data.docTitle}"</span>?
        </h2>
        <p className="text-xs text-zinc-500 mt-2">
          Esta ação é permanente e não pode ser desfeita.
        </p>
      </div>

      {/* Divider */}
      <div className="border-t border-zinc-800 mx-6" />

      {/* Actions */}
      <div className="flex items-center justify-end gap-2 px-6 py-4">
        <button
          onClick={onClose}
          className="px-4 py-1.5 text-xs font-medium text-zinc-400 border border-zinc-700 rounded hover:bg-zinc-800 hover:text-zinc-200 transition-colors"
        >
          Cancelar
        </button>
        <button
          onClick={handleConfirm}
          className="px-4 py-1.5 text-xs font-medium bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
        >
          Excluir
        </button>
      </div>
    </div>
  );
}
