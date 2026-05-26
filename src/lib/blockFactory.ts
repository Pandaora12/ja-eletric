import type { Block, BlockType } from '../types/blocks';

export interface BlockOverrides {
  signatureName?: string;
  signatureRole?: string;
}

export function createBlock(type: BlockType, overrides?: BlockOverrides): Block {
  const id = crypto.randomUUID();

  switch (type) {
    case 'heading':
      return { id, type, content: { text: 'Novo Título', level: 1 } };

    case 'paragraph':
      return { id, type, content: { text: '' } };

    case 'table':
      return {
        id, type,
        content: {
          headers: ['Item', 'Descrição', 'Qtd.', 'Un.', 'Valor Unit. (R$)', 'Total (R$)'],
          rows: [{ cells: ['1', '', '1', 'un', '0'] }],
        },
      };

    case 'divider':
      return { id, type, content: { style: 'solid' } };

    case 'signature':
      return {
        id, type,
        content: {
          label: 'Responsável Técnico',
          name: overrides?.signatureName ?? '',
          role: overrides?.signatureRole ?? '',
        },
      };

    case 'callout':
      return { id, type, content: { text: '', variant: 'info' } };

    case 'image':
      return { id, type, content: { path: '', alt: '' } };

    case 'company-header':
      return { id, type, content: { _marker: true } };

    case 'finance-summary':
      return { id, type, content: { _marker: true } };
  }
}
