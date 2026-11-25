import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { dadosParaModelo } from '../lib/modelos';
import { MODELO, type Documento } from '../tipos';
import { IconeExpandir, IconeX } from './Icones';

declare global { interface Window { __JA_DOCS?: Record<string, any> } }

/**
 * Mostra o documento exatamente como sai no PDF (mesmo modelo HTML).
 * A página do modelo carrega uma vez; a cada tecla os dados novos são enviados e o documento se redesenha na hora.
 */
function Papel({ doc, larguraMax }: { doc: Documento; larguraMax?: number }) {
  const modelo = MODELO[doc.tipo];
  const caixa = useRef<HTMLDivElement>(null);
  const quadro = useRef<HTMLIFrameElement>(null);
  const [escala, setEscala] = useState(0.6);
  const [altura, setAltura] = useState(1123);
  const [chave] = useState(() => 'previa-' + doc.id + '-' + Math.random().toString(36).slice(2, 7));
  const dados = dadosParaModelo(doc);
  const json = JSON.stringify(dados);

  // dados iniciais (lidos pela página do modelo ao carregar)
  if (!window.__JA_DOCS?.[chave]) window.__JA_DOCS = { ...(window.__JA_DOCS || {}), [chave]: dados };

  // envia cada alteração direto para a página do modelo
  useEffect(() => {
    window.__JA_DOCS = { ...(window.__JA_DOCS || {}), [chave]: JSON.parse(json) };
    quadro.current?.contentWindow?.postMessage({ tipo: 'ja-doc', dados: JSON.parse(json) }, '*');
  }, [json, chave]);

  // altura do documento (muda quando o orçamento ganha páginas)
  useEffect(() => {
    const ouvir = (e: MessageEvent) => {
      if (e.source === quadro.current?.contentWindow && e.data?.tipo === 'ja-altura' && e.data.h > 0) setAltura(e.data.h);
    };
    window.addEventListener('message', ouvir);
    return () => { window.removeEventListener('message', ouvir); if (window.__JA_DOCS) delete window.__JA_DOCS[chave]; };
  }, [chave]);

  useEffect(() => {
    const el = caixa.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setEscala(Math.min(larguraMax ?? 1, (el.clientWidth - 36) / 794)));
    ro.observe(el);
    return () => ro.disconnect();
  }, [larguraMax]);

  if (!modelo) return null;
  return (
    <div className="papel" ref={caixa}>
      <div style={{ width: 794 * escala, height: altura * escala, margin: '0 auto' }}>
        <iframe
          ref={quadro} title="Prévia do documento"
          src={`/modelos/${modelo}.html?k=${chave}`}
          style={{ width: 794, height: altura, transform: `scale(${escala})` }}
        />
      </div>
    </div>
  );
}

export function Previa({ doc }: { doc: Documento }) {
  const [cheia, setCheia] = useState(false);
  useEffect(() => {
    if (!cheia) return;
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setCheia(false); };
    window.addEventListener('keydown', esc);
    return () => window.removeEventListener('keydown', esc);
  }, [cheia]);

  return (
    <>
      <div className="cab-previa">
        <span className="rotulo">Prévia · igual ao PDF</span>
        <button className="btn pequeno" onClick={() => setCheia(true)} title="Ver em tela cheia"><IconeExpandir />Tela cheia</button>
      </div>
      <Papel doc={doc} />
      {cheia && createPortal(
        <div className="tela-cheia" onClick={() => setCheia(false)}>
          <div className="tela-cheia-topo" onClick={(e) => e.stopPropagation()}>
            <span><b>{doc.numero}</b>{doc.cliente ? ` · ${doc.cliente}` : ''}</span>
            <button className="btn icone-btn" title="Fechar (Esc)" onClick={() => setCheia(false)}><IconeX /></button>
          </div>
          <div className="tela-cheia-papel" onClick={(e) => e.stopPropagation()}>
            <Papel doc={doc} larguraMax={1.35} />
          </div>
        </div>,
        document.body,
      )}
    </>
  );
}
