import { useMemo } from 'react';
import { useApp } from '../estado';
import { brl, brlCurto, dataBR } from '../lib/formato';
import { NOME_PAGAMENTO, NOME_SITUACAO, NOME_TIPO, type TipoDoc } from '../tipos';
import { IconeMais, IconePessoa, IconeSeta } from './Icones';

const COR: Record<TipoDoc, string> = {
  orcamento: '#3f8f4f',
  relatorio: '#2f6f9f',
  recibo: '#c9772b',
  comprovante: '#2a8f8a',
};
const SIGLA: Record<TipoDoc, string> = { orcamento: 'ORC', relatorio: 'REL', recibo: 'REC', comprovante: 'PAG' };
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** Início: só o painel com os números do mês, atalhos, últimos clientes e últimos documentos. */
export function Painel() {
  const { documentos, clientes, abrir, irPara, criar, abrirCliente, novoCliente, perfil } = useApp();

  const n = useMemo(() => {
    const agora = new Date();
    const doMes = (d: string) => { const x = new Date(d); return x.getMonth() === agora.getMonth() && x.getFullYear() === agora.getFullYear(); };
    const dataDoc = (d: { dados: any; criado_em: string }) => (d.dados?.data ? d.dados.data + 'T12:00:00' : d.criado_em);
    const orc = documentos.filter((d) => d.tipo === 'orcamento');
    const rec = documentos.filter((d) => d.tipo === 'recibo');
    const meses = Array.from({ length: 6 }, (_, i) => {
      const dt = new Date(agora.getFullYear(), agora.getMonth() - 5 + i, 1);
      const total = rec.filter((d) => { const x = new Date(dataDoc(d)); return x.getMonth() === dt.getMonth() && x.getFullYear() === dt.getFullYear(); })
        .reduce((a, d) => a + (d.valor || 0), 0);
      return { nome: MESES[dt.getMonth()], total };
    });
    return {
      recebidoMes: rec.filter((d) => doMes(dataDoc(d))).reduce((a, d) => a + (d.valor || 0), 0),
      recibosMes: rec.filter((d) => doMes(dataDoc(d))).length,
      orcMes: orc.filter((d) => doMes(dataDoc(d))).length,
      orcValorMes: orc.filter((d) => doMes(dataDoc(d))).reduce((a, d) => a + (d.valor || 0), 0),
      orcTotal: orc.length,
      aguardando: clientes.filter((c) => c.situacao === 'orcamento').length,
      aprovados: clientes.filter((c) => c.situacao === 'aprovado').length,
      aReceber: clientes.filter((c) => c.pagamento !== 'pago').length,
      meses,
      max: Math.max(1, ...meses.map((m) => m.total)),
    };
  }, [documentos, clientes]);

  const recentes = documentos.slice(0, 6);
  const ultimosClientes = [...clientes].sort((a, b) => b.atualizado_em.localeCompare(a.atualizado_em)).slice(0, 5);
  const mesNome = new Date().toLocaleDateString('pt-BR', { month: 'long' });
  const primeiroNome = (perfil?.nome || '').split(' ')[0];
  const novo = async (t: TipoDoc) => { const d = await criar(t); abrir(d, t); };

  return (
    <div className="inicio-painel">
      <section className="boas-vindas-painel">
        <div>
          <div className="rotulo">Painel · {mesNome}</div>
          <h1 className="titulo-tela">Bem-vindo{primeiroNome ? <>, <em>{primeiroNome}</em></> : ''}</h1>
        </div>
        <div className="atalhos">
          <button className="btn forte" onClick={() => novo('orcamento')}><IconeMais />Orçamento</button>
          <button className="btn" onClick={() => novo('recibo')}><IconeMais />Recibo</button>
          <button className="btn" onClick={() => novo('comprovante')}><IconeMais />Pagamento</button>
          <button className="btn" onClick={async () => { await novoCliente(); irPara('clientes'); }}><IconePessoa />Cliente</button>
        </div>
      </section>

      <div className="kpis-linha">
        <div className="kpi-grande vidro">
          <div className="rotulo">Recebido em {mesNome}</div>
          <div className="v">{brl(n.recebidoMes)}</div>
          <div className="s">{n.recibosMes} recibo{n.recibosMes === 1 ? '' : 's'} emitido{n.recibosMes === 1 ? '' : 's'}</div>
        </div>
        <div className="kpi-mini vidro" onClick={() => irPara('orcamento')}>
          <div className="rotulo">Orçado no mês</div>
          <div className="v">{brlCurto(n.orcValorMes)}</div>
          <div className="s">{n.orcMes} este mês · {n.orcTotal} no total</div>
          <div className="barra" style={{ background: COR.orcamento }} />
        </div>
        <div className="kpi-mini vidro" onClick={() => irPara('clientes')}>
          <div className="rotulo">Orçamentos aguardando</div>
          <div className="v">{n.aguardando}</div>
          <div className="s">{n.aprovados} aprovado{n.aprovados === 1 ? '' : 's'}</div>
          <div className="barra" style={{ background: '#d9773b' }} />
        </div>
        <div className="kpi-mini vidro" onClick={() => irPara('clientes')}>
          <div className="rotulo">Clientes a receber</div>
          <div className="v">{n.aReceber}</div>
          <div className="s">de {clientes.length} cliente{clientes.length === 1 ? '' : 's'}</div>
          <div className="barra" style={{ background: '#3a8fb0' }} />
        </div>
      </div>

      <div className="grade-painel">
        <div className="recentes vidro">
          <div className="titulo-bloco">
            <div className="rotulo">Últimos clientes</div>
            <button className="btn pequeno acento" onClick={() => irPara('clientes')}>Ver clientes <IconeSeta /></button>
          </div>
          {ultimosClientes.length === 0 && <div style={{ color: 'var(--texto-3)', padding: '10px 0' }}>Nenhum cliente cadastrado ainda.</div>}
          {ultimosClientes.map((c) => (
            <div key={c.id} className="recente" onClick={() => abrirCliente(c.id)}>
              <span className="tag-tipo" style={{ background: '#3a6f8f' }}>{(c.nome || '?').slice(0, 2).toUpperCase()}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="nome">{c.nome || 'Sem nome'}</div>
                <div className="sub">{NOME_SITUACAO[c.situacao]} · {NOME_PAGAMENTO[c.pagamento]}</div>
              </div>
            </div>
          ))}
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
          {recentes.length === 0 && <div style={{ color: 'var(--texto-3)', padding: '10px 0' }}>Nada ainda. Crie o primeiro pelos atalhos acima.</div>}
          {recentes.map((d) => (
            <div key={d.id} className="recente" onClick={() => abrir(d, d.tipo)}>
              <span className="tag-tipo" style={{ background: COR[d.tipo] }}>{SIGLA[d.tipo]}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div className="nome">{d.cliente || d.titulo}</div>
                <div className="sub">{NOME_TIPO[d.tipo]} · {d.numero} · {dataBR(d.dados?.data || d.criado_em.slice(0, 10))}</div>
              </div>
              {d.valor != null && <b style={{ fontFamily: 'var(--f-rot)', whiteSpace: 'nowrap' }}>{brl(d.valor)}</b>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
