import { useMemo, useState } from 'react';
import { useApp } from '../estado';
import { baixarPdf, enviarWhatsApp, noApp } from '../lib/saida';
import { brl, dataBR } from '../lib/formato';
import { NOME_TIPO, type Documento, type TipoDoc } from '../tipos';
import { Editor } from '../componentes/Editores';
import { Previa } from '../componentes/Previa';
import { IconeBaixar, IconeBusca, IconeForm, IconeLixo, IconeMais, IconeVoltar, IconeWhats } from '../componentes/Icones';
import { normalizar } from '../componentes/Seletores';
import { FORMAS } from '../lib/formas';

const TEXTO_TELA: Record<TipoDoc, { titulo: React.ReactNode; frase: string }> = {
  orcamento: { titulo: <>Orça<em>mentos</em></>, frase: 'Propostas no padrão Georevest, com setores, incluso e responsabilidades.' },
  recibo: { titulo: <>Re<em>cibos</em></>, frase: 'Recibo pronto em segundos, com valor por extenso e Pix.' },
  comprovante: { titulo: <>Paga<em>mento</em></>, frase: 'Pix, transferência, boleto ou cartão, com ou sem valor.' },
  relatorio: { titulo: <>Rela<em>tórios</em></>, frase: '' },
};

export function TelaDocumento({ tipo }: { tipo: TipoDoc }) {
  const { documentos, abertos, abrir, criar, atualizar, excluir, avisar } = useApp();
  const [busca, setBusca] = useState('');
  const [ocupado, setOcupado] = useState<'' | 'pdf' | 'whats'>('');
  const [vista, setVista] = useState<'editar' | 'previa'>('editar');
  const lista = useMemo(() => {
    const b = normalizar(busca.trim());
    return documentos.filter((d) => d.tipo === tipo && (!b || normalizar(`${d.numero} ${d.cliente} ${d.titulo}`).includes(b)));
  }, [documentos, tipo, busca]);
  const doc = documentos.find((d) => d.id === abertos[tipo] && d.tipo === tipo) ?? null;

  if (tipo === 'relatorio') return <RelatorioEmBreve />;

  async function pdf(d: Documento) {
    try {
      const c = await baixarPdf(d, () => setOcupado('pdf'));
      if (c) avisar(noApp ? 'PDF salvo em ' + c : 'PDF baixado.');
    } catch (e: any) { avisar(String(e?.message ?? e), 'erro'); }
    finally { setOcupado(''); }
  }

  async function whats(d: Documento) {
    setOcupado('whats');
    try {
      const msg = `Olá! Segue o ${NOME_TIPO[d.tipo].toLowerCase()} ${d.numero} da J.A Eletricidades${d.valor ? ` (${brl(d.valor)})` : ''}.`;
      const r = await enviarWhatsApp(d, msg);
      if (r.onde === 'compartilhar' || r.onde === 'cancelado') { /* o próprio celular cuidou do envio */ }
      else if (!noApp) avisar('PDF baixado. Anexe o arquivo na conversa do WhatsApp que abriu.');
      else if (r.onde === 'celular') avisar('PDF salvo. No WhatsApp, escolha o contato e anexe o PDF (clipe › Documento).');
      else if (r.copiado) avisar(`WhatsApp ${r.onde === 'web' ? 'Web aberto no navegador' : 'aberto'}. Escolha o contato e aperte Ctrl+V para colar o PDF.`);
      else avisar('WhatsApp aberto. Arraste o PDF da pasta que abriu para a conversa.');
    } catch (e: any) { avisar(String(e?.message ?? e), 'erro'); }
    finally { setOcupado(''); }
  }

  return (
    <div className={`tela-doc ${doc ? 'tem-aberto' : ''}`}>
      <section className="lista-docs vidro">
        <div className="cab">
          <h1 className="titulo-tela" style={{ fontSize: 23 }}>{TEXTO_TELA[tipo].titulo}</h1>
          <div style={{ color: 'var(--texto-2)', fontSize: 15, marginTop: -6 }}>{TEXTO_TELA[tipo].frase}</div>
          <button className="btn forte" onClick={() => criar(tipo).then((d) => abrir(d, tipo))}><IconeMais />Novo {NOME_TIPO[tipo].toLowerCase()}</button>
          {tipo === 'comprovante' && (
            <div className="formas">
              <span className="rotulo">Forma de pagamento</span>
              {FORMAS.map((f) => (
                <button key={f.tipo} className={`forma ${doc && (doc.dados.forma_tipo || 'pix_pf') === f.tipo ? 'on' : ''}`}
                  onClick={async () => {
                    if (doc) atualizar({ ...doc, dados: { ...doc.dados, forma_tipo: f.tipo } });
                    else { const d = await criar('comprovante', { forma_tipo: f.tipo }); abrir(d, 'comprovante'); }
                  }}>
                  <b>{f.nome}</b><span>{f.sub}</span>
                </button>
              ))}
            </div>
          )}
          <div className="busca"><IconeBusca /><input className="entrada" placeholder="Procurar por cliente ou número" value={busca} onChange={(e) => setBusca(e.target.value)} /></div>
        </div>
        <div className="itens">
          {lista.length === 0 && <div className="vazio-lista">{busca ? 'Nada encontrado.' : 'Nenhum documento ainda.'}</div>}
          {lista.map((d) => (
            <button key={d.id} className={`item-doc ${doc?.id === d.id ? 'on' : ''}`} onClick={() => abrir(d, tipo)}>
              <div className="l1"><span>{d.numero}</span><span>{dataBR(d.dados?.data || d.criado_em.slice(0, 10))}</span></div>
              <div className="nome">{d.cliente || 'Sem cliente'}</div>
              {d.valor != null && <div className="valor">{brl(d.valor)}</div>}
            </button>
          ))}
        </div>
      </section>

      {!doc ? (
        <section className="vidro vazio-detalhe" style={{ display: 'flex' }}>
          <div className="vazio-doc">
            <div className="icone-grande"><IconeForm /></div>
            <h2 className="titulo-tela" style={{ fontSize: 30 }}>Novo {NOME_TIPO[tipo].toLowerCase()}</h2>
            <p style={{ color: 'var(--texto-2)', fontSize: 17, margin: 0 }}>Preencha o formulário e acompanhe a prévia ao lado. Depois é só baixar o PDF ou mandar no WhatsApp.</p>
            <button className="btn forte" onClick={() => criar(tipo).then((d) => abrir(d, tipo))}><IconeMais />Criar agora</button>
          </div>
        </section>
      ) : (
        <section className="area-doc">
          <div className="barra-acoes vidro">
            <button className="btn icone-btn so-celular" title="Voltar para a lista" onClick={() => { abrir(null, tipo); setVista('editar'); }}><IconeVoltar /></button>
            <div className="ident">
              <label className="numero-doc" title="Clique para renomear">
                <input value={doc.numero} onChange={(e) => atualizar({ ...doc, numero: e.target.value.toUpperCase() })} spellCheck={false} />
              </label>
              <span>{doc.cliente || 'Sem cliente'}{doc.valor != null ? ` · ${brl(doc.valor)}` : ''}</span>
            </div>
            <div className="acoes-doc">
              <button className="btn acento" disabled={!!ocupado} onClick={() => pdf(doc)}><IconeBaixar /><span>{ocupado === 'pdf' ? 'Gerando PDF…' : 'Baixar PDF'}</span></button>
              <button className="btn whats" disabled={!!ocupado} onClick={() => whats(doc)}><IconeWhats /><span>{ocupado === 'whats' ? 'Preparando…' : 'WhatsApp'}</span></button>
            <button className="btn icone-btn perigo" title="Excluir" onClick={async () => { if (confirm(`Excluir ${doc.numero}? Não dá para desfazer.`)) { await excluir(doc); avisar('Documento excluído.'); } }}><IconeLixo /></button>
            </div>
          </div>
          <div className="alterna so-celular">
            <button className={vista === 'editar' ? 'on' : ''} onClick={() => setVista('editar')}>Editar</button>
            <button className={vista === 'previa' ? 'on' : ''} onClick={() => setVista('previa')}>Prévia</button>
          </div>
          <div className={`divide vista-${vista}`}>
            <div className="editor vidro">
              <Editor doc={doc} muda={(dados) => atualizar({ ...doc, dados })} />
            </div>
            <div className="previa vidro">
              <Previa key={doc.id} doc={doc} />
            </div>
          </div>
        </section>
      )}
    </div>
  );
}

function RelatorioEmBreve() {
  return (
    <section className="vidro" style={{ height: '100%', display: 'flex' }}>
      <div className="em-breve">
        <div className="vazio-doc" style={{ padding: 0 }}><div className="icone-grande"><IconeForm /></div></div>
        <h1 className="titulo-tela">Rela<em>tórios</em></h1>
        <p style={{ fontSize: 18, color: 'var(--texto-2)', margin: 0 }}>
          Esta aba recebe o modelo de relatório que vamos montar juntos (a 4ª skill). Ele vai usar o mesmo padrão visual dos orçamentos,
          com formulário, prévia, PDF e WhatsApp.
        </p>
        <span className="chip" style={{ fontSize: 14 }}>Em breve</span>
      </div>
    </section>
  );
}
