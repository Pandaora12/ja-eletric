import { useCallback } from 'react';
import type {
  Block, LoadReportContent, LoadReportRow,
  StartupType, VoltagePhase, DutyRegime,
} from '../../types/blocks';

// ─── Constantes de engenharia (NBR 5410 / IEC 60034-1) ───────────────────────

const CV_TO_KW = 0.7355;
const EFF      = 0.90;   // rendimento típico
const COS_PHI  = 0.85;   // fator de potência típico
const SQRT3    = Math.sqrt(3);

// Denominador para cálculo de I_nom = P_kW × 1000 / DENOM
const VOLT_DENOM: Record<VoltagePhase, number> = {
  '127-1': 127         * EFF * COS_PHI,
  '220-1': 220         * EFF * COS_PHI,
  '220-3': SQRT3 * 220 * EFF * COS_PHI,
  '380-3': SQRT3 * 380 * EFF * COS_PHI,
  '440-3': SQRT3 * 440 * EFF * COS_PHI,
  '690-3': SQRT3 * 690 * EFF * COS_PHI,
};

// Relação Ip/In por tipo de partida
const STARTUP_FACTOR: Record<StartupType, number> = {
  'direct':       7.0,
  'star-delta':   2.5,
  'soft-starter': 3.0,
  'vfd':          1.2,
};

export const STARTUP_LABELS: Record<StartupType, string> = {
  'direct':       'Direta',
  'star-delta':   'Estrela-Δ',
  'soft-starter': 'Soft-Starter',
  'vfd':          'Inversor VFD',
};

export const VOLTAGE_LABELS: Record<VoltagePhase, string> = {
  '127-1': '127V 1φ',
  '220-1': '220V 1φ',
  '220-3': '220V 3φ',
  '380-3': '380V 3φ',
  '440-3': '440V 3φ',
  '690-3': '690V 3φ',
};

const REGIMES: DutyRegime[] = ['S1','S2','S3','S4','S5','S6','S7','S8','S9','S10'];

const REGIME_DESC: Record<DutyRegime, string> = {
  S1: 'Contínuo', S2: 'Temporário', S3: 'Intermitente periódico',
  S4: 'Interm. c/ partidas', S5: 'Interm. c/ frenagem',
  S6: 'Contínuo c/ carga interm.', S7: 'Contínuo c/ frenagem',
  S8: 'Contínuo c/ var. velocidade', S9: 'Carga não periódica',
  S10: 'Operação discreta',
};

// ─── Cálculo por linha ────────────────────────────────────────────────────────

export interface RowCalc {
  kw: number;
  iNom: number;
  iPart: number;
  demandKw: number;
}

export function calcLoadRow(row: LoadReportRow): RowCalc {
  const kw      = row.powerCV * CV_TO_KW;
  const denom   = VOLT_DENOM[row.voltagePhase];
  const iNom    = denom > 0 && kw > 0 ? (kw * 1000) / denom : 0;
  const iPart   = iNom * STARTUP_FACTOR[row.startupType];
  const demandKw = kw * row.qty * row.demandFactor;
  return { kw, iNom, iPart, demandKw };
}

function n(v: number, d = 2) { return v.toFixed(d); }

// ─── Linha da tabela ──────────────────────────────────────────────────────────

function NumInput({
  value, min = 0, step = 1, onChange, className = '',
}: {
  value: number; min?: number; step?: number;
  onChange: (v: number) => void; className?: string;
}) {
  return (
    <input
      type="number"
      value={value || ''}
      min={min}
      step={step}
      placeholder="0"
      onChange={(e) => onChange(parseFloat(e.target.value) || 0)}
      className={`w-full bg-transparent outline-none text-right tabular-nums text-zinc-700 dark:text-zinc-300 placeholder:text-zinc-300 dark:placeholder:text-zinc-600 ${className}`}
    />
  );
}

function Computed({ value, unit, warn = false }: { value: string; unit: string; warn?: boolean }) {
  return (
    <div className={`px-2 py-1.5 text-right tabular-nums select-none text-xs font-mono ${
      warn
        ? 'text-amber-600 dark:text-amber-400 font-bold'
        : 'text-sky-700 dark:text-sky-400'
    }`}>
      {value}
      <span className="text-[9px] text-zinc-400 dark:text-zinc-600 ml-0.5">{unit}</span>
    </div>
  );
}

// ─── Componente principal ─────────────────────────────────────────────────────

interface Props {
  block: Block & { content: LoadReportContent };
  onChange: (content: Partial<LoadReportContent>) => void;
}

export function LoadReportBlock({ block, onChange }: Props) {
  const { rows } = block.content;

  const updateRow = useCallback((idx: number, patch: Partial<LoadReportRow>) => {
    onChange({ rows: rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)) });
  }, [rows, onChange]);

  const addRow = () => onChange({
    rows: [...rows, {
      equipment: '', qty: 1, powerCV: 0,
      startupType: 'direct', voltagePhase: '380-3',
      regime: 'S1', demandFactor: 0.80,
    }],
  });

  const removeRow = (idx: number) => {
    if (rows.length <= 1) return;
    onChange({ rows: rows.filter((_, i) => i !== idx) });
  };

  // Cálculos por linha
  const computed = rows.map(calcLoadRow);

  // Resumo executivo
  const totalInstalledKW = computed.reduce((s, c, i) => s + c.kw * rows[i].qty, 0);
  const totalDemandKW    = computed.reduce((s, c) => s + c.demandKw, 0);

  let maxIPartVal  = 0;
  let maxIPartName = '';
  computed.forEach((c, i) => {
    const val = c.iPart * rows[i].qty;
    if (val > maxIPartVal) {
      maxIPartVal  = val;
      maxIPartName = rows[i].equipment.trim() || `Linha ${i + 1}`;
    }
  });
  const flickerRisk = maxIPartVal > 300;

  const thTd = 'text-[9px] font-bold uppercase tracking-wide text-zinc-500 dark:text-zinc-400 px-2 py-1.5 whitespace-nowrap';
  const compBg = 'bg-sky-50/60 dark:bg-sky-950/20';

  return (
    <div className="rounded border border-zinc-200 dark:border-zinc-700 overflow-hidden text-xs">

      {/* Cabeçalho do bloco */}
      <div className="flex items-center justify-between px-3 py-2 bg-zinc-50 dark:bg-zinc-800/60 border-b border-zinc-200 dark:border-zinc-700">
        <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
          ⚡ Relatório de Carga — NBR 5410 / IEC 60034-1
        </p>
        <p className="text-[9px] text-zinc-400 dark:text-zinc-600">
          η = {(EFF * 100).toFixed(0)}% · cosφ = {COS_PHI.toFixed(2)} · 1 CV = {CV_TO_KW} kW
        </p>
      </div>

      {/* Tabela */}
      <div className="overflow-x-auto">
        <table className="border-collapse text-left" style={{ minWidth: 880 }}>
          <colgroup>
            <col style={{ width: 28 }} />
            <col style={{ minWidth: 160 }} />
            <col style={{ width: 48 }} />
            <col style={{ width: 64 }} />
            <col style={{ width: 58 }} />
            <col style={{ width: 116 }} />
            <col style={{ width: 88 }} />
            <col style={{ width: 64 }} />
            <col style={{ width: 64 }} />
            <col style={{ width: 56 }} />
            <col style={{ width: 46 }} />
            <col style={{ width: 76 }} />
            <col style={{ width: 28 }} />
          </colgroup>

          <thead>
            <tr className="bg-zinc-100 dark:bg-zinc-800 border-b border-zinc-200 dark:border-zinc-700">
              <th className={thTd}>#</th>
              <th className={thTd}>Equipamento</th>
              <th className={`${thTd} text-right`}>Qtd</th>
              <th className={`${thTd} text-right`}>CV</th>
              <th className={`${thTd} text-right ${compBg}`}>kW</th>
              <th className={thTd}>Tipo de Partida</th>
              <th className={thTd}>Tensão</th>
              <th className={`${thTd} text-right ${compBg}`}>I-nom (A)</th>
              <th className={`${thTd} text-right ${compBg}`}>I-part (A)</th>
              <th className={thTd}>Regime</th>
              <th className={`${thTd} text-right`}>Fd</th>
              <th className={`${thTd} text-right ${compBg}`}>Dem. (kW)</th>
              <th />
            </tr>
          </thead>

          <tbody>
            {rows.map((row, idx) => {
              const c = computed[idx];
              return (
                <tr
                  key={idx}
                  className="border-b border-zinc-100 dark:border-zinc-800 last:border-b-0 hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30 group/row"
                >
                  {/* # */}
                  <td className="px-2 py-1 text-center text-zinc-400 dark:text-zinc-600 select-none">
                    {idx + 1}
                  </td>

                  {/* Equipamento */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800">
                    <input
                      type="text"
                      value={row.equipment}
                      onChange={(e) => updateRow(idx, { equipment: e.target.value })}
                      placeholder="Nome do equipamento..."
                      className="w-full px-2 py-1.5 bg-transparent outline-none text-zinc-700 dark:text-zinc-300 placeholder:text-zinc-300 dark:placeholder:text-zinc-600"
                    />
                  </td>

                  {/* Qtd */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-2 py-1.5">
                    <NumInput value={row.qty} min={1} onChange={(v) => updateRow(idx, { qty: Math.max(1, Math.round(v)) })} />
                  </td>

                  {/* CV */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-2 py-1.5">
                    <NumInput value={row.powerCV} step={0.5} onChange={(v) => updateRow(idx, { powerCV: v })} />
                  </td>

                  {/* kW (computado) */}
                  <td className={`border-r border-zinc-100 dark:border-zinc-800 ${compBg}`}>
                    <Computed value={n(c.kw)} unit="kW" />
                  </td>

                  {/* Tipo de Partida */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-1 py-1">
                    <select
                      value={row.startupType}
                      onChange={(e) => updateRow(idx, { startupType: e.target.value as StartupType })}
                      className="w-full bg-transparent outline-none text-zinc-700 dark:text-zinc-300 text-xs cursor-pointer py-0.5"
                    >
                      {(Object.keys(STARTUP_LABELS) as StartupType[]).map((k) => (
                        <option key={k} value={k}>{STARTUP_LABELS[k]} (×{STARTUP_FACTOR[k]})</option>
                      ))}
                    </select>
                  </td>

                  {/* Tensão */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-1 py-1">
                    <select
                      value={row.voltagePhase}
                      onChange={(e) => updateRow(idx, { voltagePhase: e.target.value as VoltagePhase })}
                      className="w-full bg-transparent outline-none text-zinc-700 dark:text-zinc-300 text-xs cursor-pointer py-0.5"
                    >
                      {(Object.keys(VOLTAGE_LABELS) as VoltagePhase[]).map((k) => (
                        <option key={k} value={k}>{VOLTAGE_LABELS[k]}</option>
                      ))}
                    </select>
                  </td>

                  {/* I-nom (computado) */}
                  <td className={`border-r border-zinc-100 dark:border-zinc-800 ${compBg}`}>
                    <Computed value={n(c.iNom)} unit="A" />
                  </td>

                  {/* I-part (computado) */}
                  <td className={`border-r border-zinc-100 dark:border-zinc-800 ${compBg}`}>
                    <Computed
                      value={n(c.iPart)}
                      unit="A"
                      warn={c.iPart * row.qty > 300}
                    />
                  </td>

                  {/* Regime */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-1 py-1">
                    <select
                      value={row.regime}
                      onChange={(e) => updateRow(idx, { regime: e.target.value as DutyRegime })}
                      title={REGIME_DESC[row.regime]}
                      className="w-full bg-transparent outline-none text-zinc-700 dark:text-zinc-300 text-xs cursor-pointer py-0.5"
                    >
                      {REGIMES.map((r) => (
                        <option key={r} value={r} title={REGIME_DESC[r]}>{r}</option>
                      ))}
                    </select>
                  </td>

                  {/* Fd */}
                  <td className="border-r border-zinc-100 dark:border-zinc-800 px-2 py-1.5">
                    <NumInput
                      value={row.demandFactor}
                      min={0}
                      step={0.05}
                      onChange={(v) => updateRow(idx, { demandFactor: Math.min(1, Math.max(0, v)) })}
                    />
                  </td>

                  {/* Demanda kW (computado) */}
                  <td className={`border-r border-zinc-100 dark:border-zinc-800 ${compBg}`}>
                    <Computed value={n(c.demandKw)} unit="kW" />
                  </td>

                  {/* Delete */}
                  <td className="text-center px-1">
                    <button
                      onClick={() => removeRow(idx)}
                      disabled={rows.length <= 1}
                      className="opacity-0 group-hover/row:opacity-100 text-zinc-300 dark:text-zinc-600 hover:text-red-400 disabled:opacity-0 transition-opacity"
                      title="Remover"
                    >✕</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Botão adicionar */}
      <div className="border-t border-zinc-200 dark:border-zinc-700 px-3 py-2">
        <button
          onClick={addRow}
          className="text-xs text-zinc-400 dark:text-zinc-500 hover:text-zinc-600 dark:hover:text-zinc-300 transition-colors"
        >
          + Adicionar equipamento
        </button>
      </div>

      {/* ── Resumo Executivo ─────────────────────────────────────── */}
      <div className="border-t-2 border-zinc-300 dark:border-zinc-600 bg-zinc-50 dark:bg-zinc-800/50 px-4 py-3">
        <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400 mb-3">
          Resumo Executivo
        </p>

        <div className="grid grid-cols-3 gap-3">
          {/* Carga Instalada */}
          <div className="bg-white dark:bg-zinc-800 rounded border border-zinc-200 dark:border-zinc-700 px-3 py-2.5">
            <p className="text-[9px] text-zinc-500 dark:text-zinc-500 uppercase tracking-wide mb-1">Carga Instalada</p>
            <p className="text-lg font-bold tabular-nums text-zinc-800 dark:text-zinc-100">
              {n(totalInstalledKW)} <span className="text-xs font-normal text-zinc-400">kW</span>
            </p>
            <p className="text-[9px] text-zinc-400 dark:text-zinc-600 mt-0.5">
              {n(totalInstalledKW / CV_TO_KW, 1)} CV total
            </p>
          </div>

          {/* Demanda Total */}
          <div className="bg-white dark:bg-zinc-800 rounded border border-zinc-200 dark:border-zinc-700 px-3 py-2.5">
            <p className="text-[9px] text-zinc-500 dark:text-zinc-500 uppercase tracking-wide mb-1">Demanda Total</p>
            <p className="text-lg font-bold tabular-nums text-sky-700 dark:text-sky-400">
              {n(totalDemandKW)} <span className="text-xs font-normal text-zinc-400">kW</span>
            </p>
            <p className="text-[9px] text-zinc-400 dark:text-zinc-600 mt-0.5">
              Fd médio aplicado
            </p>
          </div>

          {/* Maior I-part */}
          <div className={`rounded border px-3 py-2.5 ${
            flickerRisk
              ? 'bg-amber-50 dark:bg-amber-950/30 border-amber-300 dark:border-amber-800'
              : 'bg-white dark:bg-zinc-800 border-zinc-200 dark:border-zinc-700'
          }`}>
            <p className="text-[9px] text-zinc-500 dark:text-zinc-500 uppercase tracking-wide mb-1">
              Maior I-partida
            </p>
            <p className={`text-lg font-bold tabular-nums ${
              flickerRisk ? 'text-amber-600 dark:text-amber-400' : 'text-zinc-800 dark:text-zinc-100'
            }`}>
              {n(maxIPartVal)} <span className="text-xs font-normal text-zinc-400">A</span>
            </p>
            <p className="text-[9px] text-zinc-500 dark:text-zinc-500 mt-0.5 truncate">{maxIPartName}</p>
            {flickerRisk && (
              <p className="text-[9px] font-semibold text-amber-600 dark:text-amber-400 mt-1">
                ⚠ Risco de flicker — verificar queda de tensão
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
