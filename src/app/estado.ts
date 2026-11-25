import { create } from 'zustand';
import * as repo from './lib/repositorio';
import * as cad from './lib/cadastros';
import { novoDocumento, resumir } from './lib/modelos';
import { uid } from './lib/formato';
import type { Aba, Cliente, Documento, Norma, TipoDoc } from './tipos';
import { lerPerfil, salvarPerfil, type Perfil } from './lib/perfil';
import { gravarFormas, lerFormas, type FormaPagamento, type TipoForma } from './lib/formas';

const K_BRILHO = 'ja.brilho';
const lerBrilho = () => { try { const v = Number(localStorage.getItem(K_BRILHO)); return v >= 40 && v <= 100 ? v : 100; } catch { return 100; } };

interface Estado {
  pronto: boolean;
  perfil: Perfil | null;
  dentro: boolean; // já clicou em Entrar
  documentos: Documento[];
  clientes: Cliente[];
  normas: Norma[];
  formas: Record<TipoForma, FormaPagamento>;
  aba: Aba;
  abertos: Partial<Record<TipoDoc, string>>; // documento aberto em cada aba
  clienteAberto: string | null;
  brilho: number; // 40 a 100 (%)
  aviso: { texto: string; tipo: 'ok' | 'erro' } | null;

  iniciar: () => Promise<void>;
  criarPerfil: (p: Perfil) => void;
  entrar: () => Promise<void>;
  irPara: (aba: Aba) => void;
  abrir: (doc: Documento | null, tipo: TipoDoc) => void;
  criar: (tipo: TipoDoc, dados?: Record<string, any>) => Promise<Documento>;
  atualizar: (doc: Documento) => void;
  excluir: (doc: Documento) => Promise<void>;

  abrirCliente: (id: string | null) => void;
  novoCliente: (dados?: Partial<Cliente>) => Promise<Cliente>;
  atualizarCliente: (c: Cliente) => void;
  excluirCliente: (c: Cliente) => Promise<void>;

  salvarNorma: (n: Norma) => void;
  excluirNorma: (n: Norma) => Promise<void>;

  salvarForma: (f: FormaPagamento) => void;
  mudarBrilho: (v: number) => void;
  avisar: (texto: string, tipo?: 'ok' | 'erro') => void;
}

const atrasos = new Map<string, number>();
/** salva sozinho 700 ms depois da última alteração */
function salvarDepois(chave: string, salvar: () => Promise<void>, avisar: (t: string, tipo?: 'ok' | 'erro') => void) {
  window.clearTimeout(atrasos.get(chave));
  atrasos.set(chave, window.setTimeout(() => { salvar().catch((e) => avisar('Não consegui salvar: ' + e.message, 'erro')); }, 700));
}

export const useApp = create<Estado>((set, get) => ({
  pronto: false,
  perfil: null,
  dentro: false,
  documentos: [],
  clientes: [],
  normas: [],
  formas: lerFormas(),
  aba: 'inicio',
  abertos: {},
  clienteAberto: null,
  brilho: lerBrilho(),
  aviso: null,

  iniciar: async () => {
    set({ perfil: lerPerfil(), pronto: true });
  },

  criarPerfil: (p) => {
    salvarPerfil(p);
    set({ perfil: p });
  },

  entrar: async () => {
    set({ dentro: true });
    const [docs, clientes, normas] = await Promise.allSettled([repo.listarDocumentos(), cad.listarClientes(), cad.listarNormas()]);
    set({
      documentos: docs.status === 'fulfilled' ? docs.value : [],
      clientes: clientes.status === 'fulfilled' ? clientes.value : [],
      normas: normas.status === 'fulfilled' ? normas.value : [],
    });
    const falha = [docs, clientes, normas].find((r) => r.status === 'rejected') as PromiseRejectedResult | undefined;
    if (falha) get().avisar('Não consegui carregar tudo: ' + (falha.reason?.message ?? falha.reason), 'erro');
  },

  irPara: (aba) => set({ aba }),

  abrir: (doc, tipo) => set((s) => ({ abertos: { ...s.abertos, [tipo]: doc?.id }, aba: tipo })),

  criar: async (tipo, dados) => {
    const doc = novoDocumento(tipo, repo.proximoNumero(tipo, get().documentos), dados);
    set((s) => ({ documentos: [doc, ...s.documentos], abertos: { ...s.abertos, [tipo]: doc.id } }));
    try { await repo.salvarDocumento(doc); } catch (e: any) { get().avisar('Não consegui salvar: ' + e.message, 'erro'); }
    return doc;
  },

  atualizar: (doc) => {
    const d = { ...doc, ...resumir(doc.tipo, doc.dados) };
    set((s) => ({ documentos: s.documentos.map((x) => (x.id === d.id ? d : x)) }));
    salvarDepois('doc-' + d.id, () => repo.salvarDocumento(d).then(() => undefined), get().avisar);
  },

  excluir: async (doc) => {
    await repo.excluirDocumento(doc.id);
    set((s) => ({ documentos: s.documentos.filter((x) => x.id !== doc.id), abertos: { ...s.abertos, [doc.tipo]: undefined } }));
  },

  abrirCliente: (id) => set({ clienteAberto: id, aba: 'clientes' }),

  novoCliente: async (dados) => {
    const c = { ...cad.clienteVazio(), ...(dados || {}) };
    set((s) => ({ clientes: [c, ...s.clientes], clienteAberto: c.id }));
    try { await cad.salvarCliente(c); } catch (e: any) { get().avisar('Não consegui salvar o cliente: ' + e.message, 'erro'); }
    return c;
  },

  atualizarCliente: (c) => {
    set((s) => ({ clientes: s.clientes.map((x) => (x.id === c.id ? c : x)) }));
    salvarDepois('cli-' + c.id, () => cad.salvarCliente(c), get().avisar);
  },

  excluirCliente: async (c) => {
    await cad.excluirCliente(c.id);
    set((s) => ({ clientes: s.clientes.filter((x) => x.id !== c.id), clienteAberto: s.clienteAberto === c.id ? null : s.clienteAberto }));
  },

  salvarNorma: (n) => {
    const norma = n.id ? n : { ...n, id: uid() };
    set((s) => ({ normas: s.normas.some((x) => x.id === norma.id) ? s.normas.map((x) => (x.id === norma.id ? norma : x)) : [...s.normas, norma] }));
    salvarDepois('norma-' + norma.id, () => cad.salvarNorma(norma), get().avisar);
  },

  excluirNorma: async (n) => {
    await cad.excluirNorma(n.id);
    set((s) => ({ normas: s.normas.filter((x) => x.id !== n.id) }));
  },

  salvarForma: (f) => {
    const formas = { ...get().formas, [f.tipo]: f };
    gravarFormas(formas);
    set({ formas });
  },

  mudarBrilho: (v) => {
    set({ brilho: v });
    try { localStorage.setItem(K_BRILHO, String(v)); } catch { /* opcional */ }
  },

  avisar: (texto, tipo = 'ok') => {
    set({ aviso: { texto, tipo } });
    window.setTimeout(() => { if (get().aviso?.texto === texto) set({ aviso: null }); }, tipo === 'erro' ? 9000 : 4500);
  },
}));
