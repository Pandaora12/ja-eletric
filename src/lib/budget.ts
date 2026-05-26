// ─── Parsing robusto de números em formato brasileiro e neutro ───────────────
//
// Suporta:
//   '100'        → 100
//   '1500'       → 1500
//   '1500.50'    → 1500.50   (ponto = decimal, estilo neutro/US)
//   '1500,50'    → 1500.50   (vírgula = decimal, estilo BR)
//   '1.500,50'   → 1500.50   (ponto = milhar, vírgula = decimal, estilo BR)
//   '1,500.50'   → 1500.50   (vírgula = milhar, ponto = decimal, estilo US)
//   '1.500'      → 1500      (ponto com 3 casas = milhar BR)

export function parseBRL(raw: string): number {
  if (!raw) return 0;
  const s = raw.trim();
  if (!s) return 0;

  const hasDot   = s.includes('.');
  const hasComma = s.includes(',');

  // Ambos presentes: o último separador é o decimal
  if (hasDot && hasComma) {
    if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
      // '1.500,50' — vírgula é decimal
      return parseFloat(s.replace(/\./g, '').replace(',', '.')) || 0;
    }
    // '1,500.50' — ponto é decimal
    return parseFloat(s.replace(/,/g, '')) || 0;
  }

  // Apenas vírgula
  if (hasComma) {
    const parts = s.split(',');
    // '1500,50' — 1 vírgula com ≤2 dígitos depois → decimal BR
    if (parts.length === 2 && parts[1].length <= 2) {
      return parseFloat(s.replace(',', '.')) || 0;
    }
    // '1,500' ou '1,000,000' → separador de milhar
    return parseFloat(s.replace(/,/g, '')) || 0;
  }

  // Apenas ponto
  if (hasDot) {
    const parts = s.split('.');
    if (parts.length === 2) {
      // '1.500' com exatamente 3 casas após o ponto → milhar BR
      if (parts[1].length === 3) return parseFloat(s.replace('.', '')) || 0;
      // '1.5', '1.50', '1.55' → decimal
      return parseFloat(s) || 0;
    }
    // Múltiplos pontos: milhar (ex: '1.000.000')
    return parseFloat(s.replace(/\./g, '')) || 0;
  }

  return parseFloat(s) || 0;
}

// ─── Formatação no padrão pt-BR ──────────────────────────────────────────────

const BRL_FORMAT = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatBRL(n: number): string {
  return BRL_FORMAT.format(isFinite(n) ? n : 0);
}

// ─── Cálculo do total de uma linha de orçamento ───────────────────────────────
// cells[2] = Qtd.   cells[4] = Valor Unit.

export function computeRowTotal(cells: string[]): number {
  return parseBRL(cells[2] ?? '0') * parseBRL(cells[4] ?? '0');
}

// ─── Detecta se a tabela é um orçamento (6 colunas, última = Total) ───────────

export function isBudgetTable(headers: string[]): boolean {
  return headers.length === 6 && headers[5].toLowerCase().includes('total');
}
