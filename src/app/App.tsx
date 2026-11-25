import { useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useApp } from './estado';
import { ABAS } from './tipos';
import { Acesso } from './telas/Acesso';
import { TelaDocumento } from './telas/TelaDocumento';
import { Chat } from './componentes/Chat';
import { Painel } from './componentes/Painel';
import { IconeSair } from './componentes/Icones';
import './estilo.css';

const COR_PONTO: Record<string, string> = { aurora: '#00e5a0', floresta: '#c8f53b', oceano: '#00d1ff', deserto: '#ffc300', canion: '#ff5fa2' };

export default function App() {
  const { pronto, usuario, aba, irPara, iniciar, sair } = useApp();
  useEffect(() => { iniciar(); }, [iniciar]);
  const paisagem = ABAS.find((a) => a.id === aba)?.paisagem ?? 'aurora';
  useEffect(() => { document.documentElement.dataset.paisagem = usuario ? paisagem : 'aurora'; }, [paisagem, usuario]);

  const ceu = <div className="ceu" aria-hidden><i /><i /><i /></div>;
  if (!pronto) return <>{ceu}</>;
  if (!usuario) return <>{ceu}<Acesso /><Aviso /></>;

  const iniciais = (usuario.nome || usuario.email).split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

  return (
    <>
      {ceu}
      <div className="app">
        <header className="topo">
          <div className="marca-app">
            <img src="/modelos/logo-ja.png" alt="" />
            <div><b>J.A Documentos</b><small>{usuario.empresa || 'J.A Eletricidades'}</small></div>
          </div>
          <nav className="abas">
            {ABAS.map((a) => (
              <button key={a.id} className={`aba ${aba === a.id ? 'ativa' : ''}`} onClick={() => irPara(a.id)} title={a.descricao}>
                {aba === a.id && <motion.span layoutId="pilula" className="pilula" transition={{ type: 'spring', stiffness: 380, damping: 32 }} />}
                <span className="ponto" style={{ background: COR_PONTO[a.paisagem] }} />{a.nome}
              </button>
            ))}
          </nav>
          <div className="usuario-menu">
            <div style={{ textAlign: 'right' }} className="nome-u">
              <div style={{ fontWeight: 700 }}>{usuario.nome || usuario.email}</div>
              {usuario.codigoConvite && <div style={{ fontSize: 12, color: 'var(--texto-3)' }} title="Passe este código para outra pessoa da empresa se cadastrar">Convite: {usuario.codigoConvite}</div>}
            </div>
            <div className="avatar">{iniciais}</div>
            <button className="btn icone-btn" title="Sair" onClick={() => { if (confirm('Sair da conta?')) sair(); }}><IconeSair /></button>
          </div>
        </header>

        <main className="conteudo">
          <AnimatePresence mode="wait">
            <motion.div key={aba} style={{ height: '100%' }} initial={{ opacity: 0, y: 14 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -10 }} transition={{ duration: 0.28 }}>
              {aba === 'inicio' ? (
                <div className="inicio"><Chat /><Painel /></div>
              ) : (
                <TelaDocumento tipo={aba} />
              )}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>
      <Aviso />
    </>
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
