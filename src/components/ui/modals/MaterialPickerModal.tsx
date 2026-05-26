import { useState, useEffect } from 'react';
import { useItemsStore } from '../../../stores/itemsStore';
import { useDocumentStore } from '../../../stores/documentStore';
import { ITEM_CATEGORIES } from '../../../lib/itemsStorage';
import type { Item } from '../../../lib/itemsStorage';
import type { TableContent, TableRow } from '../../../types/blocks';
import { formatBRL } from '../../../lib/budget';

interface Props {
  data: { docId: string; blockId: string };
  onClose: () => void;
}

export function MaterialPickerModal({ data, onClose }: Props) {
  const { docId, blockId } = data;

  const items             = useItemsStore((s) => s.items);
  const getByCategory     = useItemsStore((s) => s.getItemsByCategory);
  const searchFn          = useItemsStore((s) => s.searchItems);

  const documents   = useDocumentStore((s) => s.documents);
  const updateBlock = useDocumentStore((s) => s.updateBlock);

  const [search,   setSearch]   = useState('');
  const [category, setCategory] = useState<string>(ITEM_CATEGORIES[0]);
  const [added,    setAdded]    = useState<Set<string>>(new Set());
  const [visible,  setVisible]  = useState(false);

  // Animação de entrada
  useEffect(() => { requestAnimationFrame(() => setVisible(true)); }, []);

  // Filtra as categorias que existem no store
  const categories = ITEM_CATEGORIES.filter((c) =>
    items.some((it) => it.category === c),
  );

  const displayed: Item[] = search.trim()
    ? searchFn(search)
    : getByCategory(category);

  const handleAdd = (item: Item) => {
    const doc   = documents[docId];
    const block = doc?.blocks.find((b) => b.id === blockId);
    if (!block || !('rows' in block.content)) return;

    const content    = block.content as TableContent;
    const nextIndex  = content.rows.length + 1;

    const newRow: TableRow = {
      cells: [
        String(nextIndex),   // Item
        item.name,           // Descrição
        '1',                 // Qtd.
        item.unit,           // Un.
        String(item.unitPrice), // Valor Unit.
      ],
    };

    updateBlock(docId, blockId, { rows: [...content.rows, newRow] });
    setAdded((prev) => new Set([...prev, item.id]));
  };

  return (
    <div
      className={`w-full max-w-2xl mx-4 bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl max-h-[82vh] flex flex-col
        transition-all duration-200 ease-out
        ${visible ? 'opacity-100 scale-100' : 'opacity-0 scale-95'}`}
    >
      {/* ── Header ── */}
      <div className="px-6 pt-5 pb-4 border-b border-zinc-800 shrink-0 flex items-center justify-between">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-0.5">
            Dicionário de Materiais
          </p>
          <h2 className="text-sm font-semibold text-zinc-100">
            Adicionar itens à tabela
          </h2>
        </div>
        <button
          onClick={onClose}
          className="w-7 h-7 flex items-center justify-center rounded text-zinc-500 hover:text-zinc-200 hover:bg-zinc-800 transition-colors text-sm"
        >
          ✕
        </button>
      </div>

      {/* ── Busca ── */}
      <div className="px-4 py-3 border-b border-zinc-800 shrink-0">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar materiais..."
          autoFocus
          className="w-full px-3 py-1.5 text-xs bg-zinc-800 border border-zinc-700 rounded text-zinc-200 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors"
        />
      </div>

      {/* ── Body ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* Sidebar de categorias — oculta durante busca */}
        {!search.trim() && (
          <div className="w-44 shrink-0 border-r border-zinc-800 overflow-y-auto py-1">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setCategory(cat)}
                className={`w-full text-left px-3 py-2 text-xs transition-colors ${
                  category === cat
                    ? 'bg-zinc-700 text-zinc-100 font-medium'
                    : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        )}

        {/* Lista de itens */}
        <div className="flex-1 overflow-y-auto">
          {displayed.length === 0 && (
            <p className="text-xs text-zinc-600 text-center mt-8 px-4">
              {search.trim() ? `Nenhum resultado para "${search}"` : 'Nenhum item nesta categoria.'}
            </p>
          )}

          {displayed.map((item) => {
            const isAdded = added.has(item.id);
            return (
              <button
                key={item.id}
                onClick={() => handleAdd(item)}
                className="w-full flex items-center justify-between px-4 py-3 border-b border-zinc-800/60 hover:bg-zinc-800/70 transition-colors text-left group"
              >
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-medium text-zinc-200 group-hover:text-white truncate">
                    {item.name}
                  </p>
                  {item.description && (
                    <p className="text-[10px] text-zinc-600 mt-0.5 truncate">{item.description}</p>
                  )}
                </div>

                <div className="flex items-center gap-3 shrink-0 ml-4">
                  <span className="text-[10px] text-zinc-500 uppercase">{item.unit}</span>
                  <span className="text-xs font-mono text-zinc-300">
                    R$&nbsp;{formatBRL(item.unitPrice)}
                  </span>
                  {isAdded
                    ? <span className="text-xs text-green-500 font-bold w-4">✓</span>
                    : <span className="text-xs text-zinc-700 group-hover:text-zinc-400 w-4">+</span>
                  }
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Footer ── */}
      <div className="px-6 py-3 border-t border-zinc-800 shrink-0 flex items-center justify-between">
        <p className="text-[10px] text-zinc-600">
          {added.size > 0 ? `${added.size} item(s) adicionado(s)` : 'Clique em um item para adicioná-lo'}
        </p>
        <button
          onClick={onClose}
          className="px-4 py-1.5 text-xs font-medium bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors"
        >
          Concluir
        </button>
      </div>
    </div>
  );
}
