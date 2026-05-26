import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { open } from '@tauri-apps/plugin-dialog';
import { useAppStore } from '../../stores/appStore';
import { useSettingsStore } from '../../stores/settingsStore';
import type { Settings } from '../../lib/settingsStorage';

type FormState = Omit<Settings, never>;

export function SettingsModal() {
  const showSettings  = useAppStore((s) => s.showSettings);
  const toggleSettings = useAppStore((s) => s.toggleSettings);
  const updateSettings = useSettingsStore((s) => s.updateSettings);

  const [form, setForm] = useState<FormState>({
    userName: '',
    companyName: '',
    primaryColor: '#0ea5e9',
    logoPath: '',
    defaultSignatureName: '',
    defaultSignatureRole: '',
  });

  // Sincroniza o formulário com o store sempre que o modal abre
  useEffect(() => {
    if (showSettings) {
      const s = useSettingsStore.getState();
      setForm({
        userName: s.userName,
        companyName: s.companyName,
        primaryColor: s.primaryColor,
        logoPath: s.logoPath,
        defaultSignatureName: s.defaultSignatureName,
        defaultSignatureRole: s.defaultSignatureRole,
      });
    }
  }, [showSettings]);

  // ESC fecha
  useEffect(() => {
    if (!showSettings) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') toggleSettings(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [showSettings, toggleSettings]);

  // Trava scroll do body
  useEffect(() => {
    document.body.style.overflow = showSettings ? 'hidden' : '';
    return () => { document.body.style.overflow = ''; };
  }, [showSettings]);

  if (!showSettings) return null;

  const patch = (key: keyof FormState, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSave = () => {
    updateSettings(form);
    toggleSettings();
  };

  const pickLogo = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'Imagem', extensions: ['jpg', 'jpeg', 'png', 'svg', 'webp'] }],
    });
    if (typeof selected === 'string') patch('logoPath', selected);
  };

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
      onMouseDown={(e) => { if (e.target === e.currentTarget) toggleSettings(); }}
    >
      <div className="w-full max-w-lg mx-4 bg-zinc-900 border border-zinc-800 rounded-lg shadow-2xl max-h-[92vh] flex flex-col">

        {/* Header */}
        <div className="px-6 pt-6 pb-5 border-b border-zinc-800 shrink-0">
          <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 mb-1">
            Perfil & Empresa
          </p>
          <h2 className="text-base font-semibold text-zinc-100">Configurações</h2>
        </div>

        {/* Body — scrollável */}
        <div className="overflow-y-auto flex-1 px-6 py-5 flex flex-col gap-6">

          {/* ── Identidade ── */}
          <section>
            <SectionTitle>Identidade</SectionTitle>
            <div className="flex flex-col gap-3">
              <Field
                label="Seu nome"
                value={form.userName}
                placeholder="ex: João Alves"
                onChange={(v) => patch('userName', v)}
              />
              <Field
                label="Nome da empresa"
                value={form.companyName}
                placeholder="ex: JA Elétrica Ltda."
                onChange={(v) => patch('companyName', v)}
              />
            </div>
          </section>

          {/* ── Logo ── */}
          <section>
            <SectionTitle>Logo da empresa</SectionTitle>
            <div className="flex items-center gap-2">
              <div className="flex-1 px-3 py-2 text-sm bg-zinc-950 border border-zinc-700 rounded text-zinc-500 truncate min-w-0">
                {form.logoPath
                  ? form.logoPath.split(/[\\/]/).pop()
                  : 'Nenhum arquivo selecionado'}
              </div>
              <button
                onClick={pickLogo}
                className="shrink-0 px-3 py-2 text-xs font-medium bg-zinc-800 border border-zinc-700 rounded text-zinc-300 hover:bg-zinc-700 hover:text-zinc-100 transition-colors"
              >
                Escolher
              </button>
              {form.logoPath && (
                <button
                  onClick={() => patch('logoPath', '')}
                  className="shrink-0 text-zinc-600 hover:text-red-400 transition-colors text-xs px-1"
                  title="Remover logo"
                >
                  ✕
                </button>
              )}
            </div>
          </section>

          {/* ── Cor principal ── */}
          <section>
            <SectionTitle>Cor principal</SectionTitle>
            <div className="flex items-center gap-3">
              <input
                type="color"
                value={form.primaryColor}
                onChange={(e) => patch('primaryColor', e.target.value)}
                className="w-10 h-10 rounded cursor-pointer border border-zinc-700 bg-zinc-950 p-0.5 shrink-0"
              />
              <input
                type="text"
                value={form.primaryColor}
                onChange={(e) => patch('primaryColor', e.target.value)}
                maxLength={7}
                placeholder="#0ea5e9"
                className="w-28 shrink-0 px-3 py-2 text-sm bg-zinc-950 border border-zinc-700 rounded text-zinc-100 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors font-mono"
              />
              <div
                className="flex-1 h-10 rounded border border-zinc-700/50 transition-colors"
                style={{ backgroundColor: form.primaryColor }}
              />
            </div>
          </section>

          {/* ── Assinatura padrão ── */}
          <section>
            <SectionTitle>Assinatura padrão</SectionTitle>
            <p className="text-[10px] text-zinc-600 mb-3 leading-relaxed">
              Preencherá automaticamente novos blocos de assinatura.
            </p>
            <div className="flex flex-col gap-3">
              <Field
                label="Nome completo"
                value={form.defaultSignatureName}
                placeholder="ex: João Alves"
                onChange={(v) => patch('defaultSignatureName', v)}
              />
              <Field
                label="Cargo / Registro"
                value={form.defaultSignatureRole}
                placeholder="ex: Eng. Elétrico — CREA-SP 123456/D"
                onChange={(v) => patch('defaultSignatureRole', v)}
              />
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="border-t border-zinc-800 shrink-0">
          <div className="flex items-center justify-end gap-2 px-6 py-4">
            <button
              onClick={toggleSettings}
              className="px-4 py-1.5 text-xs font-medium text-zinc-400 border border-zinc-700 rounded hover:bg-zinc-800 hover:text-zinc-200 transition-colors"
            >
              Cancelar
            </button>
            <button
              onClick={handleSave}
              className="px-5 py-1.5 text-xs font-semibold bg-zinc-100 text-zinc-900 rounded hover:bg-white transition-colors tracking-wide"
            >
              Salvar
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}

// ─── Subcomponentes ───────────────────────────────────────────────────────────

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-600 mb-3">
      {children}
    </p>
  );
}

function Field({
  label, value, placeholder, onChange,
}: {
  label: string;
  value: string;
  placeholder: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <label className="block text-[10px] font-medium text-zinc-500 mb-1.5">{label}</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full px-3 py-2 text-sm bg-zinc-950 border border-zinc-700 rounded text-zinc-100 placeholder:text-zinc-600 outline-none focus:border-zinc-500 transition-colors"
      />
    </div>
  );
}
