/**
 * Gera o PDF dentro do próprio app, sem navegador externo:
 * abre o modelo numa moldura invisível, fotografa cada página A4 em alta resolução e monta o PDF.
 */
import { MODELO, type Documento } from '../tipos';
import { dadosParaModelo } from './modelos';

const LARGURA = 794; // A4 a 96 dpi
const ALTURA = 1123;

export async function gerarPdfBytes(doc: Documento): Promise<Uint8Array> {
  const modelo = MODELO[doc.tipo];
  if (!modelo) throw new Error('Este tipo de documento ainda não tem modelo de PDF.');
  const chave = 'pdf-' + doc.id + '-' + Date.now();
  (window as any).__JA_DOCS = { ...((window as any).__JA_DOCS || {}), [chave]: dadosParaModelo(doc) };

  const quadro = document.createElement('iframe');
  quadro.style.cssText = `position:fixed;left:-10000px;top:0;width:${LARGURA}px;height:${ALTURA}px;border:0;visibility:hidden`;
  quadro.src = `/modelos/${modelo}.html?k=${chave}`;
  document.body.appendChild(quadro);
  try {
    // espera o modelo terminar de desenhar
    const inicio = Date.now();
    await new Promise<void>((ok, falha) => {
      const ver = () => {
        const w = quadro.contentWindow as any;
        if (w && w.__PRONTO) return ok();
        if (Date.now() - inicio > 20000) return falha(new Error('O modelo do documento demorou demais para carregar.'));
        setTimeout(ver, 120);
      };
      ver();
    });
    const docQ = quadro.contentDocument!;
    await (docQ as any).fonts?.ready;
    await new Promise((r) => setTimeout(r, 250));

    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([import('html2canvas-pro'), import('jspdf')]);
    const paginas = Array.from(docQ.querySelectorAll<HTMLElement>('.page'));
    if (!paginas.length) throw new Error('O documento ficou vazio.');
    const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait', compress: true });
    for (let i = 0; i < paginas.length; i++) {
      const canvas = await html2canvas(paginas[i], {
        scale: 2.4, backgroundColor: '#ECEFE6', useCORS: true, logging: false,
        width: LARGURA, height: ALTURA, windowWidth: LARGURA, windowHeight: ALTURA,
      });
      if (i > 0) pdf.addPage('a4', 'portrait');
      pdf.addImage(canvas.toDataURL('image/jpeg', 0.92), 'JPEG', 0, 0, 210, 297, undefined, 'FAST');
    }
    return new Uint8Array(pdf.output('arraybuffer'));
  } finally {
    quadro.remove();
    delete (window as any).__JA_DOCS[chave];
  }
}
