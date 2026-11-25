import { hojeISO, uid } from './formato';
import type { Documento, TipoDoc } from '../tipos';

/** Dados iniciais de cada tipo, para o "Preencher formulário". */
export function dadosVazios(tipo: TipoDoc): Record<string, any> {
  const data = hojeISO();
  switch (tipo) {
    case 'orcamento':
      return {
        data, cidade: 'Atibaia', validade_dias: 10, status: 'aguardando',
        tipo: 'Mão de obra · Instalações elétricas', titulo: '', subtitulo: '',
        cliente: { nome: '', solicitante: '', local: 'Atibaia · SP' },
        referencia: '', carta: ['Atendendo à solicitação, apresentamos nosso orçamento conforme as normas vigentes da ABNT.'],
        escopo_tipo: 'Mão de obra',
        setores: [{ nome: 'Setor 1', itens: [{ tipo: 'servico', descricao: '' }] }],
        incluso: [], cliente_responsabilidades: [], observacoes: [], normas: ['NBR 5410 · Instalações de baixa tensão'],
        valor: 0, valor_rotulo: 'Valor da mão de obra', valor_nota: '',
        forma_pagamento: 'Boleto bancário', parcelas: [{ percentual: 100, quando: 'no ato' }],
        prazo: { inicio: 'até 7 dias', termino: '', nota: '' },
      };
    case 'recibo':
      return {
        data, cidade: 'Atibaia', rotulo: 'Prestação de serviço',
        pagador: { nome: '', documento: '', endereco: '', complemento: '' },
        local: { nome: '', cidade: 'Atibaia/SP' },
        referente: 'ao serviço e ao material descritos abaixo',
        itens: [{ descricao: '', detalhe: 'Mão de obra', tipo: 'servico', valor: 0 }],
      };
    case 'comprovante':
      return { data, valor: null, referente: '', cliente: '', itens: [] };
    default:
      return {};
  }
}

const soma = (itens: any[] = []) => itens.reduce((a, i) => a + (Number(i?.valor) || 0), 0);

/** Campos de resumo (lista e painel) a partir dos dados. */
export function resumir(tipo: TipoDoc, d: Record<string, any>): Pick<Documento, 'titulo' | 'cliente' | 'valor'> {
  switch (tipo) {
    case 'orcamento':
      return { titulo: d.titulo || 'Orçamento', cliente: d.cliente?.nome || '', valor: Number(d.valor) || 0 };
    case 'recibo':
      return { titulo: d.rotulo || 'Recibo', cliente: d.pagador?.nome || '', valor: d.total != null ? Number(d.total) : soma(d.itens) };
    case 'comprovante':
      return { titulo: d.referente || 'Dados para pagamento Pix', cliente: d.cliente || '', valor: d.valor == null || d.valor === '' ? null : Number(d.valor) };
    default:
      return { titulo: d.titulo || 'Relatório', cliente: d.cliente || '', valor: null };
  }
}

export function novoDocumento(tipo: TipoDoc, numero: string, dados?: Record<string, any>): Documento {
  const base = { ...dadosVazios(tipo), ...(dados || {}) };
  if (!base.data) base.data = hojeISO();
  const agora = new Date().toISOString();
  return {
    id: uid(), tipo, numero, ...resumir(tipo, base),
    status: tipo === 'recibo' ? 'pago' : tipo === 'orcamento' ? 'aguardando' : 'rascunho',
    dados: base, criado_em: agora, atualizado_em: agora,
  };
}

/** O que vai para o modelo HTML (prévia e PDF). */
export function dadosParaModelo(doc: Documento): Record<string, any> {
  const d: Record<string, any> = { ...doc.dados, numero: doc.numero };
  if (doc.tipo === 'orcamento') d.status = doc.status === 'aprovado' ? 'aprovado' : doc.status === 'rascunho' ? 'rascunho' : 'aguardando';
  if (doc.tipo === 'comprovante' && (d.valor === '' || d.valor === 0)) d.valor = null;
  return d;
}
