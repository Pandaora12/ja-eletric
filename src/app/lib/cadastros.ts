/**
 * Clientes e normas. Com Supabase configurado usa o banco na nuvem; sem ele, salva só neste computador.
 */
import { supabase } from './supabase';
import { uid } from './formato';
import type { Cliente, Norma } from '../tipos';

const K_CLIENTES = 'ja.local.clientes';
const K_NORMAS = 'ja.local.normas';

const ler = <T,>(k: string, padrao: T): T => {
  try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : padrao; } catch { return padrao; }
};
const gravar = (k: string, v: unknown) => localStorage.setItem(k, JSON.stringify(v));

export const NORMAS_PADRAO: Omit<Norma, 'id'>[] = [
  { codigo: 'NBR 5410', descricao: 'Instalações elétricas de baixa tensão' },
  { codigo: 'NBR 5419', descricao: 'Proteção contra descargas atmosféricas (SPDA)' },
  { codigo: 'NBR 14039', descricao: 'Instalações elétricas de média tensão (1,0 kV a 36,2 kV)' },
  { codigo: 'NBR IEC 61439', descricao: 'Conjuntos de manobra e comando de baixa tensão (quadros)' },
  { codigo: 'NBR IEC 60947-2', descricao: 'Disjuntores de baixa tensão' },
  { codigo: 'NBR NM 60898', descricao: 'Disjuntores para instalações domésticas e similares' },
  { codigo: 'NR-10', descricao: 'Segurança em instalações e serviços em eletricidade' },
  { codigo: 'NR-35', descricao: 'Trabalho em altura' },
];

export function clienteVazio(): Cliente {
  const agora = new Date().toISOString();
  return {
    id: uid(), nome: '', documento: '', contato: '', endereco: '', cidade: 'Atibaia/SP', cep: '', telefone: '', email: '',
    situacao: 'atendido', pagamento: 'nao_pago', observacoes: '', criado_em: agora, atualizado_em: agora,
  };
}

// ---------------- clientes ----------------
export async function listarClientes(): Promise<Cliente[]> {
  if (!supabase) return ler<Cliente[]>(K_CLIENTES, []);
  const { data, error } = await supabase.from('clientes').select('*').order('nome');
  if (error) throw new Error(error.message);
  return (data ?? []) as Cliente[];
}

export async function salvarCliente(c: Cliente): Promise<void> {
  const d = { ...c, atualizado_em: new Date().toISOString() };
  if (!supabase) {
    gravar(K_CLIENTES, [d, ...ler<Cliente[]>(K_CLIENTES, []).filter((x) => x.id !== d.id)]);
    return;
  }
  const { data: emp } = await supabase.rpc('minha_empresa');
  const { error } = await supabase.from('clientes').upsert({ ...d, empresa_id: emp });
  if (error) throw new Error(error.message);
}

export async function excluirCliente(id: string): Promise<void> {
  if (!supabase) return gravar(K_CLIENTES, ler<Cliente[]>(K_CLIENTES, []).filter((x) => x.id !== id));
  const { error } = await supabase.from('clientes').delete().eq('id', id);
  if (error) throw new Error(error.message);
}

// ---------------- normas ----------------
export async function listarNormas(): Promise<Norma[]> {
  if (!supabase) {
    const salvas = ler<Norma[] | null>(K_NORMAS, null);
    if (salvas) return salvas;
    const iniciais = NORMAS_PADRAO.map((n) => ({ ...n, id: uid() }));
    gravar(K_NORMAS, iniciais);
    return iniciais;
  }
  const { data, error } = await supabase.from('normas').select('id, codigo, descricao').order('codigo');
  if (error) throw new Error(error.message);
  return (data ?? []) as Norma[];
}

export async function salvarNorma(n: Norma): Promise<void> {
  if (!supabase) {
    const todas = ler<Norma[]>(K_NORMAS, []);
    const i = todas.findIndex((x) => x.id === n.id);
    if (i >= 0) todas[i] = n; else todas.push(n);
    return gravar(K_NORMAS, todas);
  }
  const { data: emp } = await supabase.rpc('minha_empresa');
  const { error } = await supabase.from('normas').upsert({ ...n, empresa_id: emp });
  if (error) throw new Error(error.message);
}

export async function excluirNorma(id: string): Promise<void> {
  if (!supabase) return gravar(K_NORMAS, ler<Norma[]>(K_NORMAS, []).filter((x) => x.id !== id));
  const { error } = await supabase.from('normas').delete().eq('id', id);
  if (error) throw new Error(error.message);
}

/** Texto da norma como vai no orçamento: "NBR 5410 · Instalações elétricas de baixa tensão" */
export const textoNorma = (n: Pick<Norma, 'codigo' | 'descricao'>) => [n.codigo, n.descricao].filter(Boolean).join(' · ');
