import { useState, useEffect } from 'react';
import { useItemsStore } from '../../../stores/itemsStore';
import { BT_CATEGORIES, AT_CATEGORIES } from '../../../lib/itemsStorage';
import type { Item } from '../../../lib/itemsStorage';
import { formatBRL } from '../../../lib/budget';

interface Props {
  onClose: () => void;
}

type Voltage = 'BT' | 'AT';

const VOLTAGE_LABEL: Record<Voltage, string> = {
  BT: '⚡ Baixa Tensão',
  AT: '🔴 Alta Tensão',
};

type EditState = {
  id: string;
  name: string;
  unit: string;
  unitPrice: number;
  description: string;
};

function buildEmpty(voltage: Voltage, category: string): Omit<Item, 'id'> {
  return { voltage, category, name: '', unit: 'un', unitPrice: 0, description: '' };
}

export function DictionaryModal({ onClose }: Props) {
  const { items, addItem, updateItem, removeItem } = useItemsStore();

  const [voltage,   setVoltage]   = useState<Voltage>('BT');
  const [category,  setCategory]  = useState<string>(BT_CATEGORIES[0]);
  const [search,    setSearch]    = useState('');
  const [editState, setEditState] = useState<EditState | null>(null);
  const [isNew,     setIsNew]     = useState(false);
  const [visible,   setVisible]   = useState(false);

  useEffect(() => { requestAnimationFrame(() => setVisible(true)); }, []);

  const categories = voltage === 'BT' ? [...BT_CATEGORIES] : [...AT_CATEGORIES];

  // Quando muda tensão, reseta categoria para o primeiro da nova lista
  const handleVoltageChange = (v: Voltage) => {
    setVoltage(v);
    setCategory(v === 'BT' ? BT_CATEGORIES[0] : AT_CATEGORIES[0]);
    setEditState(null);
    setIsNew(false);
  };

  const displayed: Item[] = search.trim()
    ? items.filter((it) => {
        const q = search.toLowerCase();
        return (
          it.name.toLowerCase().includes(q) ||
          it.category.toLowerCase().includes(q) ||
          it.description.toLowerCase().includes(q)
        );
      })
    : items.filter((it) => (it.voltage ?? 'BT') === voltage && it.category === category);

  const startEdit = (item: Item) => {
    setEditState({ id: item.id, name: item.name, unit: item.unit, unitPrice: item.unitPrice, description: item.description });
    setIsNew(false);
  };

  const startNew = () => {
    setEditState({ id: '', name: '', unit: 'un', unitPrice: 0, description: '' });
    setIsNew(true);
  };

  const cancelEdit = () => { setEditState(null); setIsNew(false); };

  const saveEdit = () => {
    if (!editState) return;
    if (!editState.name.trim()) return;
    if (isNew) {
      addItem(buildEmpty(voltage, category));
      // update the newly added item with our values
      const allItems = useItemsStore.getState().items;
      const newItem = allItems[allItems.length - 1];
      if (newItem) {
        updateItem(newItem.id, {
          name: editState.name.trim(),
          unit: editState.unit.trim() || 'un',
          unitPrice: editState.unitPrice,
          description: editState.description,
        });
      }
    } else {
      updateItem(editState.id, {
        name: editState.name.trim(),
        unit: editState.unit.trim() || 'un',
        unitPrice: editState.unitPrice,
        description: editState.description,
      });
    }
    setEditState(null);
    setIsNew(false);
  };

  const handleRemove = (id: string) => {
    if (editState?.id === id) setEditState(null);
    removeItem(id);
  };

  const inputCls = 'w-full px-2 py-1.5 text-xs bg-zinc-800 border border-zinc-700 rounded text-zinc-200 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors';

  return (
    <div
      className={`w-[88vw] max-w-5xl mx-4 bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl max-h-[88vh] flex flex-col
        transition-all duration-200 ease-out ${visible ? 'opacity-100 scale-100' : 'opacity-0 scale-95'}`}
    >
      {/* ── Header ── */}
      <div className="px-6 pt-5 pb-4 border-b border-zinc-800 shrink-0 flex items-center justify-between">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-0.5">Gerenciar</p>
          <h2 className="text-sm font-semibold text-zinc-100">Dicionário de Materiais</h2>
        </div>
        <button onClick={onClose} className="w-7 h-7 flex items-center justify-center rounded text-zinc-500 hover:text-zinc-200 hover:bg-zinc-800 transition-colors text-sm">✕</button>
      </div>

      {/* ── Busca global ── */}
      <div className="px-4 py-2.5 border-b border-zinc-800 shrink-0">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar em todos os itens..."
          className="w-full px-3 py-1.5 text-xs bg-zinc-800 border border-zinc-700 rounded text-zinc-200 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors"
        />
      </div>

      {/* ── Corpo 3 painéis ── */}
      <div className="flex flex-1 overflow-hidden">

        {/* Painel 1 — BT / AT */}
        <div className="w-44 shrink-0 border-r border-zinc-800 flex flex-col py-1 overflow-y-auto">
          <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-600 px-3 py-2">Nível de tensão</p>
          {(['BT', 'AT'] as Voltage[]).map((v) => (
            <button
              key={v}
              onClick={() => handleVoltageChange(v)}
              className={`w-full text-left px-3 py-2.5 text-xs transition-colors font-medium ${
                voltage === v && !search.trim()
                  ? 'bg-zinc-700 text-zinc-100'
                  : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
              }`}
            >
              {VOLTAGE_LABEL[v]}
            </button>
          ))}
        </div>

        {/* Painel 2 — Categorias */}
        {!search.trim() && (
          <div className="w-52 shrink-0 border-r border-zinc-800 flex flex-col py-1 overflow-y-auto">
            <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-600 px-3 py-2">Categoria</p>
            {categories.map((cat) => {
              const count = items.filter((it) => (it.voltage ?? 'BT') === voltage && it.category === cat).length;
              return (
                <button
                  key={cat}
                  onClick={() => { setCategory(cat); setEditState(null); setIsNew(false); }}
                  className={`w-full text-left px-3 py-2 text-xs transition-colors flex items-center justify-between ${
                    category === cat
                      ? 'bg-zinc-700 text-zinc-100 font-medium'
                      : 'text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200'
                  }`}
                >
                  <span className="truncate">{cat}</span>
                  <span className="text-[9px] text-zinc-600 ml-1 shrink-0">{count}</span>
                </button>
              );
            })}
          </div>
        )}

        {/* Painel 3 — Itens + editor inline */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Lista */}
          <div className="flex-1 overflow-y-auto">
            {displayed.length === 0 && !isNew && (
              <p className="text-xs text-zinc-600 text-center mt-10 px-4">
                {search.trim() ? `Nenhum resultado para "${search}"` : 'Nenhum item nesta categoria.'}
              </p>
            )}

            {displayed.map((item) => {
              const isEditing = editState?.id === item.id && !isNew;
              return (
                <div key={item.id} className={`border-b border-zinc-800/60 ${isEditing ? 'bg-zinc-800/50' : 'hover:bg-zinc-800/30'}`}>
                  {isEditing && editState ? (
                    // ── Editor inline ──
                    <div className="px-4 py-3 flex flex-col gap-2">
                      <div className="grid grid-cols-3 gap-2">
                        <div className="col-span-2">
                          <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Nome</label>
                          <input className={inputCls} value={editState.name} onChange={(e) => setEditState({ ...editState, name: e.target.value })} placeholder="Nome do item" />
                        </div>
                        <div>
                          <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Unidade</label>
                          <input className={inputCls} value={editState.unit} onChange={(e) => setEditState({ ...editState, unit: e.target.value })} placeholder="un / m / h" />
                        </div>
                      </div>
                      <div className="grid grid-cols-3 gap-2">
                        <div>
                          <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Preço unitário</label>
                          <input className={`${inputCls} font-mono`} type="number" step="0.01" min="0" value={editState.unitPrice} onChange={(e) => setEditState({ ...editState, unitPrice: parseFloat(e.target.value) || 0 })} />
                        </div>
                        <div className="col-span-2">
                          <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Descrição / especificação</label>
                          <input className={inputCls} value={editState.description} onChange={(e) => setEditState({ ...editState, description: e.target.value })} placeholder="Ex: 750V, 70°C" />
                        </div>
                      </div>
                      <div className="flex items-center gap-2 mt-1">
                        <button onClick={saveEdit} className="px-3 py-1 text-xs font-semibold bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors">Salvar</button>
                        <button onClick={cancelEdit} className="px-3 py-1 text-xs text-zinc-400 border border-zinc-700 rounded hover:bg-zinc-800 transition-colors">Cancelar</button>
                        <button onClick={() => handleRemove(item.id)} className="ml-auto px-3 py-1 text-xs text-red-500 border border-red-900/40 rounded hover:bg-red-950/30 transition-colors">Excluir</button>
                      </div>
                    </div>
                  ) : (
                    // ── Linha normal ──
                    <div className="flex items-center px-4 py-2.5 gap-3 group">
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-medium text-zinc-200 truncate">{item.name}</p>
                        {item.description && <p className="text-[10px] text-zinc-600 truncate">{item.description}</p>}
                      </div>
                      <div className="flex items-center gap-3 shrink-0">
                        <span className="text-[10px] text-zinc-500 uppercase">{item.unit}</span>
                        <span className="text-xs font-mono text-zinc-300 w-20 text-right">R$ {formatBRL(item.unitPrice)}</span>
                        <button
                          onClick={() => startEdit(item)}
                          className="opacity-0 group-hover:opacity-100 text-[10px] text-zinc-500 hover:text-zinc-200 px-2 py-0.5 border border-zinc-700 rounded transition-all"
                        >
                          Editar
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}

            {/* Formulário de novo item */}
            {isNew && editState && (
              <div className="border-b border-zinc-800/60 bg-zinc-800/50 px-4 py-3 flex flex-col gap-2">
                <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500">Novo item — {category}</p>
                <div className="grid grid-cols-3 gap-2">
                  <div className="col-span-2">
                    <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Nome</label>
                    <input autoFocus className={inputCls} value={editState.name} onChange={(e) => setEditState({ ...editState, name: e.target.value })} placeholder="Nome do item" />
                  </div>
                  <div>
                    <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Unidade</label>
                    <input className={inputCls} value={editState.unit} onChange={(e) => setEditState({ ...editState, unit: e.target.value })} placeholder="un / m / h" />
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Preço unitário</label>
                    <input className={`${inputCls} font-mono`} type="number" step="0.01" min="0" value={editState.unitPrice} onChange={(e) => setEditState({ ...editState, unitPrice: parseFloat(e.target.value) || 0 })} />
                  </div>
                  <div className="col-span-2">
                    <label className="text-[9px] text-zinc-500 uppercase tracking-wide mb-0.5 block">Descrição / especificação</label>
                    <input className={inputCls} value={editState.description} onChange={(e) => setEditState({ ...editState, description: e.target.value })} placeholder="Ex: 750V, 70°C" />
                  </div>
                </div>
                <div className="flex items-center gap-2 mt-1">
                  <button onClick={saveEdit} className="px-3 py-1 text-xs font-semibold bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors">Salvar</button>
                  <button onClick={cancelEdit} className="px-3 py-1 text-xs text-zinc-400 border border-zinc-700 rounded hover:bg-zinc-800 transition-colors">Cancelar</button>
                </div>
              </div>
            )}
          </div>

          {/* Rodapé do painel */}
          {!search.trim() && !isNew && (
            <div className="border-t border-zinc-800 px-4 py-2.5 shrink-0">
              <button onClick={startNew} className="text-xs text-teal-500 hover:text-teal-300 transition-colors">
                + Novo item em "{category}"
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ── Footer ── */}
      <div className="px-6 py-3 border-t border-zinc-800 shrink-0 flex items-center justify-between">
        <p className="text-[10px] text-zinc-600">
          {items.length} itens cadastrados ({items.filter((i) => (i.voltage ?? 'BT') === 'BT').length} BT · {items.filter((i) => i.voltage === 'AT').length} AT)
        </p>
        <button onClick={onClose} className="px-4 py-1.5 text-xs font-medium bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors">
          Fechar
        </button>
      </div>
    </div>
  );
}
