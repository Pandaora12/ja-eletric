import { create } from 'zustand';
import * as repo from './lib/repositorio';
import { perguntarIA } from './lib/ia';
import { novoDocumento, resumir } from './lib/modelos';
import { uid } from './lib/formato';
import { NOME_TIPO, type Aba, type Anexo, type Documento, type Mensagem, type TipoDoc, type Usuario } from './tipos';

const K_CHAT = 'ja.chat';

interface Estado {
  pronto: boolean;
  usuario: Usuario | null;
  documentos: Documento[];
  aba: Aba;
  abertos: Partial<Record<TipoDoc, string>>; // documento aberto em cada aba
  mensagens: Mensagem[];
  pensando: boolean;
  aviso: { texto: string; tipo: 'ok' | 'erro' } | null;

  iniciar: () => Promise<void>;
  entrar: (u: Usuario) => Promise<void>;
  sair: () => Promise<void>;
  irPara: (aba: Aba) => void;
  abrir: (doc: Documento | null, tipo: TipoDoc) => void;
  criar: (tipo: TipoDoc, dados?: Record<string, any>) => Promise<Documento>;
  atualizar: (doc: Documento) => void;
  excluir: (doc: Documento) => Promise<void>;
  enviar: (texto: string, anexos: Anexo[]) => Promise<void>;
  limparChat: () => void;
  avisar: (texto: string, tipo?: 'ok' | 'erro') => void;
}

const salvarAtrasado = new Map<string, number>();

export const useApp = create<Estado>((set, get) => ({
  pronto: false,
  usuario: null,
  documentos: [],
  aba: 'inicio',
  abertos: {},
  mensagens: [],
  pensando: false,
  aviso: null,

  iniciar: async () => {
    const usuario = await repo.usuarioAtual().catch(() => null);
    set({ usuario, pronto: true });
    if (usuario) await get().entrar(usuario);
  },

  entrar: async (usuario) => {
    let mensagens: Mensagem[] = [];
    try { mensagens = JSON.parse(localStorage.getItem(`${K_CHAT}.${usuario.id}`) || '[]'); } catch { mensagens = []; }
    set({ usuario, mensagens });
    try { set({ documentos: await repo.listarDocumentos() }); } catch (e: any) { get().avisar(e.message, 'erro'); }
  },

  sair: async () => {
    await repo.sair();
    set({ usuario: null, documentos: [], mensagens: [], abertos: {}, aba: 'inicio' });
  },

  irPara: (aba) => set({ aba }),

  abrir: (doc, tipo) => set((s) => ({ abertos: { ...s.abertos, [tipo]: doc?.id }, aba: tipo })),

  criar: async (tipo, dados) => {
    const doc = novoDocumento(tipo, repo.proximoNumero(tipo, get().documentos), dados);
    set((s) => ({ documentos: [doc, ...s.documentos], abertos: { ...s.abertos, [tipo]: doc.id } }));
    try { await repo.salvarDocumento(doc); } catch (e: any) { get().avisar('Não consegui salvar: ' + e.message, 'erro'); }
    return doc;
  },

  // salva sozinho 800 ms depois da última alteração
  atualizar: (doc) => {
    const d = { ...doc, ...resumir(doc.tipo, doc.dados) };
    set((s) => ({ documentos: s.documentos.map((x) => (x.id === d.id ? d : x)) }));
    window.clearTimeout(salvarAtrasado.get(d.id));
    salvarAtrasado.set(d.id, window.setTimeout(() => {
      repo.salvarDocumento(d).catch((e) => get().avisar('Não consegui salvar: ' + e.message, 'erro'));
    }, 800));
  },

  excluir: async (doc) => {
    await repo.excluirDocumento(doc.id);
    set((s) => ({ documentos: s.documentos.filter((x) => x.id !== doc.id), abertos: { ...s.abertos, [doc.tipo]: undefined } }));
  },

  enviar: async (texto, anexos) => {
    const { usuario } = get();
    const minha: Mensagem = { id: uid(), papel: 'usuario', texto, anexos };
    const historico = [...get().mensagens, minha];
    set({ mensagens: historico, pensando: true });
    try {
      const r = await perguntarIA(historico, get().aba);
      let documentoId: string | undefined;
      if (r.documento && r.documento.tipo in NOME_TIPO) {
        const doc = await get().criar(r.documento.tipo, r.documento.dados);
        documentoId = doc.id;
      }
      set((s) => ({ mensagens: [...s.mensagens, { id: uid(), papel: 'assistente', texto: r.texto, documentoId }] }));
    } catch (e: any) {
      set((s) => ({ mensagens: [...s.mensagens, { id: uid(), papel: 'assistente', texto: e.message, erro: true }] }));
    } finally {
      set({ pensando: false });
      if (usuario) {
        // guarda o histórico sem o conteúdo dos anexos (só o nome)
        const leve = get().mensagens.slice(-60).map((m) => ({ ...m, anexos: m.anexos?.map((a) => ({ tipo: a.tipo, nome: a.nome })) }));
        localStorage.setItem(`${K_CHAT}.${usuario.id}`, JSON.stringify(leve));
      }
    }
  },

  limparChat: () => {
    const { usuario } = get();
    if (usuario) localStorage.removeItem(`${K_CHAT}.${usuario.id}`);
    set({ mensagens: [] });
  },

  avisar: (texto, tipo = 'ok') => {
    set({ aviso: { texto, tipo } });
    window.setTimeout(() => { if (get().aviso?.texto === texto) set({ aviso: null }); }, 4500);
  },
}));
