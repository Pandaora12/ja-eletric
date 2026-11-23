package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import static br.pdfleitor.core.Util.esc;

/** Gera planilhas Excel (.xlsx). Cada tabela vira uma aba. */
public class XlsxWriter {

    /** Organiza o documento em abas (tabelas nomeadas). */
    public static List<Doc.Table> sheets(Doc doc) {
        List<Doc.Table> out = new ArrayList<Doc.Table>();
        String lastHeading = null;
        Doc.Table text = new Doc.Table();
        text.name = "Texto";
        int tableNo = 0;
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                lastHeading = ((Doc.Heading) b).text().trim();
                List<String> row = new ArrayList<String>();
                row.add(lastHeading);
                text.rows.add(row);
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                if (p.isBlank()) continue;
                List<String> row = new ArrayList<String>();
                String t = p.text();
                // texto com colunas separadas por tabulação ou muitos espaços
                if (t.contains("\t")) {
                    for (String c : t.split("\t")) row.add(c.trim());
                } else if (t.contains("    ")) {
                    for (String c : t.trim().split("\\s{4,}")) row.add(c.trim());
                } else row.add(t);
                text.rows.add(row);
            } else if (b instanceof Doc.Table) {
                Doc.Table t = (Doc.Table) b;
                if (t.rows.isEmpty()) continue;
                tableNo++;
                Doc.Table copy = new Doc.Table();
                copy.rows.addAll(t.rows);
                copy.header = t.header;
                copy.name = t.name != null ? t.name : (lastHeading != null ? lastHeading : "Tabela " + tableNo);
                out.add(copy);
                // o título já virou nome da aba
                if (lastHeading != null && !text.rows.isEmpty()
                        && text.rows.get(text.rows.size() - 1).size() == 1
                        && lastHeading.equals(text.rows.get(text.rows.size() - 1).get(0))) {
                    text.rows.remove(text.rows.size() - 1);
                }
                lastHeading = null;
            }
        }
        boolean meaningfulText = false;
        for (List<String> r : text.rows) for (String c : r) if (c.trim().length() > 0) meaningfulText = true;
        if (meaningfulText && (out.isEmpty() || text.rows.size() > 2)) {
            if (out.isEmpty()) out.add(text);
            else out.add(text);
        }
        if (out.isEmpty()) {
            Doc.Table empty = new Doc.Table();
            empty.name = "Planilha1";
            out.add(empty);
        }
        // nomes válidos e únicos
        Set<String> used = new HashSet<String>();
        for (Doc.Table t : out) {
            String n = t.name == null ? "Planilha" : t.name.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
            if (n.isEmpty()) n = "Planilha";
            if (n.length() > 28) n = n.substring(0, 28).trim();
            String base = n;
            int k = 2;
            while (used.contains(n.toLowerCase())) n = base + " " + (k++);
            used.add(n.toLowerCase());
            t.name = n;
        }
        return out;
    }

    private static final Pattern NUM = Pattern.compile("-?(0|[1-9]\\d{0,14})([.]\\d+)?");
    private static final Pattern NUM_BR = Pattern.compile("-?(0|[1-9]\\d{0,2}([.]\\d{3})*|[1-9]\\d*),\\d+");

    /** Converte texto em número se for numérico; devolve null caso contrário. */
    public static String numeric(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || t.length() > 18) return null;
        if (NUM.matcher(t).matches()) return t;
        if (NUM_BR.matcher(t).matches()) return t.replace(".", "").replace(',', '.');
        return null;
    }

    public void write(Doc doc, OutputStream os) throws IOException {
        List<Doc.Table> sheets = sheets(doc);
        ZipOutputStream zos = new ZipOutputStream(os);
        StringBuilder ct = new StringBuilder();
        ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
                .append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
        for (int i = 1; i <= sheets.size(); i++) {
            ct.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        ct.append("</Types>");
        Util.zipPut(zos, "[Content_Types].xml", ct.toString());
        Util.zipPut(zos, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "</Relationships>");
        Util.zipPut(zos, "docProps/core.xml", DocxWriter.coreXml(doc.title));

        StringBuilder wb = new StringBuilder();
        wb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
                .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder wbRels = new StringBuilder();
        wbRels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheets.size(); i++) {
            wb.append("<sheet name=\"").append(esc(sheets.get(i - 1).name)).append("\" sheetId=\"").append(i)
                    .append("\" r:id=\"rId").append(i).append("\"/>");
            wbRels.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        wb.append("</sheets></workbook>");
        wbRels.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                .append("</Relationships>");
        Util.zipPut(zos, "xl/workbook.xml", wb.toString());
        Util.zipPut(zos, "xl/_rels/workbook.xml.rels", wbRels.toString());
        Util.zipPut(zos, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFC62828\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
                + "<border><left style=\"thin\"><color rgb=\"FFBFBFBF\"/></left><right style=\"thin\"><color rgb=\"FFBFBFBF\"/></right>"
                + "<top style=\"thin\"><color rgb=\"FFBFBFBF\"/></top><bottom style=\"thin\"><color rgb=\"FFBFBFBF\"/></bottom><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment wrapText=\"1\" vertical=\"top\"/></xf></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");

        for (int i = 0; i < sheets.size(); i++) {
            Util.zipPut(zos, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
        }
        zos.finish();
        zos.flush();
    }

    private String sheetXml(Doc.Table t) {
        int cols = Math.max(1, t.columnCount());
        boolean bordered = t.name == null || !t.name.equals("Texto");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
                .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        if (t.header && t.rows.size() > 1) {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        }
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int c = 0; c < cols; c++) {
            int len = 6;
            for (List<String> r : t.rows) {
                if (c < r.size() && r.get(c) != null) {
                    for (String l : r.get(c).split("\n")) len = Math.max(len, l.length());
                }
            }
            double w = Math.min(t.name != null && t.name.equals("Texto") && cols == 1 ? 120 : 60, len * 1.1 + 2);
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", w)).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        for (int r = 0; r < t.rows.size(); r++) {
            List<String> row = t.rows.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            boolean header = t.header && r == 0;
            for (int c = 0; c < row.size(); c++) {
                String v = row.get(c) == null ? "" : row.get(c);
                if (v.isEmpty() && !header) continue;
                String ref = colName(c) + (r + 1);
                String style = header ? " s=\"1\"" : bordered ? " s=\"2\"" : "";
                String num = header ? null : numeric(v);
                if (num != null) {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append("><v>").append(num).append("</v></c>");
                } else {
                    sb.append("<c r=\"").append(ref).append("\"").append(style).append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(esc(v.length() > 32000 ? v.substring(0, 32000) : v)).append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    static String colName(int c) {
        StringBuilder sb = new StringBuilder();
        c++;
        while (c > 0) {
            int m = (c - 1) % 26;
            sb.insert(0, (char) ('A' + m));
            c = (c - 1) / 26;
        }
        return sb.toString();
    }
}
