export type TipoDoc = 'orcamento' | 'relatorio' | 'recibo' | 'comprovante';
export type Aba = 'inicio' | 'clientes' | TipoDoc | 'normas';

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

export type Situacao = 'orcamento' | 'aprovado' | 'atendido' | 'nao_atendido';
export type Pagamento = 'pago' | 'nao_pago' | 'parcial';

export interface Cliente {
  id: string;
  nome: string; // nome ou razão social
  documento: string; // CPF ou CNPJ
  contato: string; // pessoa de contato / solicitante
  endereco: string;
  cidade: string;
  cep: string;
  telefone: string;
  email: string;
  situacao: Situacao;
  pagamento: Pagamento;
  observacoes: string;
  criado_em: string;
  atualizado_em: string;
}

export interface Norma {
  id: string;
  codigo: string; // ex.: NBR 5410
  descricao: string; // ex.: Instalações elétricas de baixa tensão
}


export const ABAS: { id: Aba; nome: string; descricao: string }[] = [
  { id: 'inicio', nome: 'Início', descricao: 'Aurora boreal' },
  { id: 'clientes', nome: 'Clientes', descricao: 'Cachoeira e água turquesa' },
  { id: 'orcamento', nome: 'Orçamento', descricao: 'Floresta e montanhas' },
  { id: 'recibo', nome: 'Recibos', descricao: 'Lago de montanha no outono' },
  { id: 'comprovante', nome: 'Pagamento', descricao: 'Campo dourado' },
  { id: 'relatorio', nome: 'Relatório', descricao: 'Vilarejo nos Alpes' },
  { id: 'normas', nome: 'Normas', descricao: 'Rio na mata' },
];

export const NOME_TIPO: Record<TipoDoc, string> = {
  orcamento: 'Orçamento',
  relatorio: 'Relatório',
  recibo: 'Recibo',
  comprovante: 'Pagamento',
};

/** modelo HTML usado para cada tipo (public/modelos) */
export const MODELO: Record<TipoDoc, 'orcamento' | 'recibo' | 'pix' | null> = {
  orcamento: 'orcamento',
  recibo: 'recibo',
  comprovante: 'pix',
  relatorio: null,
};

export const NOME_SITUACAO: Record<Situacao, string> = {
  orcamento: 'Orçamento enviado',
  aprovado: 'Aprovado',
  atendido: 'Atendido',
  nao_atendido: 'Não atendido',
};

export const NOME_PAGAMENTO: Record<Pagamento, string> = {
  pago: 'Pago',
  nao_pago: 'Não pago',
  parcial: 'Pago em parte',
};
