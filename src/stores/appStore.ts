import { create } from 'zustand';

type Theme = 'light' | 'dark';
const STORAGE_KEY = 'ja-eletric-theme';

function getInitialTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') return stored;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

interface AppState {
  theme: Theme;
  showSettings: boolean;
  toggleTheme: () => void;
  toggleSettings: () => void;
}

export const useAppStore = create<AppState>((set, get) => ({
  theme: getInitialTheme(),
  showSettings: false,

  toggleTheme: () => {
    const next = get().theme === 'light' ? 'dark' : 'light';
    try { localStorage.setItem(STORAGE_KEY, next); } catch {}
    document.documentElement.classList.toggle('dark', next === 'dark');
    set({ theme: next });
  },

  toggleSettings: () => set((state) => ({ showSettings: !state.showSettings })),
}));
