import { useAppStore } from '../../stores/appStore';

function SunIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  );
}

export function ThemeToggle() {
  const { theme, toggleTheme } = useAppStore();
  const isDark = theme === 'dark';

  return (
    <button
      onClick={toggleTheme}
      title={isDark ? 'Ativar modo claro' : 'Ativar modo escuro'}
      className="
        fixed bottom-5 right-5 z-40
        w-9 h-9 flex items-center justify-center
        rounded-full
        bg-white dark:bg-zinc-800
        border border-zinc-200 dark:border-zinc-700
        text-zinc-500 dark:text-zinc-400
        hover:text-zinc-900 dark:hover:text-zinc-100
        hover:border-zinc-400 dark:hover:border-zinc-500
        shadow-md
        transition-colors
      "
    >
      {isDark ? <SunIcon /> : <MoonIcon />}
    </button>
  );
}
