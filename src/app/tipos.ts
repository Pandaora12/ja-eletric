export type TipoDoc = 'orcamento' | 'relatorio' | 'recibo' | 'comprovante';
export type Aba = 'inicio' | TipoDoc;

export interface Documento {
  id: string;
  tipo: TipoDoc;
  numero: string;
  titulo: string;
  cliente: string;
  valor: number | null;
  status: 'rascunho' | 'aguardando' | 'aprovado' | 'pago' | 'enviado';
  dados: Record<string, any>;
  criado_em: string;
  atualizado_em: string;
}

export interface Anexo {
  tipo: 'imagem' | 'pdf' | 'texto';
  nome: string;
  mime?: string;
  base64?: string;
  texto?: string;
}

export interface Mensagem {
  id: string;
  papel: 'usuario' | 'assistente';
  texto: string;
  anexos?: Anexo[];
  documentoId?: string;
  erro?: boolean;
}

export interface Usuario {
  id: string;
  email: string;
  nome: string;
  empresa: string;
  codigoConvite?: string;
}

/** Cada aba tem a cor de uma paisagem. */
export const ABAS: { id: Aba; nome: string; paisagem: string; descricao: string }[] = [
  { id: 'inicio', nome: 'Início', paisagem: 'aurora', descricao: 'Aurora boreal' },
  { id: 'orcamento', nome: 'Orçamento', paisagem: 'floresta', descricao: 'Floresta tropical' },
  { id: 'relatorio', nome: 'Relatório', paisagem: 'oceano', descricao: 'Oceano profundo' },
  { id: 'recibo', nome: 'Recibos', paisagem: 'deserto', descricao: 'Pôr do sol no deserto' },
  { id: 'comprovante', nome: 'Comprovante', paisagem: 'canion', descricao: 'Cânion e flores' },
];

export const NOME_TIPO: Record<TipoDoc, string> = {
  orcamento: 'Orçamento',
  relatorio: 'Relatório',
  recibo: 'Recibo',
  comprovante: 'Comprovante Pix',
};

/** modelo HTML usado para cada tipo (public/modelos) */
export const MODELO: Record<TipoDoc, 'orcamento' | 'recibo' | 'pix' | null> = {
  orcamento: 'orcamento',
  recibo: 'recibo',
  comprovante: 'pix',
  relatorio: null,
};
