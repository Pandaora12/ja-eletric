import { useEffect, useRef, useState } from 'react';
import { dadosParaModelo } from '../lib/modelos';
import { MODELO, type Documento } from '../tipos';

declare global { interface Window { __JA_DOCS?: Record<string, any> } }

/** Mostra o documento exatamente como sai no PDF (mesmo modelo HTML), atualizando enquanto a pessoa edita. */
export function Previa({ doc }: { doc: Documento }) {
  const modelo = MODELO[doc.tipo];
  const caixa = useRef<HTMLDivElement>(null);
  const quadro = useRef<HTMLIFrameElement>(null);
  const [escala, setEscala] = useState(0.6);
  const [altura, setAltura] = useState(1123);
  const [versao, setVersao] = useState(0);
  const dados = JSON.stringify(dadosParaModelo(doc));

  // recarrega a prévia 500 ms depois da última alteração
  useEffect(() => {
    const k = 'previa-' + doc.id;
    window.__JA_DOCS = { ...(window.__JA_DOCS || {}), [k]: JSON.parse(dados) };
    const t = window.setTimeout(() => setVersao((v) => v + 1), 500);
    return () => window.clearTimeout(t);
  }, [dados, doc.id]);

  useEffect(() => {
    const el = caixa.current;
    if (!el) return;
    const ro = new ResizeObserver(() => setEscala(Math.min(1, (el.clientWidth - 36) / 794)));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  function carregou() {
    const f = quadro.current;
    const medir = () => {
      const d = f?.contentDocument;
      if (d && (f!.contentWindow as any)?.__PRONTO) setAltura(d.documentElement.scrollHeight);
      else window.setTimeout(medir, 150);
    };
    medir();
  }

  if (!modelo) return null;
  return (
    <div className="papel" ref={caixa}>
      <div style={{ width: 794 * escala, height: altura * escala, margin: '0 auto' }}>
        <iframe
          ref={quadro} key={versao} title="Prévia do documento" onLoad={carregou}
          src={`/modelos/${modelo}.html?k=previa-${doc.id}&v=${versao}`}
          style={{ width: 794, height: altura, transform: `scale(${escala})` }}
        />
      </div>
    </div>
  );
}
