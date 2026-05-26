export type BlockType =
  | 'heading'
  | 'paragraph'
  | 'table'
  | 'divider'
  | 'signature'
  | 'callout'
  | 'image'
  | 'company-header'
  | 'finance-summary';

export interface HeadingContent {
  text: string;
  level: 1 | 2 | 3;
}

export interface ParagraphContent {
  text: string;
}

export interface TableRow {
  cells: string[];
  isHighlighted?: boolean;
}

export type TableCategory = 'material' | 'labor' | 'other';

export interface TableContent {
  headers: string[];
  rows: TableRow[];
  tableCategory?: TableCategory;
}

export interface DividerContent {
  style: 'solid' | 'dashed';
}

export interface SignatureContent {
  label: string;
  name: string;
  role: string;
}

export type CalloutVariant = 'info' | 'warning' | 'danger' | 'success';

export interface CalloutContent {
  text: string;
  variant: CalloutVariant;
}

export interface ImageContent {
  path: string;
  alt: string;
  width?: number; // percentage 25–100, default 100
}

export interface CompanyHeaderContent {
  _marker: true;
}

export interface FinanceSummaryContent {
  _marker: true;
}

export type BlockContent =
  | HeadingContent
  | ParagraphContent
  | TableContent
  | DividerContent
  | SignatureContent
  | CalloutContent
  | ImageContent
  | CompanyHeaderContent
  | FinanceSummaryContent;

export interface Block {
  id: string;
  type: BlockType;
  content: BlockContent;
}

// ─── Type guards ─────────────────────────────────────────────────────────────

export const isHeading        = (b: Block): b is Block & { content: HeadingContent }        => b.type === 'heading';
export const isParagraph      = (b: Block): b is Block & { content: ParagraphContent }      => b.type === 'paragraph';
export const isTable          = (b: Block): b is Block & { content: TableContent }          => b.type === 'table';
export const isDivider        = (b: Block): b is Block & { content: DividerContent }        => b.type === 'divider';
export const isSignature      = (b: Block): b is Block & { content: SignatureContent }      => b.type === 'signature';
export const isCallout        = (b: Block): b is Block & { content: CalloutContent }        => b.type === 'callout';
export const isImage          = (b: Block): b is Block & { content: ImageContent }          => b.type === 'image';
export const isCompanyHeader  = (b: Block): b is Block & { content: CompanyHeaderContent } => b.type === 'company-header';
export const isFinanceSummary = (b: Block): b is Block & { content: FinanceSummaryContent } => b.type === 'finance-summary';
