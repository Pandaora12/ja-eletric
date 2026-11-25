import { useEffect, useRef, useState } from 'react';

/**
 * Mascote: uma capivara de capacete de eletricista que passeia pela parte de baixo do app.
 * Ela anda, para e olha para você (piscando), vira de costas, senta e volta a andar.
 * Clicar nela faz ela pular.
 */
type Pose = 'andando' | 'olhando' | 'costas' | 'sentada' | 'pulando';

const COR = { pelo: '#9c6b3f', sombra: '#7a5130', claro: '#b8875a', focinho: '#6e4a2c', olho: '#1b120b', capacete: '#ffc21a', capaceteSombra: '#d99a00', contorno: '#4a3020' };

function Lado({ passo }: { passo: boolean }) {
  return (
    <svg viewBox="0 0 110 80" className="bicho-svg">
      {/* pernas (alternam no passo) */}
      <g fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.5">
        <rect x="22" y="52" width="9" height={passo ? 20 : 17} rx="4" />
        <rect x="34" y="52" width="9" height={passo ? 17 : 20} rx="4" />
        <rect x="64" y="52" width="9" height={passo ? 17 : 20} rx="4" />
        <rect x="76" y="52" width="9" height={passo ? 20 : 17} rx="4" />
      </g>
      <ellipse cx="52" cy="44" rx="38" ry="21" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <ellipse cx="50" cy="52" rx="28" ry="9" fill={COR.claro} opacity=".55" />
      {/* cabeça */}
      <ellipse cx="86" cy="36" rx="18" ry="14" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <rect x="90" y="33" width="17" height="15" rx="7" fill={COR.focinho} stroke={COR.contorno} strokeWidth="1.5" />
      <ellipse cx="103" cy="37" rx="1.6" ry="1.2" fill={COR.olho} />
      <ellipse cx="79" cy="24" rx="4.5" ry="3.8" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      <circle cx="91" cy="31" r="2.4" fill={COR.olho} />
      <circle cx="91.8" cy="30.2" r=".8" fill="#fff" />
      {/* capacete */}
      <path d="M72 24 C 74 10, 98 10, 100 24 Z" fill={COR.capacete} stroke={COR.contorno} strokeWidth="1.5" />
      <rect x="69" y="22" width="34" height="4.5" rx="2" fill={COR.capaceteSombra} stroke={COR.contorno} strokeWidth="1.3" />
      <rect x="84" y="12" width="4" height="10" rx="1.5" fill={COR.capaceteSombra} />
    </svg>
  );
}

function Frente({ piscando }: { piscando: boolean }) {
  return (
    <svg viewBox="0 0 110 80" className="bicho-svg">
      <g fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.5">
        <rect x="36" y="58" width="11" height="16" rx="5" />
        <rect x="63" y="58" width="11" height="16" rx="5" />
      </g>
      <ellipse cx="55" cy="54" rx="30" ry="18" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <ellipse cx="55" cy="36" rx="21" ry="18" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <ellipse cx="36" cy="24" rx="5" ry="4" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      <ellipse cx="74" cy="24" rx="5" ry="4" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      <ellipse cx="55" cy="45" rx="12" ry="8.5" fill={COR.focinho} stroke={COR.contorno} strokeWidth="1.4" />
      <ellipse cx="51" cy="43" rx="1.4" ry="1.1" fill={COR.olho} /><ellipse cx="59" cy="43" rx="1.4" ry="1.1" fill={COR.olho} />
      {piscando ? (
        <g stroke={COR.olho} strokeWidth="2" strokeLinecap="round"><path d="M44 34 h6" /><path d="M60 34 h6" /></g>
      ) : (
        <g><circle cx="47" cy="34" r="2.8" fill={COR.olho} /><circle cx="63" cy="34" r="2.8" fill={COR.olho} /><circle cx="48" cy="33" r=".9" fill="#fff" /><circle cx="64" cy="33" r=".9" fill="#fff" /></g>
      )}
      <ellipse cx="42" cy="40" rx="3" ry="1.6" fill="#e98a7a" opacity=".45" /><ellipse cx="68" cy="40" rx="3" ry="1.6" fill="#e98a7a" opacity=".45" />
      <path d="M35 24 C 37 6, 73 6, 75 24 Z" fill={COR.capacete} stroke={COR.contorno} strokeWidth="1.5" />
      <rect x="31" y="22" width="48" height="5" rx="2.2" fill={COR.capaceteSombra} stroke={COR.contorno} strokeWidth="1.3" />
      <rect x="52" y="9" width="6" height="12" rx="2" fill={COR.capaceteSombra} />
    </svg>
  );
}

function Costas() {
  return (
    <svg viewBox="0 0 110 80" className="bicho-svg">
      <g fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.5">
        <rect x="36" y="58" width="11" height="16" rx="5" />
        <rect x="63" y="58" width="11" height="16" rx="5" />
      </g>
      <ellipse cx="55" cy="52" rx="31" ry="20" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <ellipse cx="55" cy="33" rx="20" ry="16" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <ellipse cx="37" cy="24" rx="5" ry="4" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      <ellipse cx="73" cy="24" rx="5" ry="4" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      <path d="M45 40 C 50 46, 60 46, 65 40" stroke={COR.sombra} strokeWidth="2" fill="none" />
      <path d="M36 25 C 38 7, 72 7, 74 25 Z" fill={COR.capacete} stroke={COR.contorno} strokeWidth="1.5" />
      <rect x="32" y="23" width="46" height="5" rx="2.2" fill={COR.capaceteSombra} stroke={COR.contorno} strokeWidth="1.3" />
    </svg>
  );
}

function Sentada({ piscando }: { piscando: boolean }) {
  return (
    <svg viewBox="0 0 110 80" className="bicho-svg">
      <ellipse cx="50" cy="56" rx="34" ry="17" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <rect x="66" y="58" width="10" height="16" rx="5" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.5" />
      <ellipse cx="80" cy="38" rx="18" ry="14" fill={COR.pelo} stroke={COR.contorno} strokeWidth="1.8" />
      <rect x="84" y="35" width="16" height="14" rx="7" fill={COR.focinho} stroke={COR.contorno} strokeWidth="1.5" />
      <ellipse cx="73" cy="26" rx="4.5" ry="3.8" fill={COR.sombra} stroke={COR.contorno} strokeWidth="1.3" />
      {piscando ? <path d="M82 33 h6" stroke={COR.olho} strokeWidth="2" strokeLinecap="round" /> : <circle cx="85" cy="33" r="2.4" fill={COR.olho} />}
      <path d="M66 26 C 68 12, 92 12, 94 26 Z" fill={COR.capacete} stroke={COR.contorno} strokeWidth="1.5" />
      <rect x="63" y="24" width="34" height="4.5" rx="2" fill={COR.capaceteSombra} stroke={COR.contorno} strokeWidth="1.3" />
      <text x="16" y="30" fontSize="13" fill="#fff" opacity=".85" fontFamily="Saira">z</text>
      <text x="6" y="18" fontSize="9" fill="#fff" opacity=".6" fontFamily="Saira">z</text>
    </svg>
  );
}

export function Bichinho() {
  const [x, setX] = useState(120);
  const [dir, setDir] = useState<1 | -1>(1);
  const [pose, setPose] = useState<Pose>('andando');
  const [passo, setPasso] = useState(false);
  const [piscando, setPiscando] = useState(false);
  const estado = useRef({ x: 120, dir: 1 as 1 | -1, pose: 'andando' as Pose, ate: 0 });

  // movimento e troca de poses
  useEffect(() => {
    let raf = 0, antes = performance.now(), passoAcum = 0;
    const sortearPose = (agora: number) => {
      const s = estado.current;
      const r = Math.random();
      if (s.pose === 'andando') {
        s.pose = r < 0.45 ? 'olhando' : r < 0.7 ? 'costas' : r < 0.85 ? 'sentada' : 'andando';
        if (s.pose === 'andando') s.dir = (s.dir * -1) as 1 | -1;
      } else {
        s.pose = 'andando';
        if (Math.random() < 0.4) s.dir = (s.dir * -1) as 1 | -1;
      }
      const dur = { andando: 5000 + Math.random() * 7000, olhando: 2600 + Math.random() * 2500, costas: 2200 + Math.random() * 2000, sentada: 4000 + Math.random() * 4000, pulando: 700 }[s.pose];
      s.ate = agora + dur;
      setPose(s.pose); setDir(s.dir);
    };
    estado.current.ate = performance.now() + 6000;
    const quadro = (agora: number) => {
      const dt = Math.min(0.05, (agora - antes) / 1000);
      antes = agora;
      const s = estado.current;
      if (s.pose === 'andando') {
        const max = Math.max(200, window.innerWidth - 90);
        s.x += s.dir * 42 * dt;
        if (s.x > max) { s.x = max; s.dir = -1; setDir(-1); }
        if (s.x < 20) { s.x = 20; s.dir = 1; setDir(1); }
        setX(s.x);
        passoAcum += dt;
        if (passoAcum > 0.18) { passoAcum = 0; setPasso((p) => !p); }
      }
      if (agora > s.ate) sortearPose(agora);
      raf = requestAnimationFrame(quadro);
    };
    raf = requestAnimationFrame(quadro);
    return () => cancelAnimationFrame(raf);
  }, []);

  // piscar de vez em quando
  useEffect(() => {
    let t = 0;
    const ciclo = () => {
      t = window.setTimeout(() => { setPiscando(true); window.setTimeout(() => setPiscando(false), 160); ciclo(); }, 1800 + Math.random() * 2600);
    };
    ciclo();
    return () => window.clearTimeout(t);
  }, []);

  const pular = () => {
    const s = estado.current;
    s.pose = 'pulando'; s.ate = performance.now() + 700;
    setPose('pulando');
  };

  const mostrar = pose === 'olhando' ? <Frente piscando={piscando} />
    : pose === 'costas' ? <Costas />
    : pose === 'sentada' ? <Sentada piscando={piscando} />
    : <Lado passo={pose === 'andando' && passo} />;

  return (
    <div className={`bichinho pose-${pose}`} style={{ transform: `translateX(${x}px)` }} onClick={pular} title="Oi! Sou a Capi.">
      <div style={{ transform: pose === 'andando' || pose === 'pulando' ? `scaleX(${dir})` : undefined }}>{mostrar}</div>
      <div className="bicho-sombra" />
    </div>
  );
}
