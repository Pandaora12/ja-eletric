import { useEffect, useRef, useState } from 'react';

/**
 * Mascote "Capi": capivara eletricista (personagem original da J.A Documentos).
 * Passeia pela parte de baixo do app: anda balançando, para e acena para você, vira de costas,
 * senta e cochila. Clicar nela faz ela pular de alegria.
 */
type Pose = 'andando' | 'acenando' | 'costas' | 'cochilando' | 'pulando';

function Defs() {
  return (
    <defs>
      <radialGradient id="capi-pelo" cx="40%" cy="30%" r="75%"><stop offset="0" stopColor="#d39a63" /><stop offset=".65" stopColor="#b47a46" /><stop offset="1" stopColor="#8f5c32" /></radialGradient>
      <radialGradient id="capi-focinho" cx="45%" cy="35%" r="70%"><stop offset="0" stopColor="#9a6a42" /><stop offset="1" stopColor="#6f4626" /></radialGradient>
      <linearGradient id="capi-capacete" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#ffe066" /><stop offset=".6" stopColor="#ffc21a" /><stop offset="1" stopColor="#e9a400" /></linearGradient>
      <linearGradient id="capi-colete" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#ff9a3c" /><stop offset="1" stopColor="#ef6a10" /></linearGradient>
    </defs>
  );
}

function Olho({ x, y, fechado }: { x: number; y: number; fechado: boolean }) {
  return fechado
    ? <path d={`M${x - 5} ${y + 1} Q ${x} ${y + 5} ${x + 5} ${y + 1}`} stroke="#2a1a0e" strokeWidth="2.4" fill="none" strokeLinecap="round" />
    : (
      <g>
        <ellipse cx={x} cy={y} rx="5.2" ry="6.4" fill="#2a1a0e" />
        <circle cx={x + 1.8} cy={y - 2.4} r="2" fill="#fff" />
        <circle cx={x - 1.6} cy={y + 2.2} r=".9" fill="#fff" opacity=".8" />
      </g>
    );
}

function Capacete({ cx, cy, w }: { cx: number; cy: number; w: number }) {
  const r = w / 2;
  return (
    <g>
      <path d={`M${cx - r} ${cy} C ${cx - r + 2} ${cy - r * 1.05}, ${cx + r - 2} ${cy - r * 1.05}, ${cx + r} ${cy} Z`} fill="url(#capi-capacete)" />
      <path d={`M${cx - 3} ${cy - r * 0.78} L${cx + 3} ${cy - r * 0.78} L${cx + 3.5} ${cy} L${cx - 3.5} ${cy} Z`} fill="#f2b000" opacity=".9" />
      <rect x={cx - r - 5} y={cy - 1.5} width={w + 10} height="6.5" rx="3.2" fill="#e9a400" />
      <path d={`M${cx - r + 6} ${cy - r * 0.55} Q ${cx - r * 0.3} ${cy - r * 0.95} ${cx - 6} ${cy - r * 0.86}`} stroke="#fff6c8" strokeWidth="2.4" fill="none" strokeLinecap="round" opacity=".8" />
      <text x={cx + r * 0.42} y={cy - r * 0.28} fontFamily="Saira, sans-serif" fontWeight="900" fontSize={r * 0.36} fill="#0e6b38" textAnchor="middle">J.A</text>
    </g>
  );
}

/** De lado, olhando para a direita. */
function Lado({ olhoFechado, sentada }: { olhoFechado: boolean; sentada?: boolean }) {
  return (
    <svg viewBox="0 0 130 120" className="bicho-svg">
      <Defs />
      <g className="corpo-todo">
        {!sentada && (
          <>
            <g className="perna perna-b"><rect x="30" y="88" width="11" height="20" rx="5.5" fill="#8f5c32" /><ellipse cx="35.5" cy="107" rx="7" ry="3.4" fill="#5e3a1d" /></g>
            <g className="perna perna-a"><rect x="66" y="88" width="11" height="20" rx="5.5" fill="#8f5c32" /><ellipse cx="71.5" cy="107" rx="7" ry="3.4" fill="#5e3a1d" /></g>
          </>
        )}
        <ellipse cx="54" cy={sentada ? 92 : 80} rx="36" ry={sentada ? 20 : 24} fill="url(#capi-pelo)" />
        {/* colete */}
        <path d={sentada ? 'M40 78 Q 54 72 72 78 L 76 104 Q 56 110 38 104 Z' : 'M38 62 Q 56 56 74 62 L 80 98 Q 58 106 34 98 Z'} fill="url(#capi-colete)" />
        <path d={sentada ? 'M38 92 L 76 92' : 'M35 84 L 79 84'} stroke="#f4f7ef" strokeWidth="5" />
        <path d={sentada ? 'M38 92 L 76 92' : 'M35 84 L 79 84'} stroke="#cfd6c8" strokeWidth="1.2" strokeDasharray="3 3" />
        {!sentada && (
          <>
            <g className="perna perna-a"><rect x="24" y="90" width="12" height="20" rx="6" fill="#a46c3c" /><ellipse cx="30" cy="109" rx="7.5" ry="3.6" fill="#5e3a1d" /></g>
            <g className="perna perna-b"><rect x="60" y="90" width="12" height="20" rx="6" fill="#a46c3c" /><ellipse cx="66" cy="109" rx="7.5" ry="3.6" fill="#5e3a1d" /></g>
          </>
        )}
        {sentada && <ellipse cx="80" cy="108" rx="10" ry="4" fill="#5e3a1d" />}
        {/* cabeça */}
        <g transform={sentada ? 'translate(0 10)' : undefined}>
          <ellipse cx="66" cy="30" rx="6" ry="5" fill="#8f5c32" />
          <ellipse cx="66" cy="31" rx="3.2" ry="2.6" fill="#5e3a1d" />
          <ellipse cx="82" cy="50" rx="29" ry="26" fill="url(#capi-pelo)" />
          <rect x="92" y="44" width="30" height="27" rx="13" fill="url(#capi-focinho)" />
          <ellipse cx="116" cy="51" rx="2.4" ry="1.8" fill="#2a1a0e" />
          <path d="M104 65 Q 110 69 116 64" stroke="#3a2412" strokeWidth="1.8" fill="none" strokeLinecap="round" />
          <ellipse cx="94" cy="62" rx="5" ry="3" fill="#ff8f7a" opacity=".45" />
          <Olho x={90} y={44} fechado={olhoFechado} />
          <Capacete cx={80} cy={32} w={50} />
        </g>
      </g>
      {sentada && <g className="zzz" fill="#fff" fontFamily="Saira, sans-serif" fontWeight="800"><text x="104" y="22" fontSize="14">z</text><text x="114" y="10" fontSize="10">z</text></g>}
    </svg>
  );
}

/** De frente, acenando. */
function Frente({ olhoFechado, acenando }: { olhoFechado: boolean; acenando: boolean }) {
  return (
    <svg viewBox="0 0 130 120" className="bicho-svg">
      <Defs />
      <g className="corpo-todo">
        <ellipse cx="50" cy="109" rx="9" ry="4" fill="#5e3a1d" /><ellipse cx="80" cy="109" rx="9" ry="4" fill="#5e3a1d" />
        <ellipse cx="65" cy="88" rx="30" ry="21" fill="url(#capi-pelo)" />
        <path d="M44 74 Q 65 68 86 74 L 90 102 Q 65 110 40 102 Z" fill="url(#capi-colete)" />
        <path d="M42 90 L 88 90" stroke="#f4f7ef" strokeWidth="5" />
        <path d="M65 72 L 65 104" stroke="#d25a0c" strokeWidth="1.5" />
        <ellipse cx="38" cy="88" rx="7" ry="10" fill="#a46c3c" />
        <g className={acenando ? 'braco-aceno' : ''} style={{ transformOrigin: '92px 86px' }}>
          <ellipse cx="94" cy={acenando ? 74 : 88} rx="7" ry="10" fill="#a46c3c" transform={acenando ? 'rotate(-35 94 74)' : undefined} />
        </g>
        <ellipse cx="38" cy="26" rx="7" ry="6" fill="#8f5c32" /><ellipse cx="92" cy="26" rx="7" ry="6" fill="#8f5c32" />
        <ellipse cx="38" cy="27" rx="3.6" ry="3" fill="#5e3a1d" /><ellipse cx="92" cy="27" rx="3.6" ry="3" fill="#5e3a1d" />
        <ellipse cx="65" cy="50" rx="31" ry="27" fill="url(#capi-pelo)" />
        <ellipse cx="65" cy="63" rx="16" ry="12" fill="url(#capi-focinho)" />
        <ellipse cx="60" cy="59" rx="2" ry="1.5" fill="#2a1a0e" /><ellipse cx="70" cy="59" rx="2" ry="1.5" fill="#2a1a0e" />
        <path d="M59 67 Q 62 70 65 67 Q 68 70 71 67" stroke="#3a2412" strokeWidth="1.8" fill="none" strokeLinecap="round" />
        <ellipse cx="45" cy="57" rx="5" ry="3" fill="#ff8f7a" opacity=".5" /><ellipse cx="85" cy="57" rx="5" ry="3" fill="#ff8f7a" opacity=".5" />
        <Olho x={53} y={46} fechado={olhoFechado} /><Olho x={77} y={46} fechado={olhoFechado} />
        <Capacete cx={65} cy={32} w={56} />
      </g>
    </svg>
  );
}

/** De costas. */
function Costas() {
  return (
    <svg viewBox="0 0 130 120" className="bicho-svg">
      <Defs />
      <g className="corpo-todo">
        <ellipse cx="50" cy="109" rx="9" ry="4" fill="#5e3a1d" /><ellipse cx="80" cy="109" rx="9" ry="4" fill="#5e3a1d" />
        <ellipse cx="65" cy="88" rx="31" ry="22" fill="url(#capi-pelo)" />
        <path d="M42 74 Q 65 68 88 74 L 92 102 Q 65 110 38 102 Z" fill="url(#capi-colete)" />
        <path d="M40 90 L 90 90" stroke="#f4f7ef" strokeWidth="5" />
        <text x="65" y="85" fontFamily="Saira, sans-serif" fontWeight="900" fontSize="9" fill="#fff" textAnchor="middle">J.A</text>
        <ellipse cx="38" cy="26" rx="7" ry="6" fill="#8f5c32" /><ellipse cx="92" cy="26" rx="7" ry="6" fill="#8f5c32" />
        <ellipse cx="65" cy="50" rx="31" ry="27" fill="url(#capi-pelo)" />
        <path d="M52 66 Q 65 72 78 66" stroke="#8f5c32" strokeWidth="2.5" fill="none" />
        <Capacete cx={65} cy={32} w={56} />
      </g>
    </svg>
  );
}

export function Bichinho() {
  const [x, setX] = useState(140);
  const [dir, setDir] = useState<1 | -1>(1);
  const [pose, setPose] = useState<Pose>('andando');
  const [piscando, setPiscando] = useState(false);
  const estado = useRef({ x: 140, dir: 1 as 1 | -1, pose: 'andando' as Pose, ate: 0 });

  useEffect(() => {
    let raf = 0, antes = performance.now();
    const proxima = (agora: number) => {
      const s = estado.current;
      if (s.pose === 'andando') {
        const r = Math.random();
        s.pose = r < 0.45 ? 'acenando' : r < 0.7 ? 'costas' : r < 0.88 ? 'cochilando' : 'andando';
        if (s.pose === 'andando') s.dir = (s.dir * -1) as 1 | -1;
      } else {
        s.pose = 'andando';
        if (Math.random() < 0.45) s.dir = (s.dir * -1) as 1 | -1;
      }
      const dur = { andando: 6000 + Math.random() * 8000, acenando: 2800 + Math.random() * 2000, costas: 2400 + Math.random() * 2000, cochilando: 5000 + Math.random() * 5000, pulando: 900 }[s.pose];
      s.ate = agora + dur;
      setPose(s.pose); setDir(s.dir);
    };
    estado.current.ate = performance.now() + 7000;
    const quadro = (agora: number) => {
      const dt = Math.min(0.05, (agora - antes) / 1000);
      antes = agora;
      const s = estado.current;
      if (s.pose === 'andando') {
        const max = Math.max(220, window.innerWidth - 130);
        s.x += s.dir * 38 * dt;
        if (s.x > max) { s.x = max; s.dir = -1; setDir(-1); }
        if (s.x < 80) { s.x = 80; s.dir = 1; setDir(1); }
        setX(s.x);
      }
      if (agora > s.ate) proxima(agora);
      raf = requestAnimationFrame(quadro);
    };
    raf = requestAnimationFrame(quadro);
    return () => cancelAnimationFrame(raf);
  }, []);

  useEffect(() => {
    let t = 0;
    const ciclo = () => { t = window.setTimeout(() => { setPiscando(true); window.setTimeout(() => setPiscando(false), 150); ciclo(); }, 2200 + Math.random() * 2800); };
    ciclo();
    return () => window.clearTimeout(t);
  }, []);

  const pular = () => { const s = estado.current; s.pose = 'pulando'; s.ate = performance.now() + 900; setPose('pulando'); };

  const desenho = pose === 'acenando' || pose === 'pulando' ? <Frente olhoFechado={piscando || pose === 'pulando'} acenando />
    : pose === 'costas' ? <Costas />
    : pose === 'cochilando' ? <Lado olhoFechado sentada />
    : <Lado olhoFechado={piscando} />;

  return (
    <div className={`bichinho pose-${pose}`} style={{ transform: `translateX(${x}px)` }} onClick={pular} title="Oi! Eu sou a Capi.">
      <div className="bicho-vira" style={{ transform: pose === 'andando' || pose === 'cochilando' ? `scaleX(${dir})` : undefined }}>{desenho}</div>
      <div className="bicho-sombra" />
    </div>
  );
}
