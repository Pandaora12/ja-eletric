import { create } from 'zustand';
import {
  loadItems, saveItems, DEFAULT_ITEMS,
  type Item,
} from '../lib/itemsStorage';

interface ItemsState {
  items: Item[];
  isInitialized: boolean;

  initialize: () => Promise<void>;
  addItem: (item: Omit<Item, 'id'>) => void;
  updateItem: (id: string, patch: Partial<Omit<Item, 'id'>>) => void;
  removeItem: (id: string) => void;
  getItemsByCategory: (category: string) => Item[];
  searchItems: (query: string) => Item[];
}

export const useItemsStore = create<ItemsState>((set, get) => ({
  items: [],
  isInitialized: false,

  initialize: async () => {
    const loaded = await loadItems();
    if (!loaded) {
      await saveItems(DEFAULT_ITEMS);
      set({ items: DEFAULT_ITEMS, isInitialized: true });
    } else {
      set({ items: loaded, isInitialized: true });
    }
  },

  addItem: (item) => {
    const newItem: Item = { id: crypto.randomUUID(), ...item };
    set((state) => ({ items: [...state.items, newItem] }));
  },

  updateItem: (id, patch) => {
    set((state) => ({
      items: state.items.map((it) => (it.id === id ? { ...it, ...patch } : it)),
    }));
  },

  removeItem: (id) => {
    set((state) => ({ items: state.items.filter((it) => it.id !== id) }));
  },

  getItemsByCategory: (category) =>
    get().items.filter((it) => it.category === category),

  searchItems: (query) => {
    const q = query.toLowerCase().trim();
    if (!q) return get().items;
    return get().items.filter(
      (it) =>
        it.name.toLowerCase().includes(q) ||
        it.category.toLowerCase().includes(q) ||
        it.description.toLowerCase().includes(q),
    );
  },
}));

// ─── Auto-save com debounce de 800ms ─────────────────────────────────────────

let _timer: ReturnType<typeof setTimeout>;

useItemsStore.subscribe((state) => {
  if (!state.isInitialized) return;
  clearTimeout(_timer);
  _timer = setTimeout(() => saveItems(state.items), 800);
});
