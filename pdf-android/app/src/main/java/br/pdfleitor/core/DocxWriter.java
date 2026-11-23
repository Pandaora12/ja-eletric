package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static br.pdfleitor.core.Util.esc;

/** Gera documentos Word (.docx) a partir do modelo Doc. */
public class DocxWriter {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    public boolean letter; // tamanho Carta em vez de A4
    public int marginTwips = 1134; // 2 cm

    private final List<String[]> media = new ArrayList<String[]>(); // {rid, path}
    private final List<byte[]> mediaData = new ArrayList<byte[]>();
    private final List<Integer> numberedLists = new ArrayList<Integer>();
    private int imageCounter;

    public void write(Doc doc, OutputStream os) throws IOException {
        boolean landscape = doc.landscape && doc.slides == null;
        int pageW = letter ? 12240 : 11906, pageH = letter ? 15840 : 16838;
        if (landscape) {
            int t = pageW;
            pageW = pageH;
            pageH = t;
        }
        int contentW = pageW - 2 * marginTwips;
        int contentH = pageH - 2 * marginTwips;

        StringBuilder body = new StringBuilder();
        Doc.Block prev = null;
        int numId = 2;
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading").append(h.level).append("\"/>");
                appendJc(body, h.align);
                body.append("</w:pPr>");
                appendRuns(body, h.runs, false);
                body.append("</w:p>");
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                body.append("<w:p><w:pPr>");
                if (p.list != Doc.LIST_NONE) {
                    int id = 1;
                    if (p.list == Doc.LIST_NUMBER) {
                        boolean continues = prev instanceof Doc.Para && ((Doc.Para) prev).list == Doc.LIST_NUMBER
                                && !(prev instanceof Doc.Heading);
                        if (!continues || numberedLists.isEmpty()) {
                            numId++;
                            numberedLists.add(numId);
                        }
                        id = numberedLists.get(numberedLists.size() - 1);
                    }
                    body.append("<w:pStyle w:val=\"ListParagraph\"/><w:numPr><w:ilvl w:val=\"")
                            .append(Math.min(8, Math.max(0, p.listLevel))).append("\"/><w:numId w:val=\"")
                            .append(id).append("\"/></w:numPr>");
                }
                appendJc(body, p.align);
                body.append("</w:pPr>");
                appendRuns(body, p.runs, false);
                body.append("</w:p>");
            } else if (b instanceof Doc.Table) {
                appendTable(body, (Doc.Table) b, contentW);
            } else if (b instanceof Doc.Image) {
                appendImage(body, (Doc.Image) b, contentW, contentH);
            } else if (b instanceof Doc.PageBreak) {
                body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
            }
            prev = b;
        }
        if (doc.blocks.isEmpty()) body.append("<w:p/>");

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<w:document xmlns:w=\"").append(W).append("\" xmlns:r=\"").append(R)
                .append("\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"")
                .append(" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"")
                .append(" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>")
                .append(body)
                .append("<w:sectPr><w:pgSz w:w=\"").append(pageW).append("\" w:h=\"").append(pageH).append("\"")
                .append(landscape ? " w:orient=\"landscape\"" : "").append("/>")
                .append("<w:pgMar w:top=\"").append(marginTwips).append("\" w:right=\"").append(marginTwips)
                .append("\" w:bottom=\"").append(marginTwips).append("\" w:left=\"").append(marginTwips)
                .append("\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");

        ZipOutputStream zos = new ZipOutputStream(os);
        Util.zipPut(zos, "[Content_Types].xml", contentTypes());
        Util.zipPut(zos, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "</Relationships>");
        Util.zipPut(zos, "docProps/core.xml", coreXml(doc.title));
        Util.zipPut(zos, "word/document.xml", xml.toString());
        Util.zipPut(zos, "word/styles.xml", stylesXml());
        Util.zipPut(zos, "word/numbering.xml", numberingXml());
        StringBuilder rels = new StringBuilder();
        rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
                .append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
                .append("<Relationship Id=\"rIdNum\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering\" Target=\"numbering.xml\"/>");
        for (int i = 0; i < media.size(); i++) {
            rels.append("<Relationship Id=\"").append(media.get(i)[0])
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"")
                    .append(media.get(i)[1]).append("\"/>");
            Util.zipPut(zos, "word/" + media.get(i)[1], mediaData.get(i), false);
        }
        rels.append("</Relationships>");
        Util.zipPut(zos, "word/_rels/document.xml.rels", rels.toString());
        zos.finish();
        zos.flush();
    }

    static String coreXml(String title) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:title>" + esc(title) + "</dc:title><dc:creator>PDF Leitor</dc:creator></cp:coreProperties>";
    }

    private void appendJc(StringBuilder sb, int align) {
        if (align == Doc.ALIGN_CENTER) sb.append("<w:jc w:val=\"center\"/>");
        else if (align == Doc.ALIGN_RIGHT) sb.append("<w:jc w:val=\"right\"/>");
        else if (align == Doc.ALIGN_JUSTIFY) sb.append("<w:jc w:val=\"both\"/>");
    }

    private void appendRuns(StringBuilder sb, List<Doc.Run> runs, boolean forceBold) {
        for (Doc.Run r : runs) {
            if (r.text == null || r.text.isEmpty()) continue;
            StringBuilder rpr = new StringBuilder();
            if (r.bold || forceBold) rpr.append("<w:b/>");
            if (r.italic) rpr.append("<w:i/>");
            if (r.underline) rpr.append("<w:u w:val=\"single\"/>");
            if (r.color != 0 && (r.color & 0xFFFFFF) != 0) rpr.append("<w:color w:val=\"").append(Util.hex6(r.color)).append("\"/>");
            if (r.size > 0) rpr.append("<w:sz w:val=\"").append(Math.round(r.size * 2)).append("\"/>");
            String text = r.text;
            int start = 0;
            sb.append("<w:r>");
            if (rpr.length() > 0) sb.append("<w:rPr>").append(rpr).append("</w:rPr>");
            for (int i = 0; i <= text.length(); i++) {
                char c = i < text.length() ? text.charAt(i) : 0;
                if (i == text.length() || c == '\n' || c == '\t') {
                    if (i > start)
                        sb.append("<w:t xml:space=\"preserve\">").append(esc(text.substring(start, i))).append("</w:t>");
                    if (c == '\n') sb.append("<w:br/>");
                    else if (c == '\t') sb.append("<w:tab/>");
                    start = i + 1;
                }
            }
            sb.append("</w:r>");
        }
    }

    private void appendTable(StringBuilder sb, Doc.Table t, int contentW) {
        int cols = Math.max(1, t.columnCount());
        int[] widths = columnWidths(t, cols, contentW);
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>");
        for (String side : new String[]{"top", "left", "bottom", "right", "insideH", "insideV"}) {
            sb.append("<w:").append(side).append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"A6A6A6\"/>");
        }
        sb.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"80\" w:type=\"dxa\"/><w:right w:w=\"80\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int w : widths) sb.append("<w:gridCol w:w=\"").append(w).append("\"/>");
        sb.append("</w:tblGrid>");
        int small = cols > 8 ? 16 : cols > 5 ? 18 : 0; // meia-pontos
        for (int ri = 0; ri < t.rows.size(); ri++) {
            List<String> row = t.rows.get(ri);
            boolean header = t.header && ri == 0;
            sb.append("<w:tr>");
            if (header) sb.append("<w:trPr><w:tblHeader/></w:trPr>");
            for (int c = 0; c < cols; c++) {
                String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                sb.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[c]).append("\" w:type=\"dxa\"/>");
                if (header) sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E8EAED\"/>");
                sb.append("</w:tcPr>");
                String[] lines = v.split("\n", -1);
                for (String line : lines) {
                    sb.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr>");
                    if (line.length() > 0) {
                        sb.append("<w:r>");
                        if (header || small > 0) {
                            sb.append("<w:rPr>");
                            if (header) sb.append("<w:b/>");
                            if (small > 0) sb.append("<w:sz w:val=\"").append(small).append("\"/>");
                            sb.append("</w:rPr>");
                        }
                        sb.append("<w:t xml:space=\"preserve\">").append(esc(line.replace('\t', ' '))).append("</w:t></w:r>");
                    }
                    sb.append("</w:p>");
                }
                sb.append("</w:tc>");
            }
            sb.append("</w:tr>");
        }
        sb.append("</w:tbl><w:p/>");
    }

    /** Larguras proporcionais ao conteúdo, somando "total". */
    public static int[] columnWidths(Doc.Table t, int cols, int total) {
        double[] score = new double[cols];
        for (List<String> row : t.rows) {
            for (int c = 0; c < cols && c < row.size(); c++) {
                String v = row.get(c) == null ? "" : row.get(c);
                int longest = 0;
                for (String l : v.split("\n")) longest = Math.max(longest, l.length());
                score[c] = Math.max(score[c], Math.min(40, longest));
            }
        }
        double sum = 0;
        for (int c = 0; c < cols; c++) {
            score[c] = Math.max(4, score[c]);
            sum += score[c];
        }
        int[] w = new int[cols];
        int acc = 0;
        for (int c = 0; c < cols; c++) {
            w[c] = (int) (total * score[c] / sum);
            acc += w[c];
        }
        w[cols - 1] += total - acc;
        return w;
    }

    private void appendImage(StringBuilder sb, Doc.Image img, int contentW, int contentH) {
        String ext = Util.imageExt(img.mime);
        if (!(ext.equals("png") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("bmp"))) return;
        imageCounter++;
        String rid = "rIdImg" + imageCounter;
        media.add(new String[]{rid, "media/image" + imageCounter + "." + ext});
        mediaData.add(img.data);
        double wPt = img.widthPt > 0 ? img.widthPt : img.width * 0.75;
        double maxW = contentW / 20.0, maxH = contentH / 20.0 - 4;
        if (img.fullPage || wPt > maxW) wPt = maxW;
        double hPt = wPt * img.height / Math.max(1, img.width);
        if (hPt > maxH) {
            hPt = maxH;
            wPt = hPt * img.width / Math.max(1, img.height);
        }
        long cx = (long) (wPt * 12700), cy = (long) (hPt * 12700);
        sb.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>")
                .append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"").append(cx)
                .append("\" cy=\"").append(cy).append("\"/><wp:docPr id=\"").append(imageCounter)
                .append("\" name=\"Imagem ").append(imageCounter).append("\"/>")
                .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
                .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"").append(imageCounter).append("\" name=\"image")
                .append(imageCounter).append(".").append(ext).append("\"/><pic:cNvPicPr/></pic:nvPicPr>")
                .append("<pic:blipFill><a:blip r:embed=\"").append(rid).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
                .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy)
                .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>")
                .append("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
    }

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>"
                + "<Default Extension=\"gif\" ContentType=\"image/gif\"/>"
                + "<Default Extension=\"bmp\" ContentType=\"image/bmp\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "</Types>";
    }

    private String stylesXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles xmlns:w=\"").append(W).append("\">")
                .append("<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Calibri\"/>")
                .append("<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"pt-BR\"/></w:rPr></w:rPrDefault>")
                .append("<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"264\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>")
                .append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>");
        int[] sizes = {32, 28, 26, 24, 22, 22};
        for (int i = 1; i <= 6; i++) {
            sb.append("<w:style w:type=\"paragraph\" w:styleId=\"Heading").append(i).append("\"><w:name w:val=\"heading ")
                    .append(i).append("\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>")
                    .append("<w:pPr><w:keepNext/><w:spacing w:before=\"").append(i <= 2 ? 240 : 160)
                    .append("\" w:after=\"80\"/><w:outlineLvl w:val=\"").append(i - 1).append("\"/></w:pPr>")
                    .append("<w:rPr><w:b/><w:color w:val=\"").append(i <= 2 ? "1F3864" : "2F5496").append("\"/><w:sz w:val=\"")
                    .append(sizes[i - 1]).append("\"/></w:rPr></w:style>");
        }
        sb.append("<w:style w:type=\"paragraph\" w:styleId=\"ListParagraph\"><w:name w:val=\"List Paragraph\"/><w:basedOn w:val=\"Normal\"/>")
                .append("<w:pPr><w:spacing w:after=\"40\"/><w:ind w:left=\"720\"/><w:contextualSpacing/></w:pPr></w:style>")
                .append("</w:styles>");
        return sb.toString();
    }

    private String numberingXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:numbering xmlns:w=\"").append(W).append("\">");
        String[] bullets = {"•", "◦", "▪"};
        sb.append("<w:abstractNum w:abstractNumId=\"0\"><w:multiLevelType w:val=\"hybridMultilevel\"/>");
        for (int l = 0; l < 9; l++) {
            sb.append("<w:lvl w:ilvl=\"").append(l).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"")
                    .append(bullets[l % 3]).append("\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"").append(720 + 360 * l)
                    .append("\" w:hanging=\"360\"/></w:pPr></w:lvl>");
        }
        sb.append("</w:abstractNum>");
        String[] fmts = {"decimal", "lowerLetter", "lowerRoman"};
        sb.append("<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"hybridMultilevel\"/>");
        for (int l = 0; l < 9; l++) {
            sb.append("<w:lvl w:ilvl=\"").append(l).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"").append(fmts[l % 3])
                    .append("\"/><w:lvlText w:val=\"%").append(l + 1).append(".\"/><w:lvlJc w:val=\"left\"/><w:pPr><w:ind w:left=\"")
                    .append(720 + 360 * l).append("\" w:hanging=\"360\"/></w:pPr></w:lvl>");
        }
        sb.append("</w:abstractNum>");
        sb.append("<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num>");
        sb.append("<w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num>");
        for (int id : numberedLists) {
            sb.append("<w:num w:numId=\"").append(id).append("\"><w:abstractNumId w:val=\"1\"/>")
                    .append("<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>");
        }
        sb.append("</w:numbering>");
        return sb.toString();
    }
}
