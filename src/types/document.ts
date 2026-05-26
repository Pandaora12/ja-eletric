import type { Block } from './blocks';

export type DocumentType =
  | 'memorial'
  | 'proposal'
  | 'material-list'
  | 'load-report'
  | 'blank';

export type DocumentStatus = 'running' | 'completed';

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  'memorial':      'Memorial Descritivo',
  'proposal':      'Proposta Comercial',
  'material-list': 'Lista de Materiais',
  'load-report':   'Relatório de Carga',
  'blank':         'Documento em Branco',
};

export interface Document {
  id: string;
  title: string;
  type: DocumentType;
  status: DocumentStatus;
  blocks: Block[];
  createdAt: number;
  updatedAt: number;
  savedPath?: string;
  savedAt?: number;
}
