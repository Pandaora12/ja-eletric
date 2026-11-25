import { brl } from '../lib/formato';
import type { Documento } from '../tipos';
import { Data, Dinheiro, Escolha, ListaTexto, Texto } from './Campos';
import { IconeLixo, IconeMais } from './Icones';

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

// ---------------- recibo ----------------
function EditorRecibo({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const s = (c: string) => (v: any) => muda(com(d, c, v));
  const itens: any[] = d.itens ?? [];
  const total = itens.reduce((a, i) => a + (Number(i.valor) || 0), 0);
  const setItem = (i: number, c: string, v: any) => muda({ ...d, itens: itens.map((x, j) => (j === i ? { ...x, [c]: v } : x)) });
  return (
    <>
      <div className="bloco-form">
        <h3>Quem pagou</h3>
        <Texto rotulo="Nome / razão social" valor={d.pagador?.nome} muda={s('pagador.nome')} />
        <div className="grade-2">
          <Texto rotulo="CNPJ ou CPF" valor={d.pagador?.documento} muda={s('pagador.documento')} dica="CNPJ 00.000.000/0000-00" />
          <Texto rotulo="Endereço" valor={d.pagador?.endereco} muda={s('pagador.endereco')} />
        </div>
        <Texto rotulo="Cidade, CEP, telefone" valor={d.pagador?.complemento} muda={s('pagador.complemento')} />
      </div>
      <div className="bloco-form">
        <h3>Serviço</h3>
        <div className="grade-2">
          <Texto rotulo="Tipo de atendimento (aparece acima de RECIBO)" valor={d.rotulo} muda={s('rotulo')} dica="Ex.: Atendimento emergencial" />
          <Data rotulo="Data" valor={d.data} muda={s('data')} />
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
  return (
    <>
      <div className="bloco-form">
        <h3>Valor a cobrar</h3>
        <p style={{ margin: 0, color: 'var(--texto-2)', fontSize: 15 }}>Deixe o valor em branco para mandar só a chave Pix.</p>
        <Dinheiro rotulo="Valor" valor={d.valor} muda={s('valor')} vazioPermitido />
        <Texto rotulo="Referente a" valor={d.referente} muda={s('referente')} dica="Ex.: Atendimento emergencial · troca de cartucho" />
        <div className="grade-2">
          <Texto rotulo="Cliente" valor={d.cliente} muda={s('cliente')} />
          <Data rotulo="Vencimento (opcional)" valor={d.vencimento} muda={s('vencimento')} />
        </div>
      </div>
      <div className="bloco-form">
        <h3>Composição do valor (opcional)</h3>
        {itens.map((it, i) => (
          <div key={i} className="linha-item" style={{ gridTemplateColumns: '1fr 160px auto' }}>
            <Texto rotulo="Descrição" valor={it.descricao} muda={(v) => setItem(i, 'descricao', v)} />
            <Dinheiro rotulo="Valor" valor={it.valor} muda={(v) => setItem(i, 'valor', v ?? 0)} />
            <button className="btn icone-btn perigo" title="Remover" onClick={() => muda({ ...d, itens: itens.filter((_, j) => j !== i) })}><IconeLixo /></button>
          </div>
        ))}
        <button className="btn" onClick={() => muda({ ...d, itens: [...itens, { descricao: '', valor: 0 }] })}><IconeMais />Adicionar linha</button>
      </div>
      <div className="bloco-form">
        <h3>Chave Pix</h3>
        <p style={{ margin: 0, color: 'var(--texto-2)', fontSize: 15 }}>CPF 151.272.348-74 · Jorge Luiz dos Santos · Itaú</p>
      </div>
    </>
  );
}

// ---------------- orçamento ----------------
const TIPOS_ITEM: [string, string][] = [
  ['servico', 'Serviço'], ['alimentador', 'Alimentador'], ['quadro', 'Quadro'], ['infraestrutura', 'Infraestrutura'], ['circuitos', 'Circuitos'],
  ['iluminacao', 'Iluminação'], ['spda', 'SPDA'], ['suportes', 'Suportes'], ['material', 'Material'], ['ensaio', 'Ensaio'], ['outro', 'Outro'], ['nao_orcado', 'Não orçado'],
];

function EditorOrcamento({ d, muda }: { d: Record<string, any>; muda: Muda }) {
  const s = (c: string) => (v: any) => muda(com(d, c, v));
  const setores: any[] = d.setores ?? [];
  const parcelas: any[] = d.parcelas ?? [];
  const setSetor = (i: number, novo: any) => muda({ ...d, setores: setores.map((x, j) => (j === i ? novo : x)) });
  const somaPct = parcelas.reduce((a, p) => a + (Number(p.percentual) || 0), 0);
  return (
    <>
      <div className="bloco-form">
        <h3>Cliente</h3>
        <Texto rotulo="Empresa / cliente" valor={d.cliente?.nome} muda={s('cliente.nome')} />
        <div className="grade-3">
          <Texto rotulo="Solicitante" valor={d.cliente?.solicitante} muda={s('cliente.solicitante')} dica="Sr. Rafael" />
          <Texto rotulo="Local" valor={d.cliente?.local} muda={s('cliente.local')} />
          <Texto rotulo="Nome curto" valor={d.cliente?.nome_curto} muda={s('cliente.nome_curto')} dica="Georevest" />
        </div>
      </div>
      <div className="bloco-form">
        <h3>Capa e apresentação</h3>
        <Texto rotulo="Título" valor={d.titulo} muda={s('titulo')} longo />
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
        <ListaTexto rotulo="Normas" valor={d.normas} muda={s('normas')} />
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
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <button className="btn pequeno" onClick={() => muda({ ...d, parcelas: [...parcelas, { percentual: 0, quando: '' }] })}><IconeMais />Parcela</button>
          {parcelas.length > 0 && somaPct !== 100 && <span style={{ color: '#ff9aa5', fontWeight: 600 }}>As parcelas somam {somaPct}% (deveria ser 100%)</span>}
        </div>
        <div className="grade-2">
          <Texto rotulo="Início" valor={d.prazo?.inicio} muda={s('prazo.inicio')} />
          <Texto rotulo="Término" valor={d.prazo?.termino} muda={s('prazo.termino')} />
        </div>
      </div>
    </>
  );
}
