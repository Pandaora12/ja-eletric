/**
 * Paisagens de fundo (desenhos originais, inspirados nas fotos de referência).
 * Ficam desfocadas atrás do app; cada aba tem a sua.
 */
import { useEffect, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';

export type NomePaisagem = 'aurora' | 'cachoeira' | 'floresta' | 'outono' | 'campo' | 'alpes' | 'rio' | 'trem' | 'cidade' | 'lago';

const pinheiros = (y: number, n: number, cor: string, alt: number, semente: number) =>
  Array.from({ length: n }, (_, i) => {
    const x = (i * 1600) / n + ((i * semente) % 37) - 18;
    const h = alt * (0.7 + (((i * 13 + semente) % 10) / 22));
    return <path key={i} d={`M${x} ${y - h} L${x - h * 0.28} ${y} L${x + h * 0.28} ${y} Z`} fill={cor} />;
  });

function Cena({ nome }: { nome: NomePaisagem }) {
  switch (nome) {
    case 'aurora': // aurora boreal sobre o fiorde
      return (
        <>
          <defs>
            <linearGradient id="ceu-a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#030b1f" /><stop offset=".55" stopColor="#0b2a4a" /><stop offset="1" stopColor="#123d55" /></linearGradient>
            <linearGradient id="agua-a" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#0f3a46" /><stop offset="1" stopColor="#031018" /></linearGradient>
          </defs>
          <rect width="1600" height="900" fill="url(#ceu-a)" />
          {Array.from({ length: 70 }, (_, i) => <circle key={i} cx={(i * 233) % 1600} cy={(i * 97) % 420} r={(i % 3) * 0.7 + 0.6} fill="#e8f4ff" opacity={0.5 + (i % 5) / 10} />)}
          <path d="M150 380 C 420 120, 640 360, 860 150 S 1300 90, 1480 300" stroke="#37f0a0" strokeWidth="90" fill="none" opacity=".55" />
          <path d="M300 420 C 560 220, 760 420, 1000 220 S 1350 200, 1550 360" stroke="#7dffc4" strokeWidth="40" fill="none" opacity=".45" />
          <path d="M80 300 C 300 160, 520 300, 700 120" stroke="#2bd4ff" strokeWidth="60" fill="none" opacity=".25" />
          <path d="M0 560 L180 430 L330 520 L470 400 L640 560 Z" fill="#dfe9f2" />
          <path d="M900 560 L1080 410 L1220 480 L1380 380 L1600 520 L1600 560 Z" fill="#cfdcea" />
          <path d="M0 560 L180 470 L330 540 L470 450 L640 560 Z" fill="#9fb3c6" opacity=".6" />
          <rect y="560" width="1600" height="340" fill="url(#agua-a)" />
          <path d="M200 600 C 420 640, 700 600, 980 650 S 1400 610, 1600 660" stroke="#37f0a0" strokeWidth="26" fill="none" opacity=".25" />
          {Array.from({ length: 14 }, (_, i) => <rect key={i} x={40 + i * 26} y={548} width="14" height="8" fill="#ffcf7a" opacity=".8" />)}
        </>
      );
    case 'cachoeira': // cachoeira na mata e água turquesa
      return (
        <>
          <defs>
            <linearGradient id="ceu-c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#5fb2ff" /><stop offset="1" stopColor="#c9ecff" /></linearGradient>
            <linearGradient id="agua-c" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#25d0d0" /><stop offset="1" stopColor="#0b6f7a" /></linearGradient>
          </defs>
          <rect width="1600" height="900" fill="url(#ceu-c)" />
          <circle cx="1180" cy="110" r="60" fill="#fffbe0" />
          <path d="M600 520 L900 220 L1150 420 L1350 260 L1600 470 L1600 560 L600 560 Z" fill="#3f8f5f" opacity=".7" />
          <path d="M0 0 L520 0 C 480 160, 560 300, 470 560 L0 560 Z" fill="#1f5a2a" />
          <path d="M1600 0 L1180 0 C 1240 140, 1160 260, 1260 560 L1600 560 Z" fill="#256b34" />
          <rect x="180" y="200" width="260" height="380" fill="#2d3a2c" />
          <rect x="230" y="200" width="70" height="380" fill="#eafcff" opacity=".9" />
          <rect x="320" y="220" width="45" height="360" fill="#d6f7ff" opacity=".85" />
          <rect y="560" width="1600" height="340" fill="url(#agua-c)" />
          {Array.from({ length: 30 }, (_, i) => <ellipse key={i} cx={(i * 211) % 1600} cy={600 + ((i * 53) % 280)} rx={40 + (i % 4) * 18} ry="5" fill="#bffcff" opacity=".35" />)}
          <ellipse cx="160" cy="820" rx="220" ry="90" fill="#6d5a45" />
          <ellipse cx="1450" cy="640" rx="140" ry="40" fill="#5c4c3b" />
        </>
      );
    case 'floresta': // floresta e montanhas verdes
      return (
        <>
          <defs><linearGradient id="ceu-f" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7cc4ff" /><stop offset="1" stopColor="#d9f1ff" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-f)" />
          <path d="M0 470 L260 300 L520 430 L800 260 L1100 420 L1350 300 L1600 410 L1600 900 L0 900 Z" fill="#6c9a7e" />
          <path d="M0 560 L300 420 L620 540 L900 430 L1250 560 L1600 470 L1600 900 L0 900 Z" fill="#3d7a4f" />
          {pinheiros(640, 40, '#1f5232', 150, 7)}
          <rect y="640" width="1600" height="260" fill="#2f6b3c" />
          {pinheiros(900, 30, '#163d24', 260, 11)}
        </>
      );
    case 'outono': // lago de montanha no outono
      return (
        <>
          <defs>
            <linearGradient id="ceu-o" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#2f78d6" /><stop offset="1" stopColor="#a8d4ff" /></linearGradient>
            <linearGradient id="agua-o" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#1d4f8a" /><stop offset="1" stopColor="#0b2340" /></linearGradient>
          </defs>
          <rect width="1600" height="900" fill="url(#ceu-o)" />
          <path d="M420 520 L700 220 L860 360 L980 260 L1220 520 Z" fill="#8fa6bd" />
          <path d="M650 520 L700 220 L760 300 Z" fill="#e8eef5" />
          <path d="M0 520 L300 380 L520 520 Z M1100 520 L1380 360 L1600 470 L1600 520 Z" fill="#2c4f43" />
          <rect y="520" width="1600" height="380" fill="url(#agua-o)" />
          <path d="M420 520 L700 820 L860 680 L980 780 L1220 520 Z" fill="#6f87a3" opacity=".45" />
          <circle cx="230" cy="250" r="230" fill="#e8731b" /><circle cx="90" cy="420" r="180" fill="#f29a2e" />
          <circle cx="1420" cy="200" r="260" fill="#d9541a" /><circle cx="1560" cy="430" r="170" fill="#f2a33a" />
          <rect x="210" y="380" width="22" height="300" fill="#4a2c18" /><rect x="1430" y="330" width="26" height="360" fill="#4a2c18" />
        </>
      );
    case 'campo': // campo dourado ao entardecer
      return (
        <>
          <defs><linearGradient id="ceu-p" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#5aa9e6" /><stop offset=".7" stopColor="#ffe6a3" /><stop offset="1" stopColor="#ffcf6a" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-p)" />
          <circle cx="520" cy="420" r="90" fill="#fff6c8" />
          <path d="M0 470 L300 380 L620 450 L980 360 L1300 440 L1600 380 L1600 900 L0 900 Z" fill="#5f8f73" />
          <path d="M0 560 L400 470 L800 540 L1200 460 L1600 540 L1600 900 L0 900 Z" fill="#7fa94a" />
          <path d="M0 680 C 400 600, 900 640, 1600 600 L1600 900 L0 900 Z" fill="#c9b24a" />
          <path d="M0 780 C 500 720, 1000 760, 1600 720 L1600 900 L0 900 Z" fill="#e0c25a" />
          {pinheiros(560, 24, '#2f5a3a', 90, 5)}
        </>
      );
    case 'alpes': // vilarejo nos Alpes à noite
      return (
        <>
          <defs><linearGradient id="ceu-n" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#0d2348" /><stop offset="1" stopColor="#2c5d96" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-n)" />
          <path d="M600 620 L860 130 L930 230 L1180 620 Z" fill="#c9d8ea" />
          <path d="M860 130 L800 300 L900 260 Z" fill="#ffffff" />
          <path d="M0 620 L260 380 L520 620 Z M1100 620 L1380 360 L1600 520 L1600 620 Z" fill="#8ea7c4" />
          <path d="M0 900 L0 640 L400 600 L900 660 L1600 610 L1600 900 Z" fill="#1d2f4a" />
          {Array.from({ length: 140 }, (_, i) => (
            <rect key={i} x={(i * 113) % 1600} y={650 + ((i * 41) % 230)} width={10 + (i % 3) * 4} height="8" fill={i % 4 ? '#ffb44d' : '#ffd88a'} opacity=".85" />
          ))}
        </>
      );
    case 'rio': // rio na mata com neblina
      return (
        <>
          <defs><linearGradient id="ceu-r" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#9fc7c0" /><stop offset="1" stopColor="#e3efe6" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-r)" />
          <path d="M0 420 L400 300 L800 400 L1200 280 L1600 380 L1600 900 L0 900 Z" fill="#7f9f8c" />
          {pinheiros(560, 36, '#3f6b52', 160, 3)}
          <rect y="560" width="1600" height="340" fill="#4f7d5a" />
          <path d="M700 560 C 760 650, 600 720, 760 900 L1060 900 C 900 760, 1020 660, 900 560 Z" fill="#7fc6c4" />
          {pinheiros(900, 26, '#22452f', 280, 9)}
        </>
      );
    case 'trem': // trem vermelho na neve dos Alpes
      return (
        <>
          <defs><linearGradient id="ceu-t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#7fa2c4" /><stop offset="1" stopColor="#dfe7ee" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-t)" />
          <path d="M0 520 L250 160 L420 260 L640 90 L980 380 L1300 300 L1600 460 L1600 900 L0 900 Z" fill="#e9eef2" />
          <path d="M250 160 L330 300 L200 360 Z M640 90 L720 260 L560 300 Z" fill="#9aa8b6" opacity=".6" />
          <path d="M0 620 C 400 560, 900 640, 1600 580 L1600 900 L0 900 Z" fill="#f7f9fb" />
          <path d="M0 700 C 300 680, 600 760, 900 720 S 1400 640, 1600 660" stroke="#8a8f96" strokeWidth="6" fill="none" />
          {[0,1,2,3,4,5].map((i) => <rect key={i} x={560 + i * 62} y={690 - i * 4} width="58" height="26" rx="5" fill="#c8202a" />)}
          {Array.from({ length: 12 }, (_, i) => <rect key={i} x={80 + i * 130} y={600} width="4" height="120" fill="#5b5f66" />)}
          <ellipse cx="140" cy="760" rx="160" ry="40" fill="#8fb3c9" opacity=".7" />
        </>
      );
    case 'cidade': // cidade à beira-rio no entardecer
      return (
        <>
          <defs><linearGradient id="ceu-x" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#1f4f73" /><stop offset=".6" stopColor="#6f8fa6" /><stop offset=".85" stopColor="#f08c4a" /><stop offset="1" stopColor="#f6c27a" /></linearGradient></defs>
          <rect width="1600" height="900" fill="url(#ceu-x)" />
          {Array.from({ length: 22 }, (_, i) => { const h = 80 + ((i * 53) % 180) + (i === 15 ? 260 : 0); return <rect key={i} x={i * 74} y={560 - h} width={60} height={h} fill="#1d2a36" />; })}
          {Array.from({ length: 160 }, (_, i) => <rect key={i} x={(i * 97) % 1600} y={380 + ((i * 37) % 170)} width="6" height="5" fill="#ffd27a" opacity=".8" />)}
          <path d="M0 560 C 400 600, 900 560, 1600 600 L1600 900 L0 900 Z" fill="#3b5466" />
          <path d="M0 560 L0 900 L380 900 C 420 760, 300 660, 200 560 Z" fill="#1e3a26" />
          <path d="M1600 600 L1600 900 L1150 900 C 1180 780, 1300 680, 1600 600 Z" fill="#244a2e" />
          {Array.from({ length: 40 }, (_, i) => <rect key={i} x={420 + (i * 17) % 700} y={640 + ((i * 29) % 200)} width={30 + (i % 4) * 10} height="3" fill="#ffd27a" opacity=".25" />)}
        </>
      );
    case 'lago': // lago turquesa entre montanhas ao pôr do sol
      return (
        <>
          <defs>
            <linearGradient id="ceu-l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#3b3f6e" /><stop offset=".55" stopColor="#e98a4c" /><stop offset="1" stopColor="#ffd9a0" /></linearGradient>
            <linearGradient id="agua-l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#3fb8c2" /><stop offset="1" stopColor="#0e5a66" /></linearGradient>
          </defs>
          <rect width="1600" height="900" fill="url(#ceu-l)" />
          <circle cx="820" cy="330" r="46" fill="#fff3c4" />
          <path d="M0 520 L200 200 L380 360 L560 150 L760 380 L940 180 L1160 400 L1380 220 L1600 380 L1600 540 L0 540 Z" fill="#5b5a6e" />
          <path d="M200 200 L250 300 L160 300 Z M560 150 L610 260 L520 260 Z M940 180 L990 290 L900 290 Z M1380 220 L1430 320 L1340 320 Z" fill="#f3eef0" />
          <rect y="540" width="1600" height="360" fill="url(#agua-l)" />
          {pinheiros(560, 22, '#1f3d2a', 150, 4)}
          <ellipse cx="200" cy="860" rx="320" ry="90" fill="#6b6457" />
          {pinheiros(900, 10, '#183323', 300, 6)}
        </>
      );
  }
}

export const TODAS: NomePaisagem[] = ['lago', 'floresta', 'cachoeira', 'trem', 'outono', 'aurora', 'campo', 'cidade', 'alpes', 'rio'];
const K = 'ja.paisagem';

/** Fundo da tela: paisagens desfocadas que trocam a cada 5 minutos, numa transição bem lenta. */
export function Paisagem({ intervaloMs = 5 * 60 * 1000 }: { intervaloMs?: number }) {
  const [i, setI] = useState(() => { try { return (Number(localStorage.getItem(K)) || 0) % TODAS.length; } catch { return 0; } });
  useEffect(() => {
    const t = window.setInterval(() => setI((x) => { const n = (x + 1) % TODAS.length; try { localStorage.setItem(K, String(n)); } catch { /* opcional */ } return n; }), intervaloMs);
    return () => window.clearInterval(t);
  }, [intervaloMs]);
  return (
    <div className="paisagem" aria-hidden>
      <AnimatePresence>
        <motion.svg key={TODAS[i]} viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice"
          initial={{ opacity: 0, scale: 1.04 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0 }} transition={{ duration: 8, ease: 'easeInOut' }}>
          <Cena nome={TODAS[i]} />
        </motion.svg>
      </AnimatePresence>
      <div className="paisagem-veu" />
    </div>
  );
}
