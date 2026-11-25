/**
 * Login e documentos. Com Supabase configurado (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY), usa o banco na nuvem.
 * Sem ele, funciona em "modo local": tudo fica salvo só neste computador.
 */
import { supabase } from './supabase';
import { uid } from './formato';
import type { Documento, TipoDoc, Usuario } from '../tipos';

const K_USUARIOS = 'ja.local.usuarios';
const K_SESSAO = 'ja.local.sessao';
const K_DOCS = 'ja.local.documentos';

const ler = <T,>(k: string, padrao: T): T => {
  try { const v = localStorage.getItem(k); return v ? (JSON.parse(v) as T) : padrao; } catch { return padrao; }
};
const gravar = (k: string, v: unknown) => localStorage.setItem(k, JSON.stringify(v));

async function hash(s: string) {
  const b = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return Array.from(new Uint8Array(b)).map((x) => x.toString(16).padStart(2, '0')).join('');
}

function erroAmigavel(msg: string) {
  if (/invalid login/i.test(msg)) return 'E-mail ou senha incorretos.';
  if (/already registered|already exists/i.test(msg)) return 'Este e-mail já tem cadastro. Use "Entrar".';
  if (/password/i.test(msg) && /6/.test(msg)) return 'A senha precisa ter pelo menos 6 caracteres.';
  if (/convite/i.test(msg)) return 'Código de convite inválido.';
  if (/email not confirmed/i.test(msg)) return 'Confirme seu e-mail pelo link que enviamos e tente de novo.';
  return msg;
}

// ---------------- sessão ----------------
export async function usuarioAtual(): Promise<Usuario | null> {
  if (!supabase) return ler<Usuario | null>(K_SESSAO, null);
  const { data } = await supabase.auth.getUser();
  if (!data.user) return null;
  const { data: m } = await supabase.from('membros').select('nome, empresas(nome, codigo_convite)').eq('user_id', data.user.id).maybeSingle();
  const emp = (m as any)?.empresas;
  return { id: data.user.id, email: data.user.email ?? '', nome: (m as any)?.nome ?? '', empresa: emp?.nome ?? '', codigoConvite: emp?.codigo_convite };
}

export async function entrar(email: string, senha: string): Promise<Usuario> {
  if (!supabase) {
    const us = ler<Record<string, Usuario & { senha: string }>>(K_USUARIOS, {});
    const u = us[email.toLowerCase()];
    if (!u || u.senha !== (await hash(senha))) throw new Error('E-mail ou senha incorretos.');
    const { senha: _s, ...sessao } = u;
    gravar(K_SESSAO, sessao);
    return sessao;
  }
  const { error } = await supabase.auth.signInWithPassword({ email, password: senha });
  if (error) throw new Error(erroAmigavel(error.message));
  const u = await usuarioAtual();
  if (!u) throw new Error('Não consegui carregar seu cadastro.');
  return u;
}

export async function cadastrar(p: { nome: string; email: string; senha: string; empresa: string; codigoConvite?: string }): Promise<Usuario | 'confirmar-email'> {
  if (p.senha.length < 6) throw new Error('A senha precisa ter pelo menos 6 caracteres.');
  if (!supabase) {
    const us = ler<Record<string, Usuario & { senha: string }>>(K_USUARIOS, {});
    const email = p.email.toLowerCase();
    if (us[email]) throw new Error('Este e-mail já tem cadastro. Use "Entrar".');
    const u: Usuario = { id: uid(), email, nome: p.nome, empresa: p.empresa || 'J.A Eletricidades' };
    us[email] = { ...u, senha: await hash(p.senha) };
    gravar(K_USUARIOS, us);
    gravar(K_SESSAO, u);
    return u;
  }
  const { data, error } = await supabase.auth.signUp({
    email: p.email,
    password: p.senha,
    options: { data: { nome: p.nome, empresa: p.empresa, codigo_convite: p.codigoConvite || '' } },
  });
  if (error) throw new Error(erroAmigavel(error.message));
  if (!data.session) return 'confirmar-email';
  const u = await usuarioAtual();
  if (!u) throw new Error('Não consegui carregar seu cadastro.');
  return u;
}

export async function sair() {
  if (!supabase) return localStorage.removeItem(K_SESSAO);
  await supabase.auth.signOut();
}

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

const PREFIXO: Record<TipoDoc, string> = { orcamento: 'ORC', recibo: 'REC', comprovante: 'PIX', relatorio: 'REL' };

/** Próximo número no padrão PREFIXO-AAMM-NNN (ex.: REC-2611-004). */
export function proximoNumero(tipo: TipoDoc, existentes: Documento[]): string {
  const d = new Date();
  const base = `${PREFIXO[tipo]}-${String(d.getFullYear()).slice(2)}${String(d.getMonth() + 1).padStart(2, '0')}-`;
  const usados = existentes.filter((x) => x.tipo === tipo && x.numero?.startsWith(base)).map((x) => Number(x.numero.slice(base.length)) || 0);
  return base + String((usados.length ? Math.max(...usados) : 0) + 1).padStart(3, '0');
}
