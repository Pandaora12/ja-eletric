import { supabase } from './supabase';
import { hojeISO } from './formato';
import type { Anexo, Aba, Mensagem, TipoDoc } from '../tipos';

export interface RespostaIA { texto: string; documento: { tipo: TipoDoc; dados: Record<string, any> } | null }

/** Envia a conversa para a função "assistente" no Supabase (a chave da IA fica lá, nunca no app). */
export async function perguntarIA(historico: Mensagem[], aba: Aba): Promise<RespostaIA> {
  if (!supabase) {
    throw new Error('A IA precisa do Supabase configurado (veja docs/CONFIGURAR.md). Enquanto isso, use "Preencher formulário".');
  }
  // anexos só da última mensagem da pessoa: economiza envio e custo
  const ultimoUsuario = historico.map((m) => m.papel).lastIndexOf('usuario');
  const mensagens = historico
    .filter((m) => !m.erro)
    .map((m, i) => ({
      papel: m.papel,
      texto: m.texto + (i !== ultimoUsuario && m.anexos?.length ? `\n[anexos enviados antes: ${m.anexos.map((a) => a.nome).join(', ')}]` : ''),
      anexos: i === ultimoUsuario ? m.anexos : undefined,
    }));
  const { data, error } = await supabase.functions.invoke('assistente', { body: { mensagens, aba, hoje: hojeISO() } });
  if (error) {
    let msg = 'Não consegui falar com o assistente. Verifique a internet.';
    try { const b = await (error as any).context?.json?.(); if (b?.erro) msg = b.erro; } catch { /* segue */ }
    throw new Error(msg);
  }
  if (data?.erro) throw new Error(data.erro);
  return data as RespostaIA;
}

/** Lê um arquivo escolhido pela pessoa e prepara para enviar à IA. */
export async function lerAnexo(f: File): Promise<Anexo> {
  const nome = f.name;
  const base64 = async () => {
    const buf = new Uint8Array(await f.arrayBuffer());
    let s = '';
    for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode(...buf.subarray(i, i + 0x8000));
    return btoa(s);
  };
  if (f.type.startsWith('image/')) {
    if (f.size > 4.5 * 1024 * 1024) return { tipo: 'imagem', nome, mime: 'image/jpeg', base64: await reduzirImagem(f) };
    return { tipo: 'imagem', nome, mime: f.type, base64: await base64() };
  }
  if (f.type === 'application/pdf' || nome.toLowerCase().endsWith('.pdf')) return { tipo: 'pdf', nome, mime: 'application/pdf', base64: await base64() };
  if (nome.toLowerCase().endsWith('.docx')) {
    const mammoth = await import('mammoth');
    const r = await mammoth.extractRawText({ arrayBuffer: await f.arrayBuffer() });
    return { tipo: 'texto', nome, texto: r.value };
  }
  if (f.type.startsWith('text/') || /\.(txt|csv|md)$/i.test(nome)) return { tipo: 'texto', nome, texto: await f.text() };
  throw new Error(`Não sei ler "${nome}". Envie foto, PDF, Word (.docx) ou texto.`);
}

async function reduzirImagem(f: File): Promise<string> {
  const img = await createImageBitmap(f);
  const k = Math.min(1, 2000 / Math.max(img.width, img.height));
  const c = document.createElement('canvas');
  c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
  c.getContext('2d')!.drawImage(img, 0, 0, c.width, c.height);
  return c.toDataURL('image/jpeg', 0.85).split(',')[1];
}
