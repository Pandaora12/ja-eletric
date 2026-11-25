import { useMemo, useState } from 'react';
import { useApp } from '../estado';
import { brl, dataBR } from '../lib/formato';
import { NOME_PAGAMENTO, NOME_SITUACAO, NOME_TIPO, type Cliente, type Documento, type Pagamento, type Situacao, type TipoDoc } from '../tipos';
import { Escolha, Texto } from '../componentes/Campos';
import { IconeBusca, IconeLixo, IconeMais, IconePessoa } from '../componentes/Icones';
import { normalizar } from '../componentes/Seletores';

type FiltroS = 'todos' | Situacao;
type FiltroP = 'todos' | Pagamento;

/** documentos ligados ao cliente (pelo vínculo ou pelo nome) */
export function documentosDoCliente(c: Cliente, docs: Documento[]) {
  const nome = normalizar(c.nome.trim());
  return docs.filter((d) => d.dados?.cliente_id === c.id || (!!nome && normalizar(d.cliente || '').startsWith(nome)));
}

export function TelaClientes() {
  const { clientes, documentos, clienteAberto, abrirCliente, novoCliente, atualizarCliente, excluirCliente, avisar } = useApp();
  const [busca, setBusca] = useState('');
  const [fs, setFs] = useState<FiltroS>('todos');
  const [fp, setFp] = useState<FiltroP>('todos');

  const lista = useMemo(() => {
    const b = normalizar(busca.trim());
    return clientes
      .filter((c) => (fs === 'todos' || c.situacao === fs) && (fp === 'todos' || c.pagamento === fp))
      .filter((c) => !b || normalizar(`${c.nome} ${c.documento} ${c.contato} ${c.cidade} ${c.telefone} ${c.email}`).includes(b))
      .sort((a, b2) => a.nome.localeCompare(b2.nome));
  }, [clientes, busca, fs, fp]);

  const conta = (f: (c: Cliente) => boolean) => clientes.filter(f).length;
  const c = clientes.find((x) => x.id === clienteAberto) ?? null;

  return (
    <div className="tela-doc tela-clientes">
      <section className="lista-docs vidro">
        <div className="cab">
          <h1 className="titulo-tela" style={{ fontSize: 23 }}>Cli<em>entes</em></h1>
          <div style={{ color: 'var(--texto-2)', fontSize: 15, marginTop: -6 }}>Quem você atendeu, quem pagou e quem falta pagar.</div>
          <button className="btn forte" onClick={() => novoCliente()}><IconeMais />Novo cliente</button>
          <div className="busca"><IconeBusca /><input className="entrada" placeholder="Nome, razão social, CNPJ, cidade…" value={busca} onChange={(e) => setBusca(e.target.value)} /></div>
          <div className="filtros">
            <span className="rotulo">Situação</span>
            <div className="chips">
              <Chip on={fs === 'todos'} onClick={() => setFs('todos')}>Todos · {clientes.length}</Chip>
              {(Object.keys(NOME_SITUACAO) as Situacao[]).map((k) => (
                <Chip key={k} on={fs === k} onClick={() => setFs(k)} cor={k}>{NOME_SITUACAO[k]} · {conta((x) => x.situacao === k)}</Chip>
              ))}
            </div>
            <span className="rotulo">Pagamento</span>
            <div className="chips">
              <Chip on={fp === 'todos'} onClick={() => setFp('todos')}>Todos</Chip>
              {(Object.keys(NOME_PAGAMENTO) as Pagamento[]).map((k) => (
                <Chip key={k} on={fp === k} onClick={() => setFp(k)} cor={k}>{NOME_PAGAMENTO[k]} · {conta((x) => x.pagamento === k)}</Chip>
              ))}
            </div>
          </div>
        </div>
        <div className="itens">
          {lista.length === 0 && <div className="vazio-lista">{clientes.length ? 'Nenhum cliente com esse filtro.' : 'Nenhum cliente ainda. Clique em "Novo cliente".'}</div>}
          {lista.map((x) => (
            <button key={x.id} className={`item-doc ${c?.id === x.id ? 'on' : ''}`} onClick={() => abrirCliente(x.id)}>
              <div className="nome">{x.nome || 'Sem nome'}</div>
              <div className="l1"><span>{[x.cidade, x.telefone].filter(Boolean).join(' · ') || '—'}</span></div>
              <div className="l1" style={{ justifyContent: 'flex-start', gap: 6 }}>
                <span className={`status s-${x.situacao}`}>{NOME_SITUACAO[x.situacao]}</span>
                <span className={`status p-${x.pagamento}`}>{NOME_PAGAMENTO[x.pagamento]}</span>
              </div>
            </button>
          ))}
        </div>
      </section>

      {!c ? (
        <section className="vidro" style={{ display: 'flex' }}>
          <div className="vazio-doc">
            <div className="icone-grande"><IconePessoa /></div>
            <h2 className="titulo-tela" style={{ fontSize: 30 }}>Seus clientes</h2>
            <p style={{ color: 'var(--texto-2)', fontSize: 17, margin: 0 }}>
              Cadastre também quem chegou no boca a boca, sem orçamento. Depois é só importar o cliente no recibo, no orçamento ou no Pix.
            </p>
            <button className="btn forte" onClick={() => novoCliente()}><IconeMais />Cadastrar cliente</button>
          </div>
        </section>
      ) : (
        <FichaCliente c={c} documentos={documentosDoCliente(c, documentos)} muda={atualizarCliente}
          excluir={async () => { if (confirm(`Excluir ${c.nome || 'este cliente'}? Os documentos dele continuam salvos.`)) { await excluirCliente(c); avisar('Cliente excluído.'); } }} />
      )}
    </div>
  );
}

function Chip({ on, onClick, cor, children }: { on: boolean; onClick: () => void; cor?: string; children: React.ReactNode }) {
  return <button className={`chip-filtro ${on ? 'on' : ''} ${cor ? 'c-' + cor : ''}`} onClick={onClick}>{children}</button>;
}

function FichaCliente({ c, documentos, muda, excluir }: { c: Cliente; documentos: Documento[]; muda: (c: Cliente) => void; excluir: () => void }) {
  const { criar, abrir } = useApp();
  const s = <K extends keyof Cliente>(k: K) => (v: Cliente[K]) => muda({ ...c, [k]: v });
  const total = documentos.filter((d) => d.tipo === 'recibo').reduce((a, d) => a + (d.valor || 0), 0);

  const novo = async (tipo: TipoDoc) => {
    const base: Record<TipoDoc, Record<string, any>> = {
      recibo: { cliente_id: c.id, pagador: { nome: c.nome, documento: c.documento, endereco: c.endereco, cidade: c.cidade, cep: c.cep, telefone: c.telefone, email: c.email } },
      orcamento: { cliente_id: c.id, cliente: { nome: c.nome, solicitante: c.contato, local: c.cidade, endereco: c.endereco } },
      comprovante: { cliente_id: c.id, cliente: [c.nome, c.cidade].filter(Boolean).join(' · ') },
      relatorio: {},
    };
    const d = await criar(tipo, base[tipo]);
    abrir(d, tipo);
  };

  return (
    <section className="area-doc">
      <div className="barra-acoes vidro">
        <div className="ident">
          <b>{c.nome || 'Novo cliente'}</b>
          <span>{documentos.length} documento{documentos.length === 1 ? '' : 's'}{total ? ` · recebido ${brl(total)}` : ''}</span>
        </div>
        <button className="btn pequeno" onClick={() => novo('orcamento')}><IconeMais />Orçamento</button>
        <button className="btn pequeno" onClick={() => novo('recibo')}><IconeMais />Recibo</button>
        <button className="btn pequeno" onClick={() => novo('comprovante')}><IconeMais />Pagamento</button>
        <button className="btn icone-btn perigo" title="Excluir cliente" onClick={excluir}><IconeLixo /></button>
      </div>
      <div className="divide">
        <div className="editor vidro">
          <div className="bloco-form">
            <h3>Situação</h3>
            <div className="grade-2">
              <Escolha rotulo="Atendimento" valor={c.situacao} opcoes={Object.entries(NOME_SITUACAO) as [Situacao, string][]} muda={s('situacao')} />
              <Escolha rotulo="Pagamento" valor={c.pagamento} opcoes={Object.entries(NOME_PAGAMENTO) as [Pagamento, string][]} muda={s('pagamento')} />
            </div>
          </div>
          <div className="bloco-form">
            <h3>Dados</h3>
            <Texto rotulo="Nome / razão social" valor={c.nome} muda={s('nome')} />
            <div className="grade-2">
              <Texto rotulo="CNPJ ou CPF" valor={c.documento} muda={s('documento')} />
              <Texto rotulo="Pessoa de contato" valor={c.contato} muda={s('contato')} dica="Sr. Rafael" />
            </div>
            <Texto rotulo="Endereço" valor={c.endereco} muda={s('endereco')} dica="Rua, número · bairro" />
            <div className="grade-2">
              <Texto rotulo="Cidade" valor={c.cidade} muda={s('cidade')} />
              <Texto rotulo="CEP" valor={c.cep} muda={s('cep')} />
            </div>
            <div className="grade-2">
              <Texto rotulo="Telefone" valor={c.telefone} muda={s('telefone')} />
              <Texto rotulo="E-mail" valor={c.email} muda={s('email')} />
            </div>
            <Texto rotulo="Observações" valor={c.observacoes} muda={s('observacoes')} longo dica="Ex.: indicação do Sr. João, voltar em março para manutenção." />
          </div>
        </div>
        <div className="previa vidro">
          <div className="cab-previa"><span className="rotulo">Documentos deste cliente</span></div>
          <div className="lista-cliente-docs">
            {documentos.length === 0 && <div className="vazio-lista">Nenhum documento ainda. Use os botões acima para criar um orçamento, recibo ou pagamento já com os dados do cliente.</div>}
            {documentos.map((d) => (
              <button key={d.id} className="item-doc" onClick={() => abrir(d, d.tipo)}>
                <div className="l1"><span>{NOME_TIPO[d.tipo]} · {d.numero}</span><span>{dataBR(d.dados?.data || d.criado_em.slice(0, 10))}</span></div>
                <div className="nome">{d.titulo}</div>
                {d.valor != null && <div className="valor">{brl(d.valor)}</div>}
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
