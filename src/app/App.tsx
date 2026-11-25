import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useApp } from './estado';
import { ABAS } from './tipos';
import { Acesso, Avatar } from './telas/Acesso';
import { TelaDocumento } from './telas/TelaDocumento';
import { TelaClientes } from './telas/TelaClientes';
import { TelaNormas } from './telas/TelaNormas';
import { Painel } from './componentes/Painel';
import { Paisagem } from './componentes/Paisagem';
import { Relogio } from './componentes/Relogio';
import { Bichinho } from './componentes/Bichinho';
import { IconeCartao, IconeCasa, IconeForm, IconeGrafico, IconeLivro, IconePessoa, IconeRecibo, IconeSol } from './componentes/Icones';

const ICONE_ABA: Record<string, () => React.ReactElement> = { inicio: IconeCasa, clientes: IconePessoa, orcamento: IconeForm, recibo: IconeRecibo, comprovante: IconeCartao, relatorio: IconeGrafico, normas: IconeLivro };
import './estilo.css';

export default function App() {
  const { pronto, perfil, dentro, aba, irPara, iniciar, brilho } = useApp();
  useEffect(() => { iniciar(); }, [iniciar]);

  const fundo = <Paisagem />;
  const escuro = <div className="escurecer" aria-hidden style={{ opacity: (100 - brilho) / 100 }} />;
  if (!pronto) return fundo;
  if (!perfil || !dentro) return <>{fundo}<Acesso /><Claridade />{escuro}<Aviso /></>;

  return (
    <>
      {fundo}
      <div className="app">
        <header className="topo">
          <div className="marca-app">
            <img src="/modelos/logo-ja.png" alt="" />
            <b>J.A Documentos</b>
          </div>
          <nav className="abas">
            {ABAS.map((a) => (
              <button key={a.id} className={`aba ${aba === a.id ? 'ativa' : ''}`} onClick={() => irPara(a.id)}>
                {aba === a.id && <motion.span layoutId="pilula" className="pilula" transition={{ type: 'spring', stiffness: 380, damping: 32 }} />}
                <span className="aba-icone">{(() => { const I = ICONE_ABA[a.id]; return <I />; })()}</span>
                <span className="aba-nome">{a.nome}</span>
              </button>
            ))}
          </nav>
          <div className="usuario-menu">
            <div className="nome-u">{perfil.nome}</div>
            <Avatar nome={perfil.nome} foto={perfil.foto} />
          </div>
        </header>

        <div className="faixa-relogio"><Relogio /></div>

        <main className="conteudo">
          <AnimatePresence mode="wait">
            <motion.div key={aba} style={{ height: '100%' }} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }} transition={{ duration: 0.25 }}>
              {aba === 'inicio' ? <Painel />
                : aba === 'clientes' ? <TelaClientes />
                : aba === 'normas' ? <TelaNormas />
                : <TelaDocumento tipo={aba} />}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
      <Bichinho />
      <Claridade />
      {escuro}
      <Aviso />
    </>
  );
}

/** Controle de claridade da tela (canto inferior esquerdo). */
function Claridade() {
  const { brilho, mudarBrilho } = useApp();
  const [aberto, setAberto] = useState(false);
  return (
    <div className="claridade">
      <button className="btn icone-btn" title="Claridade da tela" onClick={() => setAberto(!aberto)}><IconeSol /></button>
      {aberto && (
        <div className="claridade-painel vidro">
          <span className="rotulo">Claridade</span>
          <input type="range" min={40} max={100} step={5} value={brilho} onChange={(e) => mudarBrilho(Number(e.target.value))} />
          <b>{brilho}%</b>
        </div>
      )}
    </div>
  );
}

function Aviso() {
  const aviso = useApp((s) => s.aviso);
  return (
    <AnimatePresence>
      {aviso && (
        <motion.div className={`aviso-flutuante ${aviso.tipo}`} initial={{ opacity: 0, y: 20, x: '-50%' }} animate={{ opacity: 1, y: 0, x: '-50%' }} exit={{ opacity: 0, y: 20, x: '-50%' }}>
          {aviso.texto}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
