import { useState, useRef, useEffect } from 'react';
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { restrictToVerticalAxis } from '@dnd-kit/modifiers';
import { save } from '@tauri-apps/plugin-dialog';
import { writeFile } from '@tauri-apps/plugin-fs';

import { useDocumentStore } from '../../stores/documentStore';
import { useSettingsStore } from '../../stores/settingsStore';
import { BlockWrapper } from './BlockWrapper';
import type { Block, BlockType } from '../../types/blocks';
import { generatePdfBytes } from '../../lib/pdfGenerator';
import { generateWordBytes } from '../../lib/wordGenerator';
import type { Settings } from '../../lib/settingsStorage';

// ─── Ícone de download ────────────────────────────────────────────────────────

function DownloadIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="7 10 12 15 17 10" />
      <line x1="12" y1="15" x2="12" y2="3" />
    </svg>
  );
}

// ─── Canvas ───────────────────────────────────────────────────────────────────

export function Canvas() {
  const {
    activeDocumentId, documents,
    addBlock, updateBlock, deleteBlock, moveBlock, reorderBlocks,
    updateDocumentTitle, updateDocumentStatus, setSavedPath, toggleCommercialMode,
  } = useDocumentStore();

  const settings = useSettingsStore();

  const [draggingId, setDraggingId]   = useState<string | null>(null);
  const [showSaveMenu, setShowSaveMenu] = useState(false);
  const [isSaving, setIsSaving]       = useState(false);
  const saveMenuRef = useRef<HTMLDivElement>(null);

  const doc = activeDocumentId ? documents[activeDocumentId] : null;

  // Fecha o menu ao clicar fora
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (saveMenuRef.current && !saveMenuRef.current.contains(e.target as Node)) {
        setShowSaveMenu(false);
      }
    };
    if (showSaveMenu) document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [showSaveMenu]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
  );

  const handleDragStart = ({ active }: DragStartEvent) => setDraggingId(active.id as string);

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    setDraggingId(null);
    if (!over || active.id === over.id || !doc) return;
    reorderBlocks(doc.id, active.id as string, over.id as string);
  };

  const handleExport = async (format: 'pdf' | 'word') => {
    if (!doc || isSaving) return;
    setShowSaveMenu(false);
    setIsSaving(true);

    try {
      const ext      = format === 'pdf' ? 'pdf' : 'docx';
      const label    = format === 'pdf' ? 'PDF' : 'Word';
      const savePath = await save({
        filters: [{ name: label, extensions: [ext] }],
        defaultPath: `${doc.title}.${ext}`,
      });

      if (!savePath) return;

      const settingsSnapshot: Settings = {
        userName: settings.userName,
        companyName: settings.companyName,
        primaryColor: settings.primaryColor,
        logoPath: settings.logoPath,
        defaultSignatureName: settings.defaultSignatureName,
        defaultSignatureRole: settings.defaultSignatureRole,
      };

      const bytes = format === 'pdf'
        ? await generatePdfBytes(doc, settingsSnapshot)
        : await generateWordBytes(doc, settingsSnapshot);

      await writeFile(savePath, bytes);
      setSavedPath(doc.id, savePath, Date.now());
    } catch (err) {
      console.error('[Canvas] export failed:', err);
    } finally {
      setIsSaving(false);
    }
  };

  if (!doc) {
    return (
      <div className="flex-1 flex items-center justify-center bg-zinc-50 dark:bg-zinc-950">
        <div className="text-center">
          <p className="text-zinc-400 dark:text-zinc-500 text-sm">Nenhum documento aberto</p>
          <p className="text-zinc-300 dark:text-zinc-600 text-xs mt-1">Crie ou selecione um documento na barra lateral</p>
        </div>
      </div>
    );
  }

  const draggingBlock = draggingId ? doc.blocks.find((b) => b.id === draggingId) : null;

  return (
    <div className="flex-1 overflow-y-auto bg-zinc-100 dark:bg-zinc-950">
      <div className="max-w-4xl mx-auto my-8 px-8">

        {/* Cabeçalho do documento */}
        <div className="mb-6 flex items-center justify-between">
          <input
            type="text"
            value={doc.title}
            onChange={(e) => updateDocumentTitle(doc.id, e.target.value)}
            className="text-lg font-semibold text-zinc-800 dark:text-zinc-200 bg-transparent border-none outline-none w-full placeholder:text-zinc-300 dark:placeholder:text-zinc-600"
            placeholder="Título do documento"
          />

          <div className="flex items-center gap-2 shrink-0">
            {/* Status */}
            <span className="text-xs text-zinc-400 dark:text-zinc-500">Status:</span>
            <button
              onClick={() =>
                updateDocumentStatus(doc.id, doc.status === 'running' ? 'completed' : 'running')
              }
              className={`text-xs px-2.5 py-1 rounded-full font-medium transition-colors ${
                doc.status === 'completed'
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200 dark:bg-emerald-950 dark:text-emerald-400 dark:border-emerald-900'
                  : 'bg-amber-50 text-amber-700 border border-amber-200 dark:bg-amber-950 dark:text-amber-400 dark:border-amber-900'
              }`}
            >
              {doc.status === 'completed' ? 'Concluído' : 'Em execução'}
            </button>

            {/* Modo Comercial */}
            <button
              onClick={() => toggleCommercialMode(doc.id)}
              title={doc.isCommercialMode ? 'PDF mostra apenas resumo financeiro' : 'PDF mostra relatório completo'}
              className={`text-xs px-2.5 py-1 rounded-full font-medium border transition-colors ${
                doc.isCommercialMode
                  ? 'bg-green-50 text-green-700 border-green-200 dark:bg-green-950 dark:text-green-400 dark:border-green-900'
                  : 'text-zinc-400 border-zinc-200 dark:text-zinc-500 dark:border-zinc-700 hover:border-zinc-400 dark:hover:border-zinc-500'
              }`}
            >
              {doc.isCommercialMode ? '🔒 Comercial' : '📋 Técnico'}
            </button>

            {/* Salvar no computador */}
            <div className="relative" ref={saveMenuRef}>
              <button
                onClick={() => setShowSaveMenu((s) => !s)}
                disabled={isSaving}
                title="Salvar no computador"
                className={`flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium border transition-colors ${
                  isSaving
                    ? 'text-zinc-300 border-zinc-200 dark:text-zinc-600 dark:border-zinc-700 cursor-wait'
                    : 'text-zinc-500 dark:text-zinc-400 border-zinc-200 dark:border-zinc-700 hover:text-zinc-700 dark:hover:text-zinc-200 hover:border-zinc-400 dark:hover:border-zinc-500 hover:bg-white dark:hover:bg-zinc-800'
                }`}
              >
                <DownloadIcon />
                {isSaving ? 'Salvando...' : 'Salvar'}
              </button>

              {showSaveMenu && (
                <div className="absolute top-full right-0 mt-1 w-36 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded shadow-lg overflow-hidden z-20">
                  <button
                    onClick={() => handleExport('pdf')}
                    className="w-full text-left px-3 py-2 text-xs text-zinc-600 dark:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-700 transition-colors flex items-center gap-2"
                  >
                    <span className="text-red-500 font-bold text-[10px]">PDF</span>
                    Exportar PDF
                  </button>
                  <button
                    onClick={() => handleExport('word')}
                    className="w-full text-left px-3 py-2 text-xs text-zinc-600 dark:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-700 transition-colors flex items-center gap-2 border-t border-zinc-100 dark:border-zinc-700"
                  >
                    <span className="text-blue-500 font-bold text-[10px]">W</span>
                    Exportar Word
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Folha A4 */}
        <div className="bg-white dark:bg-zinc-900 rounded-sm shadow-sm border border-zinc-200 dark:border-zinc-700 min-h-[calc(100vh-12rem)]">
          <div className="p-12">

            {doc.blocks.length === 0 && (
              <p className="text-zinc-300 dark:text-zinc-600 text-sm text-center py-8">
                Nenhum bloco. Adicione um abaixo.
              </p>
            )}

            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              modifiers={[restrictToVerticalAxis]}
              onDragStart={handleDragStart}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={doc.blocks.map((b) => b.id)}
                strategy={verticalListSortingStrategy}
              >
                <div className="flex flex-row flex-wrap items-start gap-4">
                  {doc.blocks.map((block: Block, idx: number) => (
                    <BlockWrapper
                      key={block.id}
                      block={block}
                      isFirst={idx === 0}
                      isLast={idx === doc.blocks.length - 1}
                      isDragging={block.id === draggingId}
                      onUpdate={(content) => updateBlock(doc.id, block.id, content)}
                      onDelete={() => deleteBlock(doc.id, block.id)}
                      onMove={(dir) => moveBlock(doc.id, block.id, dir)}
                      onAddAfter={(type) => addBlock(doc.id, type, block.id)}
                    />
                  ))}
                </div>
              </SortableContext>

              <DragOverlay dropAnimation={null} modifiers={[restrictToVerticalAxis]}>
                {draggingBlock ? (
                  <div className="flex items-center gap-3 px-3 py-2.5 rounded bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-600 shadow-xl opacity-95 cursor-grabbing">
                    <DragHandleIcon className="text-zinc-300 dark:text-zinc-600 shrink-0" />
                    <div className="h-3 w-48 bg-zinc-100 dark:bg-zinc-700 rounded" />
                  </div>
                ) : null}
              </DragOverlay>
            </DndContext>

            {/* Barra de adição */}
            <div className="mt-8 pt-4 border-t border-zinc-100 dark:border-zinc-800">
              <AddBlockBar onAdd={(type) => addBlock(doc.id, type)} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Barra de adição ──────────────────────────────────────────────────────────

const QUICK_BLOCKS: { type: BlockType; label: string }[] = [
  { type: 'paragraph',       label: '¶ Parágrafo' },
  { type: 'heading',         label: 'H Título' },
  { type: 'table',           label: '⊞ Tabela' },
  { type: 'callout',         label: '⚠ Aviso' },
  { type: 'divider',         label: '― Divisor' },
  { type: 'signature',       label: '✎ Assinatura' },
  { type: 'image',           label: '📷 Foto' },
  { type: 'finance-summary', label: '💰 Resumo' },
  { type: 'load-report',    label: '⚡ Carga' },
];

function AddBlockBar({ onAdd }: { onAdd: (type: BlockType) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      <span className="text-xs text-zinc-300 dark:text-zinc-600 self-center mr-1">Adicionar bloco:</span>
      {QUICK_BLOCKS.map(({ type, label }) => (
        <button
          key={type}
          onClick={() => onAdd(type)}
          className="text-xs px-3 py-1.5 rounded border border-zinc-200 dark:border-zinc-700 text-zinc-400 dark:text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-200 hover:border-zinc-400 dark:hover:border-zinc-500 hover:bg-zinc-50 dark:hover:bg-zinc-800 transition-colors"
        >
          {label}
        </button>
      ))}
    </div>
  );
}

// ─── Ícone de handle ──────────────────────────────────────────────────────────

export function DragHandleIcon({ className = '' }: { className?: string }) {
  return (
    <svg width="10" height="15" viewBox="0 0 10 15" fill="currentColor" className={className} aria-hidden="true">
      <circle cx="2" cy="1.5"  r="1.5" />
      <circle cx="8" cy="1.5"  r="1.5" />
      <circle cx="2" cy="7.5"  r="1.5" />
      <circle cx="8" cy="7.5"  r="1.5" />
      <circle cx="2" cy="13.5" r="1.5" />
      <circle cx="8" cy="13.5" r="1.5" />
    </svg>
  );
}
