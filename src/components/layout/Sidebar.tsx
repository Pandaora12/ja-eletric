import { useState } from 'react';
import { useDocumentStore } from '../../stores/documentStore';
import { DOCUMENT_TYPE_LABELS } from '../../types/document';

function formatCreatedAt(ts: number): string {
  return new Date(ts).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

function shortPath(fullPath: string): string {
  return fullPath.split(/[\\/]/).pop() ?? fullPath;
}

export function Sidebar() {
  const { documents, activeDocumentId, setActiveDocument, openModal } = useDocumentStore();
  const [search, setSearch] = useState('');

  const docList = Object.values(documents)
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .filter((d) => !search || d.title.toLowerCase().includes(search.toLowerCase()));

  return (
    <aside className="w-64 shrink-0 flex flex-col bg-zinc-900 dark:bg-zinc-950 text-zinc-100 border-r border-zinc-800 dark:border-zinc-900">

      {/* Marca */}
      <div className="px-4 py-4 border-b border-zinc-800 dark:border-zinc-900">
        <span className="text-sm font-bold tracking-widest text-zinc-100 uppercase">JA ELETRIC</span>
        <p className="text-xs text-zinc-500 mt-0.5">Documentação Técnica</p>
      </div>

      {/* Barra de pesquisa */}
      <div className="px-3 py-2.5 border-b border-zinc-800 dark:border-zinc-900">
        <div className="relative">
          <svg
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-zinc-600"
            width="12" height="12" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" strokeWidth="2" strokeLinecap="round"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar documentos..."
            className="w-full pl-7 pr-2 py-1.5 text-xs bg-zinc-800 border border-zinc-700 rounded text-zinc-300 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors"
          />
        </div>
      </div>

      {/* Cabeçalho da lista */}
      <div className="px-4 py-2 border-b border-zinc-800 dark:border-zinc-900">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-600">
          Documentos {docList.length > 0 && `(${docList.length})`}
        </p>
      </div>

      {/* Lista */}
      <div className="flex-1 overflow-y-auto px-2 py-2">
        {docList.length === 0 && (
          <p className="text-xs text-zinc-600 text-center mt-6 px-2 leading-relaxed">
            {search
              ? `Nenhum resultado para "${search}"`
              : 'Nenhum documento ainda.\nClique em + Novo Documento para começar.'}
          </p>
        )}

        <div className="flex flex-col gap-0.5">
          {docList.map((doc) => {
            const isActive = doc.id === activeDocumentId;
            return (
              <div
                key={doc.id}
                onClick={() => setActiveDocument(doc.id)}
                className={`group/item flex flex-col rounded px-2 py-2 cursor-pointer transition-colors ${
                  isActive
                    ? 'bg-zinc-700 dark:bg-zinc-800 text-zinc-100'
                    : 'text-zinc-400 hover:bg-zinc-800 dark:hover:bg-zinc-800/70 hover:text-zinc-200'
                }`}
              >
                {/* Linha superior: título + ações */}
                <div className="flex items-start gap-1">
                  {/* Título — sem truncagem */}
                  <p className="text-xs font-medium break-words flex-1 leading-snug">
                    {doc.title}
                  </p>

                  {/* Botões de ação */}
                  <div className="flex items-center gap-0.5 shrink-0 opacity-0 group-hover/item:opacity-100 transition-opacity mt-0.5">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        openModal({ type: 'rename-doc', data: { docId: doc.id, currentTitle: doc.title } });
                      }}
                      title="Renomear"
                      className="w-5 h-5 flex items-center justify-center rounded text-zinc-500 hover:text-zinc-200 hover:bg-zinc-600 transition-colors text-xs"
                    >
                      ✎
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        openModal({ type: 'delete-doc', data: { docId: doc.id, docTitle: doc.title } });
                      }}
                      title="Excluir"
                      className="w-5 h-5 flex items-center justify-center rounded text-red-500/70 hover:text-red-400 hover:bg-zinc-600 transition-colors text-xs"
                    >
                      ✕
                    </button>
                  </div>
                </div>

                {/* Linha inferior: tipo + status + data */}
                <div className="flex items-center gap-1.5 mt-1 flex-wrap">
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                    doc.status === 'completed' ? 'bg-emerald-500' : 'bg-amber-400'
                  }`} />
                  <span className="text-[10px] text-zinc-600">
                    {DOCUMENT_TYPE_LABELS[doc.type]}
                  </span>
                  <span className="text-[10px] text-zinc-700">
                    {formatCreatedAt(doc.createdAt)}
                  </span>
                </div>

                {/* Caminho salvo */}
                {doc.savedPath && (
                  <div className="mt-1 flex items-center gap-1">
                    <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-teal-600 shrink-0">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="7 10 12 15 17 10" />
                      <line x1="12" y1="15" x2="12" y2="3" />
                    </svg>
                    <span className="text-[9px] text-teal-700 dark:text-teal-600 break-all leading-tight">
                      {shortPath(doc.savedPath)}
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Rodapé */}
      <div className="px-4 py-3 border-t border-zinc-800 dark:border-zinc-900">
        <p className="text-[10px] text-zinc-700">v0.1.0 — alpha</p>
      </div>
    </aside>
  );
}
