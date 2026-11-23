package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static br.pdfleitor.core.Util.htmlEsc;

/** Escritores de formatos de texto: HTML, Markdown, TXT, CSV, RTF e EPUB. */
public final class TextWriters {

    private TextWriters() {
    }

    // ------------------------------------------------------------------ TXT

    public static void txt(Doc doc, OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        Doc.Block prev = null;
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                if (sb.length() > 0) sb.append('\n');
                String t = ((Doc.Heading) b).text();
                sb.append(t).append('\n');
                if (((Doc.Heading) b).level <= 2) sb.append(repeat(((Doc.Heading) b).level == 1 ? '=' : '-', Math.min(80, t.length()))).append('\n');
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                sb.append(indent(p.listLevel * 2));
                if (p.list == Doc.LIST_BULLET) sb.append("• ");
                else if (p.list == Doc.LIST_NUMBER) sb.append(Math.max(1, p.listIndex)).append(". ");
                sb.append(p.text()).append('\n');
            } else if (b instanceof Doc.Table) {
                if (prev != null) sb.append('\n');
                sb.append(asciiTable((Doc.Table) b)).append('\n');
            } else if (b instanceof Doc.PageBreak) {
                sb.append('\n');
            }
            prev = b;
        }
        os.write(sb.toString().replace("\n", "\r\n").getBytes(Util.UTF8));
    }

    static String asciiTable(Doc.Table t) {
        int cols = t.columnCount();
        int[] w = new int[cols];
        for (List<String> r : t.rows)
            for (int c = 0; c < r.size(); c++) w[c] = Math.min(40, Math.max(w[c], flat(r.get(c)).length()));
        StringBuilder sb = new StringBuilder();
        for (int ri = 0; ri < t.rows.size(); ri++) {
            List<String> r = t.rows.get(ri);
            for (int c = 0; c < cols; c++) {
                String v = c < r.size() ? flat(r.get(c)) : "";
                if (v.length() > 40) v = v.substring(0, 39) + "…";
                sb.append(v);
                if (c < cols - 1) sb.append(indent(w[c] - v.length() + 2));
            }
            sb.append('\n');
            if (ri == 0 && t.header) {
                int total = 0;
                for (int x : w) total += x + 2;
                sb.append(repeat('-', Math.max(3, total - 2))).append('\n');
            }
        }
        return sb.toString();
    }

    private static String flat(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\t', ' ');
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    private static String indent(int n) {
        return repeat(' ', Math.max(0, n));
    }

    // ------------------------------------------------------------------ CSV

    public static void csv(Doc doc, OutputStream os) throws IOException {
        List<Doc.Table> sheets = XlsxWriter.sheets(doc);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM para o Excel reconhecer UTF-8
        boolean first = true;
        for (Doc.Table t : sheets) {
            if (!first) sb.append("\r\n");
            if (sheets.size() > 1) sb.append(csvCell(t.name)).append("\r\n");
            first = false;
            int cols = t.columnCount();
            for (List<String> r : t.rows) {
                for (int c = 0; c < cols; c++) {
                    if (c > 0) sb.append(',');
                    sb.append(csvCell(c < r.size() ? r.get(c) : ""));
                }
                sb.append("\r\n");
            }
        }
        os.write(sb.toString().getBytes(Util.UTF8));
    }

    private static String csvCell(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r") || v.contains(";"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    // ------------------------------------------------------------------ Markdown

    public static void markdown(Doc doc, OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        int imgNo = 0;
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                sb.append(repeat('#', h.level)).append(' ').append(h.text().replace('\n', ' ')).append("\n\n");
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                if (p.isBlank()) continue;
                StringBuilder line = new StringBuilder();
                for (Doc.Run r : p.runs) {
                    String t = mdEsc(r.text);
                    if (t.trim().isEmpty()) {
                        line.append(t);
                        continue;
                    }
                    String lead = t.substring(0, t.length() - ltrim(t).length());
                    String trail = t.substring(rtrim(t).length());
                    String core = t.trim();
                    if (r.bold && r.italic) core = "***" + core + "***";
                    else if (r.bold) core = "**" + core + "**";
                    else if (r.italic) core = "*" + core + "*";
                    line.append(lead).append(core).append(trail);
                }
                String text = line.toString().replace("\n", "  \n");
                if (p.list != Doc.LIST_NONE) {
                    sb.append(indent(p.listLevel * 2)).append(p.list == Doc.LIST_NUMBER ? Math.max(1, p.listIndex) + ". " : "- ")
                            .append(text).append('\n');
                } else {
                    sb.append(text).append("\n\n");
                }
            } else if (b instanceof Doc.Table) {
                Doc.Table t = (Doc.Table) b;
                int cols = Math.max(1, t.columnCount());
                for (int ri = 0; ri < t.rows.size(); ri++) {
                    List<String> r = t.rows.get(ri);
                    sb.append('|');
                    for (int c = 0; c < cols; c++) {
                        String v = c < r.size() && r.get(c) != null ? r.get(c) : "";
                        sb.append(' ').append(v.replace("|", "\\|").replace("\n", "<br>")).append(" |");
                    }
                    sb.append('\n');
                    if (ri == 0) {
                        sb.append('|');
                        for (int c = 0; c < cols; c++) sb.append(" --- |");
                        sb.append('\n');
                    }
                }
                sb.append('\n');
            } else if (b instanceof Doc.Image) {
                imgNo++;
                Doc.Image img = (Doc.Image) b;
                if (img.data.length < 400 * 1024) {
                    sb.append("![Imagem ").append(imgNo).append("](data:").append(img.mime).append(";base64,")
                            .append(Base64.encode(img.data)).append(")\n\n");
                } else {
                    sb.append("*[Imagem ").append(imgNo).append("]*\n\n");
                }
            } else if (b instanceof Doc.PageBreak) {
                sb.append("\n---\n\n");
            }
        }
        os.write(sb.toString().getBytes(Util.UTF8));
    }

    private static String mdEsc(String s) {
        return s.replace("\\", "\\\\").replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
    }

    private static String ltrim(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return s.substring(i);
    }

    private static String rtrim(String s) {
        int i = s.length();
        while (i > 0 && Character.isWhitespace(s.charAt(i - 1))) i--;
        return s.substring(0, i);
    }

    // ------------------------------------------------------------------ HTML

    public static void html(Doc doc, OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"pt-BR\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>").append(htmlEsc(doc.title))
                .append("</title><style>body{font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif;max-width:900px;margin:2em auto;padding:0 1em;line-height:1.55;color:#222}")
                .append("h1,h2,h3{color:#1f3864;line-height:1.25}table{border-collapse:collapse;margin:1em 0;width:100%}")
                .append("td,th{border:1px solid #bbb;padding:.35em .5em;vertical-align:top;text-align:left}th{background:#eee}")
                .append("img{max-width:100%;height:auto;display:block;margin:1em auto}.pb{border:0;border-top:1px dashed #ccc;margin:2em 0}")
                .append("@media print{.pb{page-break-after:always;border:0}}</style></head><body>\n");
        body(doc.blocks, sb, false);
        sb.append("</body></html>\n");
        os.write(sb.toString().getBytes(Util.UTF8));
    }

    /** Corpo HTML/XHTML. Em XHTML (EPUB) as imagens são referências externas. */
    static void body(List<Doc.Block> blocks, StringBuilder sb, boolean xhtml) {
        int i = 0;
        while (i < blocks.size()) {
            Doc.Block b = blocks.get(i);
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                sb.append("<h").append(h.level).append(alignAttr(h.align)).append('>');
                runs(h.runs, sb, xhtml);
                sb.append("</h").append(h.level).append(">\n");
                i++;
            } else if (b instanceof Doc.Para && ((Doc.Para) b).list != Doc.LIST_NONE) {
                int type = ((Doc.Para) b).list;
                String tag = type == Doc.LIST_NUMBER ? "ol" : "ul";
                sb.append('<').append(tag).append(">\n");
                while (i < blocks.size() && blocks.get(i) instanceof Doc.Para && !(blocks.get(i) instanceof Doc.Heading)
                        && ((Doc.Para) blocks.get(i)).list == type) {
                    Doc.Para p = (Doc.Para) blocks.get(i);
                    sb.append("<li");
                    if (p.listLevel > 0) sb.append(" style=\"margin-left:").append(p.listLevel * 1.5).append("em\"");
                    sb.append('>');
                    runs(p.runs, sb, xhtml);
                    sb.append("</li>\n");
                    i++;
                }
                sb.append("</").append(tag).append(">\n");
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                sb.append("<p").append(alignAttr(p.align)).append('>');
                if (p.isBlank()) sb.append(xhtml ? "&#160;" : "&nbsp;");
                else runs(p.runs, sb, xhtml);
                sb.append("</p>\n");
                i++;
            } else if (b instanceof Doc.Table) {
                Doc.Table t = (Doc.Table) b;
                int cols = Math.max(1, t.columnCount());
                sb.append("<table>\n");
                for (int r = 0; r < t.rows.size(); r++) {
                    String cell = t.header && r == 0 ? "th" : "td";
                    sb.append("<tr>");
                    List<String> row = t.rows.get(r);
                    for (int c = 0; c < cols; c++) {
                        String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                        sb.append('<').append(cell).append('>').append(htmlEsc(v).replace("\n", xhtml ? "<br/>" : "<br>"))
                                .append("</").append(cell).append('>');
                    }
                    sb.append("</tr>\n");
                }
                sb.append("</table>\n");
                i++;
            } else if (b instanceof Doc.Image) {
                Doc.Image img = (Doc.Image) b;
                if (xhtml) {
                    sb.append("<img src=\"").append(img.mime == null ? "" : htmlEsc(imageHref(img))).append("\" alt=\"\"/>\n");
                } else {
                    sb.append("<img alt=\"\" src=\"data:").append(img.mime).append(";base64,").append(Base64.encode(img.data)).append('"');
                    if (img.widthPt > 0 && !img.fullPage) sb.append(" style=\"width:").append(Math.round(img.widthPt / 0.75)).append("px\"");
                    sb.append(">\n");
                }
                i++;
            } else if (b instanceof Doc.PageBreak) {
                sb.append(xhtml ? "<hr class=\"pb\"/>\n" : "<hr class=\"pb\">\n");
                i++;
            } else i++;
        }
    }

    private static String imageHref(Doc.Image img) {
        return img.mime == null ? "" : (String) imgHrefs.get(img);
    }

    private static final java.util.Map<Doc.Image, String> imgHrefs = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<Doc.Image, String>());

    private static String alignAttr(int align) {
        switch (align) {
            case Doc.ALIGN_CENTER: return " style=\"text-align:center\"";
            case Doc.ALIGN_RIGHT: return " style=\"text-align:right\"";
            case Doc.ALIGN_JUSTIFY: return " style=\"text-align:justify\"";
            default: return "";
        }
    }

    private static void runs(List<Doc.Run> runs, StringBuilder sb, boolean xhtml) {
        for (Doc.Run r : runs) {
            String t = htmlEsc(r.text).replace("\n", xhtml ? "<br/>" : "<br>").replace("\t", xhtml ? "&#160;&#160;&#160;&#160;" : "&emsp;");
            StringBuilder style = new StringBuilder();
            if (r.color != 0 && (r.color & 0xFFFFFF) != 0) style.append("color:#").append(Util.hex6(r.color)).append(';');
            if (style.length() > 0) t = "<span style=\"" + style + "\">" + t + "</span>";
            if (r.underline) t = "<u>" + t + "</u>";
            if (r.italic) t = "<em>" + t + "</em>";
            if (r.bold) t = "<strong>" + t + "</strong>";
            sb.append(t);
        }
    }

    // ------------------------------------------------------------------ RTF

    public static void rtf(Doc doc, OutputStream os) throws IOException {
        List<Integer> colors = new ArrayList<Integer>();
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Para) {
                for (Doc.Run r : ((Doc.Para) b).runs) {
                    if (r.color != 0 && !colors.contains(r.color)) colors.add(r.color);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1046{\\fonttbl{\\f0\\fswiss Calibri;}}{\\colortbl;");
        for (int c : colors) {
            sb.append("\\red").append((c >> 16) & 0xFF).append("\\green").append((c >> 8) & 0xFF).append("\\blue").append(c & 0xFF).append(';');
        }
        sb.append("}\n\\paperw11906\\paperh16838\\margl1134\\margr1134\\margt1134\\margb1134\\fs22\n");
        int[] hs = {32, 28, 26, 24, 22, 22};
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                sb.append("{\\pard\\sb240\\sa120\\keepn\\b\\fs").append(hs[h.level - 1]).append(' ')
                        .append(rtfEsc(h.text())).append("\\par}\n");
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                sb.append("{\\pard\\sa120");
                if (p.align == Doc.ALIGN_CENTER) sb.append("\\qc");
                else if (p.align == Doc.ALIGN_RIGHT) sb.append("\\qr");
                else if (p.align == Doc.ALIGN_JUSTIFY) sb.append("\\qj");
                if (p.list != Doc.LIST_NONE) {
                    sb.append("\\fi-360\\li").append(720 + 360 * p.listLevel).append(' ');
                    sb.append(p.list == Doc.LIST_NUMBER ? Math.max(1, p.listIndex) + ".\\tab " : "\\bullet\\tab ");
                } else sb.append(' ');
                for (Doc.Run r : p.runs) {
                    sb.append('{');
                    if (r.bold) sb.append("\\b");
                    if (r.italic) sb.append("\\i");
                    if (r.underline) sb.append("\\ul");
                    if (r.size > 0) sb.append("\\fs").append(Math.round(r.size * 2));
                    if (r.color != 0) sb.append("\\cf").append(colors.indexOf(r.color) + 1);
                    sb.append(' ').append(rtfEsc(r.text)).append('}');
                }
                sb.append("\\par}\n");
            } else if (b instanceof Doc.Table) {
                Doc.Table t = (Doc.Table) b;
                int cols = Math.max(1, t.columnCount());
                int[] w = DocxWriter.columnWidths(t, cols, 9638);
                for (int r = 0; r < t.rows.size(); r++) {
                    List<String> row = t.rows.get(r);
                    sb.append("\\trowd\\trgaph70");
                    int x = 0;
                    for (int c = 0; c < cols; c++) {
                        x += w[c];
                        sb.append("\\clbrdrt\\brdrs\\brdrw10\\clbrdrl\\brdrs\\brdrw10\\clbrdrb\\brdrs\\brdrw10\\clbrdrr\\brdrs\\brdrw10\\cellx").append(x);
                    }
                    sb.append('\n');
                    for (int c = 0; c < cols; c++) {
                        String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
                        sb.append("\\pard\\intbl ");
                        if (t.header && r == 0) sb.append("{\\b ").append(rtfEsc(v)).append('}');
                        else sb.append(rtfEsc(v));
                        sb.append("\\cell ");
                    }
                    sb.append("\\row\n");
                }
                sb.append("\\pard\\par\n");
            } else if (b instanceof Doc.Image) {
                Doc.Image img = (Doc.Image) b;
                String type = "image/png".equals(img.mime) ? "\\pngblip" : "image/jpeg".equals(img.mime) ? "\\jpegblip" : null;
                if (type == null) continue;
                double wPt = img.widthPt > 0 ? img.widthPt : img.width * 0.75;
                if (img.fullPage || wPt > 481) wPt = 481;
                double hPt = wPt * img.height / Math.max(1, img.width);
                if (hPt > 700) {
                    hPt = 700;
                    wPt = hPt * img.width / Math.max(1, img.height);
                }
                sb.append("{\\pard\\qc{\\pict").append(type).append("\\picw").append(img.width).append("\\pich").append(img.height)
                        .append("\\picwgoal").append(Math.round(wPt * 20)).append("\\pichgoal").append(Math.round(hPt * 20)).append('\n');
                char[] hex = "0123456789abcdef".toCharArray();
                for (int k = 0; k < img.data.length; k++) {
                    sb.append(hex[(img.data[k] >> 4) & 15]).append(hex[img.data[k] & 15]);
                    if (k % 64 == 63) sb.append('\n');
                }
                sb.append("}\\par}\n");
            } else if (b instanceof Doc.PageBreak) {
                sb.append("\\page\n");
            }
        }
        sb.append("}");
        os.write(sb.toString().getBytes(Charset1252.CS));
    }

    private static final class Charset1252 {
        static final java.nio.charset.Charset CS = java.nio.charset.Charset.forName("US-ASCII");
    }

    static String rtfEsc(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '{' || c == '}') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\line ");
            else if (c == '\t') sb.append("\\tab ");
            else if (c < 0x80) sb.append(c);
            else sb.append("\\u").append((int) (short) c).append('?');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ EPUB

    public static void epub(Doc doc, OutputStream os) throws IOException {
        // divide em capítulos nos títulos de nível 1 (ou 2) e quebras de página
        int splitLevel = 1;
        boolean hasH1 = false;
        for (Doc.Block b : doc.blocks) if (b instanceof Doc.Heading && ((Doc.Heading) b).level == 1) hasH1 = true;
        if (!hasH1) splitLevel = 2;
        List<List<Doc.Block>> chapters = new ArrayList<List<Doc.Block>>();
        List<String> titles = new ArrayList<String>();
        List<Doc.Block> cur = new ArrayList<Doc.Block>();
        String curTitle = null;
        for (Doc.Block b : doc.blocks) {
            boolean split = (b instanceof Doc.Heading && ((Doc.Heading) b).level <= splitLevel);
            if (split && !cur.isEmpty()) {
                chapters.add(cur);
                titles.add(curTitle);
                cur = new ArrayList<Doc.Block>();
                curTitle = null;
            }
            if (b instanceof Doc.PageBreak) continue;
            if (curTitle == null && b instanceof Doc.Heading) curTitle = ((Doc.Heading) b).text().trim();
            cur.add(b);
        }
        if (!cur.isEmpty() || chapters.isEmpty()) {
            chapters.add(cur);
            titles.add(curTitle);
        }
        ZipOutputStream zos = new ZipOutputStream(os);
        Util.zipPut(zos, "mimetype", "application/epub+zip".getBytes(Util.UTF8), true);
        Util.zipPut(zos, "META-INF/container.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>"
                + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
        StringBuilder manifest = new StringBuilder();
        StringBuilder spine = new StringBuilder();
        StringBuilder nav = new StringBuilder();
        int imgNo = 0;
        for (int c = 0; c < chapters.size(); c++) {
            List<Doc.Block> ch = chapters.get(c);
            for (Doc.Block b : ch) {
                if (b instanceof Doc.Image) {
                    Doc.Image img = (Doc.Image) b;
                    String ext = Util.imageExt(img.mime);
                    if (!(ext.equals("png") || ext.equals("jpeg") || ext.equals("gif"))) continue;
                    imgNo++;
                    String href = "img" + imgNo + "." + ext;
                    imgHrefs.put(img, href);
                    Util.zipPut(zos, "OEBPS/" + href, img.data, false);
                    manifest.append("<item id=\"img").append(imgNo).append("\" href=\"").append(href).append("\" media-type=\"")
                            .append(img.mime).append("\"/>");
                }
            }
            String title = titles.get(c) != null ? titles.get(c) : (chapters.size() == 1 ? doc.title : "Parte " + (c + 1));
            StringBuilder x = new StringBuilder();
            x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"pt-BR\"><head><title>")
                    .append(htmlEsc(title)).append("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body>\n");
            List<Doc.Block> safe = new ArrayList<Doc.Block>();
            for (Doc.Block b : ch) if (!(b instanceof Doc.Image) || imgHrefs.containsKey(b)) safe.add(b);
            body(safe, x, true);
            x.append("</body></html>");
            Util.zipPut(zos, "OEBPS/cap" + (c + 1) + ".xhtml", xmlSafe(x.toString()));
            manifest.append("<item id=\"cap").append(c + 1).append("\" href=\"cap").append(c + 1).append(".xhtml\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"cap").append(c + 1).append("\"/>");
            nav.append("<li><a href=\"cap").append(c + 1).append(".xhtml\">").append(htmlEsc(title)).append("</a></li>");
        }
        for (Doc.Block b : doc.blocks) imgHrefs.remove(b);
        Util.zipPut(zos, "OEBPS/style.css", "body{font-family:serif;line-height:1.5;margin:0 4%}h1,h2,h3{font-family:sans-serif;color:#1f3864}"
                + "table{border-collapse:collapse;width:100%}td,th{border:1px solid #999;padding:.2em .4em}th{background:#eee}"
                + "img{max-width:100%}.pb{border:0}");
        Util.zipPut(zos, "OEBPS/nav.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html>\n<html xmlns=\"http://www.w3.org/1999/xhtml\" "
                + "xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"pt-BR\"><head><title>Sumário</title></head><body>"
                + "<nav epub:type=\"toc\" id=\"toc\"><h1>Sumário</h1><ol>" + xmlSafe(nav.toString()) + "</ol></nav></body></html>");
        String uuid = java.util.UUID.randomUUID().toString();
        String date = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT) {
            {
                setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            }
        }.format(new java.util.Date());
        Util.zipPut(zos, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\" xml:lang=\"pt-BR\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"uid\">urn:uuid:" + uuid + "</dc:identifier>"
                + "<dc:title>" + Util.esc(doc.title) + "</dc:title><dc:language>pt-BR</dc:language>"
                + "<meta property=\"dcterms:modified\">" + date + "</meta></metadata><manifest>"
                + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                + "<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>" + manifest + "</manifest><spine>" + spine
                + "</spine></package>");
        zos.finish();
        zos.flush();
    }

    /** Remove caracteres inválidos em XML. */
    private static String xmlSafe(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') continue;
            sb.append(c);
        }
        return sb.toString();
    }
}
