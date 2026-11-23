package br.pdfleitor.core;

import org.xmlpull.v1.XmlPullParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static br.pdfleitor.core.Util.attr;
import static br.pdfleitor.core.Util.ln;

/** Lê planilhas Excel (.xlsx). Cada aba vira um título + tabela. */
public class XlsxReader {

    public static final int MAX_ROWS = 20000;
    public static final int MAX_COLS = 200;

    private Map<String, byte[]> zip;
    private final List<String> shared = new ArrayList<String>();
    private final List<Boolean> dateStyles = new ArrayList<Boolean>();

    public Doc read(byte[] data) throws Exception {
        zip = Util.unzip(data);
        String wb = "xl/workbook.xml";
        if (!zip.containsKey(wb)) throw new Exception("Planilha Excel inválida");
        loadShared();
        loadStyles();
        Map<String, String> rels = Util.rels(zip, wb);
        Doc doc = new Doc();
        doc.title = "Planilha";
        XmlPullParser p = Util.parser(zip.get(wb));
        int ev;
        List<String[]> sheets = new ArrayList<String[]>();
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && ln(p.getName()).equals("sheet")) {
                String state = attr(p, "state");
                if ("hidden".equals(state) || "veryHidden".equals(state)) continue;
                String path = rels.get(Util.attrQ(p, "r:id"));
                if (path != null && zip.containsKey(path)) sheets.add(new String[]{attr(p, "name"), path});
            }
        }
        for (String[] sh : sheets) {
            Doc.Table t = readSheet(zip.get(sh[1]));
            t.name = sh[0];
            t.trim();
            if (sheets.size() > 1 || t.rows.isEmpty()) doc.add(new Doc.Heading(2, sh[0]));
            if (t.rows.isEmpty()) {
                doc.add(new Doc.Para("(aba vazia)"));
            } else {
                t.header = true;
                doc.add(t);
                if (t.columnCount() > 6) doc.landscape = true;
            }
        }
        return doc;
    }

    private void loadShared() throws Exception {
        byte[] s = zip.get("xl/sharedStrings.xml");
        if (s == null) return;
        XmlPullParser p = Util.parser(s);
        int ev;
        StringBuilder cur = null;
        boolean inRph = false;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = ln(p.getName());
                if (n.equals("si")) cur = new StringBuilder();
                else if (n.equals("rPh")) inRph = true;
                else if (n.equals("t") && cur != null && !inRph) cur.append(p.nextText());
            } else if (ev == XmlPullParser.END_TAG) {
                String n = ln(p.getName());
                if (n.equals("si") && cur != null) {
                    shared.add(cur.toString());
                    cur = null;
                } else if (n.equals("rPh")) inRph = false;
            }
        }
    }

    private void loadStyles() {
        byte[] s = zip.get("xl/styles.xml");
        if (s == null) return;
        try {
            Set<String> customDates = new HashSet<String>();
            XmlPullParser p = Util.parser(s);
            int ev;
            boolean inXfs = false;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = ln(p.getName());
                    if (n.equals("numFmt")) {
                        String code = attr(p, "formatCode");
                        if (code != null && isDateFormat(code)) customDates.add(attr(p, "numFmtId"));
                    } else if (n.equals("cellXfs")) {
                        inXfs = true;
                    } else if (n.equals("xf") && inXfs) {
                        String id = attr(p, "numFmtId");
                        int v = -1;
                        try {
                            v = Integer.parseInt(id);
                        } catch (Exception ignored) {
                        }
                        boolean date = (v >= 14 && v <= 22) || (v >= 45 && v <= 47) || customDates.contains(id);
                        dateStyles.add(date);
                    }
                } else if (ev == XmlPullParser.END_TAG && ln(p.getName()).equals("cellXfs")) {
                    inXfs = false;
                }
            }
        } catch (Exception ignored) {
        }
    }

    static boolean isDateFormat(String code) {
        String c = code.replaceAll("\"[^\"]*\"", "").replaceAll("\\[[^\\]]*\\]", "").toLowerCase();
        return (c.contains("d") || c.contains("y") || (c.contains("m") && !c.contains("0"))) && !c.contains("#");
    }

    private Doc.Table readSheet(byte[] xml) throws Exception {
        Doc.Table t = new Doc.Table();
        XmlPullParser p = Util.parser(xml);
        int ev;
        List<String> row = null;
        int expectedRow = 1;
        String cellType = null, cellRef = null;
        int cellStyle = 0;
        String value = null;
        StringBuilder inline = null;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = ln(p.getName());
                if (n.equals("row")) {
                    if (t.rows.size() >= MAX_ROWS) break;
                    int r = expectedRow;
                    try {
                        r = Integer.parseInt(attr(p, "r"));
                    } catch (Exception ignored) {
                    }
                    // linhas vazias intermediárias (limita saltos grandes)
                    int gap = Math.min(r - expectedRow, 50);
                    for (int i = 0; i < gap; i++) t.rows.add(new ArrayList<String>());
                    expectedRow = r + 1;
                    row = new ArrayList<String>();
                } else if (n.equals("c") && row != null) {
                    cellType = attr(p, "t");
                    cellRef = attr(p, "r");
                    String s = attr(p, "s");
                    cellStyle = 0;
                    if (s != null) try {
                        cellStyle = Integer.parseInt(s);
                    } catch (Exception ignored) {
                    }
                    value = null;
                    inline = null;
                } else if (n.equals("v") && row != null) {
                    value = p.nextText();
                } else if (n.equals("is")) {
                    inline = new StringBuilder();
                } else if (n.equals("t") && inline != null) {
                    inline.append(p.nextText());
                }
            } else if (ev == XmlPullParser.END_TAG) {
                String n = ln(p.getName());
                if (n.equals("c") && row != null) {
                    int col = cellRef != null ? colIndex(cellRef) : row.size();
                    if (col >= MAX_COLS) continue;
                    while (row.size() < col) row.add("");
                    String text = cellText(cellType, value, inline, cellStyle);
                    if (row.size() == col) row.add(text);
                    else row.set(col, text);
                } else if (n.equals("row") && row != null) {
                    t.rows.add(row);
                    row = null;
                }
            }
        }
        return t;
    }

    private String cellText(String type, String v, StringBuilder inline, int style) {
        if ("inlineStr".equals(type)) return inline == null ? "" : inline.toString();
        if (v == null) return "";
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(v.trim());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (Exception e) {
                return "";
            }
        }
        if ("b".equals(type)) return "1".equals(v) ? "VERDADEIRO" : "FALSO";
        if ("str".equals(type) || "e".equals(type)) return v;
        // número
        try {
            double d = Double.parseDouble(v);
            if (style < dateStyles.size() && dateStyles.get(style)) return excelDate(d);
            return formatNumber(d);
        } catch (Exception e) {
            return v;
        }
    }

    public static String formatNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        BigDecimal bd = new BigDecimal(d).round(new java.math.MathContext(12));
        String s = bd.stripTrailingZeros().toPlainString();
        return s;
    }

    static String excelDate(double serial) {
        long days = (long) Math.floor(serial);
        double frac = serial - days;
        // 1900 date system (com o bug do ano bissexto de 1900)
        long epochDay = days >= 61 ? days - 25569 : days - 25568; // 25569 = 1970-01-01
        int[] ymd = civil(epochDay);
        String date = pad(ymd[2]) + "/" + pad(ymd[1]) + "/" + ymd[0];
        if (frac > 1e-6) {
            long secs = Math.round(frac * 86400);
            date += " " + pad((int) (secs / 3600)) + ":" + pad((int) (secs / 60 % 60));
        }
        if (serial < 1 && serial > 0) {
            long secs = Math.round(serial * 86400);
            return pad((int) (secs / 3600)) + ":" + pad((int) (secs / 60 % 60));
        }
        return date;
    }

    private static String pad(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    /** Converte dias desde 1970-01-01 para {ano, mês, dia}. */
    static int[] civil(long z) {
        z += 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        return new int[]{(int) (m <= 2 ? y + 1 : y), (int) m, (int) d};
    }

    static int colIndex(String ref) {
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c >= 'A' && c <= 'Z') col = col * 26 + (c - 'A' + 1);
            else if (c >= 'a' && c <= 'z') col = col * 26 + (c - 'a' + 1);
            else break;
        }
        return col - 1;
    }
}
