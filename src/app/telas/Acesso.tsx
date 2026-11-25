import { useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useApp } from '../estado';
import { prepararFoto } from '../lib/perfil';
import { uid } from '../lib/formato';
import { Relogio } from '../componentes/Relogio';
import { IconePessoa } from '../componentes/Icones';

/** Foto redonda do perfil (ou as iniciais). */
export function Avatar({ nome, foto, tamanho = 44 }: { nome: string; foto: string | null; tamanho?: number }) {
  const iniciais = nome.trim().split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase() || '?';
  return foto
    ? <img className="avatar" src={foto} alt="" style={{ width: tamanho, height: tamanho }} />
    : <div className="avatar" style={{ width: tamanho, height: tamanho, fontSize: tamanho * 0.38 }}>{iniciais}</div>;
}

/**
 * Entrada do app, como a tela de bloqueio do Windows:
 * - primeira vez: "Crie seu perfil" (nome e foto opcional);
 * - depois: aparece só o seu perfil e o botão Entrar.
 */
export function Acesso() {
  const { perfil, criarPerfil, entrar, avisar } = useApp();
  const [editando, setEditando] = useState(!perfil);
  const [nome, setNome] = useState(perfil?.nome ?? '');
  const [foto, setFoto] = useState<string | null>(perfil?.foto ?? null);
  const arquivo = useRef<HTMLInputElement>(null);

  async function escolherFoto(f?: File) {
    if (!f) return;
    try { setFoto(await prepararFoto(f)); } catch (e: any) { avisar(e.message, 'erro'); }
  }

  function salvar(e: React.FormEvent) {
    e.preventDefault();
    if (!nome.trim()) { avisar('Digite seu nome.', 'erro'); return; }
    criarPerfil({ id: perfil?.id ?? uid(), nome: nome.trim(), foto, criado_em: perfil?.criado_em ?? new Date().toISOString() });
    setEditando(false);
  }

  return (
    <div className="acesso">
      <div className="acesso-relogio"><Relogio grande /></div>

      {editando ? (
        <motion.form className="cartao-perfil vidro" onSubmit={salvar} initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <h2>{perfil ? 'Editar perfil' : 'Crie seu perfil'}</h2>
          <button type="button" className="foto-perfil" onClick={() => arquivo.current?.click()} title="Escolher foto">
            {foto ? <img src={foto} alt="" /> : <IconePessoa />}
            <span>{foto ? 'Trocar foto' : 'Adicionar foto'}</span>
          </button>
          <input ref={arquivo} type="file" accept="image/*" hidden onChange={(e) => { escolherFoto(e.target.files?.[0]); e.target.value = ''; }} />
          {foto && <button type="button" className="btn pequeno" onClick={() => setFoto(null)}>Tirar foto</button>}
          <label className="campo" style={{ width: '100%' }}><span>Seu nome</span><input className="entrada" autoFocus value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Jorge Luiz dos Santos" /></label>
          <button className="btn forte grande">{perfil ? 'Salvar' : 'Criar perfil'}</button>
          {perfil && <button type="button" className="btn pequeno" onClick={() => setEditando(false)}>Cancelar</button>}
          <p className="nota">Tudo fica salvo só neste aparelho. Não precisa de internet.</p>
        </motion.form>
      ) : perfil && (
        <motion.div className="cartao-perfil" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
          <Avatar nome={perfil.nome} foto={perfil.foto} tamanho={132} />
          <div className="nome-perfil">{perfil.nome}</div>
          <button className="btn forte grande" autoFocus onClick={() => entrar()}>Entrar</button>
          <button className="btn pequeno transparente" onClick={() => setEditando(true)}>Editar perfil</button>
        </motion.div>
      )}

      <div className="acesso-marca">
        <img src="/modelos/logo-ja.png" alt="" />
        <b>J.A Documentos</b>
      </div>
    </div>
  );
}
