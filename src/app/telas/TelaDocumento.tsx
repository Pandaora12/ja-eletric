import { useMemo, useState } from 'react';
import { useApp } from '../estado';
import { baixarPdf, enviarWhatsApp, noApp } from '../lib/saida';
import { brl, dataBR } from '../lib/formato';
import { NOME_TIPO, type Documento, type TipoDoc } from '../tipos';
import { Editor } from '../componentes/Editores';
import { Previa } from '../componentes/Previa';
import { IconeBaixar, IconeChat, IconeForm, IconeImprimir, IconeLixo, IconeMais, IconeWhats } from '../componentes/Icones';

const STATUS: Record<TipoDoc, [Documento['status'], string][]> = {
  orcamento: [['rascunho', 'Rascunho'], ['aguardando', 'Aguardando'], ['aprovado', 'Aprovado'], ['enviado', 'Enviado']],
  recibo: [['pago', 'Pago'], ['enviado', 'Enviado'], ['rascunho', 'Rascunho']],
  comprovante: [['rascunho', 'Rascunho'], ['enviado', 'Enviado'], ['pago', 'Pago']],
  relatorio: [['rascunho', 'Rascunho']],
};

const TEXTO_TELA: Record<TipoDoc, { titulo: React.ReactNode; frase: string }> = {
  orcamento: { titulo: <>Orça<em>mentos</em></>, frase: 'Propostas no padrão Georevest, com setores, incluso e responsabilidades.' },
  recibo: { titulo: <>Re<em>cibos</em></>, frase: 'Recibo pronto em segundos, com valor por extenso e Pix.' },
  comprovante: { titulo: <>Compro<em>vante</em></>, frase: 'Dados para pagamento via Pix, com ou sem valor.' },
  relatorio: { titulo: <>Rela<em>tórios</em></>, frase: '' },
};

export function TelaDocumento({ tipo }: { tipo: TipoDoc }) {
  const { documentos, abertos, abrir, criar, atualizar, excluir, avisar, irPara } = useApp();
  const [busca, setBusca] = useState('');
  const [ocupado, setOcupado] = useState<'' | 'pdf' | 'whats'>('');
  const lista = useMemo(() => {
    const b = busca.trim().toLowerCase();
    return documentos.filter((d) => d.tipo === tipo && (!b || `${d.numero} ${d.cliente} ${d.titulo}`.toLowerCase().includes(b)));
  }, [documentos, tipo, busca]);
  const doc = documentos.find((d) => d.id === abertos[tipo] && d.tipo === tipo) ?? null;

  if (tipo === 'relatorio') return <RelatorioEmBreve />;

  async function pdf(d: Documento) {
    setOcupado('pdf');
    try { const c = await baixarPdf(d); if (c) avisar('PDF salvo em ' + c); }
    catch (e: any) { avisar(e.message, 'erro'); }
    finally { setOcupado(''); }
  }

  async function whats(d: Documento) {
    setOcupado('whats');
    try {
      const msg = `Olá! Segue o ${NOME_TIPO[d.tipo].toLowerCase()} ${d.numero} da J.A Eletricidades${d.valor ? ` (${brl(d.valor)})` : ''}.`;
      const r = await enviarWhatsApp(d, msg);
      if (!noApp) avisar('No app de computador, o PDF já vai copiado para colar na conversa.');
      else avisar(r.copiado ? 'WhatsApp aberto. Escolha o contato e aperte Ctrl+V para colar o PDF.' : 'WhatsApp aberto. Arraste o PDF da pasta que abriu para a conversa.');
      if (d.status === 'rascunho' || d.status === 'aguardando') atualizar({ ...d, status: d.tipo === 'orcamento' ? 'aguardando' : 'enviado' });
    } catch (e: any) { avisar(e.message, 'erro'); }
    finally { setOcupado(''); }
  }

  return (
    <div className="tela-doc">
      <section className="lista-docs vidro">
        <div className="cab">
          <h1 className="titulo-tela" style={{ fontSize: 23 }}>{TEXTO_TELA[tipo].titulo}</h1>
          <div style={{ color: 'var(--texto-2)', fontSize: 15, marginTop: -6 }}>{TEXTO_TELA[tipo].frase}</div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn forte" style={{ flex: 1 }} onClick={() => irPara('inicio')}><IconeChat />Pelo chat</button>
            <button className="btn" title="Preencher formulário" onClick={() => criar(tipo).then((d) => abrir(d, tipo))}><IconeForm /><IconeMais /></button>
          </div>
          <input className="entrada" placeholder="Buscar por cliente ou número" value={busca} onChange={(e) => setBusca(e.target.value)} />
        </div>
        <div className="itens">
          {lista.length === 0 && <div className="vazio-lista">{busca ? 'Nada encontrado.' : 'Nenhum documento ainda.'}</div>}
          {lista.map((d) => (
            <button key={d.id} className={`item-doc ${doc?.id === d.id ? 'on' : ''}`} onClick={() => abrir(d, tipo)}>
              <div className="l1"><span>{d.numero}</span><span>{dataBR(d.dados?.data || d.criado_em.slice(0, 10))}</span></div>
              <div className="nome">{d.cliente || 'Sem cliente'}</div>
              <div className="l1" style={{ alignItems: 'center' }}>
                <span className={`status ${d.status}`}>{STATUS[tipo].find((s) => s[0] === d.status)?.[1] ?? d.status}</span>
                {d.valor != null && <span className="valor">{brl(d.valor)}</span>}
              </div>
            </button>
          ))}
        </div>
      </section>

      {!doc ? (
        <section className="vidro" style={{ display: 'flex' }}>
          <div className="vazio-doc">
            <div className="icone-grande"><IconeForm /></div>
            <h2 className="titulo-tela" style={{ fontSize: 30 }}>Novo {NOME_TIPO[tipo].toLowerCase()}</h2>
            <p style={{ color: 'var(--texto-2)', fontSize: 17, margin: 0 }}>Descreva no chat do Início (por voz ou texto) ou preencha o formulário. Depois é só baixar o PDF ou mandar no WhatsApp.</p>
            <div style={{ display: 'flex', gap: 10 }}>
              <button className="btn forte" onClick={() => irPara('inicio')}><IconeChat />Descrever no chat</button>
              <button className="btn" onClick={() => criar(tipo).then((d) => abrir(d, tipo))}><IconeForm />Preencher formulário</button>
            </div>
          </div>
        </section>
      ) : (
        <section className="area-doc">
          <div className="barra-acoes vidro">
            <div className="ident">
              <b>{doc.numero}</b>
              <span>{doc.cliente || 'Sem cliente'}{doc.valor != null ? ` · ${brl(doc.valor)}` : ''}</span>
            </div>
            <select className="entrada" style={{ width: 210, minHeight: 50 }} value={doc.status} onChange={(e) => atualizar({ ...doc, status: e.target.value as Documento['status'] })}>
              {STATUS[tipo].map(([v, n]) => <option key={v} value={v}>{n}</option>)}
            </select>
            <button className="btn acento" disabled={!!ocupado} onClick={() => pdf(doc)}>{noApp ? <IconeBaixar /> : <IconeImprimir />}{ocupado === 'pdf' ? 'Gerando…' : noApp ? 'Baixar PDF' : 'Imprimir / PDF'}</button>
            <button className="btn whats" disabled={!!ocupado} onClick={() => whats(doc)}><IconeWhats />{ocupado === 'whats' ? 'Preparando…' : 'Enviar no WhatsApp'}</button>
            <button className="btn icone-btn perigo" title="Excluir" onClick={async () => { if (confirm(`Excluir ${doc.numero}? Não dá para desfazer.`)) { await excluir(doc); avisar('Documento excluído.'); } }}><IconeLixo /></button>
          </div>
          <div className="divide">
            <div className="editor vidro">
              <Editor doc={doc} muda={(dados) => atualizar({ ...doc, dados })} />
            </div>
            <div className="previa vidro">
              <div className="cab-previa"><span className="rotulo">Prévia · igual ao PDF</span><span style={{ fontSize: 13, color: 'var(--texto-3)' }}>atualiza enquanto você edita</span></div>
              <Previa doc={doc} />
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
          Esta aba recebe o modelo de relatório que vamos montar juntos (a 4ª skill). Ele vai usar o mesmo padrão visual dos orçamentos.
          Assim que o modelo estiver pronto, ele entra aqui com chat, formulário, prévia, PDF e WhatsApp.
        </p>
        <span className="chip" style={{ fontSize: 14 }}>Em breve</span>
      </div>
    </section>
  );
}
