package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import static br.pdfleitor.core.Util.esc;

/** Gera documentos OpenDocument: texto (.odt) e planilha (.ods). */
public class OdfWriter {

    private static final String NS = "xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" "
            + "xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\" "
            + "xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" "
            + "xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" "
            + "xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\" "
            + "xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\" "
            + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
            + "xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\" "
            + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
            + "xmlns:meta=\"urn:oasis:names:tc:opendocument:xmlns:meta:1.0\"";

    private final Map<String, String> textStyles = new LinkedHashMap<String, String>();
    private final List<String[]> pictures = new ArrayList<String[]>();
    private final List<byte[]> pictureData = new ArrayList<byte[]>();

    // ------------------------------------------------------------------ ODT

    public void writeOdt(Doc doc, OutputStream os) throws IOException {
        boolean landscape = doc.landscape && doc.slides == null;
        StringBuilder body = new StringBuilder();
        int tableNo = 0;
        boolean pendingBreak = false;
        int i = 0;
        List<Doc.Block> blocks = doc.blocks;
        while (i < blocks.size()) {
            Doc.Block b = blocks.get(i);
            String brk = pendingBreak ? "PBreak" : null;
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                body.append("<text:h text:style-name=\"").append(brk != null ? "HBreak" + h.level : "Heading_20_" + h.level)
                        .append("\" text:outline-level=\"").append(h.level).append("\">");
                appendSpans(body, h.runs);
                body.append("</text:h>");
                pendingBreak = false;
                i++;
            } else if (b instanceof Doc.Para && ((Doc.Para) b).list != Doc.LIST_NONE) {
                // agrupa itens de lista consecutivos do mesmo tipo
                int type = ((Doc.Para) b).list;
                body.append("<text:list text:style-name=\"").append(type == Doc.LIST_NUMBER ? "LNum" : "LBul").append("\">");
                while (i < blocks.size() && blocks.get(i) instanceof Doc.Para && !(blocks.get(i) instanceof Doc.Heading)
                        && ((Doc.Para) blocks.get(i)).list == type) {
                    Doc.Para p = (Doc.Para) blocks.get(i);
                    int lvl = Math.min(5, Math.max(0, p.listLevel));
                    body.append("<text:list-item>");
                    for (int k = 0; k < lvl; k++) body.append("<text:list><text:list-item>");
                    body.append("<text:p text:style-name=\"").append(alignStyle(p.align, brk)).append("\">");
                    appendSpans(body, p.runs);
                    body.append("</text:p>");
                    for (int k = 0; k < lvl; k++) body.append("</text:list-item></text:list>");
                    body.append("</text:list-item>");
                    brk = null;
                    i++;
                }
                body.append("</text:list>");
                pendingBreak = false;
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                body.append("<text:p text:style-name=\"").append(alignStyle(p.align, brk)).append("\">");
                appendSpans(body, p.runs);
                body.append("</text:p>");
                pendingBreak = false;
                i++;
            } else if (b instanceof Doc.Table) {
                if (brk != null) body.append("<text:p text:style-name=\"PBreak\"/>");
                Doc.Table t = (Doc.Table) b;
                int cols = Math.max(1, t.columnCount());
                tableNo++;
                body.append("<table:table table:name=\"Tabela").append(tableNo).append("\" table:style-name=\"Tbl\">")
                        .append("<table:table-column table:number-columns-repeated=\"").append(cols).append("\"/>");
                for (int r = 0; r < t.rows.size(); r++) {
                    boolean header = t.header && r == 0;
                    if (header) body.append("<table:table-header-rows>");
                    body.append("<table:table-row>");
                    List<String> row = t.rows.get(r);
                    for (int c = 0; c < cols; c++) {
                        String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                        body.append("<table:table-cell table:style-name=\"").append(header ? "CellH" : "Cell")
                                .append("\" office:value-type=\"string\">");
                        for (String line : v.split("\n", -1)) {
                            body.append("<text:p");
                            if (header) body.append(" text:style-name=\"PBold\"");
                            body.append(">").append(textEsc(line)).append("</text:p>");
                        }
                        body.append("</table:table-cell>");
                    }
                    body.append("</table:table-row>");
                    if (header) body.append("</table:table-header-rows>");
                }
                body.append("</table:table>");
                pendingBreak = false;
                i++;
            } else if (b instanceof Doc.Image) {
                Doc.Image img = (Doc.Image) b;
                String ext = Util.imageExt(img.mime);
                if (ext.equals("png") || ext.equals("jpeg") || ext.equals("gif") || ext.equals("bmp")) {
                    String path = "Pictures/img" + (pictures.size() + 1) + "." + ext;
                    pictures.add(new String[]{path, img.mime});
                    pictureData.add(img.data);
                    double maxW = landscape ? 25.7 : 17.0, maxH = landscape ? 17.0 : 25.0; // cm
                    double wcm = img.widthPt > 0 ? img.widthPt * 2.54 / 72 : img.width * 2.54 / 96;
                    if (img.fullPage || wcm > maxW) wcm = maxW;
                    double hcm = wcm * img.height / Math.max(1, img.width);
                    if (hcm > maxH) {
                        hcm = maxH;
                        wcm = hcm * img.width / Math.max(1, img.height);
                    }
                    body.append("<text:p text:style-name=\"").append(brk != null ? "PBreakC" : "PC").append("\">")
                            .append("<draw:frame draw:name=\"Imagem").append(pictures.size()).append("\" text:anchor-type=\"as-char\" svg:width=\"")
                            .append(cm(wcm)).append("\" svg:height=\"").append(cm(hcm)).append("\"><draw:image xlink:href=\"")
                            .append(path).append("\" xlink:type=\"simple\" xlink:show=\"embed\" xlink:actuate=\"onLoad\"/></draw:frame></text:p>");
                    pendingBreak = false;
                }
                i++;
            } else if (b instanceof Doc.PageBreak) {
                pendingBreak = true;
                i++;
            } else i++;
        }

        StringBuilder auto = new StringBuilder();
        auto.append("<style:style style:name=\"PL\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"/>")
                .append("<style:style style:name=\"PC\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:text-align=\"center\"/></style:style>")
                .append("<style:style style:name=\"PR\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:text-align=\"end\"/></style:style>")
                .append("<style:style style:name=\"PJ\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:text-align=\"justify\"/></style:style>")
                .append("<style:style style:name=\"PBreak\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:break-before=\"page\"/></style:style>")
                .append("<style:style style:name=\"PBreakC\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:paragraph-properties fo:break-before=\"page\" fo:text-align=\"center\"/></style:style>")
                .append("<style:style style:name=\"PBold\" style:family=\"paragraph\" style:parent-style-name=\"Standard\"><style:text-properties fo:font-weight=\"bold\"/></style:style>")
                .append("<style:style style:name=\"Tbl\" style:family=\"table\"><style:table-properties style:width=\"").append(landscape ? "25.7cm" : "17cm")
                .append("\" table:align=\"margins\"/></style:style>")
                .append("<style:style style:name=\"Cell\" style:family=\"table-cell\"><style:table-cell-properties fo:padding=\"0.1cm\" fo:border=\"0.5pt solid #a6a6a6\"/></style:style>")
                .append("<style:style style:name=\"CellH\" style:family=\"table-cell\"><style:table-cell-properties fo:background-color=\"#e8eaed\" fo:padding=\"0.1cm\" fo:border=\"0.5pt solid #a6a6a6\"/></style:style>");
        for (int l = 1; l <= 6; l++) {
            auto.append("<style:style style:name=\"HBreak").append(l).append("\" style:family=\"paragraph\" style:parent-style-name=\"Heading_20_")
                    .append(l).append("\"><style:paragraph-properties fo:break-before=\"page\"/></style:style>");
        }
        for (Map.Entry<String, String> e : textStyles.entrySet()) {
            auto.append("<style:style style:name=\"").append(e.getValue()).append("\" style:family=\"text\"><style:text-properties ")
                    .append(e.getKey()).append("/></style:style>");
        }
        auto.append("<text:list-style style:name=\"LBul\">");
        String[] bullets = {"•", "◦", "▪"};
        for (int l = 1; l <= 6; l++) {
            auto.append("<text:list-level-style-bullet text:level=\"").append(l).append("\" text:bullet-char=\"").append(bullets[(l - 1) % 3])
                    .append("\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\">")
                    .append("<style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.635cm\" fo:margin-left=\"")
                    .append(cm(0.635 * (l + 1))).append("\"/></style:list-level-properties></text:list-level-style-bullet>");
        }
        auto.append("</text:list-style><text:list-style style:name=\"LNum\">");
        for (int l = 1; l <= 6; l++) {
            auto.append("<text:list-level-style-number text:level=\"").append(l).append("\" style:num-suffix=\".\" style:num-format=\"")
                    .append(l % 2 == 1 ? "1" : "a").append("\"><style:list-level-properties text:list-level-position-and-space-mode=\"label-alignment\">")
                    .append("<style:list-level-label-alignment text:label-followed-by=\"listtab\" fo:text-indent=\"-0.635cm\" fo:margin-left=\"")
                    .append(cm(0.635 * (l + 1))).append("\"/></style:list-level-properties></text:list-level-style-number>");
        }
        auto.append("</text:list-style>");

        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content " + NS + " office:version=\"1.2\">"
                + "<office:automatic-styles>" + auto + "</office:automatic-styles><office:body><office:text>"
                + (body.length() == 0 ? "<text:p/>" : body.toString()) + "</office:text></office:body></office:document-content>";

        StringBuilder styles = new StringBuilder();
        styles.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-styles ").append(NS).append(" office:version=\"1.2\"><office:styles>")
                .append("<style:default-style style:family=\"paragraph\"><style:paragraph-properties fo:margin-bottom=\"0.2cm\"/>")
                .append("<style:text-properties style:font-name=\"Liberation Sans\" fo:font-family=\"'Liberation Sans', Arial, sans-serif\" fo:font-size=\"11pt\" fo:language=\"pt\" fo:country=\"BR\"/></style:default-style>")
                .append("<style:style style:name=\"Standard\" style:family=\"paragraph\" style:class=\"text\"/>");
        int[] sizes = {16, 14, 13, 12, 11, 11};
        for (int l = 1; l <= 6; l++) {
            styles.append("<style:style style:name=\"Heading_20_").append(l).append("\" style:display-name=\"Heading ").append(l)
                    .append("\" style:family=\"paragraph\" style:parent-style-name=\"Standard\" style:next-style-name=\"Standard\" style:default-outline-level=\"")
                    .append(l).append("\" style:class=\"text\"><style:paragraph-properties fo:margin-top=\"0.4cm\" fo:margin-bottom=\"0.2cm\" fo:keep-with-next=\"always\"/>")
                    .append("<style:text-properties fo:font-size=\"").append(sizes[l - 1]).append("pt\" fo:font-weight=\"bold\" fo:color=\"#1f3864\"/></style:style>");
        }
        styles.append("</office:styles><office:automatic-styles><style:page-layout style:name=\"pm1\"><style:page-layout-properties fo:page-width=\"")
                .append(landscape ? "29.7cm" : "21cm").append("\" fo:page-height=\"").append(landscape ? "21cm" : "29.7cm")
                .append("\" style:print-orientation=\"").append(landscape ? "landscape" : "portrait")
                .append("\" fo:margin-top=\"2cm\" fo:margin-bottom=\"2cm\" fo:margin-left=\"2cm\" fo:margin-right=\"2cm\"/></style:page-layout>")
                .append("</office:automatic-styles><office:master-styles><style:master-page style:name=\"Standard\" style:page-layout-name=\"pm1\"/></office:master-styles></office:document-styles>");

        ZipOutputStream zos = new ZipOutputStream(os);
        Util.zipPut(zos, "mimetype", "application/vnd.oasis.opendocument.text".getBytes(Util.UTF8), true);
        StringBuilder manifest = manifestStart("application/vnd.oasis.opendocument.text");
        for (int k = 0; k < pictures.size(); k++) {
            manifest.append("<manifest:file-entry manifest:full-path=\"").append(pictures.get(k)[0])
                    .append("\" manifest:media-type=\"").append(pictures.get(k)[1]).append("\"/>");
            Util.zipPut(zos, pictures.get(k)[0], pictureData.get(k), false);
        }
        manifest.append("</manifest:manifest>");
        Util.zipPut(zos, "META-INF/manifest.xml", manifest.toString());
        Util.zipPut(zos, "content.xml", content);
        Util.zipPut(zos, "styles.xml", styles.toString());
        Util.zipPut(zos, "meta.xml", meta(doc.title));
        zos.finish();
        zos.flush();
    }

    private static String cm(double v) {
        return String.format(java.util.Locale.ROOT, "%.3fcm", v);
    }

    private static String alignStyle(int align, String brk) {
        if (brk != null) return align == Doc.ALIGN_CENTER ? "PBreakC" : "PBreak";
        switch (align) {
            case Doc.ALIGN_CENTER: return "PC";
            case Doc.ALIGN_RIGHT: return "PR";
            case Doc.ALIGN_JUSTIFY: return "PJ";
            default: return "PL";
        }
    }

    private void appendSpans(StringBuilder sb, List<Doc.Run> runs) {
        for (Doc.Run r : runs) {
            if (r.text == null || r.text.isEmpty()) continue;
            StringBuilder props = new StringBuilder();
            if (r.bold) props.append("fo:font-weight=\"bold\" ");
            if (r.italic) props.append("fo:font-style=\"italic\" ");
            if (r.underline) props.append("style:text-underline-style=\"solid\" style:text-underline-width=\"auto\" style:text-underline-color=\"font-color\" ");
            if (r.size > 0) props.append("fo:font-size=\"").append(Math.round(r.size * 10) / 10f).append("pt\" ");
            if (r.color != 0 && (r.color & 0xFFFFFF) != 0) props.append("fo:color=\"#").append(Util.hex6(r.color).toLowerCase()).append("\" ");
            String key = props.toString().trim();
            if (key.isEmpty()) {
                sb.append(textEsc(r.text));
            } else {
                String name = textStyles.get(key);
                if (name == null) {
                    name = "T" + (textStyles.size() + 1);
                    textStyles.put(key, name);
                }
                sb.append("<text:span text:style-name=\"").append(name).append("\">").append(textEsc(r.text)).append("</text:span>");
            }
        }
    }

    /** Escapa texto preservando espaços múltiplos, tabs e quebras de linha (ODF). */
    static String textEsc(String s) {
        StringBuilder sb = new StringBuilder();
        int spaces = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                spaces++;
                continue;
            }
            if (spaces > 0) {
                sb.append(' ');
                if (spaces > 1) sb.append("<text:s text:c=\"").append(spaces - 1).append("\"/>");
                spaces = 0;
            }
            if (c == '\t') sb.append("<text:tab/>");
            else if (c == '\n') sb.append("<text:line-break/>");
            else sb.append(esc(String.valueOf(c)));
        }
        if (spaces > 0) {
            sb.append(' ');
            if (spaces > 1) sb.append("<text:s text:c=\"").append(spaces - 1).append("\"/>");
        }
        return sb.toString();
    }

    private static StringBuilder manifestStart(String mime) {
        StringBuilder m = new StringBuilder();
        m.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">")
                .append("<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"").append(mime).append("\"/>")
                .append("<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>")
                .append("<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>")
                .append("<manifest:file-entry manifest:full-path=\"meta.xml\" manifest:media-type=\"text/xml\"/>");
        return m;
    }

    private static String meta(String title) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-meta " + NS + " office:version=\"1.2\"><office:meta>"
                + "<dc:title>" + esc(title) + "</dc:title><meta:generator>PDF Leitor</meta:generator></office:meta></office:document-meta>";
    }

    // ------------------------------------------------------------------ ODS

    public void writeOds(Doc doc, OutputStream os) throws IOException {
        List<Doc.Table> sheets = XlsxWriter.sheets(doc);
        StringBuilder body = new StringBuilder();
        for (Doc.Table t : sheets) {
            int cols = Math.max(1, t.columnCount());
            body.append("<table:table table:name=\"").append(esc(t.name)).append("\">");
            for (int c = 0; c < cols; c++) {
                int len = 6;
                for (List<String> r : t.rows) if (c < r.size() && r.get(c) != null) len = Math.max(len, Math.min(60, r.get(c).length()));
                body.append("<table:table-column table:style-name=\"co").append(Math.min(60, len)).append("\"/>");
            }
            for (int r = 0; r < t.rows.size(); r++) {
                boolean header = t.header && r == 0;
                List<String> row = t.rows.get(r);
                body.append("<table:table-row>");
                for (int c = 0; c < cols; c++) {
                    String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                    String num = header ? null : XlsxWriter.numeric(v);
                    body.append("<table:table-cell");
                    if (header) body.append(" table:style-name=\"ceH\"");
                    if (num != null) {
                        body.append(" office:value-type=\"float\" office:value=\"").append(num).append("\">");
                    } else if (v.isEmpty()) {
                        body.append("/>");
                        continue;
                    } else body.append(" office:value-type=\"string\">");
                    for (String line : v.split("\n", -1)) body.append("<text:p>").append(textEsc(line)).append("</text:p>");
                    body.append("</table:table-cell>");
                }
                body.append("</table:table-row>");
            }
            if (t.rows.isEmpty()) body.append("<table:table-row><table:table-cell/></table:table-row>");
            body.append("</table:table>");
        }
        StringBuilder auto = new StringBuilder();
        for (int w = 6; w <= 60; w++) {
            auto.append("<style:style style:name=\"co").append(w).append("\" style:family=\"table-column\"><style:table-column-properties style:column-width=\"")
                    .append(cm(Math.max(2.0, w * 0.22))).append("\"/></style:style>");
        }
        auto.append("<style:style style:name=\"ceH\" style:family=\"table-cell\"><style:table-cell-properties fo:background-color=\"#c62828\"/>")
                .append("<style:text-properties fo:font-weight=\"bold\" fo:color=\"#ffffff\"/></style:style>");
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-content " + NS + " office:version=\"1.2\">"
                + "<office:automatic-styles>" + auto + "</office:automatic-styles><office:body><office:spreadsheet>"
                + body + "</office:spreadsheet></office:body></office:document-content>";
        String styles = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<office:document-styles " + NS + " office:version=\"1.2\"><office:styles>"
                + "<style:default-style style:family=\"table-cell\"><style:text-properties fo:font-size=\"10pt\"/></style:default-style>"
                + "</office:styles></office:document-styles>";
        ZipOutputStream zos = new ZipOutputStream(os);
        Util.zipPut(zos, "mimetype", "application/vnd.oasis.opendocument.spreadsheet".getBytes(Util.UTF8), true);
        StringBuilder manifest = manifestStart("application/vnd.oasis.opendocument.spreadsheet");
        manifest.append("</manifest:manifest>");
        Util.zipPut(zos, "META-INF/manifest.xml", manifest.toString());
        Util.zipPut(zos, "content.xml", content);
        Util.zipPut(zos, "styles.xml", styles);
        Util.zipPut(zos, "meta.xml", meta(doc.title));
        zos.finish();
        zos.flush();
    }
}
