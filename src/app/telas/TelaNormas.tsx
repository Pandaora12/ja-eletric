import { useMemo, useState } from 'react';
import { useApp } from '../estado';
import { IconeBusca, IconeLixo, IconeMais } from '../componentes/Icones';
import { normalizar } from '../componentes/Seletores';

/** Cadastro das normas técnicas usadas nos orçamentos. */
export function TelaNormas() {
  const { normas, salvarNorma, excluirNorma, avisar } = useApp();
  const [busca, setBusca] = useState('');
  const lista = useMemo(() => {
    const b = normalizar(busca.trim());
    return normas.filter((n) => !b || normalizar(`${n.codigo} ${n.descricao}`).includes(b));
  }, [normas, busca]);

  return (
    <section className="vidro tela-normas">
      <div className="normas-topo">
        <div>
          <h1 className="titulo-tela" style={{ fontSize: 34 }}>Nor<em>mas</em></h1>
          <p style={{ color: 'var(--texto-2)', margin: '6px 0 0', fontSize: 16 }}>Cadastre aqui as normas uma vez. No orçamento, use "Importar normas" para escolher quais entram.</p>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <div className="busca" style={{ width: 300 }}><IconeBusca /><input className="entrada" placeholder="Procurar norma" value={busca} onChange={(e) => setBusca(e.target.value)} /></div>
          <button className="btn forte" onClick={() => salvarNorma({ id: '', codigo: 'NBR ', descricao: '' })}><IconeMais />Nova norma</button>
        </div>
      </div>
      <div className="normas-lista">
        <div className="norma-linha cab"><span>Código</span><span>Descrição</span><span /></div>
        {lista.length === 0 && <div className="vazio-lista">Nenhuma norma encontrada.</div>}
        {lista.map((n) => (
          <div key={n.id} className="norma-linha">
            <input className="entrada" value={n.codigo} onChange={(e) => salvarNorma({ ...n, codigo: e.target.value })} placeholder="NBR 5410" />
            <input className="entrada" value={n.descricao} onChange={(e) => salvarNorma({ ...n, descricao: e.target.value })} placeholder="Do que trata a norma" />
            <button className="btn icone-btn perigo" title="Excluir norma" onClick={async () => { if (confirm(`Excluir ${n.codigo}?`)) { await excluirNorma(n); avisar('Norma excluída.'); } }}><IconeLixo /></button>
          </div>
        ))}
      </div>
    </section>
  );
}
