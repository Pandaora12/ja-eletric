import { create } from 'zustand';
import { loadSettings, saveSettings, DEFAULT_SETTINGS, type Settings } from '../lib/settingsStorage';

interface SettingsState extends Settings {
  isInitialized: boolean;
  initialize: () => Promise<void>;
  updateSettings: (patch: Partial<Settings>) => void;
}

export const useSettingsStore = create<SettingsState>((set) => ({
  ...DEFAULT_SETTINGS,
  isInitialized: false,

  initialize: async () => {
    const loaded = await loadSettings();
    set({ ...(loaded ?? DEFAULT_SETTINGS), isInitialized: true });
  },

  updateSettings: (patch) => set((state) => ({ ...state, ...patch })),
}));

// Auto-save com debounce de 800ms
let _debounceTimer: ReturnType<typeof setTimeout>;

useSettingsStore.subscribe((state) => {
  if (!state.isInitialized) return;
  clearTimeout(_debounceTimer);
  _debounceTimer = setTimeout(() => {
    const { userName, companyName, primaryColor, logoPath, defaultSignatureName, defaultSignatureRole } = state;
    saveSettings({ userName, companyName, primaryColor, logoPath, defaultSignatureName, defaultSignatureRole });
  }, 800);
});
