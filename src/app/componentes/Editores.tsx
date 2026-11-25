import { useState } from 'react';
import { useApp } from '../estado';
import { brl } from '../lib/formato';
import type { Cliente, Documento } from '../tipos';
import { Data, Dinheiro, Escolha, Hora, ListaTexto, Texto } from './Campos';
import { IconeImportar, IconeLixo, IconeMais, IconePessoa } from './Icones';
import { SeletorCliente, SeletorNormas } from './Seletores';
import { FORMAS, faltando, type FormaPagamento, type TipoForma } from '../lib/formas';

type Muda = (dados: Record<string, any>) => void;

/** troca um campo (com caminho "a.b.c") sem mexer no resto */
function com(d: Record<string, any>, caminho: string, v: any): Record<string, any> {
  const [k, ...resto] = caminho.split('.');
  const copia: any = Array.isArray(d) ? [...d] : { ...d };
  copia[k] = resto.length ? com(d?.[k] ?? {}, resto.join('.'), v) : v;
  return copia;
}

export function Editor({ doc, muda }: { doc: Documento; muda: Muda }) {
  if (doc.tipo === 'recibo') return <EditorRecibo d={doc.dados} muda={muda} />;
  if (doc.tipo === 'comprovante') return <EditorPix d={doc.dados} muda={muda} />;
  if (doc.tipo === 'orcamento') return <EditorOrcamento d={doc.dados} muda={muda} />;
  return null;
}

/** Botões "Importar cliente" e "Salvar em Clientes" do topo de cada formulário. */
function BotoesCliente({ clienteId, aoImportar, paraSalvar }: { clienteId?: string; aoImportar: (c: Cliente) => void; paraSalvar: () => Partial<Cliente> | null }) {
  const [aberto, setAberto] = useState(false);
  const { clientes, novoCliente, abrirCliente, avisar } = useApp();
  const vinculado = clienteId ? clientes.find((c) => c.id === clienteId) : null;
  return (
    <div className="botoes-cliente">
      <button className="btn pequeno acento" onClick={() => setAberto(true)}><IconeImportar />Importar cliente</button>
      {vinculado ? (
        <button className="btn pequeno" title="Abrir na aba Clientes" onClick={() => abrirCliente(vinculado.id)}><IconePessoa />Ver cliente</button>
      ) : (
        <button className="btn pequeno" onClick={async () => {
          const dados = paraSalvar();
          if (!dados?.nome?.trim()) { avisar('Preencha o nome antes de salvar em Clientes.', 'erro'); return; }
          const c = await novoCliente(dados);
          aoImportar(c);
          avisar('Cliente salvo na aba Clientes.');
        }}><IconePessoa />Salvar em Clientes</button>
      )}
      {aberto && <SeletorCliente fechar={() => setAberto(false)} aoEscolher={aoImportar} />}
    </div>
  );
}

// ---------------- recibo ----------------
function EditorRecibo({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const s = (c: string) => (v: any) => muda(com(d, c, v));
  const itens: any[] = d.itens ?? [];
  const pg = d.pagador ?? {};
  const total = itens.reduce((a, i) => a + (Number(i.valor) || 0), 0);
  const setItem = (i: number, c: string, v: any) => muda({ ...d, itens: itens.map((x, j) => (j === i ? { ...x, [c]: v } : x)) });
  const importar = (c: Cliente) => muda({
    ...d, cliente_id: c.id,
    pagador: { ...pg, nome: c.nome, documento: c.documento, endereco: c.endereco, cidade: c.cidade, cep: c.cep, telefone: c.telefone, email: c.email },
  });
  return (
    <>
      <div className="bloco-form">
        <div className="titulo-bloco"><h3>Quem pagou</h3>
          <BotoesCliente clienteId={d.cliente_id} aoImportar={importar}
            paraSalvar={() => ({ nome: pg.nome, documento: pg.documento, endereco: pg.endereco, cidade: pg.cidade, cep: pg.cep, telefone: pg.telefone, email: pg.email, pagamento: 'pago', situacao: 'atendido' })} />
        </div>
        <Texto rotulo="Nome / razão social" valor={pg.nome} muda={s('pagador.nome')} />
        <div className="grade-2">
          <Texto rotulo="CNPJ ou CPF" valor={pg.documento} muda={s('pagador.documento')} dica="CNPJ 00.000.000/0000-00" />
          <Texto rotulo="Endereço" valor={pg.endereco} muda={s('pagador.endereco')} dica="Rua, número · bairro" />
        </div>
        <div className="grade-2">
          <Texto rotulo="Cidade" valor={pg.cidade} muda={s('pagador.cidade')} dica="Atibaia/SP" />
          <Texto rotulo="CEP" valor={pg.cep} muda={s('pagador.cep')} dica="00000-000" />
        </div>
        <div className="grade-2">
          <Texto rotulo="Telefone" valor={pg.telefone} muda={s('pagador.telefone')} dica="(11) 0000-0000" />
          <Texto rotulo="E-mail" valor={pg.email} muda={s('pagador.email')} />
        </div>
      </div>
      <div className="bloco-form">
        <h3>Serviço</h3>
        <Texto rotulo="Tipo de serviço (aparece acima de RECIBO)" valor={d.rotulo} muda={s('rotulo')} dica="Ex.: Prestação de serviço · Atendimento emergencial" />
        <div className="grade-3">
          <Data rotulo="Data" valor={d.data} muda={s('data')} />
          <Hora rotulo="Hora de início" valor={d.hora_inicio} muda={s('hora_inicio')} />
          <Hora rotulo="Hora de término" valor={d.hora_fim} muda={s('hora_fim')} />
        </div>
        <div className="grade-2">
          <Texto rotulo="Local do serviço" valor={d.local?.nome} muda={s('local.nome')} dica="Ex.: Condomínio Montaresa" />
          <Texto rotulo="Cidade do local" valor={d.local?.cidade} muda={s('local.cidade')} />
        </div>
        <Texto rotulo='Texto depois de "referente"' valor={d.referente} muda={s('referente')} longo />
      </div>
      <div className="bloco-form">
        <h3>Itens</h3>
        {itens.map((it, i) => (
          <div key={i} className="linha-item" style={{ gridTemplateColumns: '1fr 1fr 170px' }}>
            <div style={{ gridColumn: '1 / -1' }}><Texto rotulo={`Item ${i + 1} · descrição`} valor={it.descricao} muda={(v) => setItem(i, 'descricao', v)} /></div>
            <Texto rotulo="Detalhe" valor={it.detalhe} muda={(v) => setItem(i, 'detalhe', v)} dica="Mão de obra" />
            <Escolha rotulo="Tipo" valor={it.tipo || 'servico'} opcoes={[['servico', 'Serviço'], ['material', 'Material']]} muda={(v) => setItem(i, 'tipo', v)} />
            <Dinheiro rotulo="Valor" valor={it.valor} muda={(v) => setItem(i, 'valor', v ?? 0)} />
            <div className="acoes-linha" style={{ gridColumn: '1 / -1' }}><button className="btn pequeno perigo" onClick={() => muda({ ...d, itens: itens.filter((_, j) => j !== i) })}><IconeLixo />Remover</button></div>
          </div>
        ))}
        <button className="btn" onClick={() => muda({ ...d, itens: [...itens, { descricao: '', detalhe: '', tipo: 'servico', valor: 0 }] })}><IconeMais />Adicionar item</button>
        <div className="total-linha"><div>Total<small>soma dos itens · vai por extenso no recibo</small></div>{brl(total)}</div>
      </div>
    </>
  );
}

// ---------------- comprovante Pix ----------------
function EditorPix({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const s = (c: string) => (v: any) => muda(com(d, c, v));
  const itens: any[] = d.itens ?? [];
  const setItem = (i: number, c: string, v: any) => muda({ ...d, itens: itens.map((x, j) => (j === i ? { ...x, [c]: v } : x)) });
  const soma = itens.reduce((a, i) => a + (Number(i.valor) || 0), 0);
  return (
    <>
      <div className="bloco-form">
        <div className="titulo-bloco"><h3>Cliente</h3>
          <BotoesCliente clienteId={d.cliente_id} aoImportar={(c) => muda({ ...d, cliente_id: c.id, cliente: [c.nome, c.cidade].filter(Boolean).join(' · ') })}
            paraSalvar={() => ({ nome: (d.cliente || '').split(' · ')[0], situacao: 'atendido', pagamento: 'nao_pago' })} />
        </div>
        <Texto rotulo="Cliente" valor={d.cliente} muda={s('cliente')} dica="Ex.: Condomínio Montaresa · Atibaia/SP" />
      </div>
      <div className="bloco-form">
        <h3>Valor a cobrar</h3>
        <p className="ajuda">Deixe o valor em branco para mandar só a chave Pix.</p>
        <Dinheiro rotulo="Valor" valor={d.valor} muda={s('valor')} vazioPermitido />
        <Texto rotulo="Referente a" valor={d.referente} muda={s('referente')} dica="Ex.: Atendimento emergencial · troca de cartucho" />
        <Data rotulo="Pagar até (opcional)" valor={d.vencimento} muda={s('vencimento')} />
      </div>
      <div className="bloco-form">
        <h3>O que está sendo cobrado (opcional)</h3>
        <p className="ajuda">Separe o valor em partes para o cliente entender a cobrança. Ex.: mão de obra R$ 590 + cartucho R$ 300.</p>
        {itens.map((it, i) => (
          <div key={i} className="linha-item" style={{ gridTemplateColumns: '1fr 170px auto' }}>
            <Texto rotulo={`Parte ${i + 1}`} valor={it.descricao} muda={(v) => setItem(i, 'descricao', v)} dica="Ex.: Mão de obra" />
            <Dinheiro rotulo="Valor" valor={it.valor} muda={(v) => setItem(i, 'valor', v ?? 0)} />
            <button className="btn icone-btn perigo" title="Remover" onClick={() => muda({ ...d, itens: itens.filter((_, j) => j !== i) })}><IconeLixo /></button>
          </div>
        ))}
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="btn" onClick={() => muda({ ...d, itens: [...itens, { descricao: '', valor: 0 }] })}><IconeMais />Adicionar parte</button>
          {itens.length > 0 && Number(d.valor) > 0 && Math.abs(soma - Number(d.valor)) > 0.009 && (
            <span className="alerta-texto">As partes somam {brl(soma)}, diferente do valor ({brl(Number(d.valor))}).</span>
          )}
        </div>
      </div>
      <DadosForma d={d} muda={muda} />
    </>
  );
}

/** Dados da forma de pagamento escolhida. Ficam salvos para as próximas cobranças (menos a linha do boleto, que é de cada cobrança). */
function DadosForma({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const { formas, salvarForma } = useApp();
  const tipo = (d.forma_tipo || 'pix_pf') as TipoForma;
  const f = formas[tipo];
  const nome = FORMAS.find((x) => x.tipo === tipo)?.nome ?? '';
  const g = (k: keyof FormaPagamento) => (v: string) => salvarForma({ ...f, [k]: v });
  const falta = faltando(f);
  return (
    <div className="bloco-form">
      <h3>{nome}</h3>
      <p className="ajuda">Estes dados ficam salvos e aparecem em todas as cobranças com {nome.toLowerCase()}.</p>
      {falta.length > 0 && <span className="alerta-texto">Falta preencher: {falta.join(', ')}.</span>}
      {(tipo === 'pix_pf' || tipo === 'pix_pj') && (
        <>
          <div className="grade-2">
            <Texto rotulo="Chave Pix" valor={f.chave} muda={g('chave')} dica={tipo === 'pix_pj' ? 'CNPJ, e-mail ou chave aleatória' : 'CPF, celular ou e-mail'} />
            <Escolha rotulo="Tipo de chave" valor={(f.tipo_chave || 'CPF') as string} opcoes={[['CPF', 'CPF'], ['CNPJ', 'CNPJ'], ['E-mail', 'E-mail'], ['Celular', 'Celular'], ['Aleatória', 'Aleatória']]} muda={g('tipo_chave')} />
          </div>
          <div className="grade-2">
            <Texto rotulo="Favorecido" valor={f.favorecido} muda={g('favorecido')} />
            <Texto rotulo="Banco" valor={f.banco} muda={g('banco')} />
          </div>
          {tipo === 'pix_pj' && <Texto rotulo="CNPJ" valor={f.documento} muda={g('documento')} />}
        </>
      )}
      {tipo === 'ted' && (
        <>
          <div className="grade-3">
            <Texto rotulo="Banco" valor={f.banco} muda={g('banco')} />
            <Texto rotulo="Agência" valor={f.agencia} muda={g('agencia')} />
            <Texto rotulo="Conta" valor={f.conta} muda={g('conta')} />
          </div>
          <div className="grade-3">
            <Escolha rotulo="Tipo de conta" valor={(f.tipo_conta || 'Conta corrente') as string} opcoes={[['Conta corrente', 'Conta corrente'], ['Conta poupança', 'Conta poupança'], ['Conta pagamento', 'Conta pagamento']]} muda={g('tipo_conta')} />
            <Texto rotulo="Titular" valor={f.favorecido} muda={g('favorecido')} />
            <Texto rotulo="CPF / CNPJ do titular" valor={f.documento} muda={g('documento')} />
          </div>
        </>
      )}
      {tipo === 'boleto' && (
        <>
          <Texto rotulo="Linha digitável deste boleto" valor={d.forma_extra?.linha} muda={(v) => muda({ ...d, forma_extra: { ...(d.forma_extra || {}), linha: v } })} dica="Cole aqui a linha do boleto gerado no banco" longo />
          <div className="grade-2">
            <Texto rotulo="Beneficiário" valor={f.favorecido} muda={g('favorecido')} />
            <Texto rotulo="Banco emissor" valor={f.banco} muda={g('banco')} />
          </div>
          <Texto rotulo="Observação" valor={f.observacao} muda={g('observacao')} dica="Ex.: após o vencimento, multa de 2%." />
        </>
      )}
      {tipo === 'cartao' && (
        <>
          <Texto rotulo="Link de pagamento (opcional)" valor={f.link} muda={g('link')} dica="Deixe em branco para pagamento na maquininha" />
          <div className="grade-2">
            <Texto rotulo="Parcelamento" valor={f.parcelas} muda={g('parcelas')} dica="Ex.: 3x sem juros" />
            <Texto rotulo="Bandeiras" valor={f.bandeiras} muda={g('bandeiras')} />
          </div>
        </>
      )}
    </div>
  );
}

// ---------------- orçamento ----------------
const TIPOS_ITEM: [string, string][] = [
  ['servico', 'Serviço'], ['alimentador', 'Alimentador'], ['quadro', 'Quadro'], ['infraestrutura', 'Infraestrutura'], ['circuitos', 'Circuitos'],
  ['iluminacao', 'Iluminação'], ['spda', 'SPDA'], ['suportes', 'Suportes'], ['material', 'Material'], ['ensaio', 'Ensaio'], ['outro', 'Outro'], ['nao_orcado', 'Não orçado'],
];

function EditorOrcamento({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const s = (c: string) => (v: any) => muda(com(d, c, v));
  const [normasAberto, setNormasAberto] = useState(false);
  const setores: any[] = d.setores ?? [];
  const parcelas: any[] = d.parcelas ?? [];
  const cli = d.cliente ?? {};
  const setSetor = (i: number, novo: any) => muda({ ...d, setores: setores.map((x, j) => (j === i ? novo : x)) });
  const somaPct = parcelas.reduce((a, p) => a + (Number(p.percentual) || 0), 0);
  const importar = (c: Cliente) => muda({
    ...d, cliente_id: c.id,
    cliente: { ...cli, nome: c.nome, solicitante: c.contato || cli.solicitante, local: c.cidade || cli.local, endereco: c.endereco },
  });
  return (
    <>
      <div className="bloco-form">
        <div className="titulo-bloco"><h3>Cliente</h3>
          <BotoesCliente clienteId={d.cliente_id} aoImportar={importar}
            paraSalvar={() => ({ nome: cli.nome, contato: cli.solicitante, cidade: cli.local, endereco: cli.endereco, situacao: 'orcamento', pagamento: 'nao_pago' })} />
        </div>
        <Texto rotulo="Empresa / cliente" valor={cli.nome} muda={s('cliente.nome')} />
        <div className="grade-3">
          <Texto rotulo="Solicitante" valor={cli.solicitante} muda={s('cliente.solicitante')} dica="Sr. Rafael" />
          <Texto rotulo="Local" valor={cli.local} muda={s('cliente.local')} />
          <Texto rotulo="Nome curto" valor={cli.nome_curto} muda={s('cliente.nome_curto')} dica="Georevest" />
        </div>
      </div>
      <div className="bloco-form">
        <h3>Capa e apresentação</h3>
        <Texto rotulo="Título do orçamento" valor={d.titulo} muda={s('titulo')} longo />
        <Texto rotulo="Linha acima do título" valor={d.tipo} muda={s('tipo')} />
        <Texto rotulo="Subtítulo" valor={d.subtitulo} muda={s('subtitulo')} longo />
        <div className="grade-3">
          <Data rotulo="Data" valor={d.data} muda={s('data')} />
          <Texto rotulo="Opção" valor={d.opcao} muda={s('opcao')} dica="2ª opção" />
          <Texto rotulo="Validade (dias)" valor={String(d.validade_dias ?? '')} muda={(v) => s('validade_dias')(Number(v) || undefined)} />
        </div>
        <Texto rotulo="Referência (quadro REF.)" valor={d.referencia} muda={s('referencia')} longo />
        <ListaTexto rotulo="Parágrafos da carta" valor={d.carta} muda={s('carta')} />
      </div>
      <div className="bloco-form">
        <h3>Serviços por setores</h3>
        {setores.map((st, si) => (
          <div key={si} className="setor-card">
            <div style={{ display: 'flex', gap: 8, alignItems: 'end' }}>
              <div style={{ flex: 1 }}><Texto rotulo={`Setor ${si + 1}`} valor={st.nome} muda={(v) => setSetor(si, { ...st, nome: v })} /></div>
              <button className="btn icone-btn perigo" title="Remover setor" onClick={() => muda({ ...d, setores: setores.filter((_, j) => j !== si) })}><IconeLixo /></button>
            </div>
            {(st.itens ?? []).map((it: any, ii: number) => {
              const setIt = (c: string, v: any) => setSetor(si, { ...st, itens: st.itens.map((x: any, j: number) => (j === ii ? { ...x, [c]: v } : x)) });
              return (
                <div key={ii} className="linha-item" style={{ gridTemplateColumns: '1fr 90px auto' }}>
                  <div style={{ gridColumn: '1 / -1' }}><Texto rotulo="Item" valor={it.descricao} muda={(v) => setIt('descricao', v)} /></div>
                  <Escolha rotulo="Tipo" valor={it.tipo || 'servico'} opcoes={TIPOS_ITEM} muda={(v) => setIt('tipo', v)} />
                  <Texto rotulo="Qtd" valor={it.qtd} muda={(v) => setIt('qtd', v)} />
                  <button className="btn icone-btn perigo" title="Remover item" onClick={() => setSetor(si, { ...st, itens: st.itens.filter((_: any, j: number) => j !== ii) })}><IconeLixo /></button>
                </div>
              );
            })}
            <button className="btn pequeno" onClick={() => setSetor(si, { ...st, itens: [...(st.itens ?? []), { tipo: 'servico', descricao: '' }] })}><IconeMais />Item</button>
          </div>
        ))}
        <button className="btn" onClick={() => muda({ ...d, setores: [...setores, { nome: `Setor ${setores.length + 1}`, itens: [{ tipo: 'servico', descricao: '' }] }] })}><IconeMais />Adicionar setor</button>
      </div>
      <div className="bloco-form">
        <h3>Incluso e responsabilidades</h3>
        <ListaTexto rotulo="O que está incluso (J.A)" valor={d.incluso} muda={s('incluso')} dica="**ART** do projeto elétrico." />
        <ListaTexto rotulo="Responsabilidade do cliente" valor={d.cliente_responsabilidades} muda={s('cliente_responsabilidades')} dica="**Materiais elétricos** e de alvenaria." />
      </div>
      <div className="bloco-form">
        <div className="titulo-bloco"><h3>Normas</h3>
          <button className="btn pequeno acento" onClick={() => setNormasAberto(true)}><IconeImportar />Importar normas</button>
        </div>
        <ListaTexto rotulo="Normas do orçamento" valor={d.normas} muda={s('normas')} />
        {normasAberto && <SeletorNormas atuais={d.normas ?? []} aoConfirmar={(n) => s('normas')(n)} fechar={() => setNormasAberto(false)} />}
      </div>
      <div className="bloco-form">
        <h3>Valor e pagamento</h3>
        <div className="grade-2">
          <Dinheiro rotulo="Valor total" valor={d.valor} muda={(v) => s('valor')(v ?? 0)} />
          <Texto rotulo="Rótulo do valor" valor={d.valor_rotulo} muda={s('valor_rotulo')} />
        </div>
        <Texto rotulo="Observação do valor" valor={d.valor_nota} muda={s('valor_nota')} />
        <Texto rotulo="Forma de pagamento" valor={d.forma_pagamento} muda={s('forma_pagamento')} />
        {parcelas.map((p, i) => (
          <div key={i} className="linha-item" style={{ gridTemplateColumns: '110px 1fr auto' }}>
            <Texto rotulo={`${i + 1}ª parcela %`} valor={String(p.percentual ?? '')} muda={(v) => muda({ ...d, parcelas: parcelas.map((x, j) => (j === i ? { ...x, percentual: Number(v) || 0 } : x)) })} />
            <Texto rotulo="Quando" valor={p.quando} muda={(v) => muda({ ...d, parcelas: parcelas.map((x, j) => (j === i ? { ...x, quando: v } : x)) })} />
            <button className="btn icone-btn perigo" onClick={() => muda({ ...d, parcelas: parcelas.filter((_, j) => j !== i) })}><IconeLixo /></button>
          </div>
        ))}
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="btn pequeno" onClick={() => muda({ ...d, parcelas: [...parcelas, { percentual: 0, quando: '' }] })}><IconeMais />Parcela</button>
          {parcelas.length > 0 && somaPct !== 100 && <span className="alerta-texto">As parcelas somam {somaPct}% (deveria ser 100%)</span>}
        </div>
        <div className="grade-2">
          <Texto rotulo="Início" valor={d.prazo?.inicio} muda={s('prazo.inicio')} />
          <Texto rotulo="Término" valor={d.prazo?.termino} muda={s('prazo.termino')} />
        </div>
      </div>
    </>
  );
}
