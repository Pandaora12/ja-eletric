import { create } from 'zustand';
import type { Document, DocumentType, DocumentStatus } from '../types/document';
import type { Block, BlockContent, BlockType } from '../types/blocks';
import type { ModalPayload, OpenModalPayload } from '../types/modal';
import { arrayMove } from '@dnd-kit/sortable';
import { createBlock } from '../lib/blockFactory';
import { loadState, saveState } from '../lib/storage';
import { useSettingsStore } from './settingsStore';

// ─── State shape ────────────────────────────────────────────────────────────

interface DocumentState {
  documents: Record<string, Document>;
  activeDocumentId: string | null;
  isInitialized: boolean;
  modal: ModalPayload;

  // Lifecycle
  initialize: () => Promise<void>;

  // Modal
  openModal: (payload: OpenModalPayload) => void;
  closeModal: () => void;

  // Document operations
  createDocument: (type: DocumentType, title?: string) => string;
  deleteDocument: (id: string) => void;
  setActiveDocument: (id: string | null) => void;
  updateDocumentTitle: (id: string, title: string) => void;
  updateDocumentStatus: (id: string, status: DocumentStatus) => void;

  // Block operations
  addBlock: (documentId: string, type: BlockType, afterBlockId?: string) => void;
  updateBlock: (documentId: string, blockId: string, content: Partial<BlockContent>) => void;
  deleteBlock: (documentId: string, blockId: string) => void;
  moveBlock: (documentId: string, blockId: string, direction: 'up' | 'down') => void;
  reorderBlocks: (documentId: string, activeId: string, overId: string) => void;

  setSavedPath: (id: string, path: string, timestamp: number) => void;

  // Selectors
  getActiveDocument: () => Document | null;
  getDocument: (id: string) => Document | undefined;
}

const MODAL_CLOSED: ModalPayload = { type: null, data: null };

// ─── Store ──────────────────────────────────────────────────────────────────

export const useDocumentStore = create<DocumentState>((set, get) => ({
  documents: {},
  activeDocumentId: null,
  isInitialized: false,
  modal: MODAL_CLOSED,

  // ── Lifecycle ────────────────────────────────────────────────────────────

  initialize: async () => {
    const persisted = await loadState();
    set({
      documents: persisted?.documents ?? {},
      activeDocumentId: persisted?.activeDocumentId ?? null,
      isInitialized: true,
    });
  },

  // ── Modal ────────────────────────────────────────────────────────────────

  openModal: (payload) => set({ modal: payload }),
  closeModal: () => set({ modal: MODAL_CLOSED }),

  // ── Document operations ──────────────────────────────────────────────────

  createDocument: (type, title) => {
    const id = crypto.randomUUID();
    const now = Date.now();
    const defaultTitles: Record<DocumentType, string> = {
      'memorial':      'Memorial Descritivo',
      'proposal':      'Proposta Comercial',
      'material-list': 'Lista de Materiais',
      'load-report':   'Relatório de Carga',
      'blank':         'Documento em Branco',
    };

    const newDoc: Document = {
      id,
      title: title ?? defaultTitles[type],
      type,
      status: 'running',
      blocks: [createBlock('company-header'), createBlock('heading')],
      createdAt: now,
      updatedAt: now,
    };

    set((state) => ({
      documents: { ...state.documents, [id]: newDoc },
      activeDocumentId: id,
    }));

    return id;
  },

  deleteDocument: (id) => {
    set((state) => {
      const { [id]: _, ...rest } = state.documents;
      return {
        documents: rest,
        activeDocumentId: state.activeDocumentId === id ? null : state.activeDocumentId,
      };
    });
  },

  setActiveDocument: (id) => set({ activeDocumentId: id }),

  updateDocumentTitle: (id, title) => {
    set((state) => ({
      documents: {
        ...state.documents,
        [id]: { ...state.documents[id], title, updatedAt: Date.now() },
      },
    }));
  },

  updateDocumentStatus: (id, status) => {
    set((state) => ({
      documents: {
        ...state.documents,
        [id]: { ...state.documents[id], status, updatedAt: Date.now() },
      },
    }));
  },

  // ── Block operations ─────────────────────────────────────────────────────

  addBlock: (documentId, type, afterBlockId) => {
    const doc = get().documents[documentId];
    if (!doc) return;

    // Pré-preenche assinatura com os dados das configurações
    const overrides = type === 'signature'
      ? (() => {
          const { defaultSignatureName, defaultSignatureRole } = useSettingsStore.getState();
          return { signatureName: defaultSignatureName, signatureRole: defaultSignatureRole };
        })()
      : undefined;

    const newBlock = createBlock(type, overrides);
    const blocks = [...doc.blocks];

    if (afterBlockId) {
      const idx = blocks.findIndex((b) => b.id === afterBlockId);
      blocks.splice(idx + 1, 0, newBlock);
    } else {
      blocks.push(newBlock);
    }

    set((state) => ({
      documents: {
        ...state.documents,
        [documentId]: { ...doc, blocks, updatedAt: Date.now() },
      },
    }));
  },

  updateBlock: (documentId, blockId, content) => {
    const doc = get().documents[documentId];
    if (!doc) return;

    const blocks = doc.blocks.map((b): Block => {
      if (b.id !== blockId) return b;
      return { ...b, content: { ...b.content, ...content } as Block['content'] };
    });

    set((state) => ({
      documents: {
        ...state.documents,
        [documentId]: { ...doc, blocks, updatedAt: Date.now() },
      },
    }));
  },

  deleteBlock: (documentId, blockId) => {
    const doc = get().documents[documentId];
    if (!doc || doc.blocks.length <= 1) return;

    const blocks = doc.blocks.filter((b) => b.id !== blockId);

    set((state) => ({
      documents: {
        ...state.documents,
        [documentId]: { ...doc, blocks, updatedAt: Date.now() },
      },
    }));
  },

  moveBlock: (documentId, blockId, direction) => {
    const doc = get().documents[documentId];
    if (!doc) return;

    const blocks = [...doc.blocks];
    const idx = blocks.findIndex((b) => b.id === blockId);
    if (idx === -1) return;

    const target = direction === 'up' ? idx - 1 : idx + 1;
    if (target < 0 || target >= blocks.length) return;

    [blocks[idx], blocks[target]] = [blocks[target], blocks[idx]];

    set((state) => ({
      documents: {
        ...state.documents,
        [documentId]: { ...doc, blocks, updatedAt: Date.now() },
      },
    }));
  },

  reorderBlocks: (documentId, activeId, overId) => {
    const doc = get().documents[documentId];
    if (!doc) return;

    const oldIndex = doc.blocks.findIndex((b) => b.id === activeId);
    const newIndex = doc.blocks.findIndex((b) => b.id === overId);
    if (oldIndex === -1 || newIndex === -1 || oldIndex === newIndex) return;

    const blocks = arrayMove(doc.blocks, oldIndex, newIndex);

    set((state) => ({
      documents: {
        ...state.documents,
        [documentId]: { ...doc, blocks, updatedAt: Date.now() },
      },
    }));
  },

  setSavedPath: (id, path, timestamp) => {
    set((state) => ({
      documents: {
        ...state.documents,
        [id]: { ...state.documents[id], savedPath: path, savedAt: timestamp },
      },
    }));
  },

  // ── Selectors ────────────────────────────────────────────────────────────

  getActiveDocument: () => {
    const { documents, activeDocumentId } = get();
    return activeDocumentId ? (documents[activeDocumentId] ?? null) : null;
  },

  getDocument: (id) => get().documents[id],
}));

// ─── Auto-save ───────────────────────────────────────────────────────────────
// Debounce de 800ms — evita escritas excessivas durante edição contínua.
// O guard isInitialized impede que o próprio load inicial dispare um save.

let _debounceTimer: ReturnType<typeof setTimeout>;

useDocumentStore.subscribe((state) => {
  if (!state.isInitialized) return;

  clearTimeout(_debounceTimer);
  _debounceTimer = setTimeout(() => {
    saveState({
      documents: state.documents,
      activeDocumentId: state.activeDocumentId,
    });
  }, 800);
});
