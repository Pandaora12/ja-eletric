import {
  readTextFile, writeTextFile, mkdir, exists, BaseDirectory,
} from '@tauri-apps/plugin-fs';

export interface Item {
  id: string;
  voltage: 'BT' | 'AT';
  category: string;
  name: string;
  unit: string;
  unitPrice: number;
  description: string;
}

const FILE = 'items.json';
const BASE = BaseDirectory.AppData;

export const BT_CATEGORIES = [
  'Cabos',
  'Iluminação',
  'Tomadas e Interruptores',
  'Proteção',
  'Eletrodutos e Caixas',
  'Mão de Obra',
  'Outros',
] as const;

export const AT_CATEGORIES = [
  'Transformadores',
  'Chaves e Seccionadores',
  'Para-Raios e DPS',
  'Cabos AT',
  'Cubículos MT',
] as const;

export const ITEM_CATEGORIES = [...BT_CATEGORIES, ...AT_CATEGORIES] as const;

function uuid(): string { return crypto.randomUUID(); }
function bt(category: string, name: string, unit: string, unitPrice: number, description: string): Item {
  return { id: uuid(), voltage: 'BT', category, name, unit, unitPrice, description };
}
function at(category: string, name: string, unit: string, unitPrice: number, description: string): Item {
  return { id: uuid(), voltage: 'AT', category, name, unit, unitPrice, description };
}

export const DEFAULT_ITEMS: Item[] = [
  // ── BT: Cabos ──────────────────────────────────────────────────────────────
  bt('Cabos', 'Cabo 1,5mm² (flexível)',  'm',  2.50,  '750V, 70°C'),
  bt('Cabos', 'Cabo 2,5mm² (flexível)',  'm',  3.80,  '750V, 70°C'),
  bt('Cabos', 'Cabo 4mm² (flexível)',    'm',  6.20,  '750V, 70°C'),
  bt('Cabos', 'Cabo 6mm² (flexível)',    'm',  9.10,  '750V, 70°C'),
  bt('Cabos', 'Cabo 10mm² (flexível)',   'm',  14.50, '750V, 70°C'),
  bt('Cabos', 'Cabo 16mm² (flexível)',   'm',  22.00, '750V, 70°C'),
  bt('Cabos', 'Cabo 25mm² (flexível)',   'm',  34.00, '750V, 70°C'),
  // ── BT: Iluminação ─────────────────────────────────────────────────────────
  bt('Iluminação', 'Lâmpada LED 9W',         'un', 18.00,  'Bivolt, 6500K'),
  bt('Iluminação', 'Lâmpada LED 12W',        'un', 22.00,  'Bivolt, 6500K'),
  bt('Iluminação', 'Spot LED embutir 7W',    'un', 45.00,  'Bivolt, 4000K'),
  bt('Iluminação', 'Luminária sobrepor 40W', 'un', 120.00, 'Fluorescente'),
  bt('Iluminação', 'Refletor LED 20W',       'un', 65.00,  'IP65, externo'),
  bt('Iluminação', 'Painel LED 36W',         'un', 95.00,  '60×60cm, 4000K'),
  // ── BT: Tomadas e Interruptores ────────────────────────────────────────────
  bt('Tomadas e Interruptores', 'Tomada 10A NBR 14136',  'un', 15.00, 'Padrão brasileiro'),
  bt('Tomadas e Interruptores', 'Tomada 20A NBR 14136',  'un', 25.00, 'Padrão brasileiro'),
  bt('Tomadas e Interruptores', 'Interruptor simples',   'un', 12.00, '10A, 250V'),
  bt('Tomadas e Interruptores', 'Interruptor paralelo',  'un', 18.00, '10A, 250V'),
  bt('Tomadas e Interruptores', 'Interruptor three-way', 'un', 22.00, '10A, 250V'),
  // ── BT: Proteção ───────────────────────────────────────────────────────────
  bt('Proteção', 'Disjuntor 10A (mono)',  'un', 22.00, 'Curva C'),
  bt('Proteção', 'Disjuntor 16A (mono)',  'un', 23.00, 'Curva C'),
  bt('Proteção', 'Disjuntor 20A (mono)',  'un', 24.00, 'Curva C'),
  bt('Proteção', 'Disjuntor 32A (mono)',  'un', 28.00, 'Curva C'),
  bt('Proteção', 'Disjuntor 40A (bi)',    'un', 55.00, 'Curva C'),
  bt('Proteção', 'DR 25A (bipolar)',      'un', 85.00, '30mA, classe AC'),
  bt('Proteção', 'DPS classe II',         'un', 65.00, 'Protetor de surto'),
  // ── BT: Eletrodutos e Caixas ───────────────────────────────────────────────
  bt('Eletrodutos e Caixas', 'Eletroduto PVC 3/4"',             'm',  4.50,  ''),
  bt('Eletrodutos e Caixas', 'Eletroduto PVC 1"',               'm',  6.80,  ''),
  bt('Eletrodutos e Caixas', 'Caixa passagem 4×2"',             'un', 3.50,  ''),
  bt('Eletrodutos e Caixas', 'Caixa passagem 4×4"',             'un', 5.00,  ''),
  bt('Eletrodutos e Caixas', 'Quadro de distribuição 12 disj.', 'un', 85.00, ''),
  bt('Eletrodutos e Caixas', 'Quadro de distribuição 24 disj.', 'un', 140.00,''),
  // ── BT: Mão de Obra ────────────────────────────────────────────────────────
  bt('Mão de Obra', 'Hora técnica (eletricista)',    'h',  80.00,  'Residencial'),
  bt('Mão de Obra', 'Ponto elétrico (tomada)',       'un', 150.00, 'Inclui material básico'),
  bt('Mão de Obra', 'Ponto de iluminação',           'un', 130.00, 'Inclui material básico'),
  bt('Mão de Obra', 'Instalação de disjuntor',       'un', 60.00,  ''),
  bt('Mão de Obra', 'Instalação de luminária',       'un', 70.00,  ''),
  bt('Mão de Obra', 'Passagem de cabo (por metro)',  'm',  12.00,  ''),
  // ── AT: Transformadores ────────────────────────────────────────────────────
  at('Transformadores', 'Trafo a seco 75 kVA  13,8kV/380V',  'un', 18500.00, 'IP23, classe F'),
  at('Transformadores', 'Trafo a seco 150 kVA 13,8kV/380V',  'un', 24000.00, 'IP23, classe F'),
  at('Transformadores', 'Trafo a seco 300 kVA 13,8kV/380V',  'un', 38000.00, 'IP23, classe F'),
  at('Transformadores', 'Trafo a seco 500 kVA 13,8kV/380V',  'un', 58000.00, 'IP23, classe F'),
  at('Transformadores', 'Trafo a seco 1000 kVA 13,8kV/380V', 'un', 98000.00, 'IP23, classe F'),
  // ── AT: Chaves e Seccionadores ─────────────────────────────────────────────
  at('Chaves e Seccionadores', 'Seccionadora fusível 15kV 100A',   'un', 4200.00, 'Uso interno'),
  at('Chaves e Seccionadores', 'Seccionadora fusível 15kV 200A',   'un', 5800.00, 'Uso interno'),
  at('Chaves e Seccionadores', 'Chave a óleo 15kV 630A',           'un', 12000.00,'Uso externo'),
  at('Chaves e Seccionadores', 'Religador automático 15kV',        'un', 45000.00,'Monofásico'),
  // ── AT: Para-Raios e DPS ──────────────────────────────────────────────────
  at('Para-Raios e DPS', 'Para-raio ZnO 12kV 5kA',  'un', 380.00,  'Polimérico'),
  at('Para-Raios e DPS', 'Para-raio ZnO 15kV 5kA',  'un', 420.00,  'Polimérico'),
  at('Para-Raios e DPS', 'Para-raio ZnO 36kV 5kA',  'un', 680.00,  'Polimérico'),
  at('Para-Raios e DPS', 'DPS classe I 150kA',       'un', 1200.00, 'Linha principal'),
  // ── AT: Cabos AT ──────────────────────────────────────────────────────────
  at('Cabos AT', 'Cabo 15kV 50mm² XLPE',  'm', 85.00,  'Isolação XLPE'),
  at('Cabos AT', 'Cabo 15kV 95mm² XLPE',  'm', 145.00, 'Isolação XLPE'),
  at('Cabos AT', 'Cabo 15kV 150mm² XLPE', 'm', 220.00, 'Isolação XLPE'),
  at('Cabos AT', 'Cabo 15kV 240mm² XLPE', 'm', 350.00, 'Isolação XLPE'),
  // ── AT: Cubículos MT ──────────────────────────────────────────────────────
  at('Cubículos MT', 'Cubículo de medição 15kV',     'un', 32000.00, 'Padrão concessionária'),
  at('Cubículos MT', 'Cubículo de proteção 15kV',    'un', 28000.00, 'Com disjuntor a vácuo'),
  at('Cubículos MT', 'Cubículo de entrada 15kV',     'un', 18000.00, 'Com seccionadora'),
  at('Cubículos MT', 'Painel MT completo (3 cubíc.)', 'un', 95000.00,'Medição+Proteção+Entrada'),
];

export async function loadItems(): Promise<Item[] | null> {
  try {
    const fileExists = await exists(FILE, { baseDir: BASE });
    if (!fileExists) return null;
    const raw = await readTextFile(FILE, { baseDir: BASE });
    const parsed = JSON.parse(raw) as Item[];
    // Backward compat: items sem voltage field recebem 'BT'
    return parsed.map((it) => (it.voltage ? it : { ...it, voltage: 'BT' as const }));
  } catch {
    return null;
  }
}

export async function saveItems(items: Item[]): Promise<void> {
  try {
    await mkdir('', { baseDir: BASE, recursive: true }).catch(() => {});
    await writeTextFile(FILE, JSON.stringify(items, null, 2), { baseDir: BASE });
  } catch (err) {
    console.error('[itemsStorage] save failed:', err);
  }
}
