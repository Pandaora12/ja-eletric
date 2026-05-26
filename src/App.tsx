import { useEffect } from 'react';
import './App.css';
import { Sidebar } from './components/layout/Sidebar';
import { Navbar } from './components/layout/Navbar';
import { Canvas } from './components/canvas/Canvas';
import { GlobalModal } from './components/ui/GlobalModal';
import { SettingsModal } from './components/ui/SettingsModal';
import { ThemeToggle } from './components/ui/ThemeToggle';
import { useDocumentStore } from './stores/documentStore';
import { useAppStore } from './stores/appStore';
import { useSettingsStore } from './stores/settingsStore';
import { useItemsStore } from './stores/itemsStore';

export default function App() {
  const isInitialized = useDocumentStore((s) => s.isInitialized);
  const initialize = useDocumentStore((s) => s.initialize);
  const theme = useAppStore((s) => s.theme);
  const initializeSettings = useSettingsStore((s) => s.initialize);
  const initializeItems = useItemsStore((s) => s.initialize);

  useEffect(() => { initialize(); }, [initialize]);
  useEffect(() => { initializeSettings(); }, [initializeSettings]);
  useEffect(() => { initializeItems(); }, [initializeItems]);

  // Garante sincronização da classe dark no <html> ao montar
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  if (!isInitialized) {
    return (
      <div className="flex h-screen w-screen items-center justify-center bg-zinc-50 dark:bg-gradient-to-br dark:from-zinc-950 dark:to-zinc-900">
        <p className="text-zinc-400 text-sm tracking-widest uppercase">Carregando...</p>
      </div>
    );
  }

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-zinc-50 dark:bg-gradient-to-br dark:from-zinc-950 dark:to-zinc-900 text-zinc-900 dark:text-zinc-100 antialiased">
      <Sidebar />
      <div className="flex flex-col flex-1 overflow-hidden">
        <Navbar />
        <Canvas />
      </div>
      <GlobalModal />
      <SettingsModal />
      <ThemeToggle />
    </div>
  );
}
