import {
  Document, Packer, Paragraph, TextRun, HeadingLevel,
  Table, TableRow, TableCell, WidthType, AlignmentType,
  BorderStyle,
} from 'docx';
import type { Block } from '../types/blocks';
import type { Document as Doc } from '../types/document';
import type { Settings } from './settingsStorage';
import { formatBRL, computeRowTotal, parseBRL, isBudgetTable } from './budget';
import {
  isHeading, isParagraph, isTable, isDivider, isSignature, isCallout,
} from '../types/blocks';

// ─── Helpers ─────────────────────────────────────────────────────────────────

const TABLE_BORDER = {
  top:    { style: BorderStyle.SINGLE, size: 4, color: 'E5E7EB' },
  bottom: { style: BorderStyle.SINGLE, size: 4, color: 'E5E7EB' },
  left:   { style: BorderStyle.SINGLE, size: 4, color: 'E5E7EB' },
  right:  { style: BorderStyle.SINGLE, size: 4, color: 'E5E7EB' },
};

function hexColor(hex: string): string {
  return hex.replace('#', '').toUpperCase();
}

function fmtDate(ts: number): string {
  return new Date(ts).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'long', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

// ─── Conversores de bloco ─────────────────────────────────────────────────────

function blockToWord(block: Block): (Paragraph | Table)[] {
  if (isHeading(block)) {
    const level = block.content.level === 1 ? HeadingLevel.HEADING_1
      : block.content.level === 2 ? HeadingLevel.HEADING_2
      : HeadingLevel.HEADING_3;
    return [new Paragraph({ text: block.content.text, heading: level })];
  }

  if (isParagraph(block)) {
    return block.content.text
      ? [new Paragraph({ text: block.content.text })]
      : [new Paragraph('')];
  }

  if (isDivider(block)) {
    return [new Paragraph({
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: 'E5E7EB' } },
      spacing: { before: 120, after: 120 },
    })];
  }

  if (isCallout(block) && block.content.text) {
    const colorMap: Record<string, string> = {
      info: '3B82F6', warning: 'F59E0B', danger: 'EF4444', success: '10B981',
    };
    const color = colorMap[block.content.variant] ?? '3B82F6';
    return [new Paragraph({
      children: [new TextRun({ text: block.content.text, italics: true, color })],
      indent: { left: 360 },
      border: { left: { style: BorderStyle.SINGLE, size: 12, color } },
      spacing: { before: 80, after: 80 },
    })];
  }

  if (isSignature(block)) {
    const { name, role, label } = block.content;
    const result: Paragraph[] = [
      new Paragraph({ spacing: { before: 600 } }),
      new Paragraph({
        border: { top: { style: BorderStyle.SINGLE, size: 6, color: '1F2937' } },
        children: [new TextRun({ text: name || ' ', bold: true, size: 22 })],
      }),
    ];
    if (role) result.push(new Paragraph({ children: [new TextRun({ text: role, color: '6B7280', size: 18 })] }));
    if (label) result.push(new Paragraph({ children: [new TextRun({ text: label, color: '9CA3AF', size: 16, italics: true })] }));
    return result;
  }

  if (isTable(block)) {
    const { headers, rows } = block.content;
    const budget = isBudgetTable(headers);

    const headerRow = new TableRow({
      children: headers.map((h) => new TableCell({
        children: [new Paragraph({
          children: [new TextRun({ text: h, bold: true, size: 16, color: '6B7280' })],
        })],
        shading: { fill: 'F3F4F6' },
        borders: TABLE_BORDER,
      })),
    });

    const dataRows = rows.map((row, ri) => {
      const cells = row.cells.slice(0, budget ? 5 : headers.length);
      if (budget) {
        const total = computeRowTotal(row.cells);
        cells.push(`R$ ${formatBRL(total)}`);
      }
      return new TableRow({
        children: cells.map((cell, ci) => {
          const isRight = budget && ci >= 2;
          const display = budget && ci === 4 ? `R$ ${formatBRL(parseBRL(cell))}` : cell;
          return new TableCell({
            children: [new Paragraph({
              children: [new TextRun({ text: display, size: 18 })],
              alignment: isRight ? AlignmentType.RIGHT : AlignmentType.LEFT,
            })],
            shading: ri % 2 === 1 ? { fill: 'FAFAFA' } : undefined,
            borders: TABLE_BORDER,
          });
        }),
      });
    });

    // Grand total row for budget tables
    if (budget) {
      const grandTotal = rows.reduce((s, r) => s + computeRowTotal(r.cells), 0);
      const spanCount = headers.length - 1;
      dataRows.push(new TableRow({
        children: [
          ...Array.from({ length: spanCount }, () => new TableCell({
            children: [new Paragraph('')],
            shading: { fill: 'F3F4F6' },
            borders: TABLE_BORDER,
          })),
          new TableCell({
            children: [new Paragraph({
              children: [new TextRun({ text: `R$ ${formatBRL(grandTotal)}`, bold: true, size: 24 })],
              alignment: AlignmentType.RIGHT,
            })],
            shading: { fill: 'F3F4F6' },
            borders: TABLE_BORDER,
          }),
        ],
      }));
    }

    return [
      new Table({
        rows: [headerRow, ...dataRows],
        width: { size: 100, type: WidthType.PERCENTAGE },
      }),
      new Paragraph(''), // espaço após tabela
    ];
  }

  // Imagens: pular no Word (caminhos locais não são portáveis no DOCX)
  return [];
}

// ─── Função exportada ─────────────────────────────────────────────────────────

export async function generateWordBytes(doc: Doc, settings: Settings): Promise<Uint8Array> {
  const children: (Paragraph | Table)[] = [];

  // Cabeçalho da empresa
  if (settings.companyName) {
    children.push(new Paragraph({
      children: [new TextRun({
        text: settings.companyName,
        bold: true,
        size: 48,
        color: hexColor(settings.primaryColor || '#0ea5e9'),
      })],
      spacing: { after: 80 },
    }));
  }

  // Título do documento
  children.push(new Paragraph({
    children: [new TextRun({ text: doc.title, bold: true, size: 40, color: '111827' })],
    spacing: { after: 40 },
  }));

  // Metadados
  children.push(new Paragraph({
    children: [new TextRun({
      text: `Criado em ${fmtDate(doc.createdAt)}` + (doc.savedAt ? `   •   Salvo em ${fmtDate(doc.savedAt)}` : ''),
      color: '9CA3AF', size: 18,
    })],
    spacing: { after: 240 },
  }));

  // Blocos
  for (const block of doc.blocks) {
    if (block.type === 'company-header') continue;
    children.push(...blockToWord(block));
  }

  const wordDoc = new Document({
    sections: [{ children }],
  });

  const blob = await Packer.toBlob(wordDoc);
  const buffer = await blob.arrayBuffer();
  return new Uint8Array(buffer);
}
