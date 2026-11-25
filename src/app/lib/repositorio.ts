/**
 * Documentos. Com Supabase configurado (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY), usa o banco na nuvem.
 * Sem ele, funciona em "modo local": tudo fica salvo só neste computador.
 */
import { supabase } from './supabase';
import type { Documento, TipoDoc } from '../tipos';

const K_DOCS = 'ja.local.documentos';

const ler = <T,>(k: string, padrao: T): T => {
  try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : padrao; } catch { return padrao; }
};
const gravar = (k: string, v: unknown) => localStorage.setItem(k, JSON.stringify(v));

// ---------------- documentos ----------------
export async function listarDocumentos(): Promise<Documento[]> {
  if (!supabase) return ler<Documento[]>(K_DOCS, []).sort((a, b) => b.criado_em.localeCompare(a.criado_em));
  const { data, error } = await supabase.from('documentos').select('*').order('criado_em', { ascending: false }).limit(500);
  if (error) throw new Error(error.message);
  return (data ?? []).map((d: any) => ({ ...d, valor: d.valor == null ? null : Number(d.valor) }));
}

export async function salvarDocumento(doc: Documento): Promise<Documento> {
  const agora = new Date().toISOString();
  const d = { ...doc, atualizado_em: agora };
  if (!supabase) {
    const todos = ler<Documento[]>(K_DOCS, []).filter((x) => x.id !== d.id);
    gravar(K_DOCS, [d, ...todos]);
    return d;
  }
  const { data: emp } = await supabase.rpc('minha_empresa');
  const linha = { id: d.id, empresa_id: emp, tipo: d.tipo, numero: d.numero, titulo: d.titulo, cliente: d.cliente, valor: d.valor, status: d.status, dados: d.dados };
  const { data, error } = await supabase.from('documentos').upsert(linha).select().single();
  if (error) throw new Error(error.message);
  return { ...(data as any), valor: (data as any).valor == null ? null : Number((data as any).valor) };
}

export async function excluirDocumento(id: string) {
  if (!supabase) return gravar(K_DOCS, ler<Documento[]>(K_DOCS, []).filter((x) => x.id !== id));
  const { error } = await supabase.from('documentos').delete().eq('id', id);
  if (error) throw new Error(error.message);
}

const PREFIXO: Record<TipoDoc, string> = { orcamento: 'ORC', recibo: 'REC', comprovante: 'PAG', relatorio: 'REL' };

/** Próximo número no padrão PREFIXO-AAMM-NNN (ex.: REC-2611-004). */
export function proximoNumero(tipo: TipoDoc, existentes: Documento[]): string {
  const d = new Date();
  const base = `${PREFIXO[tipo]}-${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}-`;
  const usados = existentes.filter((x) => x.tipo === tipo && x.numero?.startsWith(base)).map((x) => Number(x.numero.slice(base.length)) || 0);
  return base + String((usados.length ? Math.max(...usados) : 0) + 1).padStart(3, '0');
}
