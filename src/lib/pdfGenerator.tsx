import React from 'react';
import {
  Document, Page, View, Text, Image, StyleSheet, pdf,
} from '@react-pdf/renderer';
import { readFile } from '@tauri-apps/plugin-fs';
import type { Block, TableContent } from '../types/blocks';
import type { Document as Doc } from '../types/document';
import type { Settings } from './settingsStorage';
import { formatBRL, computeRowTotal, parseBRL, isBudgetTable } from './budget';
import {
  isHeading, isParagraph, isTable, isDivider,
  isSignature, isCallout, isImage, isFinanceSummary, isLoadReport,
} from '../types/blocks';
import { calcLoadRow, STARTUP_LABELS, VOLTAGE_LABELS } from '../components/blocks/LoadReportBlock';

// ─── Utilitários ──────────────────────────────────────────────────────────────

function toBase64(bytes: Uint8Array): string {
  let binary = '';
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + CHUNK, bytes.length)));
  }
  return btoa(binary);
}

const MIME_MAP: Record<string, string> = {
  png: 'image/png', gif: 'image/gif',
  webp: 'image/webp', svg: 'image/svg+xml', bmp: 'image/bmp',
};

async function pathToDataUri(path: string): Promise<string | null> {
  try {
    const data = await readFile(path);
    const ext = path.split('.').pop()?.toLowerCase() ?? 'jpg';
    const mime = MIME_MAP[ext] ?? 'image/jpeg';
    return `data:${mime};base64,${toBase64(new Uint8Array(data))}`;
  } catch {
    return null;
  }
}

// ─── Estilos ──────────────────────────────────────────────────────────────────

const S = StyleSheet.create({
  page: { padding: 40, fontSize: 10, color: '#1f2937', fontFamily: 'Helvetica' },

  // Cabeçalho empresa
  companyHeader: {
    flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center',
    marginBottom: 20, paddingBottom: 12, borderBottomWidth: 2,
  },
  logo: { height: 42, objectFit: 'contain' },
  companyName: { fontSize: 20, fontFamily: 'Helvetica-Bold' },

  // Meta do documento
  docTitle: { fontSize: 18, fontFamily: 'Helvetica-Bold', color: '#111827', marginBottom: 4 },
  docMeta:  { fontSize: 8, color: '#9ca3af', marginBottom: 24 },

  // Bloco genérico
  block: { marginBottom: 10 },

  // Títulos
  h1: { fontSize: 16, fontFamily: 'Helvetica-Bold', color: '#111827', marginBottom: 6 },
  h2: { fontSize: 13, fontFamily: 'Helvetica-Bold', color: '#1f2937', marginBottom: 5 },
  h3: { fontSize: 11, fontFamily: 'Helvetica-Bold', color: '#374151', marginBottom: 4 },

  // Parágrafo
  paragraph: { fontSize: 10, lineHeight: 1.6, color: '#374151' },

  // Divisor
  divider:       { borderBottomWidth: 1, borderBottomColor: '#e5e7eb', marginVertical: 10 },
  dividerDashed: { borderBottomWidth: 1, borderBottomColor: '#d1d5db', marginVertical: 10 },

  // Tabela
  tableWrapper: { marginBottom: 12, borderWidth: 0.5, borderColor: '#e5e7eb' },
  tableHeaderRow: { flexDirection: 'row', backgroundColor: '#f3f4f6', borderBottomWidth: 1, borderBottomColor: '#d1d5db' },
  tableRow:      { flexDirection: 'row', borderBottomWidth: 0.5, borderBottomColor: '#e5e7eb' },
  tableRowAlt:   { flexDirection: 'row', borderBottomWidth: 0.5, borderBottomColor: '#e5e7eb', backgroundColor: '#fafafa' },
  tableRowHL:    { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#ca8a04', backgroundColor: '#fef08a' },
  tableCell:     { flex: 1, paddingHorizontal: 5, paddingVertical: 4, borderRightWidth: 0.5, borderRightColor: '#e5e7eb' },
  headerText:    { fontSize: 7, fontFamily: 'Helvetica-Bold', color: '#6b7280', textTransform: 'uppercase' },
  cellText:      { fontSize: 9, color: '#374151' },
  cellRight:     { textAlign: 'right' },
  cellBold:      { fontFamily: 'Helvetica-Bold' },
  grandTotalRow: { flexDirection: 'row', backgroundColor: '#f3f4f6', borderTopWidth: 1, borderTopColor: '#d1d5db' },
  grandTotalLbl: { fontSize: 8, fontFamily: 'Helvetica-Bold', color: '#6b7280', textTransform: 'uppercase', paddingHorizontal: 5, paddingVertical: 6 },
  grandTotalVal: { fontSize: 12, fontFamily: 'Helvetica-Bold', color: '#111827', textAlign: 'right', paddingHorizontal: 5, paddingVertical: 6 },

  // Callout — fundo sólido de cor saturada, borda esquerda de destaque
  callout:     { borderLeftWidth: 4, paddingHorizontal: 12, paddingVertical: 9, marginBottom: 10 },
  calloutText: { fontSize: 9, lineHeight: 1.6 },

  // Assinatura
  signatureWrapper: { marginTop: 30, width: 200 },
  signatureLine:    { borderTopWidth: 1, borderTopColor: '#1f2937', paddingTop: 6, marginTop: 36 },
  signatureName:    { fontSize: 10, fontFamily: 'Helvetica-Bold', color: '#111827' },
  signatureRole:    { fontSize: 8, color: '#6b7280', marginTop: 2 },
  signatureLabel:   { fontSize: 8, color: '#9ca3af', marginTop: 2 },

  // Imagem
  imageBlock: { maxHeight: 240, objectFit: 'contain', marginBottom: 4 },
  imageAlt:   { fontSize: 8, color: '#9ca3af', textAlign: 'center', marginBottom: 8 },

  // Relatório de carga
  lrWrapper:     { marginBottom: 14, borderWidth: 0.5, borderColor: '#d1d5db' },
  lrHeader:      { backgroundColor: '#f1f5f9', paddingHorizontal: 8, paddingVertical: 5, borderBottomWidth: 0.5, borderBottomColor: '#cbd5e1' },
  lrHeaderTxt:   { fontSize: 7, fontFamily: 'Helvetica-Bold', color: '#475569', textTransform: 'uppercase', letterSpacing: 0.5 },
  lrMeta:        { fontSize: 6, color: '#94a3b8', marginTop: 1 },
  lrColHead:     { flexDirection: 'row', backgroundColor: '#f8fafc', borderBottomWidth: 0.5, borderBottomColor: '#e2e8f0' },
  lrRow:         { flexDirection: 'row', borderBottomWidth: 0.3, borderBottomColor: '#f1f5f9' },
  lrRowAlt:      { flexDirection: 'row', borderBottomWidth: 0.3, borderBottomColor: '#f1f5f9', backgroundColor: '#fafafa' },
  lrCell:        { paddingHorizontal: 4, paddingVertical: 3, borderRightWidth: 0.3, borderRightColor: '#e2e8f0' },
  lrTh:          { fontSize: 6, fontFamily: 'Helvetica-Bold', color: '#64748b', textTransform: 'uppercase' },
  lrTd:          { fontSize: 7.5, color: '#374151' },
  lrComp:        { fontSize: 7.5, color: '#0369a1', fontFamily: 'Helvetica-Bold' },
  lrWarn:        { fontSize: 7.5, color: '#d97706', fontFamily: 'Helvetica-Bold' },
  lrSummary:     { flexDirection: 'row', borderTopWidth: 1, borderTopColor: '#cbd5e1', backgroundColor: '#f8fafc' },
  lrSumBox:      { flex: 1, paddingHorizontal: 8, paddingVertical: 6, borderRightWidth: 0.5, borderRightColor: '#e2e8f0' },
  lrSumLbl:      { fontSize: 6, fontFamily: 'Helvetica-Bold', color: '#64748b', textTransform: 'uppercase', marginBottom: 2 },
  lrSumVal:      { fontSize: 12, fontFamily: 'Helvetica-Bold', color: '#111827' },
  lrSumSub:      { fontSize: 6, color: '#94a3b8', marginTop: 1 },
  lrSumWarnBox:  { flex: 1, paddingHorizontal: 8, paddingVertical: 6, backgroundColor: '#fffbeb' },
  lrSumWarnVal:  { fontSize: 12, fontFamily: 'Helvetica-Bold', color: '#d97706' },
  lrSumWarnTxt:  { fontSize: 6, color: '#b45309', fontFamily: 'Helvetica-Bold', marginTop: 2 },

  // Resumo financeiro (modo comercial)
  summaryWrapper:  { marginBottom: 16, borderWidth: 1, borderColor: '#d1d5db', borderRadius: 4 },
  summaryHeader:   { backgroundColor: '#f3f4f6', paddingHorizontal: 12, paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#d1d5db' },
  summaryTitle:    { fontSize: 11, fontFamily: 'Helvetica-Bold', color: '#111827', textTransform: 'uppercase', letterSpacing: 1 },
  summaryRow:      { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 12, paddingVertical: 7, borderBottomWidth: 0.5, borderBottomColor: '#e5e7eb' },
  summaryRowTotal: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 12, paddingVertical: 10, backgroundColor: '#f0fdf4' },
  summaryLabel:    { fontSize: 9, color: '#374151' },
  summaryValue:    { fontSize: 9, fontFamily: 'Helvetica-Bold', color: '#111827', textAlign: 'right' },
  summaryTotalLbl: { fontSize: 11, fontFamily: 'Helvetica-Bold', color: '#166534' },
  summaryTotalVal: { fontSize: 14, fontFamily: 'Helvetica-Bold', color: '#16a34a', textAlign: 'right' },

  // Rodapé
  footer: {
    position: 'absolute', bottom: 20, left: 40, right: 40,
    textAlign: 'center', fontSize: 8, color: '#9ca3af',
    borderTopWidth: 0.5, borderTopColor: '#e5e7eb', paddingTop: 6,
  },
});

// Widths da tabela de orçamento (Item|Desc|Qtd|Un|V.Unit|Total) — soma 100%
const BW = ['7%', '37%', '8%', '6%', '20%', '22%'];

// ─── Renderers de bloco ───────────────────────────────────────────────────────

function PdfHeading({ block }: { block: Block & { content: { text: string; level: 1 | 2 | 3 } } }) {
  const style = block.content.level === 1 ? S.h1 : block.content.level === 2 ? S.h2 : S.h3;
  return <Text style={[S.block, style]}>{block.content.text || ' '}</Text>;
}

function PdfParagraph({ block }: { block: Block & { content: { text: string } } }) {
  if (!block.content.text) return null;
  return <Text style={[S.block, S.paragraph]}>{block.content.text}</Text>;
}

function PdfDivider({ block }: { block: Block & { content: { style: string } } }) {
  return <View style={block.content.style === 'dashed' ? S.dividerDashed : S.divider} />;
}

function PdfSignature({ block }: { block: Block & { content: { label: string; name: string; role: string } } }) {
  const { name, role, label } = block.content;
  return (
    <View style={[S.block, S.signatureWrapper]}>
      <View style={S.signatureLine}>
        {!!name  && <Text style={S.signatureName}>{name}</Text>}
        {!!role  && <Text style={S.signatureRole}>{role}</Text>}
        {!!label && <Text style={S.signatureLabel}>{label}</Text>}
      </View>
    </View>
  );
}

const CALLOUT_THEME: Record<string, { border: string; bg: string; text: string }> = {
  info:    { border: '#1d4ed8', bg: '#dbeafe', text: '#1e3a8a' },
  warning: { border: '#b45309', bg: '#fde68a', text: '#78350f' },
  danger:  { border: '#b91c1c', bg: '#fee2e2', text: '#7f1d1d' },
  success: { border: '#047857', bg: '#d1fae5', text: '#064e3b' },
};

function PdfCallout({ block }: { block: Block & { content: { text: string; variant: string } } }) {
  const theme = CALLOUT_THEME[block.content.variant] ?? CALLOUT_THEME.info;
  return (
    <View style={[S.block, S.callout, { borderLeftColor: theme.border, backgroundColor: theme.bg }]}>
      <Text style={[S.calloutText, { color: theme.text }]}>{block.content.text}</Text>
    </View>
  );
}

function PdfImageBlock({
  block, imageMap,
}: { block: Block & { content: { path: string; alt: string } }; imageMap: Map<string, string> }) {
  const uri = imageMap.get(block.content.path);
  if (!uri) return null;
  return (
    <View style={S.block}>
      <Image src={uri} style={S.imageBlock} />
      {!!block.content.alt && <Text style={S.imageAlt}>{block.content.alt}</Text>}
    </View>
  );
}

function PdfTable({ block }: { block: Block & { content: TableContent } }) {
  const { headers, rows } = block.content;
  const budget = isBudgetTable(headers);
  const colCount = headers.length;
  const cw = (i: number) => budget ? BW[i] : `${(100 / colCount).toFixed(1)}%`;

  const grandTotal = budget
    ? rows.reduce((s, r) => s + computeRowTotal(r.cells), 0)
    : 0;

  return (
    <View style={[S.block, S.tableWrapper]}>
      {/* Header */}
      <View style={S.tableHeaderRow}>
        {headers.map((h, i) => (
          <View key={i} style={[S.tableCell, { width: cw(i) }]}>
            <Text style={[S.headerText, budget && i >= 2 ? S.cellRight : {}]}>{h}</Text>
          </View>
        ))}
      </View>

      {/* Linhas */}
      {rows.map((row, ri) => {
        const rowTotal = budget ? computeRowTotal(row.cells) : 0;
        const rowStyle = row.isHighlighted ? S.tableRowHL : ri % 2 === 1 ? S.tableRowAlt : S.tableRow;
        return (
          <View key={ri} style={rowStyle}>
            {row.cells.slice(0, budget ? 5 : colCount).map((cell, ci) => {
              const isRight = budget && ci >= 2;
              const display = budget && ci === 4 ? `R$ ${formatBRL(parseBRL(cell))}` : cell;
              return (
                <View key={ci} style={[S.tableCell, { width: cw(ci) }]}>
                  <Text style={[S.cellText, isRight ? S.cellRight : {}, row.isHighlighted ? S.cellBold : {}]}>{display}</Text>
                </View>
              );
            })}
            {budget && (
              <View style={[S.tableCell, { width: BW[5] }]}>
                <Text style={[S.cellText, S.cellRight, S.cellBold]}>
                  {`R$ ${formatBRL(rowTotal)}`}
                </Text>
              </View>
            )}
          </View>
        );
      })}

      {/* Total geral */}
      {budget && (
        <View style={S.grandTotalRow}>
          <View style={{ width: '78%' }}>
            <Text style={S.grandTotalLbl}>Total Geral</Text>
          </View>
          <View style={{ width: '22%' }}>
            <Text style={S.grandTotalVal}>{`R$ ${formatBRL(grandTotal)}`}</Text>
          </View>
        </View>
      )}
    </View>
  );
}

function PdfLoadReport({ block }: { block: Block & { content: import('../types/blocks').LoadReportContent } }) {
  const { rows } = block.content;
  const computed = rows.map(calcLoadRow);

  const totalInstKW  = computed.reduce((s, c, i) => s + c.kw * rows[i].qty, 0);
  const totalDemKW   = computed.reduce((s, c) => s + c.demandKw, 0);
  let maxIPart = 0; let maxIName = '';
  computed.forEach((c, i) => {
    const v = c.iPart * rows[i].qty;
    if (v > maxIPart) { maxIPart = v; maxIName = rows[i].equipment || `Linha ${i + 1}`; }
  });
  const flickerRisk = maxIPart > 300;

  const n = (v: number, d = 2) => v.toFixed(d);

  // column widths (% of page width)
  const CW = { equip: '22%', qty: '5%', cv: '6%', kw: '6%', startup: '13%', volt: '8%', inom: '7%', ipart: '7%', regime: '5%', fd: '5%', dem: '7%', total: '9%' };

  return (
    <View style={[S.block, S.lrWrapper]}>
      {/* Cabeçalho */}
      <View style={S.lrHeader}>
        <Text style={S.lrHeaderTxt}>⚡ Relatório de Carga — NBR 5410 / IEC 60034-1</Text>
        <Text style={S.lrMeta}>η = 90% · cosφ = 0,85 · 1 CV = 0,7355 kW</Text>
      </View>

      {/* Cabeçalho da tabela */}
      <View style={S.lrColHead}>
        {[
          { label: 'Equipamento', w: CW.equip },
          { label: 'Qtd', w: CW.qty },
          { label: 'CV', w: CW.cv },
          { label: 'kW', w: CW.kw },
          { label: 'Tipo Partida', w: CW.startup },
          { label: 'Tensão', w: CW.volt },
          { label: 'I-nom (A)', w: CW.inom },
          { label: 'I-part (A)', w: CW.ipart },
          { label: 'Reg.', w: CW.regime },
          { label: 'Fd', w: CW.fd },
          { label: 'Dem.(kW)', w: CW.dem },
        ].map((col) => (
          <View key={col.label} style={[S.lrCell, { width: col.w }]}>
            <Text style={[S.lrTh, { textAlign: 'right' }]}>{col.label}</Text>
          </View>
        ))}
      </View>

      {/* Linhas */}
      {rows.map((row, ri) => {
        const c = computed[ri];
        const rowStyle = ri % 2 === 1 ? S.lrRowAlt : S.lrRow;
        return (
          <View key={ri} style={rowStyle}>
            <View style={[S.lrCell, { width: CW.equip }]}><Text style={S.lrTd}>{row.equipment || '—'}</Text></View>
            <View style={[S.lrCell, { width: CW.qty }]}><Text style={[S.lrTd, { textAlign: 'right' }]}>{row.qty}</Text></View>
            <View style={[S.lrCell, { width: CW.cv }]}><Text style={[S.lrTd, { textAlign: 'right' }]}>{row.powerCV}</Text></View>
            <View style={[S.lrCell, { width: CW.kw }]}><Text style={[S.lrComp, { textAlign: 'right' }]}>{n(c.kw)}</Text></View>
            <View style={[S.lrCell, { width: CW.startup }]}><Text style={S.lrTd}>{STARTUP_LABELS[row.startupType]}</Text></View>
            <View style={[S.lrCell, { width: CW.volt }]}><Text style={S.lrTd}>{VOLTAGE_LABELS[row.voltagePhase]}</Text></View>
            <View style={[S.lrCell, { width: CW.inom }]}><Text style={[S.lrComp, { textAlign: 'right' }]}>{n(c.iNom)}</Text></View>
            <View style={[S.lrCell, { width: CW.ipart }]}>
              <Text style={[c.iPart * row.qty > 300 ? S.lrWarn : S.lrComp, { textAlign: 'right' }]}>{n(c.iPart)}</Text>
            </View>
            <View style={[S.lrCell, { width: CW.regime }]}><Text style={[S.lrTd, { textAlign: 'right' }]}>{row.regime}</Text></View>
            <View style={[S.lrCell, { width: CW.fd }]}><Text style={[S.lrTd, { textAlign: 'right' }]}>{row.demandFactor.toFixed(2)}</Text></View>
            <View style={[S.lrCell, { width: CW.dem }]}><Text style={[S.lrComp, { textAlign: 'right' }]}>{n(c.demandKw)}</Text></View>
          </View>
        );
      })}

      {/* Resumo executivo */}
      <View style={S.lrSummary}>
        <View style={S.lrSumBox}>
          <Text style={S.lrSumLbl}>Carga Instalada</Text>
          <Text style={S.lrSumVal}>{n(totalInstKW)} kW</Text>
          <Text style={S.lrSumSub}>{n(totalInstKW / 0.7355, 1)} CV total</Text>
        </View>
        <View style={S.lrSumBox}>
          <Text style={S.lrSumLbl}>Demanda Total</Text>
          <Text style={[S.lrSumVal, { color: '#0369a1' }]}>{n(totalDemKW)} kW</Text>
          <Text style={S.lrSumSub}>Fd médio aplicado</Text>
        </View>
        <View style={flickerRisk ? S.lrSumWarnBox : S.lrSumBox}>
          <Text style={S.lrSumLbl}>Maior I-partida</Text>
          <Text style={flickerRisk ? S.lrSumWarnVal : S.lrSumVal}>{n(maxIPart)} A</Text>
          <Text style={S.lrSumSub}>{maxIName}</Text>
          {flickerRisk && <Text style={S.lrSumWarnTxt}>⚠ Risco de flicker</Text>}
        </View>
      </View>
    </View>
  );
}

function PdfFinanceSummary({ doc }: { doc: Doc }) {
  let materialTotal = 0;
  let laborTotal = 0;
  let otherTotal = 0;

  for (const block of doc.blocks) {
    if (!isTable(block)) continue;
    const content = block.content as TableContent;
    if (!isBudgetTable(content.headers)) continue;
    const sum = content.rows.reduce((s, r) => s + computeRowTotal(r.cells), 0);
    const cat = content.tableCategory;
    if (cat === 'material') materialTotal += sum;
    else if (cat === 'labor') laborTotal += sum;
    else otherTotal += sum;
  }

  const grandTotal = materialTotal + laborTotal + otherTotal;

  const rows: { label: string; value: number }[] = [
    { label: 'Materiais', value: materialTotal },
    { label: 'Mão de Obra', value: laborTotal },
    { label: 'Outros', value: otherTotal },
  ].filter((r) => r.value > 0);

  return (
    <View style={[S.block, S.summaryWrapper]}>
      <View style={S.summaryHeader}>
        <Text style={S.summaryTitle}>Resumo Financeiro</Text>
      </View>
      {rows.map((r) => (
        <View key={r.label} style={S.summaryRow}>
          <Text style={S.summaryLabel}>{r.label}</Text>
          <Text style={S.summaryValue}>{`R$ ${formatBRL(r.value)}`}</Text>
        </View>
      ))}
      <View style={S.summaryRowTotal}>
        <Text style={S.summaryTotalLbl}>VALOR TOTAL</Text>
        <Text style={S.summaryTotalVal}>{`R$ ${formatBRL(grandTotal)}`}</Text>
      </View>
    </View>
  );
}

// ─── Componente de documento PDF ─────────────────────────────────────────────

interface PdfDocProps {
  doc: Doc;
  settings: Settings;
  logoUri: string | null;
  imageMap: Map<string, string>;
}

function PdfDoc({ doc, settings, logoUri, imageMap }: PdfDocProps) {
  const color = settings.primaryColor || '#0ea5e9';

  const fmtDate = (ts: number) =>
    new Date(ts).toLocaleDateString('pt-BR', {
      day: '2-digit', month: 'long', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });

  return (
    <Document>
      <Page size="A4" style={S.page}>

        {/* Cabeçalho empresa */}
        {(settings.companyName || logoUri) && (
          <View style={[S.companyHeader, { borderBottomColor: color }]}>
            {logoUri
              ? <Image src={logoUri} style={S.logo} />
              : <View />}
            {!!settings.companyName && (
              <Text style={[S.companyName, { color }]}>{settings.companyName}</Text>
            )}
          </View>
        )}

        {/* Título e meta */}
        <Text style={S.docTitle}>{doc.title}</Text>
        <Text style={S.docMeta}>
          {`Criado em ${fmtDate(doc.createdAt)}`}
          {doc.savedAt ? `   •   Salvo em ${fmtDate(doc.savedAt)}` : ''}
        </Text>

        {/* Blocos */}
        {doc.blocks.map((block) => {
          if (block.type === 'company-header') return null;
          if (isFinanceSummary(block)) return null; // UI-only, replaced by PdfFinanceSummary
          if (isHeading(block))   return <PdfHeading   key={block.id} block={block} />;
          if (isParagraph(block)) return <PdfParagraph key={block.id} block={block} />;
          if (isDivider(block))   return <PdfDivider   key={block.id} block={block} />;
          if (isSignature(block)) return <PdfSignature key={block.id} block={block} />;
          if (isCallout(block))   return <PdfCallout   key={block.id} block={block} />;
          if (isImage(block))     return <PdfImageBlock key={block.id} block={block} imageMap={imageMap} />;
          if (isTable(block)) {
            if (doc.isCommercialMode) return null;
            return <PdfTable key={block.id} block={block} />;
          }
          if (isLoadReport(block)) return <PdfLoadReport key={block.id} block={block} />;
          return null;
        })}

        {/* Resumo financeiro — aparece em modo comercial */}
        {doc.isCommercialMode && <PdfFinanceSummary doc={doc} />}

        {/* Numeração de página */}
        <Text
          style={S.footer}
          render={({ pageNumber, totalPages }) =>
            `${doc.title}   •   Página ${pageNumber} de ${totalPages}`}
          fixed
        />
      </Page>
    </Document>
  );
}

// ─── Função exportada ─────────────────────────────────────────────────────────

export async function generatePdfBytes(doc: Doc, settings: Settings): Promise<Uint8Array> {
  // Pré-carrega logo e imagens como data URIs (base64)
  const logoUri = settings.logoPath ? await pathToDataUri(settings.logoPath) : null;

  const imageMap = new Map<string, string>();
  for (const block of doc.blocks) {
    if (block.type === 'image') {
      const path = (block.content as { path: string }).path;
      if (path) {
        const uri = await pathToDataUri(path);
        if (uri) imageMap.set(path, uri);
      }
    }
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const element = React.createElement(PdfDoc as any, { doc, settings, logoUri, imageMap });
  const blob = await pdf(element as any).toBlob();
  const buffer = await blob.arrayBuffer();
  return new Uint8Array(buffer);
}
