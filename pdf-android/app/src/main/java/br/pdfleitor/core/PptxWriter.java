package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static br.pdfleitor.core.Util.esc;

/** Gera apresentações PowerPoint (.pptx) a partir do modelo Doc. */
public class PptxWriter {

    private static final String NS = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" "
            + "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"";
    private static final long IN = 914400L;

    private long cx = 12192000L, cy = 6858000L;
    private final List<Doc.Slide> slides = new ArrayList<Doc.Slide>();

    public void write(Doc doc, OutputStream os) throws IOException {
        if (doc.slides != null && !doc.slides.isEmpty()) {
            slides.addAll(doc.slides);
            cx = doc.slides.get(0).cx;
            cy = doc.slides.get(0).cy;
        } else {
            paginate(doc);
        }
        if (slides.isEmpty()) {
            Doc.Slide s = new Doc.Slide();
            s.cx = cx;
            s.cy = cy;
            slides.add(s);
        }

        ZipOutputStream zos = new ZipOutputStream(os);
        int imageCount = 0;
        StringBuilder ct = new StringBuilder();
        ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Default Extension=\"png\" ContentType=\"image/png\"/><Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>")
                .append("<Default Extension=\"gif\" ContentType=\"image/gif\"/><Default Extension=\"bmp\" ContentType=\"image/bmp\"/>")
                .append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
                .append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
                .append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
                .append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
                .append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
        for (int i = 1; i <= slides.size(); i++) {
            ct.append("<Override PartName=\"/ppt/slides/slide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
        }
        ct.append("</Types>");
        Util.zipPut(zos, "[Content_Types].xml", ct.toString());
        Util.zipPut(zos, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "</Relationships>");
        Util.zipPut(zos, "docProps/core.xml", DocxWriter.coreXml(doc.title));

        StringBuilder pres = new StringBuilder();
        pres.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<p:presentation ").append(NS)
                .append(" saveSubsetFonts=\"1\"><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>");
        StringBuilder presRels = new StringBuilder();
        presRels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                .append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>")
                .append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>");
        for (int i = 1; i <= slides.size(); i++) {
            pres.append("<p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i + 2).append("\"/>");
            presRels.append("<Relationship Id=\"rId").append(i + 2)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(i).append(".xml\"/>");
        }
        pres.append("</p:sldIdLst><p:sldSz cx=\"").append(cx).append("\" cy=\"").append(cy)
                .append("\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>");
        presRels.append("</Relationships>");
        Util.zipPut(zos, "ppt/presentation.xml", pres.toString());
        Util.zipPut(zos, "ppt/_rels/presentation.xml.rels", presRels.toString());
        Util.zipPut(zos, "ppt/theme/theme1.xml", THEME);
        Util.zipPut(zos, "ppt/slideMasters/slideMaster1.xml", master());
        Util.zipPut(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>"
                + "</Relationships>");
        Util.zipPut(zos, "ppt/slideLayouts/slideLayout1.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<p:sldLayout "
                + NS + " type=\"blank\" preserve=\"1\"><p:cSld name=\"Em branco\"><p:spTree>" + GROUP
                + "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>");
        Util.zipPut(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>"
                + "</Relationships>");

        for (int i = 0; i < slides.size(); i++) {
            Doc.Slide s = slides.get(i);
            StringBuilder xml = new StringBuilder();
            StringBuilder rels = new StringBuilder();
            rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                    .append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>");
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<p:sld ").append(NS).append("><p:cSld>");
            if (s.background != 0xFFFFFFFF && s.background != 0) {
                xml.append("<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"").append(Util.hex6(s.background))
                        .append("\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>");
            }
            xml.append("<p:spTree>").append(GROUP);
            int id = 2;
            int relId = 2;
            for (Doc.Shape sh : s.shapes) {
                if (sh.kind == Doc.Shape.PICTURE && sh.image != null) {
                    String ext = Util.imageExt(sh.image.mime);
                    if (!(ext.equals("png") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("bmp"))) continue;
                    imageCount++;
                    String path = "media/image" + imageCount + "." + ext;
                    Util.zipPut(zos, "ppt/" + path, sh.image.data, false);
                    rels.append("<Relationship Id=\"rId").append(relId)
                            .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../")
                            .append(path).append("\"/>");
                    xml.append("<p:pic><p:nvPicPr><p:cNvPr id=\"").append(id).append("\" name=\"Imagem ").append(id)
                            .append("\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>")
                            .append("<p:blipFill><a:blip r:embed=\"rId").append(relId).append("\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>")
                            .append("<p:spPr>").append(xfrm(sh)).append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>");
                    relId++;
                } else if (sh.kind == Doc.Shape.TABLE && sh.table != null) {
                    appendTable(xml, sh, id);
                } else {
                    appendTextShape(xml, sh, id);
                }
                id++;
            }
            xml.append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>");
            rels.append("</Relationships>");
            Util.zipPut(zos, "ppt/slides/slide" + (i + 1) + ".xml", xml.toString());
            Util.zipPut(zos, "ppt/slides/_rels/slide" + (i + 1) + ".xml.rels", rels.toString());
        }
        zos.finish();
        zos.flush();
    }

    private static final String GROUP = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
            + "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>";

    private static String xfrm(Doc.Shape s) {
        return "<a:xfrm><a:off x=\"" + Math.max(0, s.x) + "\" y=\"" + Math.max(0, s.y) + "\"/><a:ext cx=\"" + Math.max(1, s.w)
                + "\" cy=\"" + Math.max(1, s.h) + "\"/></a:xfrm>";
    }

    private void appendTextShape(StringBuilder xml, Doc.Shape sh, int id) {
        xml.append("<p:sp><p:nvSpPr><p:cNvPr id=\"").append(id).append("\" name=\"")
                .append(sh.isTitle ? "Título " : "Texto ").append(id).append("\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>")
                .append(xfrm(sh)).append("<a:prstGeom prst=\"").append(sh.ellipse ? "ellipse" : "rect").append("\"><a:avLst/></a:prstGeom>");
        if (sh.fill != 0) xml.append("<a:solidFill><a:srgbClr val=\"").append(Util.hex6(sh.fill)).append("\"/></a:solidFill>");
        else xml.append("<a:noFill/>");
        if (sh.line != 0) xml.append("<a:ln w=\"12700\"><a:solidFill><a:srgbClr val=\"").append(Util.hex6(sh.line)).append("\"/></a:solidFill></a:ln>");
        xml.append("</p:spPr><p:txBody><a:bodyPr wrap=\"square\" lIns=\"91440\" tIns=\"45720\" rIns=\"91440\" bIns=\"45720\" rtlCol=\"0\" anchor=\"")
                .append(sh.anchor == 1 ? "ctr" : sh.anchor == 2 ? "b" : "t").append("\"><a:normAutofit/></a:bodyPr><a:lstStyle/>");
        if (sh.paras.isEmpty()) xml.append("<a:p><a:endParaRPr lang=\"pt-BR\"/></a:p>");
        int num = 0;
        for (Doc.Para p : sh.paras) {
            xml.append("<a:p>");
            StringBuilder ppr = new StringBuilder();
            String algn = p.align == Doc.ALIGN_CENTER ? "ctr" : p.align == Doc.ALIGN_RIGHT ? "r" : p.align == Doc.ALIGN_JUSTIFY ? "just" : null;
            if (p.list != Doc.LIST_NONE) {
                long mar = 342900L + 342900L * Math.max(0, p.listLevel);
                ppr.append("<a:pPr marL=\"").append(mar).append("\" indent=\"-342900\"");
                if (algn != null) ppr.append(" algn=\"").append(algn).append("\"");
                ppr.append(">");
                if (p.list == Doc.LIST_NUMBER) ppr.append("<a:buFont typeface=\"+mj-lt\"/><a:buAutoNum type=\"arabicPeriod\"/>");
                else ppr.append("<a:buFont typeface=\"Arial\"/><a:buChar char=\"•\"/>");
                ppr.append("</a:pPr>");
                num++;
            } else if (algn != null) {
                ppr.append("<a:pPr algn=\"").append(algn).append("\"/>");
            }
            xml.append(ppr);
            float lastSize = 18;
            for (Doc.Run r : p.runs) {
                if (r.text == null || r.text.isEmpty()) continue;
                float size = r.size > 0 ? r.size : (sh.isTitle ? 32 : 18);
                lastSize = size;
                String[] parts = r.text.replace('\t', ' ').split("\n", -1);
                for (int k = 0; k < parts.length; k++) {
                    if (k > 0) xml.append("<a:br><a:rPr lang=\"pt-BR\" sz=\"").append(Math.round(size * 100)).append("\"/></a:br>");
                    if (parts[k].isEmpty()) continue;
                    xml.append("<a:r><a:rPr lang=\"pt-BR\" sz=\"").append(Math.round(size * 100)).append("\"");
                    if (r.bold || (sh.isTitle && r.size == 0)) xml.append(" b=\"1\"");
                    if (r.italic) xml.append(" i=\"1\"");
                    if (r.underline) xml.append(" u=\"sng\"");
                    xml.append(" dirty=\"0\">");
                    if (r.color != 0) xml.append("<a:solidFill><a:srgbClr val=\"").append(Util.hex6(r.color)).append("\"/></a:solidFill>");
                    xml.append("</a:rPr><a:t>").append(esc(parts[k])).append("</a:t></a:r>");
                }
            }
            xml.append("<a:endParaRPr lang=\"pt-BR\" sz=\"").append(Math.round(lastSize * 100)).append("\"/></a:p>");
        }
        xml.append("</p:txBody></p:sp>");
    }

    private void appendTable(StringBuilder xml, Doc.Shape sh, int id) {
        Doc.Table t = sh.table;
        int cols = Math.max(1, t.columnCount());
        long w = Math.max(IN, sh.w);
        int[] widths = DocxWriter.columnWidths(t, cols, (int) Math.min(Integer.MAX_VALUE, w));
        int rows = t.rows.size();
        long rowH = Math.max(250000L, sh.h / Math.max(1, rows));
        int font = cols > 8 ? 1000 : cols > 5 ? 1200 : 1400;
        xml.append("<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"").append(id).append("\" name=\"Tabela ").append(id)
                .append("\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr><p:nvPr/></p:nvGraphicFramePr>")
                .append("<p:xfrm><a:off x=\"").append(sh.x).append("\" y=\"").append(sh.y).append("\"/><a:ext cx=\"").append(w)
                .append("\" cy=\"").append(rowH * rows).append("\"/></p:xfrm><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\">")
                .append("<a:tbl><a:tblPr firstRow=\"").append(t.header ? 1 : 0).append("\" bandRow=\"1\"/><a:tblGrid>");
        for (int cw : widths) xml.append("<a:gridCol w=\"").append(cw).append("\"/>");
        xml.append("</a:tblGrid>");
        for (int ri = 0; ri < rows; ri++) {
            List<String> row = t.rows.get(ri);
            boolean header = t.header && ri == 0;
            xml.append("<a:tr h=\"").append(rowH).append("\">");
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                xml.append("<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>");
                for (String line : v.split("\n", -1)) {
                    xml.append("<a:p>");
                    if (line.length() > 0) {
                        xml.append("<a:r><a:rPr lang=\"pt-BR\" sz=\"").append(font).append("\"").append(header ? " b=\"1\"" : "")
                                .append(" dirty=\"0\">");
                        if (header) xml.append("<a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill>");
                        xml.append("</a:rPr><a:t>").append(esc(line)).append("</a:t></a:r>");
                    }
                    xml.append("<a:endParaRPr lang=\"pt-BR\" sz=\"").append(font).append("\"/></a:p>");
                }
                xml.append("</a:txBody><a:tcPr>");
                for (String side : new String[]{"lnL", "lnR", "lnT", "lnB"}) {
                    xml.append("<a:").append(side).append(" w=\"6350\"><a:solidFill><a:srgbClr val=\"BFBFBF\"/></a:solidFill></a:")
                            .append(side).append(">");
                }
                xml.append("<a:solidFill><a:srgbClr val=\"").append(header ? "C62828" : (ri % 2 == 0 ? "F5F5F5" : "FFFFFF"))
                        .append("\"/></a:solidFill></a:tcPr></a:tc>");
            }
            xml.append("</a:tr>");
        }
        xml.append("</a:tbl></a:graphicData></a:graphic></p:graphicFrame>");
    }

    // ------------------------------------------------------------------ paginação

    private Doc.Slide newSlide() {
        Doc.Slide s = new Doc.Slide();
        s.cx = cx;
        s.cy = cy;
        slides.add(s);
        return s;
    }

    private void paginate(Doc doc) {
        // PDF rasterizado: um slide por página, com a proporção da página
        boolean allFullPage = true;
        boolean anyImage = false;
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Image) {
                anyImage = true;
                if (!((Doc.Image) b).fullPage) allFullPage = false;
            } else if (!(b instanceof Doc.PageBreak)) allFullPage = false;
        }
        if (anyImage && allFullPage) {
            Doc.Image first = null;
            for (Doc.Block b : doc.blocks) if (b instanceof Doc.Image) {
                first = (Doc.Image) b;
                break;
            }
            cx = 10L * IN;
            cy = cx * first.height / Math.max(1, first.width);
            for (Doc.Block b : doc.blocks) {
                if (!(b instanceof Doc.Image)) continue;
                Doc.Image img = (Doc.Image) b;
                Doc.Slide s = newSlide();
                Doc.Shape sh = new Doc.Shape();
                sh.kind = Doc.Shape.PICTURE;
                sh.image = img;
                fit(sh, img, 0, 0, cx, cy);
                s.shapes.add(sh);
            }
            return;
        }

        long margin = IN / 2;
        long titleY = (long) (IN * 0.35), titleH = (long) (IN * 0.95);
        long bodyY = (long) (IN * 1.4), bodyH = cy - bodyY - margin;
        long bodyW = cx - 2 * margin;
        final int maxLines = 14;
        final int charsPerLine = 85;

        Doc.Slide cur = null;
        Doc.Shape body = null;
        String title = null;
        int lines = 0;

        List<Doc.Block> blocks = doc.blocks;
        for (int i = 0; i < blocks.size(); i++) {
            Doc.Block b = blocks.get(i);
            if (b instanceof Doc.Heading && ((Doc.Heading) b).level <= 2) {
                Doc.Heading h = (Doc.Heading) b;
                title = h.text().replace('\n', ' ').trim();
                cur = newSlide();
                body = null;
                lines = 0;
                Doc.Shape ts = new Doc.Shape();
                ts.isTitle = true;
                ts.x = margin;
                ts.y = titleY;
                ts.w = bodyW;
                ts.h = titleH;
                ts.anchor = 1;
                Doc.Para tp = new Doc.Para();
                Doc.Run r = new Doc.Run(title);
                r.bold = true;
                r.size = title.length() > 60 ? 24 : 30;
                r.color = 0xFF1F3864;
                tp.runs.add(r);
                ts.paras.add(tp);
                cur.shapes.add(ts);
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                if (p.isBlank()) continue;
                int need = 1 + p.text().length() / charsPerLine;
                if (cur == null || lines + need > maxLines || (body == null && hasContent(cur, true))) {
                    cur = continuation(title, margin, titleY, titleH, bodyW);
                    body = null;
                    lines = 0;
                }
                if (body == null) {
                    body = new Doc.Shape();
                    body.x = margin;
                    body.y = cur.shapes.isEmpty() ? margin : bodyY;
                    body.w = bodyW;
                    body.h = cur.shapes.isEmpty() ? cy - 2 * margin : bodyH;
                    cur.shapes.add(body);
                }
                Doc.Para copy = new Doc.Para();
                copy.list = p.list;
                copy.listLevel = p.listLevel;
                copy.align = p.align == Doc.ALIGN_JUSTIFY ? Doc.ALIGN_LEFT : p.align;
                for (Doc.Run r : p.runs) {
                    Doc.Run c = r.copyStyle(r.text);
                    c.size = b instanceof Doc.Heading ? 22 : 18;
                    if (b instanceof Doc.Heading) c.bold = true;
                    copy.runs.add(c);
                }
                body.paras.add(copy);
                lines += need;
            } else if (b instanceof Doc.Image) {
                Doc.Image img = (Doc.Image) b;
                if (cur == null || body != null || hasContent(cur, false)) {
                    cur = continuation(title, margin, titleY, titleH, bodyW);
                }
                boolean hasTitle = !cur.shapes.isEmpty();
                Doc.Shape sh = new Doc.Shape();
                sh.kind = Doc.Shape.PICTURE;
                sh.image = img;
                long top = hasTitle ? bodyY : margin;
                fit(sh, img, margin, top, bodyW, cy - top - margin);
                cur.shapes.add(sh);
                body = null;
                lines = maxLines; // força novo slide para o próximo conteúdo
            } else if (b instanceof Doc.Table) {
                Doc.Table t = (Doc.Table) b;
                int perSlide = 10;
                List<String> header = t.header && !t.rows.isEmpty() ? t.rows.get(0) : null;
                int start = header != null ? 1 : 0;
                if (t.rows.size() <= start) start = 0;
                if (header != null && t.rows.size() == 1) header = null;
                for (int r0 = start; r0 < t.rows.size(); r0 += perSlide) {
                    if (cur == null || body != null || hasContent(cur, false)) {
                        cur = continuation(title, margin, titleY, titleH, bodyW);
                    }
                    Doc.Table part = new Doc.Table();
                    if (header != null) {
                        part.rows.add(header);
                        part.header = true;
                    }
                    for (int r = r0; r < Math.min(t.rows.size(), r0 + perSlide); r++) part.rows.add(t.rows.get(r));
                    Doc.Shape sh = new Doc.Shape();
                    sh.kind = Doc.Shape.TABLE;
                    sh.table = part;
                    sh.x = margin;
                    sh.y = cur.shapes.isEmpty() ? margin : bodyY;
                    sh.w = bodyW;
                    sh.h = Math.min(bodyH, 370840L * part.rows.size());
                    cur.shapes.add(sh);
                    body = null;
                    lines = maxLines;
                }
            } else if (b instanceof Doc.PageBreak) {
                lines = maxLines;
                body = null;
            }
        }
    }

    private boolean hasContent(Doc.Slide s, boolean ignoreTitle) {
        for (Doc.Shape sh : s.shapes) if (!sh.isTitle) return true;
        return false;
    }

    private Doc.Slide continuation(String title, long margin, long titleY, long titleH, long bodyW) {
        Doc.Slide s = newSlide();
        if (title != null) {
            Doc.Shape ts = new Doc.Shape();
            ts.isTitle = true;
            ts.x = margin;
            ts.y = titleY;
            ts.w = bodyW;
            ts.h = titleH;
            ts.anchor = 1;
            Doc.Para tp = new Doc.Para();
            Doc.Run r = new Doc.Run(title);
            r.bold = true;
            r.size = title.length() > 60 ? 24 : 30;
            r.color = 0xFF1F3864;
            tp.runs.add(r);
            ts.paras.add(tp);
            s.shapes.add(ts);
        }
        return s;
    }

    private static void fit(Doc.Shape sh, Doc.Image img, long x, long y, long w, long h) {
        double ar = (double) img.width / Math.max(1, img.height);
        long iw = w, ih = (long) (w / ar);
        if (ih > h) {
            ih = h;
            iw = (long) (h * ar);
        }
        sh.x = x + (w - iw) / 2;
        sh.y = y + (h - ih) / 2;
        sh.w = iw;
        sh.h = ih;
    }

    private String master() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<p:sldMaster " + NS + "><p:cSld>"
                + "<p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg1\"/></p:bgRef></p:bg><p:spTree>" + GROUP + "</p:spTree></p:cSld>"
                + "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\""
                + " accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"
                + "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>"
                + "<p:txStyles><p:titleStyle><a:lvl1pPr algn=\"l\"><a:defRPr sz=\"3200\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill>"
                + "<a:latin typeface=\"+mj-lt\"/></a:defRPr></a:lvl1pPr></p:titleStyle>"
                + "<p:bodyStyle><a:lvl1pPr><a:defRPr sz=\"1800\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill>"
                + "<a:latin typeface=\"+mn-lt\"/></a:defRPr></a:lvl1pPr></p:bodyStyle>"
                + "<p:otherStyle><a:lvl1pPr><a:defRPr sz=\"1800\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill>"
                + "<a:latin typeface=\"+mn-lt\"/></a:defRPr></a:lvl1pPr></p:otherStyle></p:txStyles></p:sldMaster>";
    }

    static final String THEME = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Office\"><a:themeElements>"
            + "<a:clrScheme name=\"Office\"><a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1>"
            + "<a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"44546A\"/></a:dk2>"
            + "<a:lt2><a:srgbClr val=\"E7E6E6\"/></a:lt2><a:accent1><a:srgbClr val=\"4472C4\"/></a:accent1>"
            + "<a:accent2><a:srgbClr val=\"ED7D31\"/></a:accent2><a:accent3><a:srgbClr val=\"A5A5A5\"/></a:accent3>"
            + "<a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4><a:accent5><a:srgbClr val=\"5B9BD5\"/></a:accent5>"
            + "<a:accent6><a:srgbClr val=\"70AD47\"/></a:accent6><a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink>"
            + "<a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink></a:clrScheme>"
            + "<a:fontScheme name=\"Office\"><a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:majorFont>"
            + "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"\"/><a:cs typeface=\"\"/></a:minorFont></a:fontScheme>"
            + "<a:fmtScheme name=\"Office\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
            + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst>"
            + "<a:lnStyleLst><a:ln w=\"6350\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>"
            + "<a:ln w=\"12700\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln>"
            + "<a:ln w=\"19050\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst>"
            + "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle>"
            + "<a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>"
            + "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
            + "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements>"
            + "<a:objectDefaults/><a:extraClrSchemeLst/></a:theme>";
}
