import { useEffect, useState } from 'react';

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];

/** Relógio digital do computador: hora:minuto:segundo, data ao lado e o nome do mês embaixo. */
export function Relogio({ grande }: { grande?: boolean }) {
  const [agora, setAgora] = useState(() => new Date());
  useEffect(() => {
    const t = window.setInterval(() => setAgora(new Date()), 1000);
    return () => window.clearInterval(t);
  }, []);
  const p = (n: number) => String(n).padStart(2, '0');
  return (
    <div className={`relogio ${grande ? 'grande' : ''}`}>
      <div className="horas num">{p(agora.getHours())}:{p(agora.getMinutes())}:{p(agora.getSeconds())}</div>
      <div className="data-bloco">
        <div className="data num">{p(agora.getDate())}/{p(agora.getMonth() + 1)}/{agora.getFullYear()}</div>
        <div className="mes">{MESES[agora.getMonth()]}</div>
      </div>
    </div>
  );
}
