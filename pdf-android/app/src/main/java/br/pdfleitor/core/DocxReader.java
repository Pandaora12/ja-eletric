package br.pdfleitor.core;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.pdfleitor.core.Util.attr;
import static br.pdfleitor.core.Util.ln;

/** Lê documentos Word (.docx). */
public class DocxReader {

    private Map<String, byte[]> zip;
    private Map<String, String> rels;
    /** styleId -> nível de título (1..6), 0 se não for título */
    private final Map<String, Integer> headingStyles = new HashMap<String, Integer>();
    /** styleId -> numId (listas definidas no estilo) */
    private final Map<String, String> styleNum = new HashMap<String, String>();
    /** numId -> true se for lista numerada */
    private final Map<String, Boolean> numbered = new HashMap<String, Boolean>();
    private final Map<String, Integer> listCounters = new HashMap<String, Integer>();

    public Doc read(byte[] data) throws Exception {
        zip = Util.unzip(data);
        String main = "word/document.xml";
        if (!zip.containsKey(main)) {
            for (String k : zip.keySet()) if (k.startsWith("word/document") && k.endsWith(".xml")) main = k;
        }
        byte[] xml = zip.get(main);
        if (xml == null) throw new Exception("Documento Word inválido");
        rels = Util.rels(zip, main);
        loadStyles();
        loadNumbering();
        Doc doc = new Doc();
        parseBody(Util.parser(xml), doc);
        return doc;
    }

    private void loadStyles() {
        byte[] s = zip.get("word/styles.xml");
        if (s == null) return;
        try {
            XmlPullParser p = Util.parser(s);
            int ev;
            String cur = null;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = ln(p.getName());
                if (n.equals("style")) {
                    cur = attr(p, "styleId");
                } else if (n.equals("name") && cur != null) {
                    String v = attr(p, "val");
                    int lvl = headingLevel(v);
                    if (lvl == 0) lvl = headingLevel(cur);
                    if (lvl > 0) headingStyles.put(cur, lvl);
                } else if (n.equals("numId") && cur != null) {
                    String v = attr(p, "val");
                    if (v != null) styleNum.put(cur, v);
                } else if (n.equals("outlineLvl") && cur != null) {
                    try {
                        int lvl = Integer.parseInt(attr(p, "val")) + 1;
                        if (lvl >= 1 && lvl <= 6 && !headingStyles.containsKey(cur)) headingStyles.put(cur, lvl);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    static int headingLevel(String name) {
        if (name == null) return 0;
        String n = name.toLowerCase().replace(" ", "");
        if (n.equals("title") || n.equals("título") || n.equals("titulo")) return 1;
        String[] prefixes = {"heading", "título", "titulo", "berschrift"};
        for (String pre : prefixes) {
            int i = n.indexOf(pre);
            if (i >= 0) {
                String rest = n.substring(i + pre.length());
                if (rest.length() > 0 && Character.isDigit(rest.charAt(0))) {
                    return Math.min(6, rest.charAt(0) - '0');
                }
            }
        }
        if (n.equals("subtitle") || n.equals("subtítulo")) return 2;
        return 0;
    }

    private void loadNumbering() {
        byte[] s = zip.get("word/numbering.xml");
        if (s == null) return;
        try {
            XmlPullParser p = Util.parser(s);
            Map<String, Boolean> abstractNumbered = new HashMap<String, Boolean>();
            Map<String, String> numToAbs = new HashMap<String, String>();
            String curAbs = null, curNum = null;
            boolean firstLevel = false;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = ln(p.getName());
                if (n.equals("abstractNum")) {
                    curAbs = attr(p, "abstractNumId");
                    curNum = null;
                } else if (n.equals("lvl")) {
                    firstLevel = "0".equals(attr(p, "ilvl"));
                } else if (n.equals("numFmt") && curAbs != null && firstLevel) {
                    String v = attr(p, "val");
                    abstractNumbered.put(curAbs, v != null && !v.equals("bullet") && !v.equals("none"));
                } else if (n.equals("num")) {
                    curNum = attr(p, "numId");
                    curAbs = null;
                } else if (n.equals("abstractNumId") && curNum != null) {
                    numToAbs.put(curNum, attr(p, "val"));
                }
            }
            for (Map.Entry<String, String> e : numToAbs.entrySet()) {
                Boolean b = abstractNumbered.get(e.getValue());
                numbered.put(e.getKey(), b != null && b);
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ corpo

    private void parseBody(XmlPullParser p, Doc doc) throws Exception {
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("p")) {
                parseParagraph(p, doc.blocks);
            } else if (n.equals("tbl")) {
                parseTable(p, doc.blocks);
            }
        }
        // título = primeiro título encontrado
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                String t = ((Doc.Heading) b).text().trim();
                if (t.length() > 0) {
                    doc.title = t.length() > 80 ? t.substring(0, 80) : t;
                    break;
                }
            }
        }
    }

    /** Lê um <w:p> e adiciona blocos (parágrafo, imagens, quebras) em out. */
    private void parseParagraph(XmlPullParser p, List<Doc.Block> out) throws Exception {
        int depth = p.getDepth();
        Doc.Para para = new Doc.Para();
        List<Doc.Block> after = new ArrayList<Doc.Block>();
        boolean pageBreakBefore = false;
        String styleId = null;
        String numId = null;
        int ilvl = 0;
        // estado do run atual
        Doc.Run style = null;
        boolean inRun = false, inRPr = false, inPPr = false;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                String n = ln(p.getName());
                if (n.equals("r")) inRun = false;
                else if (n.equals("rPr")) inRPr = false;
                else if (n.equals("pPr")) inPPr = false;
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("Fallback")) {
                skip(p);
            } else if (n.equals("pPr")) {
                inPPr = true;
            } else if (n.equals("pStyle") && inPPr) {
                styleId = attr(p, "val");
            } else if (n.equals("jc") && inPPr && !inRPr) {
                String v = attr(p, "val");
                if ("center".equals(v)) para.align = Doc.ALIGN_CENTER;
                else if ("right".equals(v) || "end".equals(v)) para.align = Doc.ALIGN_RIGHT;
                else if ("both".equals(v) || "distribute".equals(v)) para.align = Doc.ALIGN_JUSTIFY;
            } else if (n.equals("numId") && inPPr) {
                numId = attr(p, "val");
            } else if (n.equals("ilvl") && inPPr) {
                try {
                    ilvl = Integer.parseInt(attr(p, "val"));
                } catch (Exception ignored) {
                }
            } else if (n.equals("pageBreakBefore") && inPPr) {
                pageBreakBefore = !"0".equals(attr(p, "val")) && !"false".equals(attr(p, "val"));
            } else if (n.equals("r")) {
                inRun = true;
                style = new Doc.Run("");
            } else if (n.equals("rPr") && inRun) {
                inRPr = true;
            } else if (inRPr && style != null) {
                String v = attr(p, "val");
                boolean on = v == null || !(v.equals("0") || v.equals("false") || v.equals("none"));
                if (n.equals("b")) style.bold = on;
                else if (n.equals("i")) style.italic = on;
                else if (n.equals("u")) style.underline = on;
                else if (n.equals("sz")) {
                    try {
                        style.size = Integer.parseInt(v) / 2f;
                    } catch (Exception ignored) {
                    }
                } else if (n.equals("color")) {
                    if (v != null && !v.equals("auto")) style.color = Util.parseColor(v);
                }
            } else if (n.equals("t") && inRun) {
                String t = p.nextText();
                if (t != null && t.length() > 0) para.runs.add(style != null ? style.copyStyle(t) : new Doc.Run(t));
            } else if (n.equals("tab") && inRun) {
                para.runs.add(style != null ? style.copyStyle("\t") : new Doc.Run("\t"));
            } else if ((n.equals("br") || n.equals("cr")) && inRun) {
                String type = attr(p, "type");
                if ("page".equals(type)) {
                    after.add(new Doc.PageBreak());
                } else {
                    para.runs.add(style != null ? style.copyStyle("\n") : new Doc.Run("\n"));
                }
            } else if (n.equals("blip")) {
                String rid = Util.attrQ(p, "r:embed");
                if (rid == null) rid = Util.attrQ(p, "r:link");
                Doc.Image img = loadImage(rid);
                if (img != null) after.add(img);
            } else if (n.equals("imagedata")) { // VML (documentos antigos)
                Doc.Image img = loadImage(Util.attrQ(p, "r:id"));
                if (img != null) after.add(img);
            } else if (n.equals("extent")) {
                // dimensão exibida (EMU) da próxima imagem: aplicada depois
                try {
                    long cx = Long.parseLong(attr(p, "cx"));
                    pendingWidthPt = cx / 12700f;
                } catch (Exception ignored) {
                }
            } else if (n.equals("tbl")) {
                parseTable(p, after);
            } else if (n.equals("txbxContent")) {
                // caixas de texto: lidas como parágrafos normais
                int d2 = p.getDepth();
                while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.END_TAG && p.getDepth() == d2) break;
                    if (ev == XmlPullParser.START_TAG && ln(p.getName()).equals("p")) parseParagraph(p, after);
                }
            } else if (n.equals("instrText") || n.equals("delText")) {
                p.nextText(); // ignora códigos de campo e texto excluído
            }
        }

        if (pageBreakBefore) out.add(new Doc.PageBreak());
        int hl = styleId != null && headingStyles.containsKey(styleId) ? headingStyles.get(styleId) : headingLevel(styleId);
        Doc.Para result = para;
        if (numId == null && styleId != null && hl == 0) {
            numId = styleNum.get(styleId);
            if (numId == null) {
                String sl = styleId.toLowerCase();
                if (sl.startsWith("listbullet") || sl.startsWith("listadecommarcadores")) numId = "_bullet";
                else if (sl.startsWith("listnumber") || sl.startsWith("listanumerada")) numId = "_number";
            }
        }
        if (hl > 0) {
            Doc.Heading h = new Doc.Heading(hl);
            h.runs.addAll(para.runs);
            h.align = para.align;
            result = h;
        } else if (numId != null && !numId.equals("0")) {
            boolean isNum = numId.equals("_number") || (numbered.containsKey(numId) && numbered.get(numId));
            para.list = isNum ? Doc.LIST_NUMBER : Doc.LIST_BULLET;
            para.listLevel = ilvl;
            String key = numId + ":" + ilvl;
            Integer c = listCounters.get(key);
            c = c == null ? 1 : c + 1;
            listCounters.put(key, c);
            // reinicia níveis mais profundos
            for (int l = ilvl + 1; l < 9; l++) listCounters.remove(numId + ":" + l);
            para.listIndex = c;
        }
        // mescla runs adjacentes com o mesmo estilo
        mergeRuns(result);
        if (!result.runs.isEmpty() || after.isEmpty()) out.add(result);
        out.addAll(after);
    }

    private float pendingWidthPt;

    static void skip(XmlPullParser p) throws Exception {
        int d = p.getDepth();
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == d) return;
        }
    }

    static void mergeRuns(Doc.Para p) {
        List<Doc.Run> merged = new ArrayList<Doc.Run>();
        for (Doc.Run r : p.runs) {
            if (!merged.isEmpty()) {
                Doc.Run last = merged.get(merged.size() - 1);
                if (last.bold == r.bold && last.italic == r.italic && last.underline == r.underline
                        && last.size == r.size && last.color == r.color) {
                    last.text = last.text + r.text;
                    continue;
                }
            }
            merged.add(r);
        }
        p.runs.clear();
        p.runs.addAll(merged);
    }

    private Doc.Image loadImage(String rid) {
        if (rid == null) return null;
        String path = rels.get(rid);
        if (path == null) return null;
        byte[] data = zip.get(path);
        Doc.Image img = Util.image(data);
        if (img != null && pendingWidthPt > 0) img.widthPt = pendingWidthPt;
        pendingWidthPt = 0;
        return img;
    }

    private void parseTable(XmlPullParser p, List<Doc.Block> out) throws Exception {
        int depth = p.getDepth();
        Doc.Table t = new Doc.Table();
        List<String> row = null;
        StringBuilder cell = null;
        int cellParas = 0;
        List<Doc.Block> extra = new ArrayList<Doc.Block>();
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                String n = ln(p.getName());
                if (n.equals("tc") && row != null && cell != null) {
                    row.add(cell.toString().trim());
                    cell = null;
                } else if (n.equals("tr") && row != null) {
                    t.rows.add(row);
                    row = null;
                }
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("tr")) {
                row = new ArrayList<String>();
            } else if (n.equals("tc")) {
                cell = new StringBuilder();
                cellParas = 0;
            } else if (n.equals("gridSpan") && row != null) {
                // células mescladas horizontalmente: preenche colunas extras vazias
                try {
                    int span = Integer.parseInt(attr(p, "val"));
                    for (int i = 1; i < span; i++) row.add("");
                } catch (Exception ignored) {
                }
            } else if (n.equals("tblHeader")) {
                t.header = true;
            } else if (n.equals("p") && cell != null) {
                List<Doc.Block> tmp = new ArrayList<Doc.Block>();
                parseParagraph(p, tmp);
                for (Doc.Block b : tmp) {
                    if (b instanceof Doc.Para) {
                        String s = ((Doc.Para) b).text();
                        if (s.trim().length() == 0) continue;
                        if (cellParas++ > 0) cell.append('\n');
                        cell.append(s);
                    } else if (b instanceof Doc.Table) {
                        for (List<String> r : ((Doc.Table) b).rows) {
                            if (cellParas++ > 0) cell.append('\n');
                            cell.append(join(r, " | "));
                        }
                    } else if (b instanceof Doc.Image) {
                        extra.add(b);
                    }
                }
            } else if (n.equals("tbl") && cell != null) {
                // tabela aninhada: achatada no texto da célula
                List<Doc.Block> tmp = new ArrayList<Doc.Block>();
                parseTable(p, tmp);
                for (Doc.Block b : tmp) {
                    if (b instanceof Doc.Table) {
                        for (List<String> r : ((Doc.Table) b).rows) {
                            if (cellParas++ > 0) cell.append('\n');
                            cell.append(join(r, " | "));
                        }
                    }
                }
            }
        }
        // primeira linha em negrito costuma ser cabeçalho; mantemos a marcação explícita
        if (!t.rows.isEmpty()) out.add(t);
        out.addAll(extra);
    }

    static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(l.get(i));
        }
        return sb.toString();
    }
}
