import { useMemo } from 'react';
import { useApp } from '../estado';
import { brl, brlCurto, dataBR } from '../lib/formato';
import { NOME_TIPO, type TipoDoc } from '../tipos';

const COR: Record<TipoDoc, string> = {
  orcamento: 'linear-gradient(135deg,#00a859,#c8f53b)',
  relatorio: 'linear-gradient(135deg,#0057ff,#00d1ff)',
  recibo: 'linear-gradient(135deg,#ff6a00,#ffc300)',
  comprovante: 'linear-gradient(135deg,#ff2d3a,#c026d3)',
};
const SIGLA: Record<TipoDoc, string> = { orcamento: 'ORC', relatorio: 'REL', recibo: 'REC', comprovante: 'PIX' };
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Painéis do lado direito do Início. */
export function Painel() {
  const { documentos, abrir, irPara } = useApp();

  const n = useMemo(() => {
    const agora = new Date();
    const doMes = (d: string) => { const x = new Date(d); return x.getMonth() === agora.getMonth() && x.getFullYear() === agora.getFullYear(); };
    const orc = documentos.filter((d) => d.tipo === 'orcamento');
    const rec = documentos.filter((d) => d.tipo === 'recibo');
    const meses = Array.from({ length: 6 }, (_, i) => {
      const dt = new Date(agora.getFullYear(), agora.getMonth() - 5 + i, 1);
      const total = rec.filter((d) => { const x = new Date(d.criado_em); return x.getMonth() === dt.getMonth() && x.getFullYear() === dt.getFullYear(); })
        .reduce((a, d) => a + (d.valor || 0), 0);
      return { nome: MESES[dt.getMonth()], total };
    });
    return {
      recebidoMes: rec.filter((d) => doMes(d.criado_em)).reduce((a, d) => a + (d.valor || 0), 0),
      recibosMes: rec.filter((d) => doMes(d.criado_em)).length,
      orcMes: orc.filter((d) => doMes(d.criado_em)).length,
      orcValorMes: orc.filter((d) => doMes(d.criado_em)).reduce((a, d) => a + (d.valor || 0), 0),
      aguardando: orc.filter((d) => d.status === 'aguardando'),
      aprovados: orc.filter((d) => d.status === 'aprovado').length,
      pix: documentos.filter((d) => d.tipo === 'comprovante').length,
      meses,
      max: Math.max(1, ...meses.map((m) => m.total)),
    };
  }, [documentos]);

  const recentes = documentos.slice(0, 6);
  const mesNome = new Date().toLocaleDateString('pt-BR', { month: 'long' });

  return (
    <aside className="painel">
      <div className="kpi-grande vidro">
        <div className="rotulo">Recebido em {mesNome}</div>
        <div className="v">{brl(n.recebidoMes)}</div>
        <div className="s">{n.recibosMes} recibo{n.recibosMes === 1 ? '' : 's'} emitido{n.recibosMes === 1 ? '' : 's'} este mês</div>
      </div>

      <div className="kpis-mini">
        <div className="kpi-mini vidro" onClick={() => irPara('orcamento')}>
          <div className="rotulo">Orçado no mês</div>
          <div className="v">{brlCurto(n.orcValorMes)}</div>
          <div className="s">{n.orcMes} orçamento{n.orcMes === 1 ? '' : 's'}</div>
          <div className="barra" style={{ background: COR.orcamento }} />
        </div>
        <div className="kpi-mini vidro" onClick={() => irPara('orcamento')}>
          <div className="rotulo">Aguardando</div>
          <div className="v">{n.aguardando.length}</div>
          <div className="s">{n.aprovados} aprovado{n.aprovados === 1 ? '' : 's'}</div>
          <div className="barra" style={{ background: 'linear-gradient(90deg,#ff2d55,#ff7a00)' }} />
        </div>
      </div>

      <div className="grafico vidro">
        <div className="rotulo">Recebido por mês</div>
        <div className="barras">
          {n.meses.map((m) => (
            <div key={m.nome} title={brl(m.total)}>
              <i style={{ height: `${(m.total / n.max) * 100}%` }} />
              <span>{m.nome}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="recentes vidro">
        <div className="rotulo" style={{ marginBottom: 6 }}>Últimos documentos</div>
        {recentes.length === 0 && <div style={{ color: 'var(--texto-3)', padding: '10px 0' }}>Nada ainda. Peça seu primeiro documento no chat.</div>}
        {recentes.map((d) => (
          <div key={d.id} className="recente" onClick={() => abrir(d, d.tipo)}>
            <span className="tag-tipo" style={{ background: COR[d.tipo] }}>{SIGLA[d.tipo]}</span>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="nome">{d.cliente || d.titulo}</div>
              <div className="sub">{NOME_TIPO[d.tipo]} · {d.numero} · {dataBR(d.criado_em.slice(0, 10))}</div>
            </div>
            {d.valor != null && <b style={{ fontFamily: 'var(--f-rot)' }}>{brl(d.valor)}</b>}
          </div>
        ))}
      </div>
    </aside>
  );
}
