/**
 * Saída dos documentos: baixar o PDF para o computador e enviar no WhatsApp.
 * O PDF é gerado dentro do app (pdfLocal.ts), sem depender do Edge ou do Chrome.
 */
import { NOME_TIPO, type Documento } from '../tipos';
import { gerarPdfBytes } from './pdfLocal';

export const noApp = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
/** app instalado no celular (Android) */
export const noCelularApp = noApp && /Android|iPhone|iPad/i.test(navigator.userAgent);

/** Android: salva pela janela do sistema (pode ser a pasta Download) usando o plugin de arquivos. */
async function salvarNoCelular(bytes: Uint8Array, nome: string): Promise<string | null> {
  const { save } = await import('@tauri-apps/plugin-dialog');
  const { writeFile, BaseDirectory, mkdir } = await import('@tauri-apps/plugin-fs');
  try {
    const destino = await save({ title: 'Salvar PDF', defaultPath: nome, filters: [{ name: 'PDF', extensions: ['pdf'] }] });
    if (!destino) return null;
    await writeFile(destino, bytes);
    return destino;
  } catch {
    // alternativa: pasta Documentos do app
    try { await mkdir('', { baseDir: BaseDirectory.Document, recursive: true }); } catch { /* já existe */ }
    await writeFile(nome, bytes, { baseDir: BaseDirectory.Document });
    return 'Documentos/' + nome;
  }
}

const invocar = async <T,>(cmd: string, args: Record<string, unknown>) => (await import('@tauri-apps/api/core')).invoke<T>(cmd, args);

export function nomeArquivo(doc: Documento) {
  return `${NOME_TIPO[doc.tipo]} ${doc.numero || ''} ${doc.cliente || ''}`.replace(/[\\/:*?"<>|]+/g, '-').replace(/\s+/g, ' ').trim().slice(0, 120);
}

function baixarNoNavegador(bytes: Uint8Array, nome: string) {
  const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type: 'application/pdf' }));
  const a = document.createElement('a');
  a.href = url; a.download = nome; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 5000);
}

/** Abre a janela "Salvar como", gera o PDF e grava no lugar escolhido. */
export async function baixarPdf(doc: Documento, aoGerar?: () => void): Promise<string | null> {
  const nome = nomeArquivo(doc) + '.pdf';
  if (!noApp) { aoGerar?.(); baixarNoNavegador(await gerarPdfBytes(doc), nome); return nome; }
  if (noCelularApp) { aoGerar?.(); return salvarNoCelular(await gerarPdfBytes(doc), nome); }
  const { save } = await import('@tauri-apps/plugin-dialog');
  const destino = await save({ title: 'Salvar PDF', defaultPath: nome, filters: [{ name: 'PDF', extensions: ['pdf'] }] });
  if (!destino) return null;
  aoGerar?.();
  const bytes = await gerarPdfBytes(doc);
  await invocar('gravar_arquivo', { caminho: destino, bytes: Array.from(bytes) });
  try { await invocar('mostrar_na_pasta', { caminho: destino }); } catch { /* opcional */ }
  return destino;
}

/**
 * Gera o PDF, deixa o arquivo copiado e abre o WhatsApp: a pessoa escolhe o contato e aperta Ctrl+V.
 * (O WhatsApp não aceita anexar arquivo por link, por isso o PDF vai pela área de transferência.)
 */
export async function enviarWhatsApp(doc: Documento, mensagem: string): Promise<{ onde: string; copiado: boolean; caminho?: string }> {
  const bytes = await gerarPdfBytes(doc);
  if (!noApp) {
    // celular: abre o "compartilhar" do telefone já com o PDF anexado (escolhe WhatsApp e o contato)
    const arquivo = new File([bytes as BlobPart], nomeArquivo(doc) + '.pdf', { type: 'application/pdf' });
    const nav = navigator as Navigator & { canShare?: (d: ShareData) => boolean };
    if (nav.share && nav.canShare?.({ files: [arquivo] })) {
      try { await nav.share({ files: [arquivo], text: mensagem }); return { onde: 'compartilhar', copiado: true }; }
      catch (e: any) { if (e?.name === 'AbortError') return { onde: 'cancelado', copiado: true }; }
    }
    baixarNoNavegador(bytes, nomeArquivo(doc) + '.pdf');
    const celular = /Android|iPhone|iPad/i.test(navigator.userAgent);
    window.open(celular ? `https://wa.me/?text=${encodeURIComponent(mensagem)}` : `https://web.whatsapp.com/send?text=${encodeURIComponent(mensagem)}`, '_blank');
    return { onde: 'web', copiado: false };
  }
  if (noCelularApp) {
    const salvo = await salvarNoCelular(bytes, nomeArquivo(doc) + '.pdf');
    if (!salvo) return { onde: 'cancelado', copiado: true };
    await invocar<string>('abrir_whatsapp', { texto: mensagem });
    return { onde: 'celular', copiado: false, caminho: salvo };
  }
  const caminho = await invocar<string>('gravar_documento', { nome: nomeArquivo(doc), bytes: Array.from(bytes) });
  let copiado = false;
  try { await invocar('copiar_arquivo', { caminho }); copiado = true; } catch { copiado = false; }
  const onde = await invocar<string>('abrir_whatsapp', { texto: mensagem });
  if (!copiado) { try { await invocar('mostrar_na_pasta', { caminho }); } catch { /* opcional */ } }
  return { onde, copiado, caminho };
}
