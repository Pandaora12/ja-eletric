import { useState } from 'react';
import { useDocumentStore } from '../../../stores/documentStore';
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../../../types/document';

interface Props {
  onClose: () => void;
}

const DOC_OPTIONS: { type: DocumentType; desc: string }[] = [
  { type: 'memorial',      desc: 'Especificação técnica detalhada da instalação' },
  { type: 'proposal',      desc: 'Proposta para cliente com orçamento integrado' },
  { type: 'material-list', desc: 'Relação completa de materiais e quantidades' },
  { type: 'load-report',   desc: 'Análise e dimensionamento da carga elétrica' },
  { type: 'blank',         desc: 'Começa do zero, sem template pré-definido' },
];

export function CreateDocModal({ onClose }: Props) {
  const createDocument = useDocumentStore((s) => s.createDocument);

  const [selected, setSelected] = useState<DocumentType | null>(null);
  const [title, setTitle] = useState('');

  const handleSelect = (type: DocumentType) => {
    setSelected(type);
    // Preenche automaticamente o título com o nome do tipo, mas deixa editar
    if (!title || DOC_OPTIONS.some((o) => DOCUMENT_TYPE_LABELS[o.type] === title)) {
      setTitle(DOCUMENT_TYPE_LABELS[type]);
    }
  };

  const handleCreate = () => {
    if (!selected) return;
    const finalTitle = title.trim() || DOCUMENT_TYPE_LABELS[selected];
    createDocument(selected, finalTitle);
    onClose();
  };

  return (
    <div className="w-full max-w-lg mx-4 bg-zinc-900 border border-zinc-800 rounded-lg shadow-2xl">
      {/* Header */}
      <div className="px-6 pt-6 pb-5 border-b border-zinc-800">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-1">
          Criar novo
        </p>
        <h2 className="text-base font-semibold text-zinc-100">
          Novo Documento
        </h2>
      </div>

      {/* Opções de tipo */}
      <div className="px-6 pt-5 pb-4">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-3">
          Tipo de documento
        </p>
        <div className="grid grid-cols-2 gap-2">
          {DOC_OPTIONS.map(({ type, desc }) => (
            <button
              key={type}
              onClick={() => handleSelect(type)}
              className={`text-left px-4 py-3 rounded border transition-all ${
                selected === type
                  ? 'bg-zinc-800 border-zinc-500 text-zinc-100'
                  : 'bg-zinc-950/50 border-zinc-800 text-zinc-400 hover:border-zinc-600 hover:text-zinc-200 hover:bg-zinc-800/50'
              } ${type === 'blank' ? 'col-span-2' : ''}`}
            >
              <p className="text-xs font-semibold mb-0.5">{DOCUMENT_TYPE_LABELS[type]}</p>
              <p className="text-[10px] text-zinc-600 leading-snug">{desc}</p>
            </button>
          ))}
        </div>
      </div>

      {/* Campo de título */}
      <div className="px-6 pb-5">
        <label className="block text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-2">
          Título do projeto
        </label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && selected) handleCreate(); }}
          placeholder={selected ? DOCUMENT_TYPE_LABELS[selected] : 'Selecione um tipo acima...'}
          className="w-full px-3 py-2 text-sm bg-zinc-950 border border-zinc-700 rounded text-zinc-100 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors"
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
          onClick={handleCreate}
          disabled={!selected}
          className="px-5 py-1.5 text-xs font-semibold bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors disabled:opacity-30 disabled:cursor-not-allowed tracking-wide"
        >
          Criar →
        </button>
      </div>
    </div>
  );
}
