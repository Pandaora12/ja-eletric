import { useEffect, useRef, useState } from 'react';
import { useApp } from '../estado';
import { lerAnexo } from '../lib/ia';
import { brl } from '../lib/formato';
import { NOME_TIPO, type Anexo, type TipoDoc } from '../tipos';
import { IconeClipe, IconeEnviar, IconeForm, IconeMic, IconeSeta, IconeX } from './Icones';

const SUGESTOES = [
  { t: 'Orçamento falado', s: 'Orçamento para o Condomínio Montaresa: troca de 3 disjuntores e revisão do quadro, mão de obra R$ 1.800 em 2 vezes.' },
  { t: 'Recibo rápido', s: 'Recibo para a Terras Altas: atendimento emergencial R$ 590 e cartucho de alta tensão R$ 300, no Condomínio Montaresa.' },
  { t: 'Pix com valor', s: 'Manda o Pix de R$ 890 do atendimento no Condomínio Montaresa.' },
  { t: 'Remodelar arquivo', s: 'Remodela este orçamento no nosso padrão (anexe o Word ou PDF).' },
];

// reconhecimento de voz do próprio navegador, quando existir
const Reconhecimento: any = typeof window !== 'undefined' ? (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition : null;

export function Chat() {
  const { mensagens, pensando, enviar, criar, abrir, documentos, limparChat, avisar, usuario } = useApp();
  const [texto, setTexto] = useState('');
  const [anexos, setAnexos] = useState<Anexo[]>([]);
  const [ouvindo, setOuvindo] = useState(false);
  const [menuForm, setMenuForm] = useState(false);
  const lista = useRef<HTMLDivElement>(null);
  const arquivo = useRef<HTMLInputElement>(null);
  const rec = useRef<any>(null);
  const area = useRef<HTMLTextAreaElement>(null);

  useEffect(() => { lista.current?.scrollTo({ top: lista.current.scrollHeight, behavior: 'smooth' }); }, [mensagens.length, pensando]);
  useEffect(() => { const a = area.current; if (a) { a.style.height = 'auto'; a.style.height = Math.min(a.scrollHeight, 200) + 'px'; } }, [texto]);

  async function mandar() {
    const t = texto.trim();
    if ((!t && !anexos.length) || pensando) return;
    setTexto(''); setAnexos([]);
    await enviar(t || 'Veja o anexo.', anexos);
  }

  async function escolherArquivos(fs: FileList | null) {
    if (!fs) return;
    for (const f of Array.from(fs)) {
      try { const a = await lerAnexo(f); setAnexos((x) => [...x, a]); } catch (e: any) { avisar(e.message, 'erro'); }
    }
  }

  function microfone() {
    if (ouvindo) { rec.current?.stop(); return; }
    if (!Reconhecimento) {
      avisar('Para falar, use o ditado do computador: no Windows aperte a tecla Windows + H; no Mac, aperte duas vezes a tecla Fn.');
      area.current?.focus();
      return;
    }
    const r = new Reconhecimento();
    r.lang = 'pt-BR'; r.continuous = true; r.interimResults = true;
    const antes = texto ? texto.trimEnd() + ' ' : '';
    r.onresult = (e: any) => {
      let fala = '';
      for (let i = 0; i < e.results.length; i++) fala += e.results[i][0].transcript;
      setTexto(antes + fala);
    };
    r.onerror = () => { avisar('O microfone não funcionou aqui. Use o ditado do Windows (tecla Windows + H).', 'erro'); setOuvindo(false); };
    r.onend = () => setOuvindo(false);
    rec.current = r;
    r.start(); setOuvindo(true);
  }

  async function formulario(tipo: TipoDoc) {
    setMenuForm(false);
    const doc = await criar(tipo);
    abrir(doc, tipo);
  }

  const primeiroNome = (usuario?.nome || '').split(' ')[0];

  return (
    <section className="chat vidro">
      <div className="chat-topo">
        <div>
          <div className="rotulo">Assistente</div>
          <h1 className="titulo-tela" style={{ fontSize: 'clamp(26px,2.4vw,38px)' }}>O que vamos <em>montar</em> hoje?</h1>
        </div>
        {mensagens.length > 0 && <button className="btn pequeno" onClick={limparChat}>Nova conversa</button>}
      </div>

      <div className="chat-lista" ref={lista}>
        {mensagens.length === 0 && (
          <div className="boas-vindas">
            <h2>{primeiroNome ? `Olá, ${primeiroNome}.` : 'Olá.'}<br />Fale ou escreva do seu jeito.</h2>
            <p style={{ color: 'var(--texto-2)', margin: 0, fontSize: 17 }}>Eu organizo, corrijo e monto o documento no padrão da J.A. Você só confere e envia.</p>
            <div className="sugestoes">
              {SUGESTOES.map((s) => (
                <button key={s.t} className="sugestao" onClick={() => { setTexto(s.s); area.current?.focus(); }}><b>{s.t}</b>{s.s}</button>
              ))}
            </div>
          </div>
        )}
        {mensagens.map((m) => {
          const doc = m.documentoId ? documentos.find((d) => d.id === m.documentoId) : null;
          return (
            <div key={m.id} className={`balao ${m.papel} ${m.erro ? 'erro' : ''}`}>
              {!!m.anexos?.length && <div className="anexos">{m.anexos.map((a, i) => <span key={i} className="chip"><IconeClipe />{a.nome}</span>)}</div>}
              {m.texto}
              {doc && (
                <div className="cartao-doc" onClick={() => abrir(doc, doc.tipo)}>
                  <div style={{ flex: 1 }}>
                    <div className="rotulo">{NOME_TIPO[doc.tipo]} · {doc.numero}</div>
                    <b>{doc.cliente || doc.titulo}</b>{doc.valor != null && <span style={{ marginLeft: 8, color: 'var(--acento)', fontWeight: 800 }}>{brl(doc.valor)}</span>}
                  </div>
                  <span className="btn pequeno acento">Abrir <IconeSeta /></span>
                </div>
              )}
            </div>
          );
        })}
        {pensando && <div className="balao assistente"><span className="digitando"><i /><i /><i /></span></div>}
      </div>

      <div className="compositor">
        {anexos.length > 0 && (
          <div className="anexos-pendentes">
            {anexos.map((a, i) => (
              <span key={i} className="chip">{a.nome}<button className="btn pequeno" style={{ height: 22, padding: '0 4px', border: 0, background: 'none' }} onClick={() => setAnexos(anexos.filter((_, j) => j !== i))}><IconeX /></button></span>
            ))}
          </div>
        )}
        <textarea
          ref={area} value={texto} rows={1}
          placeholder="Ex.: recibo para a Terras Altas, serviço R$ 590 e cartucho R$ 300…"
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); mandar(); } }}
        />
        <div className="linha">
          <button className={`btn icone-btn ${ouvindo ? 'mic-on' : ''}`} title="Falar" onClick={microfone}><IconeMic /></button>
          <button className="btn icone-btn" title="Anexar foto, PDF ou Word" onClick={() => arquivo.current?.click()}><IconeClipe /></button>
          <input ref={arquivo} type="file" hidden multiple accept="image/*,.pdf,.docx,.txt" onChange={(e) => { escolherArquivos(e.target.files); e.target.value = ''; }} />
          <div style={{ position: 'relative' }}>
            <button className="btn" onClick={() => setMenuForm(!menuForm)}><IconeForm />Preencher formulário</button>
            {menuForm && (
              <div className="menu-form vidro">
                {(['orcamento', 'recibo', 'comprovante'] as TipoDoc[]).map((t) => <button key={t} onClick={() => formulario(t)}>{NOME_TIPO[t]}</button>)}
              </div>
            )}
          </div>
          <div style={{ flex: 1 }} />
          <button className="btn forte" onClick={mandar} disabled={pensando || (!texto.trim() && !anexos.length)}>Enviar <IconeEnviar /></button>
        </div>
      </div>
    </section>
  );
}
