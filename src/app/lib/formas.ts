/** Formas de pagamento (aba Pagamento). Os dados de cada uma ficam salvos neste computador. */

export type TipoForma = 'pix_pf' | 'pix_pj' | 'ted' | 'boleto' | 'cartao';

export interface FormaPagamento {
  tipo: TipoForma;
  favorecido?: string; // titular / beneficiário
  documento?: string; // CPF ou CNPJ do titular
  banco?: string;
  chave?: string; // Pix
  tipo_chave?: string; // CPF, CNPJ, e-mail, telefone, aleatória
  agencia?: string; // TED
  conta?: string;
  tipo_conta?: string;
  linha?: string; // boleto: linha digitável (muda a cada cobrança)
  observacao?: string;
  link?: string; // cartão: link de pagamento
  parcelas?: string;
  bandeiras?: string;
}

export const FORMAS: { tipo: TipoForma; nome: string; sub: string }[] = [
  { tipo: 'pix_pf', nome: 'Pix · pessoa física', sub: 'Chave CPF' },
  { tipo: 'pix_pj', nome: 'Pix · pessoa jurídica', sub: 'Chave CNPJ da empresa' },
  { tipo: 'ted', nome: 'Transferência (TED)', sub: 'Banco, agência e conta' },
  { tipo: 'boleto', nome: 'Boleto', sub: 'Linha digitável' },
  { tipo: 'cartao', nome: 'Cartão', sub: 'Link ou maquininha' },
];

/** Só o Pix PF é conhecido; os outros a pessoa preenche uma vez e ficam salvos. */
export const FORMAS_PADRAO: Record<TipoForma, FormaPagamento> = {
  pix_pf: { tipo: 'pix_pf', chave: '151.272.348-74', tipo_chave: 'CPF', banco: 'Itaú', favorecido: 'Jorge Luiz dos Santos' },
  pix_pj: { tipo: 'pix_pj', chave: '', tipo_chave: 'CNPJ', banco: '', favorecido: 'J.A Eletricidades', documento: '09.560.091/0001-94' },
  ted: { tipo: 'ted', banco: 'Itaú', agencia: '', conta: '', tipo_conta: 'Conta corrente', favorecido: 'Jorge Luiz dos Santos', documento: '' },
  boleto: { tipo: 'boleto', favorecido: 'J.A Eletricidades', banco: '' },
  cartao: { tipo: 'cartao', link: '', parcelas: '', bandeiras: 'Visa · Mastercard · Elo', favorecido: 'J.A Eletricidades' },
};

const K = 'ja.formas';

export function lerFormas(): Record<TipoForma, FormaPagamento> {
  try {
    const v = JSON.parse(localStorage.getItem(K) || '{}');
    const r = { ...FORMAS_PADRAO };
    (Object.keys(r) as TipoForma[]).forEach((t) => { if (v[t]) r[t] = { ...r[t], ...v[t], tipo: t }; });
    return r;
  } catch { return { ...FORMAS_PADRAO }; }
}

export function gravarFormas(f: Record<TipoForma, FormaPagamento>) {
  localStorage.setItem(K, JSON.stringify(f));
}

/** O que falta preencher para a forma funcionar no PDF. */
export function faltando(f: FormaPagamento): string[] {
  const falta: string[] = [];
  if ((f.tipo === 'pix_pf' || f.tipo === 'pix_pj') && !f.chave?.trim()) falta.push('chave Pix');
  if (f.tipo === 'ted') { if (!f.agencia?.trim()) falta.push('agência'); if (!f.conta?.trim()) falta.push('conta'); }
  return falta;
}
