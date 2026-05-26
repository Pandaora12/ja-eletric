import React from 'react';
import type { Block, CalloutContent, CalloutVariant } from '../../types/blocks';

interface Props {
  block: Block & { content: CalloutContent };
  onChange: (content: Partial<CalloutContent>) => void;
}

// ─── Config de variantes ──────────────────────────────────────────────────────

const VARIANTS: CalloutVariant[] = ['info', 'warning', 'danger', 'success'];

const VARIANT_CONFIG: Record<CalloutVariant, {
  border: string;
  bg: string;
  iconColor: string;
  label: string;
}> = {
  info: {
    border:    'border-blue-400/60 dark:border-blue-500/50',
    bg:        'bg-blue-50/80 dark:bg-blue-950/20',
    iconColor: 'text-blue-500 dark:text-blue-400',
    label:     'Informação',
  },
  warning: {
    border:    'border-amber-400/60 dark:border-amber-500/50',
    bg:        'bg-amber-50/80 dark:bg-amber-950/20',
    iconColor: 'text-amber-500 dark:text-amber-400',
    label:     'Atenção',
  },
  danger: {
    border:    'border-red-400/60 dark:border-red-500/50',
    bg:        'bg-red-50/80 dark:bg-red-950/20',
    iconColor: 'text-red-500 dark:text-red-400',
    label:     'Perigo',
  },
  success: {
    border:    'border-emerald-400/60 dark:border-emerald-500/50',
    bg:        'bg-emerald-50/80 dark:bg-emerald-950/20',
    iconColor: 'text-emerald-500 dark:text-emerald-400',
    label:     'Concluído',
  },
};

// ─── Ícones SVG ───────────────────────────────────────────────────────────────

function InfoIcon()    { return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>; }
function WarnIcon()    { return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>; }
function DangerIcon()  { return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>; }
function SuccessIcon() { return <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12"/></svg>; }

const ICONS: Record<CalloutVariant, () => React.ReactElement> = {
  info:    InfoIcon,
  warning: WarnIcon,
  danger:  DangerIcon,
  success: SuccessIcon,
};

// ─── Componente ───────────────────────────────────────────────────────────────

export function CalloutBlock({ block, onChange }: Props) {
  const { text, variant } = block.content;
  const cfg = VARIANT_CONFIG[variant];
  const Icon = ICONS[variant];

  const cycleVariant = () => {
    const idx = VARIANTS.indexOf(variant);
    onChange({ variant: VARIANTS[(idx + 1) % VARIANTS.length] });
  };

  return (
    <div className={`flex gap-3 rounded-sm border-l-4 px-4 py-3 ${cfg.border} ${cfg.bg}`}>
      {/* Ícone clicável — cicla entre as variantes */}
      <button
        onClick={cycleVariant}
        title={`Tipo: ${cfg.label} (clique para alterar)`}
        className={`shrink-0 mt-0.5 ${cfg.iconColor} hover:opacity-70 transition-opacity cursor-pointer`}
      >
        <Icon />
      </button>

      {/* Área de texto */}
      <div
        contentEditable
        suppressContentEditableWarning
        onBlur={(e) => onChange({ text: e.currentTarget.textContent ?? '' })}
        className="flex-1 text-sm text-zinc-700 dark:text-zinc-300 outline-none leading-relaxed empty:before:content-['Digite_um_aviso_ou_observação...'] empty:before:text-zinc-400 dark:empty:before:text-zinc-600"
        dangerouslySetInnerHTML={{ __html: text }}
      />
    </div>
  );
}
