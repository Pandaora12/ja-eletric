package br.pdfleitor.core;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static br.pdfleitor.core.Util.attr;
import static br.pdfleitor.core.Util.ln;

/** Lê apresentações PowerPoint (.pptx), preservando a posição dos elementos. */
public class PptxReader {

    private Map<String, byte[]> zip;
    private final Map<String, Integer> theme = new HashMap<String, Integer>();
    private long slideCx = 12192000L, slideCy = 6858000L;

    /** Posições herdadas de placeholders (layout/mestre). */
    private static class PhInfo {
        long x, y, w, h;
        boolean has;
    }

    public Doc read(byte[] data) throws Exception {
        zip = Util.unzip(data);
        String pres = "ppt/presentation.xml";
        byte[] presXml = zip.get(pres);
        if (presXml == null) throw new Exception("Apresentação PowerPoint inválida");
        loadTheme();
        Map<String, String> presRels = Util.rels(zip, pres);
        List<String> slidePaths = new ArrayList<String>();
        XmlPullParser p = Util.parser(presXml);
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("sldSz")) {
                try {
                    slideCx = pl(attr(p, "cx"));
                    slideCy = pl(attr(p, "cy"));
                } catch (Exception ignored) {
                }
            } else if (n.equals("sldId")) {
                String path = presRels.get(Util.attrQ(p, "r:id"));
                if (path != null && zip.containsKey(path)) slidePaths.add(path);
            }
        }
        if (slidePaths.isEmpty()) {
            // fallback: ordena slides pelo número
            for (int i = 1; i < 2000; i++) {
                String s = "ppt/slides/slide" + i + ".xml";
                if (zip.containsKey(s)) slidePaths.add(s);
            }
        }
        Doc doc = new Doc();
        doc.slides = new ArrayList<Doc.Slide>();
        doc.landscape = slideCx >= slideCy;
        for (String path : slidePaths) {
            Doc.Slide slide = readSlide(path);
            if (slide != null) doc.slides.add(slide);
        }
        buildBlocks(doc);
        return doc;
    }

    private void loadTheme() {
        // cores padrão do Office
        theme.put("dk1", 0xFF000000);
        theme.put("lt1", 0xFFFFFFFF);
        theme.put("dk2", 0xFF44546A);
        theme.put("lt2", 0xFFE7E6E6);
        theme.put("accent1", 0xFF4472C4);
        theme.put("accent2", 0xFFED7D31);
        theme.put("accent3", 0xFFA5A5A5);
        theme.put("accent4", 0xFFFFC000);
        theme.put("accent5", 0xFF5B9BD5);
        theme.put("accent6", 0xFF70AD47);
        theme.put("hlink", 0xFF0563C1);
        byte[] t = null;
        for (String k : zip.keySet()) {
            if (k.startsWith("ppt/theme/theme") && k.endsWith(".xml")) {
                t = zip.get(k);
                break;
            }
        }
        if (t == null) return;
        try {
            XmlPullParser p = Util.parser(t);
            int ev;
            String cur = null;
            boolean inScheme = false;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.END_TAG && ln(p.getName()).equals("clrScheme")) break;
                if (ev != XmlPullParser.START_TAG) continue;
                String n = ln(p.getName());
                if (n.equals("clrScheme")) inScheme = true;
                else if (inScheme && (n.equals("srgbClr") || n.equals("sysClr")) && cur != null) {
                    String v = n.equals("srgbClr") ? attr(p, "val") : attr(p, "lastClr");
                    int c = Util.parseColor(v);
                    if (c != 0) theme.put(cur, c);
                } else if (inScheme) cur = n;
            }
        } catch (Exception ignored) {
        }
    }

    static long pl(String v) {
        if (v == null) return 0;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int schemeColor(String v) {
        if (v == null) return 0;
        if (v.equals("tx1")) v = "dk1";
        else if (v.equals("bg1")) v = "lt1";
        else if (v.equals("tx2")) v = "dk2";
        else if (v.equals("bg2")) v = "lt2";
        Integer c = theme.get(v);
        return c == null ? 0 : c;
    }

    // ------------------------------------------------------------------ slide

    private static class Xf {
        // transformação de grupo: x' = ox + (x - chx) * sx
        double ox, oy, chx, chy, sx = 1, sy = 1;

        long tx(long x) {
            return (long) (ox + (x - chx) * sx);
        }

        long ty(long y) {
            return (long) (oy + (y - chy) * sy);
        }
    }

    private Doc.Slide readSlide(String path) throws Exception {
        byte[] xml = zip.get(path);
        Map<String, String> rels = Util.rels(zip, path);
        // layout e mestre para herança de posições
        Map<String, PhInfo> layoutPh = new HashMap<String, PhInfo>();
        Map<String, PhInfo> masterPh = new HashMap<String, PhInfo>();
        int[] bg = {0};
        String layoutPath = null;
        for (String target : rels.values()) if (target.contains("slideLayouts/")) layoutPath = target;
        if (layoutPath != null && zip.containsKey(layoutPath)) {
            scanPlaceholders(zip.get(layoutPath), layoutPh, bg);
            for (String target : Util.rels(zip, layoutPath).values()) {
                if (target.contains("slideMasters/") && zip.containsKey(target)) {
                    int[] mbg = {0};
                    scanPlaceholders(zip.get(target), masterPh, mbg);
                    if (bg[0] == 0) bg[0] = mbg[0];
                }
            }
        }

        Doc.Slide slide = new Doc.Slide();
        slide.cx = slideCx;
        slide.cy = slideCy;
        if (bg[0] != 0) slide.background = bg[0];

        XmlPullParser p = Util.parser(xml);
        int ev;
        List<Xf> stack = new ArrayList<Xf>();
        stack.add(new Xf());
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String n = ln(p.getName());
                if (n.equals("sld")) {
                    if ("0".equals(attr(p, "show"))) return null; // slide oculto
                } else if (n.equals("bg")) {
                    int c = readFill(p);
                    if (c != 0) slide.background = c;
                } else if (n.equals("grpSp")) {
                    stack.add(readGroupXf(p, stack.get(stack.size() - 1)));
                } else if (n.equals("sp") || n.equals("cxnSp")) {
                    Doc.Shape s = readShape(p, rels, layoutPh, masterPh, stack.get(stack.size() - 1));
                    if (s != null) slide.shapes.add(s);
                } else if (n.equals("pic")) {
                    Doc.Shape s = readPicture(p, rels, stack.get(stack.size() - 1));
                    if (s != null) slide.shapes.add(s);
                } else if (n.equals("graphicFrame")) {
                    Doc.Shape s = readFrame(p, stack.get(stack.size() - 1));
                    if (s != null) slide.shapes.add(s);
                } else if (n.equals("Fallback")) {
                    DocxReader.skip(p);
                }
            } else if (ev == XmlPullParser.END_TAG && ln(p.getName()).equals("grpSp")) {
                if (stack.size() > 1) stack.remove(stack.size() - 1);
            }
        }
        return slide;
    }

    /** Lê o grpSpPr do grupo atual (sem consumir os filhos do grupo). */
    private Xf readGroupXf(XmlPullParser p, Xf parent) throws Exception {
        Xf xf = new Xf();
        xf.ox = parent.ox;
        xf.oy = parent.oy;
        xf.chx = parent.chx;
        xf.chy = parent.chy;
        xf.sx = parent.sx;
        xf.sy = parent.sy;
        int depth = p.getDepth();
        long offX = 0, offY = 0, extX = 0, extY = 0, chX = 0, chY = 0, chW = 0, chH = 0;
        boolean got = false;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && ln(p.getName()).equals("grpSpPr") && p.getDepth() == depth + 1) break;
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            try {
                if (n.equals("off")) {
                    offX = pl(attr(p, "x"));
                    offY = pl(attr(p, "y"));
                    got = true;
                } else if (n.equals("ext")) {
                    extX = pl(attr(p, "cx"));
                    extY = pl(attr(p, "cy"));
                } else if (n.equals("chOff")) {
                    chX = pl(attr(p, "x"));
                    chY = pl(attr(p, "y"));
                } else if (n.equals("chExt")) {
                    chW = pl(attr(p, "cx"));
                    chH = pl(attr(p, "cy"));
                }
            } catch (Exception ignored) {
            }
        }
        if (got) {
            double sx = chW > 0 ? (double) extX / chW : 1;
            double sy = chH > 0 ? (double) extY / chH : 1;
            // compõe com o pai
            xf.ox = parent.tx(offX);
            xf.oy = parent.ty(offY);
            xf.chx = chX;
            xf.chy = chY;
            xf.sx = parent.sx * sx;
            xf.sy = parent.sy * sy;
        }
        return xf;
    }

    /** Extrai as posições dos placeholders de um layout/mestre. */
    private void scanPlaceholders(byte[] xml, Map<String, PhInfo> out, int[] bg) {
        try {
            XmlPullParser p = Util.parser(xml);
            int ev;
            String key1 = null, key2 = null;
            PhInfo cur = null;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String n = ln(p.getName());
                    if (n.equals("bg")) {
                        int c = readFill(p);
                        if (c != 0) bg[0] = c;
                    } else if (n.equals("sp")) {
                        cur = new PhInfo();
                        key1 = key2 = null;
                    } else if (n.equals("ph") && cur != null) {
                        String type = attr(p, "type");
                        String idx = attr(p, "idx");
                        key1 = "type:" + (type == null ? "body" : type);
                        key2 = idx != null ? "idx:" + idx : null;
                    } else if (n.equals("off") && cur != null && !cur.has) {
                        try {
                            cur.x = pl(attr(p, "x"));
                            cur.y = pl(attr(p, "y"));
                            cur.has = true;
                        } catch (Exception ignored) {
                        }
                    } else if (n.equals("ext") && cur != null && cur.w == 0) {
                        try {
                            cur.w = pl(attr(p, "cx"));
                            cur.h = pl(attr(p, "cy"));
                        } catch (Exception ignored) {
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && ln(p.getName()).equals("sp") && cur != null) {
                    if (cur.has) {
                        if (key1 != null && !out.containsKey(key1)) out.put(key1, cur);
                        if (key2 != null) out.put(key2, cur);
                    }
                    cur = null;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Lê um elemento de fundo (<p:bg>) e devolve a cor sólida, se houver. */
    private int readFill(XmlPullParser p) throws Exception {
        int depth = p.getDepth();
        int color = 0;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev != XmlPullParser.START_TAG || color != 0) continue;
            String n = ln(p.getName());
            if (n.equals("srgbClr")) color = Util.parseColor(attr(p, "val"));
            else if (n.equals("schemeClr")) color = schemeColor(attr(p, "val"));
        }
        return color;
    }

    private Doc.Shape readShape(XmlPullParser p, Map<String, String> rels, Map<String, PhInfo> layoutPh,
                                Map<String, PhInfo> masterPh, Xf xf) throws Exception {
        int depth = p.getDepth();
        Doc.Shape s = new Doc.Shape();
        boolean hasXfrm = false;
        String phType = null, phIdx = null;
        boolean isPh = false;
        // contexto
        boolean inSpPr = false, inLn = false, inTx = false, inRPr = false, inPPr = false, inBodyPr = false;
        boolean inStyle = false, inSolid = false;
        java.util.Set<Doc.Para> noBullet = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Doc.Para, Boolean>());
        Doc.Para para = null;
        Doc.Run run = null;
        boolean buNone = false, buChar = false, buNum = false;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                String n = ln(p.getName());
                if (n.equals("spPr")) inSpPr = false;
                else if (n.equals("ln")) inLn = false;
                else if (n.equals("txBody")) inTx = false;
                else if (n.equals("rPr") || n.equals("endParaRPr") || n.equals("defRPr")) inRPr = false;
                else if (n.equals("pPr")) inPPr = false;
                else if (n.equals("style")) inStyle = false;
                else if (n.equals("solidFill")) inSolid = false;
                else if (n.equals("p") && para != null) {
                    if (buNone) {
                        para.list = Doc.LIST_NONE;
                        noBullet.add(para);
                    }
                    else if (buNum) para.list = Doc.LIST_NUMBER;
                    else if (buChar) para.list = Doc.LIST_BULLET;
                    DocxReader.mergeRuns(para);
                    s.paras.add(para);
                    para = null;
                } else if (n.equals("r") || n.equals("fld")) run = null;
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("ph")) {
                isPh = true;
                phType = attr(p, "type");
                phIdx = attr(p, "idx");
            } else if (n.equals("spPr")) {
                inSpPr = true;
            } else if (n.equals("style")) {
                inStyle = true;
            } else if (inSpPr && n.equals("off") && !hasXfrm) {
                s.x = pl(attr(p, "x"));
                s.y = pl(attr(p, "y"));
                hasXfrm = true;
            } else if (inSpPr && n.equals("ext") && s.w == 0 && attr(p, "cx") != null) {
                try {
                    s.w = pl(attr(p, "cx"));
                    s.h = pl(attr(p, "cy"));
                } catch (Exception ignored) {
                }
            } else if (inSpPr && n.equals("prstGeom")) {
                s.ellipse = "ellipse".equals(attr(p, "prst"));
            } else if (inSpPr && n.equals("ln")) {
                inLn = true;
            } else if (n.equals("solidFill")) {
                inSolid = true;
            } else if (inSpPr && inSolid && (n.equals("srgbClr") || n.equals("schemeClr")) && !inTx) {
                int c = n.equals("srgbClr") ? Util.parseColor(attr(p, "val")) : schemeColor(attr(p, "val"));
                if (inLn) s.line = c;
                else if (s.fill == 0) s.fill = c;
            } else if (inSpPr && n.equals("noFill") && !inLn) {
                s.fill = 0;
            } else if (n.equals("txBody")) {
                inTx = true;
            } else if (inTx && n.equals("bodyPr")) {
                String a = attr(p, "anchor");
                if ("ctr".equals(a)) s.anchor = 1;
                else if ("b".equals(a)) s.anchor = 2;
            } else if (inTx && n.equals("p")) {
                para = new Doc.Para();
                buNone = buChar = buNum = false;
            } else if (inTx && n.equals("pPr") && para != null) {
                inPPr = true;
                String algn = attr(p, "algn");
                if ("ctr".equals(algn)) para.align = Doc.ALIGN_CENTER;
                else if ("r".equals(algn)) para.align = Doc.ALIGN_RIGHT;
                else if ("just".equals(algn)) para.align = Doc.ALIGN_JUSTIFY;
                String lvl = attr(p, "lvl");
                if (lvl != null) try {
                    para.listLevel = Integer.parseInt(lvl);
                } catch (Exception ignored) {
                }
            } else if (inPPr && n.equals("buNone")) {
                buNone = true;
            } else if (inPPr && n.equals("buChar")) {
                buChar = true;
            } else if (inPPr && n.equals("buAutoNum")) {
                buNum = true;
            } else if (inTx && (n.equals("r") || n.equals("fld")) && para != null) {
                run = new Doc.Run("");
            } else if (inTx && n.equals("rPr") && run != null) {
                inRPr = true;
                applyRunProps(p, run);
            } else if (inRPr && inSolid && run != null && (n.equals("srgbClr") || n.equals("schemeClr"))) {
                if (run.color == 0)
                    run.color = n.equals("srgbClr") ? Util.parseColor(attr(p, "val")) : schemeColor(attr(p, "val"));
            } else if (inTx && n.equals("t") && para != null) {
                String t = p.nextText();
                Doc.Run r = run != null ? run.copyStyle(t) : new Doc.Run(t);
                para.runs.add(r);
            } else if (inTx && n.equals("br") && para != null) {
                para.runs.add(new Doc.Run("\n"));
            }
        }
        if (s.paras.isEmpty() && s.fill == 0 && s.line == 0) return null;
        // remove parágrafos vazios no fim
        while (!s.paras.isEmpty() && s.paras.get(s.paras.size() - 1).isBlank()) s.paras.remove(s.paras.size() - 1);
        boolean isTitle = phType != null && (phType.equals("title") || phType.equals("ctrTitle"));
        s.isTitle = isTitle;
        if (!hasXfrm && isPh) {
            PhInfo info = null;
            if (phIdx != null) info = layoutPh.get("idx:" + phIdx);
            String tkey = "type:" + (phType == null ? "body" : phType);
            if (info == null) info = layoutPh.get(tkey);
            if (info == null && phIdx != null) info = masterPh.get("idx:" + phIdx);
            if (info == null) info = masterPh.get(tkey);
            if (info == null && "ctrTitle".equals(phType)) info = masterPh.get("type:title");
            if (info == null && !isTitle) info = masterPh.get("type:body");
            if (info != null) {
                s.x = info.x;
                s.y = info.y;
                s.w = info.w;
                s.h = info.h;
                hasXfrm = true;
            }
        }
        if (!hasXfrm) {
            // posição padrão
            s.x = slideCx / 12;
            s.w = slideCx * 10 / 12;
            if (isTitle) {
                s.y = slideCy / 20;
                s.h = slideCy / 6;
            } else {
                s.y = slideCy / 4;
                s.h = slideCy * 2 / 3;
            }
        } else {
            s.x = xf.tx(s.x);
            s.y = xf.ty(s.y);
            s.w = (long) (s.w * xf.sx);
            s.h = (long) (s.h * xf.sy);
        }
        // tamanhos padrão de fonte e marcadores implícitos
        boolean body = isPh && !isTitle && (phType == null || phType.equals("body") || phType.equals("obj"));
        for (Doc.Para para2 : s.paras) {
            for (Doc.Run r : para2.runs) {
                if (r.size == 0) r.size = isTitle ? ("ctrTitle".equals(phType) ? 44 : 36) : (isPh ? 22 : 18);
            }
            if (body && para2.list == Doc.LIST_NONE && !para2.isBlank() && !noBullet.contains(para2)) {
                para2.list = Doc.LIST_BULLET;
            }
        }
        if (isTitle) {
            if (s.anchor == 0 && "ctrTitle".equals(phType)) s.anchor = 2;
            if (s.anchor == 0 && "title".equals(phType)) s.anchor = 1;
        }
        return s;
    }

    private void applyRunProps(XmlPullParser p, Doc.Run run) {
        String sz = attr(p, "sz");
        if (sz != null) try {
            run.size = Integer.parseInt(sz) / 100f;
        } catch (Exception ignored) {
        }
        run.bold = "1".equals(attr(p, "b")) || "true".equals(attr(p, "b"));
        run.italic = "1".equals(attr(p, "i")) || "true".equals(attr(p, "i"));
        String u = attr(p, "u");
        run.underline = u != null && !u.equals("none");
    }

    private Doc.Shape readPicture(XmlPullParser p, Map<String, String> rels, Xf xf) throws Exception {
        int depth = p.getDepth();
        Doc.Shape s = new Doc.Shape();
        s.kind = Doc.Shape.PICTURE;
        boolean has = false;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG && p.getDepth() == depth) break;
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("blip")) {
                String rid = Util.attrQ(p, "r:embed");
                String path = rid == null ? null : rels.get(rid);
                if (path != null) s.image = Util.image(zip.get(path));
            } else if (n.equals("off") && !has) {
                s.x = pl(attr(p, "x"));
                s.y = pl(attr(p, "y"));
                has = true;
            } else if (n.equals("ext") && s.w == 0 && attr(p, "cx") != null) {
                s.w = pl(attr(p, "cx"));
                s.h = pl(attr(p, "cy"));
            }
        }
        if (s.image == null) return null;
        if (!has || s.w == 0) {
            s.w = slideCx / 2;
            s.h = s.image.width > 0 ? s.w * s.image.height / s.image.width : slideCy / 2;
            s.x = (slideCx - s.w) / 2;
            s.y = (slideCy - s.h) / 2;
        } else {
            s.x = xf.tx(s.x);
            s.y = xf.ty(s.y);
            s.w = (long) (s.w * xf.sx);
            s.h = (long) (s.h * xf.sy);
        }
        return s;
    }

    private Doc.Shape readFrame(XmlPullParser p, Xf xf) throws Exception {
        int depth = p.getDepth();
        Doc.Shape s = new Doc.Shape();
        s.kind = Doc.Shape.TABLE;
        Doc.Table t = new Doc.Table();
        List<String> row = null;
        StringBuilder cell = null;
        boolean has = false;
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.END_TAG) {
                if (p.getDepth() == depth) break;
                String n = ln(p.getName());
                if (n.equals("tc") && cell != null && row != null) {
                    row.add(cell.toString().trim());
                    cell = null;
                } else if (n.equals("tr") && row != null) {
                    t.rows.add(row);
                    row = null;
                } else if (n.equals("p") && cell != null) {
                    cell.append('\n');
                }
                continue;
            }
            if (ev != XmlPullParser.START_TAG) continue;
            String n = ln(p.getName());
            if (n.equals("off") && !has) {
                s.x = pl(attr(p, "x"));
                s.y = pl(attr(p, "y"));
                has = true;
            } else if (n.equals("ext") && s.w == 0 && attr(p, "cx") != null) {
                s.w = pl(attr(p, "cx"));
                s.h = pl(attr(p, "cy"));
            } else if (n.equals("tblPr")) {
                t.header = "1".equals(attr(p, "firstRow"));
            } else if (n.equals("tr")) {
                row = new ArrayList<String>();
            } else if (n.equals("tc")) {
                cell = new StringBuilder();
            } else if (n.equals("t") && cell != null) {
                cell.append(p.nextText());
            }
        }
        if (t.rows.isEmpty()) return null;
        s.table = t;
        s.x = xf.tx(s.x);
        s.y = xf.ty(s.y);
        s.w = (long) (s.w * xf.sx);
        s.h = (long) (s.h * xf.sy);
        return s;
    }

    // ------------------------------------------------------------------ blocos

    private void buildBlocks(Doc doc) {
        boolean first = true;
        int n = 0;
        for (Doc.Slide slide : doc.slides) {
            n++;
            if (!first) doc.add(new Doc.PageBreak());
            first = false;
            List<Doc.Shape> shapes = new ArrayList<Doc.Shape>(slide.shapes);
            Collections.sort(shapes, new Comparator<Doc.Shape>() {
                @Override
                public int compare(Doc.Shape a, Doc.Shape b) {
                    if (a.isTitle != b.isTitle) return a.isTitle ? -1 : 1;
                    long dy = a.y - b.y;
                    if (Math.abs(dy) > 200000) return dy < 0 ? -1 : 1;
                    return a.x < b.x ? -1 : (a.x > b.x ? 1 : 0);
                }
            });
            boolean titled = false;
            for (Doc.Shape s : shapes) {
                if (s.kind == Doc.Shape.PICTURE && s.image != null) {
                    Doc.Image img = s.image;
                    img.widthPt = s.w / 12700f;
                    doc.add(img);
                } else if (s.kind == Doc.Shape.TABLE && s.table != null) {
                    doc.add(s.table);
                } else if (s.isTitle && !titled) {
                    StringBuilder sb = new StringBuilder();
                    for (Doc.Para pa : s.paras) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(pa.text().replace('\n', ' '));
                    }
                    if (sb.toString().trim().length() > 0) {
                        doc.add(new Doc.Heading(2, sb.toString().trim()));
                        titled = true;
                        if (doc.title.equals("Documento")) doc.title = sb.toString().trim();
                    }
                } else {
                    for (Doc.Para pa : s.paras) {
                        if (pa.isBlank()) continue;
                        Doc.Para copy = new Doc.Para();
                        copy.align = pa.align == Doc.ALIGN_CENTER ? Doc.ALIGN_CENTER : Doc.ALIGN_LEFT;
                        copy.list = pa.list;
                        copy.listLevel = pa.listLevel;
                        for (Doc.Run r : pa.runs) {
                            Doc.Run c = r.copyStyle(r.text);
                            c.size = 0; // tamanhos de slide são grandes demais para documento
                            if (c.color == 0xFFFFFFFF) c.color = 0;
                            copy.runs.add(c);
                        }
                        doc.add(copy);
                    }
                }
            }
            if (!titled && shapes.isEmpty()) doc.add(new Doc.Para("(Slide " + n + ")"));
        }
    }
}
