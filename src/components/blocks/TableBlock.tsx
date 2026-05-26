import { useState } from 'react';
import type { Block, TableContent, TableRow } from '../../types/blocks';
import { formatBRL, computeRowTotal, parseBRL } from '../../lib/budget';

interface Props {
  block: Block & { content: TableContent };
  onChange: (content: Partial<TableContent>) => void;
}

// Orçamento: 6 headers com o último sendo "Total"
function isBudgetTable(headers: string[]): boolean {
  return headers.length === 6 && headers[5].toLowerCase().includes('total');
}

// ─── Célula de texto simples (contentEditable) ────────────────────────────────

function TextCell({
  value,
  placeholder = '',
  onCommit,
}: {
  value: string;
  placeholder?: string;
  onCommit: (val: string) => void;
}) {
  return (
    <div
      contentEditable
      suppressContentEditableWarning
      onBlur={(e) => onCommit(e.currentTarget.textContent ?? '')}
      data-ph={placeholder}
      className="px-2 py-1.5 text-sm text-zinc-700 dark:text-zinc-300 outline-none min-h-[2rem] empty:before:content-[attr(data-ph)] empty:before:text-zinc-300 dark:empty:before:text-zinc-600"
      dangerouslySetInnerHTML={{ __html: value }}
    />
  );
}

// ─── Célula numérica com máscara de dígito-shift ──────────────────────────────
// Ao focar, exibe "R$" e permite digitação estilo calculadora:
// cada dígito empurra os anteriores para a esquerda (em centavos).
// Backspace remove o último dígito. Enter/Tab confirma.

const MAX_CENTS = 9_999_999_99; // R$ 99.999.999,99

function CurrencyCell({
  value,
  onCommit,
}: {
  value: string;
  onCommit: (val: string) => void;
}) {
  const [focused, setFocused] = useState(false);
  const [cents, setCents] = useState(0);

  const numeric = parseBRL(value);

  const handleFocus = () => {
    setFocused(true);
    setCents(Math.round(numeric * 100));
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.ctrlKey || e.metaKey) return; // permite Ctrl+A, Ctrl+C, etc.

    if (e.key >= '0' && e.key <= '9') {
      e.preventDefault();
      const digit = parseInt(e.key, 10);
      setCents((prev) => {
        const next = prev * 10 + digit;
        return next > MAX_CENTS ? prev : next;
      });
    } else if (e.key === 'Backspace') {
      e.preventDefault();
      setCents((prev) => Math.floor(prev / 10));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      (e.currentTarget as HTMLInputElement).blur();
    } else if (e.key !== 'Tab') {
      // Bloqueia qualquer outra tecla que não seja Tab (Tab navega para próxima célula)
      e.preventDefault();
    }
  };

  const handleBlur = () => {
    setFocused(false);
    onCommit(String(cents / 100));
  };

  return (
    <div className="flex items-center justify-end px-2 py-1.5 gap-0.5">
      {focused && (
        <span className="text-xs text-zinc-400 dark:text-zinc-500 select-none shrink-0">R$</span>
      )}
      <input
        type="text"
        inputMode="numeric"
        value={focused ? formatBRL(cents / 100) : formatBRL(numeric)}
        onFocus={handleFocus}
        onKeyDown={handleKeyDown}
        onBlur={handleBlur}
        onChange={() => {}} // controlado inteiramente via onKeyDown
        className="bg-transparent outline-none text-sm text-zinc-700 dark:text-zinc-300 text-right tabular-nums w-full placeholder:text-zinc-300 dark:placeholder:text-zinc-600"
        placeholder="0,00"
      />
    </div>
  );
}

// ─── Botão de destaque de linha ────────────────────────────────────────────────

function HighlightButton({ active, onClick }: { active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      title={active ? 'Remover destaque' : 'Destacar linha'}
      className={`w-5 h-5 flex items-center justify-center rounded transition-all ${
        active
          ? 'text-amber-400 dark:text-amber-400 opacity-100'
          : 'text-zinc-300 dark:text-zinc-700 opacity-0 group-hover/row:opacity-100'
      }`}
    >
      <svg width="9" height="9" viewBox="0 0 9 9" fill="currentColor">
        <circle cx="4.5" cy="4.5" r="4.5" />
      </svg>
    </button>
  );
}

// ─── Componente principal ─────────────────────────────────────────────────────

export function TableBlock({ block, onChange }: Props) {
  const { headers, rows } = block.content;
  const budget = isBudgetTable(headers);
  const editableCols = budget ? 5 : headers.length;

  const updateHeader = (colIdx: number, value: string) => {
    const h = [...headers]; h[colIdx] = value; onChange({ headers: h });
  };

  const updateCell = (rowIdx: number, colIdx: number, value: string) => {
    onChange({
      rows: rows.map((row, i) => {
        if (i !== rowIdx) return row;
        const cells = [...row.cells]; cells[colIdx] = value;
        return { ...row, cells };
      }),
    });
  };

  const toggleHighlight = (rowIdx: number) => {
    onChange({
      rows: rows.map((row, i) =>
        i !== rowIdx ? row : { ...row, isHighlighted: !row.isHighlighted }
      ),
    });
  };

  const addRow = () => {
    const empty: TableRow = {
      cells: Array.from({ length: editableCols }, (_, i) => {
        if (i === 0) return String(rows.length + 1);
        if (i === 2) return '1';
        if (i === 3) return 'un';
        return '0';
      }),
    };
    onChange({ rows: [...rows, empty] });
  };

  const removeRow = (rowIdx: number) => {
    if (rows.length <= 1) return;
    onChange({ rows: rows.filter((_, i) => i !== rowIdx) });
  };

  const grandTotal = budget
    ? rows.reduce((sum, row) => sum + computeRowTotal(row.cells), 0)
    : 0;

  return (
    <div className="overflow-x-auto rounded border border-zinc-200 dark:border-zinc-700">
      <table className="w-full border-collapse text-left text-sm">

        <colgroup>
          {budget && <col className="w-5" />}   {/* Destaque */}
          <col className="w-10" />              {/* Item */}
          <col />                               {/* Descrição */}
          {budget && <>
            <col className="w-16" />            {/* Qtd. */}
            <col className="w-14" />            {/* Un. */}
            <col className="w-32" />            {/* Valor Unit. */}
            <col className="w-32" />            {/* Total */}
          </>}
          <col className="w-8" />              {/* Ações */}
        </colgroup>

        {/* ── Cabeçalho ── */}
        <thead>
          <tr className="bg-zinc-50 dark:bg-zinc-800 border-b border-zinc-200 dark:border-zinc-700">
            {budget && <th className="w-5" />}

            {headers.slice(0, editableCols).map((header, colIdx) => (
              <th key={colIdx} className="border-r border-zinc-200 dark:border-zinc-700 last:border-r-0">
                <div
                  contentEditable
                  suppressContentEditableWarning
                  onBlur={(e) => updateHeader(colIdx, e.currentTarget.textContent ?? '')}
                  className="px-2 py-1.5 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400 outline-none"
                  dangerouslySetInnerHTML={{ __html: header }}
                />
              </th>
            ))}

            {/* Cabeçalho Total — fixo, sem edição */}
            {budget && (
              <th className="border-r border-zinc-200 dark:border-zinc-700 last:border-r-0 select-none pointer-events-none">
                <div className="px-2 py-1.5 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400 text-right">
                  {headers[5]}
                </div>
              </th>
            )}
            <th className="w-8" />
          </tr>
        </thead>

        {/* ── Linhas ── */}
        <tbody>
          {rows.map((row, rowIdx) => {
            const highlighted = !!row.isHighlighted;
            const rowTotal = budget ? computeRowTotal(row.cells) : 0;

            return (
              <tr
                key={rowIdx}
                className={`border-b border-zinc-100 dark:border-zinc-800 last:border-b-0 group/row transition-colors ${
                  highlighted
                    ? 'bg-amber-50/60 dark:bg-amber-950/20'
                    : 'hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30'
                }`}
              >
                {/* Botão de destaque */}
                {budget && (
                  <td className="w-5 pl-1.5 align-middle">
                    <HighlightButton
                      active={highlighted}
                      onClick={() => toggleHighlight(rowIdx)}
                    />
                  </td>
                )}

                {/* Células editáveis */}
                {Array.from({ length: editableCols }, (_, colIdx) => {
                  const isCurrency = budget && colIdx === 4;

                  return (
                    <td
                      key={colIdx}
                      className={`border-r border-zinc-100 dark:border-zinc-800 last:border-r-0 ${
                        highlighted ? 'border-amber-200/40 dark:border-amber-900/30' : ''
                      }`}
                    >
                      {isCurrency ? (
                        <CurrencyCell
                          value={row.cells[colIdx] ?? '0'}
                          onCommit={(val) => updateCell(rowIdx, colIdx, val)}
                        />
                      ) : (
                        <TextCell
                          value={row.cells[colIdx] ?? ''}
                          placeholder={colIdx === 1 ? 'Descreva o item...' : ''}
                          onCommit={(val) => updateCell(rowIdx, colIdx, val)}
                        />
                      )}
                    </td>
                  );
                })}

                {/* Total da linha — bloqueado completamente */}
                {budget && (
                  <td
                    className="border-r border-zinc-100 dark:border-zinc-800 last:border-r-0 px-2 py-1.5 pointer-events-none select-none"
                    aria-label="Total calculado automaticamente"
                  >
                    <span className={`block text-right text-sm tabular-nums font-medium ${
                      highlighted
                        ? 'text-amber-700 dark:text-amber-300'
                        : rowTotal > 0
                          ? 'text-zinc-700 dark:text-zinc-300'
                          : 'text-zinc-300 dark:text-zinc-600'
                    }`}>
                      {formatBRL(rowTotal)}
                    </span>
                  </td>
                )}

                {/* Deletar linha */}
                <td className="w-8 text-center align-middle">
                  <button
                    onClick={() => removeRow(rowIdx)}
                    disabled={rows.length <= 1}
                    className="opacity-0 group-hover/row:opacity-100 text-zinc-300 dark:text-zinc-600 hover:text-red-400 disabled:opacity-0 transition-opacity text-xs px-1"
                    title="Remover linha"
                  >✕</button>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* ── Rodapé ── */}
      <div className="flex items-center justify-between border-t border-zinc-200 dark:border-zinc-700">
        <button
          onClick={addRow}
          className="py-2 px-3 text-xs text-zinc-400 dark:text-zinc-500 hover:text-zinc-600 dark:hover:text-zinc-300 hover:bg-zinc-50 dark:hover:bg-zinc-800 transition-colors"
        >
          + Adicionar linha
        </button>

        {budget && (
          <div className="flex items-baseline gap-2 px-4 py-2 select-none pointer-events-none">
            <span className="text-[10px] font-semibold uppercase tracking-widest text-zinc-400 dark:text-zinc-500">
              Total
            </span>
            <span className={`text-base font-bold tabular-nums ${
              grandTotal > 0
                ? 'text-zinc-900 dark:text-zinc-100'
                : 'text-zinc-300 dark:text-zinc-600'
            }`}>
              R$&nbsp;{formatBRL(grandTotal)}
            </span>
          </div>
        )}
      </div>
    </div>
  );
}
