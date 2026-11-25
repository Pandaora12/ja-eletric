import { useState } from 'react';
import { motion } from 'framer-motion';
import { cadastrar, entrar } from '../lib/repositorio';
import { modoLocal } from '../lib/supabase';
import { useApp } from '../estado';

const PAISAGENS = [
  ['Aurora', 'linear-gradient(120deg,#7c3aed,#00e5a0)'],
  ['Floresta', 'linear-gradient(120deg,#00a859,#c8f53b)'],
  ['Oceano', 'linear-gradient(120deg,#0057ff,#00d1ff)'],
  ['Deserto', 'linear-gradient(120deg,#ff6a00,#ffc300)'],
  ['Cânion', 'linear-gradient(120deg,#ff2d3a,#c026d3)'],
];

/** Tela de entrada e cadastro. */
export function Acesso() {
  const entrarNoApp = useApp((s) => s.entrar);
  const [modo, setModo] = useState<'entrar' | 'cadastrar'>('entrar');
  const [f, setF] = useState({ nome: '', email: '', senha: '', empresa: 'J.A Eletricidades', convite: '' });
  const [erro, setErro] = useState('');
  const [msg, setMsg] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const muda = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function enviar(e: React.FormEvent) {
    e.preventDefault();
    setErro(''); setMsg(''); setOcupado(true);
    try {
      if (modo === 'entrar') {
        await entrarNoApp(await entrar(f.email.trim(), f.senha));
      } else {
        if (!f.nome.trim()) throw new Error('Informe seu nome.');
        const r = await cadastrar({ nome: f.nome.trim(), email: f.email.trim(), senha: f.senha, empresa: f.empresa.trim(), codigoConvite: f.convite.trim() });
        if (r === 'confirmar-email') { setMsg('Cadastro feito! Enviamos um link para o seu e-mail. Confirme e depois entre.'); setModo('entrar'); }
        else await entrarNoApp(r);
      }
    } catch (e: any) {
      setErro(e.message || 'Algo deu errado.');
    } finally { setOcupado(false); }
  }

  return (
    <div className="acesso" data-paisagem="aurora">
      <div className="lado-arte">
        <div className="marca-app">
          <img src="/modelos/logo-ja.png" alt="" />
          <div><b>J.A Documentos</b><small>J.A Eletricidades</small></div>
        </div>
        <motion.div initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.7 }}>
          <h1>Fale.<br />O documento<br /><em>sai pronto.</em></h1>
          <p>Orçamentos, recibos e comprovantes Pix no padrão da J.A. Descreva por voz ou texto, confira e envie pelo WhatsApp.</p>
        </motion.div>
        <div className="paisagens">{PAISAGENS.map(([n, g]) => <span key={n} style={{ background: g }}>{n}</span>)}</div>
      </div>

      <div className="lado-form">
        <motion.form className="cartao-acesso vidro" onSubmit={enviar} initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.5, delay: 0.1 }}>
          <div className="troca">
            <button type="button" className={modo === 'entrar' ? 'on' : ''} onClick={() => { setModo('entrar'); setErro(''); }}>Entrar</button>
            <button type="button" className={modo === 'cadastrar' ? 'on' : ''} onClick={() => { setModo('cadastrar'); setErro(''); }}>Criar conta</button>
          </div>
          <h2>{modo === 'entrar' ? 'Bem-vindo de volta' : 'Criar sua conta'}</h2>

          {modo === 'cadastrar' && (
            <label className="campo"><span>Seu nome</span><input className="entrada" value={f.nome} onChange={muda('nome')} autoComplete="name" /></label>
          )}
          <label className="campo"><span>E-mail</span><input className="entrada" type="email" required value={f.email} onChange={muda('email')} autoComplete="email" /></label>
          <label className="campo"><span>Senha</span><input className="entrada" type="password" required value={f.senha} onChange={muda('senha')} autoComplete={modo === 'entrar' ? 'current-password' : 'new-password'} /></label>
          {modo === 'cadastrar' && (
            <>
              <label className="campo"><span>Empresa</span><input className="entrada" value={f.empresa} onChange={muda('empresa')} disabled={!!f.convite} /></label>
              {!modoLocal && (
                <label className="campo"><span>Código de convite (se alguém da empresa já tem conta)</span><input className="entrada" value={f.convite} onChange={muda('convite')} placeholder="Opcional" /></label>
              )}
            </>
          )}

          {erro && <div className="erro-form">{erro}</div>}
          {msg && <div style={{ color: 'var(--acento)', fontWeight: 600 }}>{msg}</div>}
          <button className="btn forte" disabled={ocupado} style={{ height: 56, fontSize: 17 }}>{ocupado ? 'Aguarde…' : modo === 'entrar' ? 'Entrar' : 'Criar conta'}</button>
          {modoLocal && <div className="nota-local">Modo local: a conta e os documentos ficam salvos só neste computador. Para usar a IA e acessar de outros computadores, configure o Supabase.</div>}
        </motion.form>
      </div>
    </div>
  );
}
