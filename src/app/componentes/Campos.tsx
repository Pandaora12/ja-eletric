import { useEffect, useState } from 'react';
import { lerNumero } from '../lib/formato';

export function Texto({ rotulo, valor, muda, dica, longo }: { rotulo: string; valor?: string; muda: (v: string) => void; dica?: string; longo?: boolean }) {
  return (
    <label className="campo">
      <span>{rotulo}</span>
      {longo
        ? <textarea className="entrada" value={valor ?? ''} placeholder={dica} onChange={(e) => muda(e.target.value)} />
        : <input className="entrada" value={valor ?? ''} placeholder={dica} onChange={(e) => muda(e.target.value)} />}
    </label>
  );
}

/** Campo de dinheiro: aceita "1.234,56" ou "1234.56". Vazio = null. */
export function Dinheiro({ rotulo, valor, muda, vazioPermitido }: { rotulo: string; valor: number | null | undefined; muda: (v: number | null) => void; vazioPermitido?: boolean }) {
  const formata = (v: number | null | undefined) => (v == null || isNaN(v) ? '' : v.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }));
  const [txt, setTxt] = useState(formata(valor));
  const [foco, setFoco] = useState(false);
  useEffect(() => { if (!foco) setTxt(formata(valor)); }, [valor, foco]);
  return (
    <label className="campo">
      <span>{rotulo}</span>
      <div style={{ position: 'relative' }}>
        <span style={{ position: 'absolute', left: 14, top: 13, color: 'var(--texto-3)', fontWeight: 600 }}>R$</span>
        <input
          className="entrada" inputMode="decimal" value={txt} style={{ paddingLeft: 44, fontFamily: 'var(--f-rot)', fontWeight: 700 }}
          onFocus={() => setFoco(true)}
          onBlur={() => { setFoco(false); setTxt(formata(valor)); }}
          onChange={(e) => {
            setTxt(e.target.value);
            const n = lerNumero(e.target.value);
            if (e.target.value.trim() === '') muda(vazioPermitido ? null : 0);
            else if (!isNaN(n)) muda(n);
          }}
        />
      </div>
    </label>
  );
}

export function Data({ rotulo, valor, muda }: { rotulo: string; valor?: string; muda: (v: string) => void }) {
  return (
    <label className="campo"><span>{rotulo}</span><input className="entrada" type="date" value={valor ?? ''} onChange={(e) => muda(e.target.value)} /></label>
  );
}

export function Escolha<T extends string>({ rotulo, valor, opcoes, muda }: { rotulo: string; valor: T; opcoes: [T, string][]; muda: (v: T) => void }) {
  return (
    <label className="campo">
      <span>{rotulo}</span>
      <select className="entrada" value={valor} onChange={(e) => muda(e.target.value as T)}>
        {opcoes.map(([v, n]) => <option key={v} value={v}>{n}</option>)}
      </select>
    </label>
  );
}

/** Lista de frases (ex.: "o que está incluso"). Uma por linha. */
export function ListaTexto({ rotulo, valor, muda, dica }: { rotulo: string; valor?: string[]; muda: (v: string[]) => void; dica?: string }) {
  return (
    <label className="campo">
      <span>{rotulo} <small style={{ color: 'var(--texto-3)', fontWeight: 400 }}>· uma por linha</small></span>
      <textarea className="entrada" style={{ minHeight: 110 }} placeholder={dica} value={(valor ?? []).join('\n')} onChange={(e) => muda(e.target.value.split('\n'))} onBlur={(e) => muda(e.target.value.split('\n').map((s) => s.trim()).filter(Boolean))} />
    </label>
  );
}
