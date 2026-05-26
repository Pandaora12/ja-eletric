import {
  readTextFile, writeTextFile, mkdir, exists, BaseDirectory,
} from '@tauri-apps/plugin-fs';

export interface Item {
  id: string;
  category: string;
  name: string;
  unit: string;
  unitPrice: number;
  description: string;
}

const FILE = 'items.json';
const BASE = BaseDirectory.AppData;

export const ITEM_CATEGORIES = [
  'Cabos',
  'Iluminação',
  'Tomadas e Interruptores',
  'Proteção',
  'Eletrodutos e Caixas',
  'Mão de Obra',
  'Outros',
] as const;

function uuid(): string { return crypto.randomUUID(); }

export const DEFAULT_ITEMS: Item[] = [
  // Cabos
  { id: uuid(), category: 'Cabos', name: 'Cabo 1,5mm² (flexível)', unit: 'm', unitPrice: 2.50, description: '750V, 70°C' },
  { id: uuid(), category: 'Cabos', name: 'Cabo 2,5mm² (flexível)', unit: 'm', unitPrice: 3.80, description: '750V, 70°C' },
  { id: uuid(), category: 'Cabos', name: 'Cabo 4mm² (flexível)',   unit: 'm', unitPrice: 6.20, description: '750V, 70°C' },
  { id: uuid(), category: 'Cabos', name: 'Cabo 6mm² (flexível)',   unit: 'm', unitPrice: 9.10, description: '750V, 70°C' },
  { id: uuid(), category: 'Cabos', name: 'Cabo 10mm² (flexível)',  unit: 'm', unitPrice: 14.50, description: '750V, 70°C' },
  { id: uuid(), category: 'Cabos', name: 'Cabo 16mm² (flexível)',  unit: 'm', unitPrice: 22.00, description: '750V, 70°C' },
  // Iluminação
  { id: uuid(), category: 'Iluminação', name: 'Lâmpada LED 9W',          unit: 'un', unitPrice: 18.00, description: 'Bivolt, 6500K' },
  { id: uuid(), category: 'Iluminação', name: 'Lâmpada LED 12W',         unit: 'un', unitPrice: 22.00, description: 'Bivolt, 6500K' },
  { id: uuid(), category: 'Iluminação', name: 'Spot LED embutir 7W',     unit: 'un', unitPrice: 45.00, description: 'Bivolt, 4000K' },
  { id: uuid(), category: 'Iluminação', name: 'Luminária sobrepor 40W',  unit: 'un', unitPrice: 120.00, description: 'Fluorescente' },
  { id: uuid(), category: 'Iluminação', name: 'Refletor LED 20W',        unit: 'un', unitPrice: 65.00, description: 'IP65, externo' },
  // Tomadas e Interruptores
  { id: uuid(), category: 'Tomadas e Interruptores', name: 'Tomada 10A NBR 14136',  unit: 'un', unitPrice: 15.00, description: 'Padrão brasileiro' },
  { id: uuid(), category: 'Tomadas e Interruptores', name: 'Tomada 20A NBR 14136',  unit: 'un', unitPrice: 25.00, description: 'Padrão brasileiro' },
  { id: uuid(), category: 'Tomadas e Interruptores', name: 'Interruptor simples',   unit: 'un', unitPrice: 12.00, description: '10A, 250V' },
  { id: uuid(), category: 'Tomadas e Interruptores', name: 'Interruptor paralelo',  unit: 'un', unitPrice: 18.00, description: '10A, 250V' },
  { id: uuid(), category: 'Tomadas e Interruptores', name: 'Interruptor three-way', unit: 'un', unitPrice: 22.00, description: '10A, 250V' },
  // Proteção
  { id: uuid(), category: 'Proteção', name: 'Disjuntor 10A (mono)',  unit: 'un', unitPrice: 22.00, description: 'Curva C' },
  { id: uuid(), category: 'Proteção', name: 'Disjuntor 16A (mono)',  unit: 'un', unitPrice: 23.00, description: 'Curva C' },
  { id: uuid(), category: 'Proteção', name: 'Disjuntor 20A (mono)',  unit: 'un', unitPrice: 24.00, description: 'Curva C' },
  { id: uuid(), category: 'Proteção', name: 'Disjuntor 32A (mono)',  unit: 'un', unitPrice: 28.00, description: 'Curva C' },
  { id: uuid(), category: 'Proteção', name: 'Disjuntor 40A (bi)',    unit: 'un', unitPrice: 55.00, description: 'Curva C' },
  { id: uuid(), category: 'Proteção', name: 'DR 25A (bipolar)',       unit: 'un', unitPrice: 85.00, description: '30mA, classe AC' },
  { id: uuid(), category: 'Proteção', name: 'DPS classe II',          unit: 'un', unitPrice: 65.00, description: 'Protetor de surto' },
  // Eletrodutos e Caixas
  { id: uuid(), category: 'Eletrodutos e Caixas', name: 'Eletroduto PVC 3/4"',    unit: 'm',  unitPrice: 4.50, description: '' },
  { id: uuid(), category: 'Eletrodutos e Caixas', name: 'Eletroduto PVC 1"',      unit: 'm',  unitPrice: 6.80, description: '' },
  { id: uuid(), category: 'Eletrodutos e Caixas', name: 'Caixa passagem 4x2"',   unit: 'un', unitPrice: 3.50, description: '' },
  { id: uuid(), category: 'Eletrodutos e Caixas', name: 'Caixa passagem 4x4"',   unit: 'un', unitPrice: 5.00, description: '' },
  { id: uuid(), category: 'Eletrodutos e Caixas', name: 'Quadro de distribuição 12 disj.', unit: 'un', unitPrice: 85.00, description: '' },
  // Mão de Obra
  { id: uuid(), category: 'Mão de Obra', name: 'Hora técnica (eletricista)',     unit: 'h',  unitPrice: 80.00,  description: 'Residencial' },
  { id: uuid(), category: 'Mão de Obra', name: 'Ponto elétrico (tomada)',        unit: 'un', unitPrice: 150.00, description: 'Inclui material básico' },
  { id: uuid(), category: 'Mão de Obra', name: 'Ponto de iluminação',           unit: 'un', unitPrice: 130.00, description: 'Inclui material básico' },
  { id: uuid(), category: 'Mão de Obra', name: 'Instalação de disjuntor',       unit: 'un', unitPrice: 60.00,  description: '' },
  { id: uuid(), category: 'Mão de Obra', name: 'Instalação de luminária',       unit: 'un', unitPrice: 70.00,  description: '' },
  { id: uuid(), category: 'Mão de Obra', name: 'Passagem de cabo (por metro)',  unit: 'm',  unitPrice: 12.00,  description: '' },
];

export async function loadItems(): Promise<Item[] | null> {
  try {
    const fileExists = await exists(FILE, { baseDir: BASE });
    if (!fileExists) return null;
    const raw = await readTextFile(FILE, { baseDir: BASE });
    return JSON.parse(raw) as Item[];
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
