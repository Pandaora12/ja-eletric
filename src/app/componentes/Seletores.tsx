import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { useApp } from '../estado';
import { textoNorma } from '../lib/cadastros';
import type { Cliente, Norma } from '../tipos';
import { IconeBusca, IconeX } from './Icones';

function Janela({ titulo, fechar, children }: { titulo: string; fechar: () => void; children: React.ReactNode }) {
  return createPortal(
    <div className="fundo-janela" onClick={fechar}>
      <div className="janela vidro" onClick={(e) => e.stopPropagation()}>
        <div className="janela-topo">
          <h2>{titulo}</h2>
          <button className="btn icone-btn" title="Fechar" onClick={fechar}><IconeX /></button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}

export const normalizar = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** Procura um cliente da aba Clientes pelo nome, razão social, CPF/CNPJ, cidade ou telefone. */
export function SeletorCliente({ aoEscolher, fechar }: { aoEscolher: (c: Cliente) => void; fechar: () => void }) {
  const clientes = useApp((s) => s.clientes);
  const irPara = useApp((s) => s.irPara);
  const [busca, setBusca] = useState('');
  const lista = useMemo(() => {
    const b = normalizar(busca.trim());
    return clientes
      .filter((c) => !b || normalizar(`${c.nome} ${c.documento} ${c.contato} ${c.cidade} ${c.telefone}`).includes(b))
      .sort((a, b2) => a.nome.localeCompare(b2.nome));
  }, [clientes, busca]);

  return (
    <Janela titulo="Importar cliente" fechar={fechar}>
      <div className="busca">
        <IconeBusca />
        <input className="entrada" autoFocus placeholder="Nome, razão social, CNPJ ou cidade" value={busca} onChange={(e) => setBusca(e.target.value)} />
      </div>
      <div className="janela-lista">
        {clientes.length === 0 && (
          <div className="vazio-lista">
            Nenhum cliente cadastrado ainda.
            <div style={{ marginTop: 12 }}><button className="btn" onClick={() => { fechar(); irPara('clientes'); }}>Ir para Clientes</button></div>
          </div>
        )}
        {clientes.length > 0 && lista.length === 0 && <div className="vazio-lista">Nenhum cliente encontrado.</div>}
        {lista.map((c) => (
          <button key={c.id} className="linha-escolha" onClick={() => { aoEscolher(c); fechar(); }}>
            <b>{c.nome || 'Sem nome'}</b>
            <span>{[c.documento, c.cidade, c.telefone].filter(Boolean).join(' · ')}</span>
          </button>
        ))}
      </div>
    </Janela>
  );
}

/** Marca as normas da aba Normas que vão no orçamento. */
export function SeletorNormas({ atuais, aoConfirmar, fechar }: { atuais: string[]; aoConfirmar: (normas: string[]) => void; fechar: () => void }) {
  const normas = useApp((s) => s.normas);
  const irPara = useApp((s) => s.irPara);
  const [marcadas, setMarcadas] = useState<Set<string>>(() => new Set(normas.filter((n) => atuais.includes(textoNorma(n))).map((n) => n.id)));
  const alternar = (n: Norma) => setMarcadas((m) => { const x = new Set(m); if (x.has(n.id)) x.delete(n.id); else x.add(n.id); return x; });

  return (
    <Janela titulo="Importar normas" fechar={fechar}>
      <div className="janela-lista">
        {normas.length === 0 && (
          <div className="vazio-lista">
            Nenhuma norma cadastrada.
            <div style={{ marginTop: 12 }}><button className="btn" onClick={() => { fechar(); irPara('normas'); }}>Ir para Normas</button></div>
          </div>
        )}
        {normas.map((n) => (
          <label key={n.id} className={`linha-escolha marcavel ${marcadas.has(n.id) ? 'on' : ''}`}>
            <input type="checkbox" checked={marcadas.has(n.id)} onChange={() => alternar(n)} />
            <div><b>{n.codigo}</b><span>{n.descricao}</span></div>
          </label>
        ))}
      </div>
      <div className="janela-acoes">
        <button className="btn" onClick={fechar}>Cancelar</button>
        <button className="btn forte" onClick={() => {
          // mantém as normas digitadas à mão e troca as importadas pela nova seleção
          const textosCadastro = new Set(normas.map(textoNorma));
          const manuais = atuais.filter((t) => t.trim() && !textosCadastro.has(t));
          aoConfirmar([...normas.filter((n) => marcadas.has(n.id)).map(textoNorma), ...manuais]);
          fechar();
        }}>Usar {marcadas.size} norma{marcadas.size === 1 ? '' : 's'}</button>
      </div>
    </Janela>
  );
}
