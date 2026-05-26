import { useDocumentStore } from '../../stores/documentStore';
import { isTable } from '../../types/blocks';
import { formatBRL, computeRowTotal, isBudgetTable } from '../../lib/budget';

// ─── Linha de resumo ──────────────────────────────────────────────────────────

function SummaryRow({ label, value, dim = false }: { label: string; value: number; dim?: boolean }) {
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className={`text-sm ${dim ? 'text-zinc-500 dark:text-zinc-500' : 'text-zinc-600 dark:text-zinc-400'}`}>
        {label}
      </span>
      <span className={`text-sm tabular-nums font-medium ${dim ? 'text-zinc-500 dark:text-zinc-500' : 'text-zinc-700 dark:text-zinc-300'}`}>
        R$&nbsp;{formatBRL(value)}
      </span>
    </div>
  );
}

// ─── FinanceSummaryBlock ──────────────────────────────────────────────────────

export function FinanceSummaryBlock() {
  const doc = useDocumentStore((s) =>
    s.activeDocumentId ? (s.documents[s.activeDocumentId] ?? null) : null,
  );

  if (!doc) return null;

  const budgetTables = doc.blocks
    .filter(isTable)
    .filter((b) => isBudgetTable(b.content.headers));

  const sum = (category: 'material' | 'labor' | 'other' | undefined) =>
    budgetTables
      .filter((b) => (b.content.tableCategory ?? 'other') === category)
      .reduce(
        (acc, b) => acc + b.content.rows.reduce((s, r) => s + computeRowTotal(r.cells), 0),
        0,
      );

  const materialTotal = sum('material');
  const laborTotal    = sum('labor');
  const otherTotal    = sum('other');
  const grandTotal    = materialTotal + laborTotal + otherTotal;

  const hasBreakdown = materialTotal > 0 || laborTotal > 0;

  return (
    <div className="rounded-lg border border-zinc-200 dark:border-zinc-700 overflow-hidden">
      {/* Header */}
      <div className="bg-zinc-50 dark:bg-zinc-800/60 px-4 py-2.5 border-b border-zinc-200 dark:border-zinc-700 flex items-center justify-between">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
          Resumo Financeiro
        </p>
        <span className="text-[9px] text-zinc-400 dark:text-zinc-600 uppercase tracking-widest">
          Gerado automaticamente
        </span>
      </div>

      {/* Linhas */}
      <div className="px-5 py-3 divide-y divide-zinc-100 dark:divide-zinc-800">
        {hasBreakdown && materialTotal > 0 && (
          <SummaryRow label="Total Materiais"   value={materialTotal} />
        )}
        {hasBreakdown && laborTotal > 0 && (
          <SummaryRow label="Total Mão de Obra" value={laborTotal} />
        )}
        {otherTotal > 0 && (
          <SummaryRow label="Outros" value={otherTotal} dim={hasBreakdown} />
        )}

        {/* Linha de total */}
        <div className="flex items-center justify-between pt-3 pb-1">
          <span className="text-sm font-bold text-zinc-800 dark:text-zinc-100 uppercase tracking-wide">
            Valor Total
          </span>
          <span className="text-xl font-bold text-green-500 tabular-nums">
            R$&nbsp;{formatBRL(grandTotal)}
          </span>
        </div>
      </div>

      {budgetTables.length === 0 && (
        <p className="text-xs text-zinc-400 dark:text-zinc-600 text-center py-4 px-4">
          Nenhuma tabela de orçamento encontrada no documento.
        </p>
      )}
    </div>
  );
}
