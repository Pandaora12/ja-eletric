/**
 * Saída dos documentos: gerar PDF, baixar para o computador e enviar no WhatsApp.
 * No app de computador (Tauri) o PDF é gerado pelo Edge/Chrome do sistema, a partir dos mesmos modelos da prévia.
 * No navegador (desenvolvimento), abre a janela de impressão como alternativa.
 */
import { MODELO, NOME_TIPO, type Documento } from '../tipos';
import { dadosParaModelo } from './modelos';

export const noApp = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

async function tauri() {
  const core = await import('@tauri-apps/api/core');
  return core;
}

export function nomeArquivo(doc: Documento) {
  return `${NOME_TIPO[doc.tipo]} ${doc.numero || ''} ${doc.cliente || ''}`.replace(/\s+/g, ' ').trim();
}

/** Gera o PDF e devolve o caminho (no app). */
export async function gerarPdf(doc: Documento): Promise<string> {
  const modelo = MODELO[doc.tipo];
  if (!modelo) throw new Error('Este tipo de documento ainda não tem modelo de PDF.');
  if (!noApp) throw new Error('O PDF é gerado no app de computador. No navegador, use "Imprimir" e escolha "Salvar como PDF".');
  const { invoke } = await tauri();
  return invoke<string>('gerar_pdf', { modelo, dados: dadosParaModelo(doc), nome: nomeArquivo(doc) });
}

/** Pergunta onde salvar e copia o PDF para lá. */
export async function baixarPdf(doc: Documento): Promise<string | null> {
  if (!noApp) { imprimirNoNavegador(doc); return null; }
  const caminho = await gerarPdf(doc);
  const { save } = await import('@tauri-apps/plugin-dialog');
  const destino = await save({ defaultPath: nomeArquivo(doc) + '.pdf', filters: [{ name: 'PDF', extensions: ['pdf'] }] });
  if (!destino) return null;
  const { invoke } = await tauri();
  await invoke('salvar_copia', { origem: caminho, destino });
  try { const { revealItemInDir } = await import('@tauri-apps/plugin-opener'); await revealItemInDir(destino); } catch { /* opcional */ }
  return destino;
}

/**
 * Gera o PDF, deixa o arquivo copiado (Ctrl+V cola na conversa) e abre o WhatsApp para a pessoa escolher o contato.
 * O WhatsApp não aceita anexar arquivo por link, por isso o arquivo vai pela área de transferência.
 */
export async function enviarWhatsApp(doc: Documento, mensagem: string): Promise<{ copiado: boolean }> {
  const texto = encodeURIComponent(mensagem);
  if (!noApp) { window.open(`https://web.whatsapp.com/send?text=${texto}`, '_blank'); return { copiado: false }; }
  const caminho = await gerarPdf(doc);
  const { invoke } = await tauri();
  let copiado = false;
  try { await invoke('copiar_arquivo', { caminho }); copiado = true; } catch { copiado = false; }
  const { openUrl, revealItemInDir } = await import('@tauri-apps/plugin-opener');
  try { await openUrl(`whatsapp://send?text=${texto}`); } catch { await openUrl(`https://web.whatsapp.com/send?text=${texto}`); }
  if (!copiado) { try { await revealItemInDir(caminho); } catch { /* opcional */ } }
  return { copiado };
}

export function imprimirNoNavegador(doc: Documento) {
  const modelo = MODELO[doc.tipo];
  if (!modelo) return;
  const k = 'imp-' + doc.id;
  (window as any).__JA_DOCS = { ...((window as any).__JA_DOCS || {}), [k]: dadosParaModelo(doc) };
  const w = window.open(`/modelos/${modelo}.html?k=${k}`, '_blank');
  if (w) w.addEventListener('load', () => setTimeout(() => w.print(), 1200));
}
