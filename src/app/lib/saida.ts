/**
 * Saída dos documentos: baixar o PDF para o computador e enviar no WhatsApp.
 * No app de computador (Tauri) o PDF é gerado pelo Edge/Chrome do sistema, a partir dos mesmos modelos da prévia.
 * No navegador (desenvolvimento), abre a janela de impressão como alternativa.
 */
import { MODELO, NOME_TIPO, type Documento } from '../tipos';
import { dadosParaModelo } from './modelos';

export const noApp = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

const invocar = async <T,>(cmd: string, args: Record<string, unknown>) => (await import('@tauri-apps/api/core')).invoke<T>(cmd, args);

export function nomeArquivo(doc: Documento) {
  return `${NOME_TIPO[doc.tipo]} ${doc.numero || ''} ${doc.cliente || ''}`.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim();
}

function modeloDe(doc: Documento) {
  const m = MODELO[doc.tipo];
  if (!m) throw new Error('Este tipo de documento ainda não tem modelo de PDF.');
  return m;
}

/**
 * Abre a janela "Salvar como" primeiro (resposta imediata) e depois gera o PDF direto no lugar escolhido.
 * `aoGerar` avisa a tela que o PDF começou a ser gerado (para mostrar "Gerando…").
 */
export async function baixarPdf(doc: Documento, aoGerar?: () => void): Promise<string | null> {
  const modelo = modeloDe(doc);
  if (!noApp) { imprimirNoNavegador(doc); return null; }
  const { save } = await import('@tauri-apps/plugin-dialog');
  const destino = await save({ title: 'Salvar PDF', defaultPath: nomeArquivo(doc) + '.pdf', filters: [{ name: 'PDF', extensions: ['pdf'] }] });
  if (!destino) return null;
  aoGerar?.();
  const caminho = await invocar<string>('salvar_pdf', { modelo, dados: dadosParaModelo(doc), destino });
  try { await invocar('mostrar_na_pasta', { caminho }); } catch { /* opcional */ }
  return caminho;
}

/**
 * Abre o WhatsApp na hora (com a mensagem pronta), gera o PDF e deixa o arquivo copiado:
 * a pessoa escolhe o contato e aperta Ctrl+V para colar o PDF na conversa.
 * O WhatsApp não aceita anexar arquivo por link, por isso o PDF vai pela área de transferência.
 */
export async function enviarWhatsApp(doc: Documento, mensagem: string): Promise<{ onde: string; copiado: boolean; caminho?: string }> {
  const modelo = modeloDe(doc);
  if (!noApp) {
    window.open(`https://web.whatsapp.com/send?text=${encodeURIComponent(mensagem)}`, '_blank');
    return { onde: 'web', copiado: false };
  }
  const onde = await invocar<string>('abrir_whatsapp', { texto: mensagem });
  const caminho = await invocar<string>('gerar_pdf', { modelo, dados: dadosParaModelo(doc), nome: nomeArquivo(doc) });
  let copiado = false;
  try { await invocar('copiar_arquivo', { caminho }); copiado = true; } catch { copiado = false; }
  if (!copiado) { try { await invocar('mostrar_na_pasta', { caminho }); } catch { /* opcional */ } }
  return { onde, copiado, caminho };
}

export function imprimirNoNavegador(doc: Documento) {
  const modelo = MODELO[doc.tipo];
  if (!modelo) return;
  const k = 'imp-' + doc.id;
  (window as any).__JA_DOCS = { ...((window as any).__JA_DOCS || {}), [k]: dadosParaModelo(doc) };
  const w = window.open(`/modelos/${modelo}.html?k=${k}`, '_blank');
  if (w) w.addEventListener('load', () => setTimeout(() => w.print(), 1200));
}
