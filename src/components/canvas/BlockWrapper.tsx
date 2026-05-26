import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

import type { Block, BlockContent } from '../../types/blocks';
import {
  HeadingBlock, ParagraphBlock, TableBlock,
  DividerBlock, SignatureBlock, CalloutBlock,
  ImageBlock, CompanyHeaderBlock, FinanceSummaryBlock, LoadReportBlock,
} from '../blocks';
import {
  isHeading, isParagraph, isTable,
  isDivider, isSignature, isCallout,
  isImage, isCompanyHeader, isFinanceSummary, isLoadReport,
} from '../../types/blocks';
import { DragHandleIcon } from './Canvas';

interface Props {
  block: Block;
  isFirst: boolean;
  isLast: boolean;
  isDragging: boolean;
  onUpdate: (content: Partial<BlockContent>) => void;
  onDelete: () => void;
  onMove: (direction: 'up' | 'down') => void;
  onAddAfter: (type: Block['type']) => void;
}

export function BlockWrapper({
  block, isFirst, isLast, isDragging,
  onUpdate, onDelete, onMove, onAddAfter,
}: Props) {
  const { attributes, listeners, setNodeRef, transform, transition } = useSortable({ id: block.id });

  // Para imagens, o wrapper respeita a largura configurada no bloco
  // para que o usuário possa colocar duas imagens 50% lado a lado (flex-wrap).
  const imgWidth = isImage(block) ? (block.content.width ?? 100) : 100;
  const widthStyle = imgWidth < 100 ? { width: `${imgWidth}%` } : {};

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    ...widthStyle,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      data-block-container
      className={`group/block relative flex gap-2 py-1 px-1 rounded transition-colors ${
        isDragging ? 'opacity-30' : 'hover:bg-zinc-50/80 dark:hover:bg-zinc-800/40'
      }`}
    >
      {/* ── Painel lateral esquerdo ───────────────────────────────── */}
      <div className="absolute -left-9 top-0.5 flex flex-col items-center gap-0.5 opacity-0 group-hover/block:opacity-100 transition-opacity">
        <div
          {...attributes} {...listeners}
          title="Arrastar bloco"
          className="w-6 h-6 flex items-center justify-center text-zinc-300 dark:text-zinc-600 hover:text-zinc-500 dark:hover:text-zinc-400 cursor-grab active:cursor-grabbing rounded"
        >
          <DragHandleIcon />
        </div>
        <button
          onClick={() => onMove('up')} disabled={isFirst}
          title="Mover para cima"
          className="w-6 h-5 flex items-center justify-center text-zinc-300 dark:text-zinc-600 hover:text-zinc-500 dark:hover:text-zinc-400 disabled:opacity-20 disabled:cursor-not-allowed text-xs"
        >↑</button>
        <button
          onClick={() => onMove('down')} disabled={isLast}
          title="Mover para baixo"
          className="w-6 h-5 flex items-center justify-center text-zinc-300 dark:text-zinc-600 hover:text-zinc-500 dark:hover:text-zinc-400 disabled:opacity-20 disabled:cursor-not-allowed text-xs"
        >↓</button>
        <button
          onClick={onDelete}
          title="Remover bloco"
          className="w-6 h-5 flex items-center justify-center text-zinc-300 dark:text-zinc-600 hover:text-red-400 text-xs"
        >✕</button>
      </div>

      {/* ── Conteúdo ─────────────────────────────────────────────── */}
      <div className="flex-1 min-w-0">
        {isHeading(block)        && <HeadingBlock        block={block} onChange={onUpdate} />}
        {isParagraph(block)      && <ParagraphBlock       block={block} onChange={onUpdate} />}
        {isTable(block)          && <TableBlock           block={block} onChange={onUpdate} />}
        {isDivider(block)        && <DividerBlock         block={block} />}
        {isSignature(block)      && <SignatureBlock        block={block} onChange={onUpdate} />}
        {isCallout(block)        && <CalloutBlock          block={block} onChange={onUpdate} />}
        {isImage(block)          && <ImageBlock            block={block} onChange={onUpdate} />}
        {isCompanyHeader(block)  && <CompanyHeaderBlock    block={block} />}
        {isFinanceSummary(block) && <FinanceSummaryBlock />}
        {isLoadReport(block)     && <LoadReportBlock       block={block} onChange={onUpdate} />}
      </div>

      {/* ── Menu "+ bloco" abaixo ─────────────────────────────────── */}
      <div className="absolute -bottom-3 left-1/2 -translate-x-1/2 opacity-0 group-hover/block:opacity-100 transition-opacity z-10">
        <AddBlockMenu onAdd={onAddAfter} />
      </div>
    </div>
  );
}

// ─── Menu inline ─────────────────────────────────────────────────────────────

const BLOCK_OPTIONS: { type: Block['type']; label: string }[] = [
  { type: 'paragraph',      label: 'Parágrafo' },
  { type: 'heading',        label: 'Título' },
  { type: 'table',          label: 'Tabela' },
  { type: 'callout',        label: 'Aviso' },
  { type: 'divider',        label: 'Divisor' },
  { type: 'signature',      label: 'Assinatura' },
  { type: 'image',          label: '📷 Foto' },
  { type: 'finance-summary',label: '💰 Resumo' },
  { type: 'load-report',   label: '⚡ Carga' },
];

function AddBlockMenu({ onAdd }: { onAdd: (type: Block['type']) => void }) {
  return (
    <div className="relative group/menu">
      <button className="flex items-center gap-1 px-2 py-0.5 text-xs bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded shadow-sm text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:border-zinc-300 dark:hover:border-zinc-600 transition-colors">
        + bloco
      </button>
      <div className="absolute top-full left-0 mt-1 w-40 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded shadow-lg overflow-hidden hidden group-hover/menu:block z-20">
        {BLOCK_OPTIONS.map(({ type, label }) => (
          <button
            key={type}
            onMouseDown={(e) => { e.preventDefault(); onAdd(type); }}
            className="w-full text-left px-3 py-1.5 text-xs text-zinc-600 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-700 transition-colors"
          >
            {label}
          </button>
        ))}
      </div>
    </div>
  );
}
