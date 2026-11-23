package br.pdfleitor.core;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.pdfleitor.core.Util.attr;
import static br.pdfleitor.core.Util.attrQ;
import static br.pdfleitor.core.Util.ln;

/** Lê documentos OpenDocument (.odt, .ods, .odp). */
public class OdfReader {

    private Map<String, byte[]> zip;
    private final Map<String, Doc.Run> textStyles = new HashMap<String, Doc.Run>();
    private final Map<String, Integer> paraAlign = new HashMap<String, Integer>();
    private final Map<String, Integer> drawFill = new HashMap<String, Integer>();

    public Doc read(byte[] data) throws Exception {
        zip = Util.unzip(data);
        byte[] content = zip.get("content.xml");
        if (content == null) throw new Exception("Documento OpenDocument inválido");
        byte[] styles = zip.get("styles.xml");
        if (styles != null) loadStyles(styles);
        loadStyles(content);
        Doc doc = new Doc();
        XmlPullParser p = Util.parser(content);
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev != XmlPullParser.START_TAG) continue;
            String n = p.getName();
            if (n.equals("office:text")) {
                readText(p, doc.blocks, 0);
            } else if (n.equals("office:spreadsheet")) {
                readSpreadsheet(p, doc);
            } else if (n.equals("office:presentation") || n.equals("office:drawing")) {
                readPresentation(p, doc);
            }
        }
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                doc.title = ((Doc.Heading) b).text().trim();
                if (doc.title.length() > 80) doc.title = doc.title.substring(0, 80);
                break;
            }
        }
        if (doc.title.isEmpty()) doc.title = "Documento";
        return doc;
    }

    private void loadStyles(byte[] xml) {
        try {
            XmlPullParser p = Util.parser(xml);
            int ev;
            String cur = null;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev != XmlPullParser.START_TAG) continue;
                String n = p.getName();
                if (n.equals("style:style")) {
                    cur = attr(p, "name");
                } else if (n.equals("style:text-properties") && cur != null) {
                    Doc.Run r = new Doc.Run("");
                    r.bold = "bold".equals(attrQ(p, "fo:font-weight"));
                    r.italic = "italic".equals(attrQ(p, "fo:font-style"));
                    String u = attrQ(p, "style:text-underline-style");
                    r.underline = u != null && !u.equals("none");
                    String sz = attrQ(p, "fo:font-size");
                    if (sz != null && sz.endsWith("pt")) try {
                        r.size = Float.parseFloat(sz.substring(0, sz.length() - 2));
                    } catch (Exception ignored) {
                    }
                    String color = attrQ(p, "fo:color");
                    if (color != null && !color.equalsIgnoreCase("#000000")) r.color = Util.parseColor(color);
                    textStyles.put(cur, r);
                } else if (n.equals("style:paragraph-properties") && cur != null) {
                    String a = attrQ(p, "fo:text-align");
                    if ("center".equals(a)) paraAlign.put(cur, Doc.ALIGN_CENTER);
                    else if ("end".equals(a) || "right".equals(a)) paraAlign.put(cur, Doc.ALIGN_RIGHT);
                    else if ("justify".equals(a)) paraAlign.put(cur, Doc.ALIGN_JUSTIFY);
                } else if (n.equals("style:graphic-properties") && cur != null) {
                    if ("solid".equals(attrQ(p, "draw:fill"))) {
                        int c = Util.parseColor(attrQ(p, "draw:fill-color"));
                        if (c != 0) drawFill.put(cur, c);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ texto

    /** Lê o conteúdo de um contêiner de texto até o fim do elemento atual. */
    private void readText(XmlPullParser p, List<Doc.Block> out, int listDepth) throws Exception {
        int depth = p.getDepth();
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) return;
            if (ev != XmlPullParser.START_TAG) continue;
            String n = p.getName();
            if (n.equals("text:h")) {
                int lvl = 1;
                try {
                    lvl = Integer.parseInt(attrQ(p, "text:outline-level"));
                } catch (Exception ignored) {
                }
                Doc.Heading h = new Doc.Heading(lvl);
                List<Doc.Block> extra = new ArrayList<Doc.Block>();
                readInline(p, h, null, extra);
                out.add(h);
                out.addAll(extra);
            } else if (n.equals("text:p")) {
                Doc.Para para = new Doc.Para();
                String st = attrQ(p, "text:style-name");
                if (st != null && paraAlign.containsKey(st)) para.align = paraAlign.get(st);
                Doc.Run base = st != null ? textStyles.get(st) : null;
                List<Doc.Block> extra = new ArrayList<Doc.Block>();
                readInline(p, para, base, extra);
                if (listDepth > 0) {
                    para.list = Doc.LIST_BULLET;
                    para.listLevel = listDepth - 1;
                }
                if (!para.runs.isEmpty() || extra.isEmpty()) out.add(para);
                out.addAll(extra);
            } else if (n.equals("text:list")) {
                readText(p, out, listDepth + 1);
            } else if (n.equals("table:table")) {
                Doc.Table t = readTable(p, 500);
                t.trim();
                if (!t.rows.isEmpty()) out.add(t);
            } else if (n.equals("draw:frame")) {
                readFrameInto(p, out);
            } else if (n.equals("text:soft-page-break")) {
                // ignorado
            } else if (n.equals("office:annotation") || n.equals("text:tracked-changes")) {
                DocxReader.skip(p);
            }
        }
    }

    /** Lê spans/textos de um parágrafo. */
    private void readInline(XmlPullParser p, Doc.Para para, Doc.Run base, List<Doc.Block> extra) throws Exception {
        int depth = p.getDepth();
        List<Doc.Run> stack = new ArrayList<Doc.Run>();
        stack.add(base != null ? base : new Doc.Run(""));
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                if (p.getName().equals("text:span") && stack.size() > 1) stack.remove(stack.size() - 1);
                continue;
            }
            Doc.Run style = stack.get(stack.size() - 1);
            if (ev == XmlPullParser.TEXT) {
                String t = p.getText();
                if (t != null && t.length() > 0) para.runs.add(style.copyStyle(t.replace('\n', ' ')));
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = p.getName();
            if (n.equals("text:span")) {
                Doc.Run s = textStyles.get(attrQ(p, "text:style-name"));
                if (s != null) {
                    Doc.Run merged = style.copyStyle("");
                    merged.bold |= s.bold;
                    merged.italic |= s.italic;
                    merged.underline |= s.underline;
                    if (s.size > 0) merged.size = s.size;
                    if (s.color != 0) merged.color = s.color;
                    stack.add(merged);
                } else stack.add(style);
            } else if (n.equals("text:s")) {
                int c = 1;
                try {
                    c = Integer.parseInt(attrQ(p, "text:c"));
                } catch (Exception ignored) {
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(c, 200); i++) sb.append(' ');
                para.runs.add(style.copyStyle(sb.toString()));
            } else if (n.equals("text:tab")) {
                para.runs.add(style.copyStyle("\t"));
            } else if (n.equals("text:line-break")) {
                para.runs.add(style.copyStyle("\n"));
            } else if (n.equals("draw:frame")) {
                readFrameInto(p, extra);
            } else if (n.equals("text:note") || n.equals("office:annotation")) {
                DocxReader.skip(p);
            }
        }
        DocxReader.mergeRuns(para);
    }

    private void readFrameInto(XmlPullParser p, List<Doc.Block> out) throws Exception {
        int depth = p.getDepth();
        float widthPt = toPt(attrQ(p, "svg:width"));
        int ev;
        boolean gotImage = false;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) return;
            if (ev != XmlPullParser.START_TAG) continue;
            String n = p.getName();
            if (n.equals("draw:image") && !gotImage) {
                String href = attrQ(p, "xlink:href");
                if (href != null) {
                    if (href.startsWith("./")) href = href.substring(2);
                    Doc.Image img = Util.image(zip.get(href));
                    if (img != null) {
                        img.widthPt = widthPt;
                        out.add(img);
                        gotImage = true;
                    }
                }
            } else if (n.equals("draw:text-box")) {
                readText(p, out, 0);
            }
        }
    }

    private Doc.Table readTable(XmlPullParser p, int maxCols) throws Exception {
        int depth = p.getDepth();
        Doc.Table t = new Doc.Table();
        t.name = attrQ(p, "table:name");
        List<String> row = null;
        int rowRepeat = 1;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                if (p.getName().equals("table:table-row") && row != null) {
                    boolean empty = true;
                    for (String s : row) if (s.length() > 0) empty = false;
                    int reps = empty ? Math.min(rowRepeat, 3) : Math.min(rowRepeat, 1000);
                    for (int i = 0; i < reps && t.rows.size() < XlsxReader.MAX_ROWS; i++)
                        t.rows.add(new ArrayList<String>(row));
                    row = null;
                }
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = p.getName();
            if (n.equals("table:table-row")) {
                row = new ArrayList<String>();
                rowRepeat = 1;
                try {
                    rowRepeat = Integer.parseInt(attrQ(p, "table:number-rows-repeated"));
                } catch (Exception ignored) {
                }
            } else if ((n.equals("table:table-cell") || n.equals("table:covered-table-cell")) && row != null) {
                int rep = 1;
                try {
                    rep = Integer.parseInt(attrQ(p, "table:number-columns-repeated"));
                } catch (Exception ignored) {
                }
                String text = cellText(p);
                for (int i = 0; i < rep && row.size() < maxCols; i++) row.add(text);
            } else if (n.equals("table:table")) {
                DocxReader.skip(p); // tabelas aninhadas ignoradas
            }
        }
        return t;
    }

    private String cellText(XmlPullParser p) throws Exception {
        int depth = p.getDepth();
        StringBuilder sb = new StringBuilder();
        int paras = 0;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev == XmlPullParser.START_TAG) {
                String n = p.getName();
                if (n.equals("text:p") && paras++ > 0) sb.append('\n');
                else if (n.equals("text:s")) sb.append(' ');
                else if (n.equals("office:annotation")) DocxReader.skip(p);
            } else if (ev == XmlPullParser.TEXT) {
                sb.append(p.getText());
            }
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ planilha

    private void readSpreadsheet(XmlPullParser p, Doc doc) throws Exception {
        int depth = p.getDepth();
        int ev;
        List<Doc.Table> tables = new ArrayList<Doc.Table>();
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev == XmlPullParser.START_TAG && p.getName().equals("table:table")) {
                Doc.Table t = readTable(p, XlsxReader.MAX_COLS);
                t.trim();
                tables.add(t);
            }
        }
        doc.title = "Planilha";
        for (Doc.Table t : tables) {
            if (tables.size() > 1 || t.rows.isEmpty()) doc.add(new Doc.Heading(2, t.name == null ? "Planilha" : t.name));
            if (t.rows.isEmpty()) doc.add(new Doc.Para("(aba vazia)"));
            else {
                t.header = true;
                doc.add(t);
                if (t.columnCount() > 6) doc.landscape = true;
            }
        }
    }

    // ------------------------------------------------------------------ apresentação

    private void readPresentation(XmlPullParser p, Doc doc) throws Exception {
        int depth = p.getDepth();
        doc.slides = new ArrayList<Doc.Slide>();
        doc.landscape = true;
        long pageW = 10080000L, pageH = 5670000L; // 28 x 15,75 cm
        int ev;
        int n = 0;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev != XmlPullParser.START_TAG || !p.getName().equals("draw:page")) continue;
            n++;
            if (n > 1) doc.add(new Doc.PageBreak());
            Doc.Slide slide = new Doc.Slide();
            slide.cx = pageW;
            slide.cy = pageH;
            int pd = p.getDepth();
            boolean titled = false;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.END_TAG && p.getDepth() == pd) break;
                if (ev != XmlPullParser.START_TAG) continue;
                String name = p.getName();
                if (name.equals("presentation:notes")) {
                    DocxReader.skip(p);
                    continue;
                }
                if (!(name.equals("draw:frame") || name.equals("draw:custom-shape") || name.equals("draw:rect")
                        || name.equals("draw:ellipse"))) continue;
                Doc.Shape s = new Doc.Shape();
                s.x = toEmu(attrQ(p, "svg:x"));
                s.y = toEmu(attrQ(p, "svg:y"));
                s.w = toEmu(attrQ(p, "svg:width"));
                s.h = toEmu(attrQ(p, "svg:height"));
                s.ellipse = name.equals("draw:ellipse");
                String cls = attrQ(p, "presentation:class");
                s.isTitle = "title".equals(cls);
                String gs = attrQ(p, "draw:style-name");
                if (gs != null && drawFill.containsKey(gs)) s.fill = drawFill.get(gs);
                List<Doc.Block> inner = new ArrayList<Doc.Block>();
                if (name.equals("draw:frame")) readFrameInto(p, inner);
                else readText(p, inner, 0);
                for (Doc.Block b : inner) {
                    if (b instanceof Doc.Image && s.image == null) {
                        s.kind = Doc.Shape.PICTURE;
                        s.image = (Doc.Image) b;
                    } else if (b instanceof Doc.Para) {
                        Doc.Para pa = (Doc.Para) b;
                        for (Doc.Run r : pa.runs) if (r.size == 0) r.size = s.isTitle ? 36 : 20;
                        s.paras.add(pa);
                    } else if (b instanceof Doc.Table) {
                        s.kind = Doc.Shape.TABLE;
                        s.table = (Doc.Table) b;
                    }
                }
                if (s.kind == Doc.Shape.PICTURE || s.kind == Doc.Shape.TABLE || !s.paras.isEmpty() || s.fill != 0)
                    slide.shapes.add(s);
                // blocos
                if (s.isTitle && !titled) {
                    StringBuilder sb = new StringBuilder();
                    for (Doc.Para pa : s.paras) sb.append(pa.text()).append(' ');
                    if (sb.toString().trim().length() > 0) {
                        doc.add(new Doc.Heading(2, sb.toString().trim()));
                        titled = true;
                    }
                } else {
                    for (Doc.Block b : inner) {
                        if (b instanceof Doc.Para) {
                            Doc.Para src = (Doc.Para) b;
                            if (src.isBlank()) continue;
                            Doc.Para copy = new Doc.Para();
                            copy.list = src.list;
                            for (Doc.Run r : src.runs) {
                                Doc.Run c = r.copyStyle(r.text);
                                c.size = 0;
                                copy.runs.add(c);
                            }
                            doc.add(copy);
                        } else doc.add(b);
                    }
                }
            }
            doc.slides.add(slide);
        }
    }

    // ------------------------------------------------------------------ unidades

    static float toPt(String v) {
        if (v == null) return 0;
        try {
            v = v.trim();
            if (v.endsWith("cm")) return Float.parseFloat(v.substring(0, v.length() - 2)) * 72f / 2.54f;
            if (v.endsWith("mm")) return Float.parseFloat(v.substring(0, v.length() - 2)) * 72f / 25.4f;
            if (v.endsWith("in")) return Float.parseFloat(v.substring(0, v.length() - 2)) * 72f;
            if (v.endsWith("pt")) return Float.parseFloat(v.substring(0, v.length() - 2));
            if (v.endsWith("px")) return Float.parseFloat(v.substring(0, v.length() - 2)) * 0.75f;
            return Float.parseFloat(v);
        } catch (Exception e) {
            return 0;
        }
    }

    static long toEmu(String v) {
        return (long) (toPt(v) * 12700);
    }
}
