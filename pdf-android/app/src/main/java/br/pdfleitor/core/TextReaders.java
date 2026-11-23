package br.pdfleitor.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leitores de formatos de texto: TXT, Markdown, CSV, HTML, RTF e EPUB. */
public final class TextReaders {

    private TextReaders() {
    }

    // ------------------------------------------------------------------ TXT

    public static Doc txt(byte[] data) {
        Doc doc = new Doc();
        String s = Util.decodeText(data).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = s.split("\n", -1);
        for (String line : lines) {
            doc.add(new Doc.Para(line.replace("\f", "")));
            if (line.indexOf('\f') >= 0) doc.add(new Doc.PageBreak());
        }
        // remove linhas vazias no fim
        while (!doc.blocks.isEmpty() && doc.blocks.get(doc.blocks.size() - 1) instanceof Doc.Para
                && ((Doc.Para) doc.blocks.get(doc.blocks.size() - 1)).isBlank())
            doc.blocks.remove(doc.blocks.size() - 1);
        return doc;
    }

    // ------------------------------------------------------------------ CSV

    public static Doc csv(byte[] data) {
        String s = Util.decodeText(data);
        char delim = detectDelimiter(s);
        Doc.Table t = new Doc.Table();
        List<String> row = new ArrayList<String>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else quoted = false;
                } else cell.append(c);
            } else if (c == '"' && cell.length() == 0) {
                quoted = true;
            } else if (c == delim) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                t.rows.add(row);
                row = new ArrayList<String>();
                if (t.rows.size() >= XlsxReader.MAX_ROWS) break;
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            t.rows.add(row);
        }
        t.trim();
        t.header = true;
        Doc doc = new Doc();
        doc.title = "Tabela";
        if (!t.rows.isEmpty()) doc.add(t);
        if (t.columnCount() > 6) doc.landscape = true;
        return doc;
    }

    static char detectDelimiter(String s) {
        int end = s.indexOf('\n');
        String first = end > 0 ? s.substring(0, end) : s;
        int commas = count(first, ','), semis = count(first, ';'), tabs = count(first, '\t');
        if (tabs > commas && tabs > semis) return '\t';
        if (semis > commas) return ';';
        return ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    // ------------------------------------------------------------------ Markdown

    private static final Pattern MD_INLINE = Pattern.compile("(\\*\\*|__)(.+?)\\1|(\\*|_)(.+?)\\3|`([^`]+)`|!?\\[([^\\]]*)\\]\\(([^)]*)\\)");

    public static Doc markdown(byte[] data) {
        Doc doc = new Doc();
        String[] lines = Util.decodeText(data).replace("\r\n", "\n").split("\n", -1);
        StringBuilder para = new StringBuilder();
        boolean inCode = false;
        Doc.Table table = null;
        int num = 0;
        for (String raw : lines) {
            String line = raw;
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flushMd(doc, para);
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                Doc.Para p = new Doc.Para(line);
                doc.add(p);
                continue;
            }
            // tabelas
            if (trimmed.startsWith("|") && trimmed.length() > 1) {
                flushMd(doc, para);
                if (trimmed.matches("\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?")) {
                    if (table != null) table.header = true;
                    continue;
                }
                if (table == null) {
                    table = new Doc.Table();
                    doc.add(table);
                }
                String inner = trimmed.substring(1);
                if (inner.endsWith("|")) inner = inner.substring(0, inner.length() - 1);
                List<String> row = new ArrayList<String>();
                for (String c : inner.split("\\|", -1)) row.add(stripMd(c.trim()));
                table.rows.add(row);
                continue;
            }
            table = null;
            if (trimmed.isEmpty()) {
                flushMd(doc, para);
                num = 0;
                continue;
            }
            Matcher h = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*$").matcher(trimmed);
            if (h.matches()) {
                flushMd(doc, para);
                Doc.Heading hd = new Doc.Heading(h.group(1).length());
                inlineMd(h.group(2), hd);
                doc.add(hd);
                continue;
            }
            if (trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                flushMd(doc, para);
                continue;
            }
            Matcher li = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$").matcher(line);
            if (li.matches()) {
                flushMd(doc, para);
                Doc.Para p = new Doc.Para();
                boolean numbered = Character.isDigit(li.group(2).charAt(0));
                p.list = numbered ? Doc.LIST_NUMBER : Doc.LIST_BULLET;
                p.listLevel = li.group(1).replace("\t", "    ").length() / 2;
                p.listIndex = numbered ? ++num : 0;
                String body = li.group(3);
                if (body.startsWith("[ ] ")) body = "☐ " + body.substring(4);
                else if (body.startsWith("[x] ") || body.startsWith("[X] ")) body = "☑ " + body.substring(4);
                inlineMd(body, p);
                doc.add(p);
                continue;
            }
            if (trimmed.startsWith(">")) trimmed = trimmed.replaceFirst("^>+\\s?", "");
            if (para.length() > 0) para.append(raw.endsWith("  ") ? "\n" : " ");
            para.append(trimmed);
        }
        flushMd(doc, para);
        for (Doc.Block b : doc.blocks) {
            if (b instanceof Doc.Heading) {
                doc.title = ((Doc.Heading) b).text();
                break;
            }
        }
        return doc;
    }

    private static void flushMd(Doc doc, StringBuilder para) {
        if (para.length() == 0) return;
        Doc.Para p = new Doc.Para();
        inlineMd(para.toString(), p);
        doc.add(p);
        para.setLength(0);
    }

    private static void inlineMd(String s, Doc.Para p) {
        Matcher m = MD_INLINE.matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) p.runs.add(new Doc.Run(s.substring(last, m.start())));
            if (m.group(2) != null) p.runs.add(new Doc.Run(m.group(2), true, false));
            else if (m.group(4) != null) p.runs.add(new Doc.Run(m.group(4), false, true));
            else if (m.group(5) != null) p.runs.add(new Doc.Run(m.group(5)));
            else if (m.group(6) != null && !m.group(0).startsWith("!")) {
                Doc.Run r = new Doc.Run(m.group(6));
                r.underline = true;
                r.color = 0xFF1A73E8;
                p.runs.add(r);
            }
            last = m.end();
        }
        if (last < s.length()) p.runs.add(new Doc.Run(s.substring(last)));
    }

    private static String stripMd(String s) {
        return s.replaceAll("(\\*\\*|__|`)", "");
    }

    // ------------------------------------------------------------------ HTML

    /** Resolve recursos (imagens) referenciados por caminho relativo. */
    public interface Resolver {
        byte[] get(String href);
    }

    private static final Map<String, String> ENTITIES = new HashMap<String, String>();

    static {
        String[] e = {"nbsp", " ", "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "copy", "©",
                "reg", "®", "trade", "™", "hellip", "…", "mdash", "—", "ndash", "–", "laquo", "«", "raquo", "»",
                "ldquo", "“", "rdquo", "”", "lsquo", "‘", "rsquo", "’", "bull", "•", "middot", "·", "deg", "°",
                "euro", "€", "pound", "£", "sect", "§", "ordm", "º", "ordf", "ª", "times", "×", "divide", "÷",
                "aacute", "á", "Aacute", "Á", "agrave", "à", "Agrave", "À", "acirc", "â", "Acirc", "Â", "atilde", "ã",
                "Atilde", "Ã", "auml", "ä", "eacute", "é", "Eacute", "É", "egrave", "è", "ecirc", "ê", "Ecirc", "Ê",
                "iacute", "í", "Iacute", "Í", "oacute", "ó", "Oacute", "Ó", "ocirc", "ô", "Ocirc", "Ô", "otilde", "õ",
                "Otilde", "Õ", "ouml", "ö", "uacute", "ú", "Uacute", "Ú", "uuml", "ü", "Uuml", "Ü", "ccedil", "ç",
                "Ccedil", "Ç", "ntilde", "ñ", "Ntilde", "Ñ", "iexcl", "¡", "iquest", "¿", "shy", ""};
        for (int i = 0; i < e.length; i += 2) ENTITIES.put(e[i], e[i + 1]);
    }

    public static String decodeEntities(String s) {
        if (s.indexOf('&') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '&') {
                int semi = s.indexOf(';', i);
                if (semi > i && semi - i < 12) {
                    String name = s.substring(i + 1, semi);
                    String rep = null;
                    try {
                        if (name.startsWith("#x") || name.startsWith("#X"))
                            rep = new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
                        else if (name.startsWith("#"))
                            rep = new String(Character.toChars(Integer.parseInt(name.substring(1))));
                        else rep = ENTITIES.get(name);
                    } catch (Exception ignored) {
                    }
                    if (rep != null) {
                        sb.append(rep);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    public static Doc html(byte[] data, Resolver resolver) {
        Doc doc = new Doc();
        htmlInto(Util.decodeText(data), resolver, doc);
        return doc;
    }

    /** Analisador HTML tolerante (sem DOM): suficiente para conversão. */
    public static void htmlInto(String html, Resolver resolver, Doc doc) {
        HtmlState st = new HtmlState(doc, resolver);
        int i = 0, n = html.length();
        while (i < n) {
            char c = html.charAt(i);
            if (c == '<') {
                if (html.startsWith("<!--", i)) {
                    int e = html.indexOf("-->", i + 4);
                    i = e < 0 ? n : e + 3;
                    continue;
                }
                int e = html.indexOf('>', i);
                if (e < 0) break;
                String tag = html.substring(i + 1, e);
                i = e + 1;
                if (tag.startsWith("!") || tag.startsWith("?")) continue;
                boolean closing = tag.startsWith("/");
                if (closing) tag = tag.substring(1);
                String name = tag.split("[\\s/]", 2)[0].toLowerCase(Locale.ROOT);
                int colon = name.indexOf(':');
                if (colon >= 0) name = name.substring(colon + 1);
                if (!closing && (name.equals("script") || name.equals("style") || name.equals("head")
                        || name.equals("noscript") || name.equals("svg") || name.equals("title"))) {
                    if (name.equals("title")) {
                        int end = indexOfIgnoreCase(html, "</title", i);
                        if (end > i && doc.title.equals("Documento"))
                            doc.title = decodeEntities(html.substring(i, end)).trim();
                    }
                    if (name.equals("head")) {
                        int end = indexOfIgnoreCase(html, "</head", i);
                        // extrai título do head
                        if (end > 0) {
                            int ts = indexOfIgnoreCase(html, "<title", i);
                            if (ts >= 0 && ts < end) {
                                int tgt = html.indexOf('>', ts);
                                int te = indexOfIgnoreCase(html, "</title", tgt);
                                if (tgt > 0 && te > tgt) {
                                    String t = decodeEntities(html.substring(tgt + 1, te)).trim();
                                    if (t.length() > 0) doc.title = t;
                                }
                            }
                            e = html.indexOf('>', end);
                            i = e < 0 ? n : e + 1;
                        }
                        continue;
                    }
                    int end = indexOfIgnoreCase(html, "</" + name, i);
                    if (end < 0) {
                        i = n;
                    } else {
                        e = html.indexOf('>', end);
                        i = e < 0 ? n : e + 1;
                    }
                    continue;
                }
                if (closing) st.close(name);
                else st.open(name, tag, tag.endsWith("/"));
            } else {
                int e = html.indexOf('<', i);
                if (e < 0) e = n;
                st.text(decodeEntities(html.substring(i, e)));
                i = e;
            }
        }
        st.flush();
    }

    private static int indexOfIgnoreCase(String s, String what, int from) {
        int len = what.length();
        for (int i = Math.max(0, from); i <= s.length() - len; i++) {
            if (s.regionMatches(true, i, what, 0, len)) return i;
        }
        return -1;
    }

    private static final Pattern ATTR = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    static String htmlAttr(String tag, String name) {
        Matcher m = ATTR.matcher(tag);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(name)) {
                String v = m.group(3) != null ? m.group(3) : m.group(4) != null ? m.group(4) : m.group(5);
                return decodeEntities(v);
            }
        }
        return null;
    }

    private static class HtmlState {
        final Doc doc;
        final Resolver resolver;
        Doc.Para cur;
        int bold, italic, underline, pre;
        final List<int[]> lists = new ArrayList<int[]>(); // {tipo, contador}
        Doc.Table table;
        List<String> row;
        StringBuilder cell;
        int linkDepth;

        HtmlState(Doc doc, Resolver resolver) {
            this.doc = doc;
            this.resolver = resolver;
        }

        void flush() {
            if (cur != null) {
                // normaliza espaços nas pontas
                if (!cur.runs.isEmpty()) {
                    Doc.Run first = cur.runs.get(0);
                    first.text = ltrim(first.text);
                    Doc.Run last = cur.runs.get(cur.runs.size() - 1);
                    last.text = rtrim(last.text);
                }
                DocxReader.mergeRuns(cur);
                if (!cur.isBlank() || cur instanceof Doc.Heading) {
                    if (!cur.isBlank()) doc.add(cur);
                }
            }
            cur = null;
        }

        void ensure() {
            if (cur == null) {
                cur = new Doc.Para();
                if (!lists.isEmpty()) {
                    int[] l = lists.get(lists.size() - 1);
                    cur.list = l[0];
                    cur.listLevel = lists.size() - 1;
                    cur.listIndex = l[1];
                }
            }
        }

        void text(String t) {
            if (cell != null) {
                cell.append(pre > 0 ? t : t.replaceAll("\\s+", " "));
                return;
            }
            if (pre == 0) {
                t = t.replaceAll("\\s+", " ");
                if (t.equals(" ") && (cur == null || cur.runs.isEmpty())) return;
            }
            if (t.isEmpty()) return;
            ensure();
            Doc.Run r = new Doc.Run(t);
            r.bold = bold > 0 || cur instanceof Doc.Heading;
            r.italic = italic > 0;
            r.underline = underline > 0;
            if (linkDepth > 0) r.color = 0xFF1A73E8;
            cur.runs.add(r);
        }

        void open(String name, String tag, boolean selfClosing) {
            if (name.matches("h[1-6]")) {
                flush();
                cur = new Doc.Heading(name.charAt(1) - '0');
                String align = alignOf(tag);
                if (align != null) cur.align = alignCode(align);
            } else if (name.equals("p") || name.equals("div") || name.equals("section") || name.equals("article")
                    || name.equals("blockquote") || name.equals("header") || name.equals("footer")
                    || name.equals("figure") || name.equals("figcaption") || name.equals("dt") || name.equals("dd")) {
                if (cell != null) {
                    if (cell.length() > 0) cell.append('\n');
                    return;
                }
                flush();
                String align = alignOf(tag);
                if (align != null) {
                    ensure();
                    cur.align = alignCode(align);
                }
            } else if (name.equals("br")) {
                if (cell != null) cell.append('\n');
                else {
                    ensure();
                    cur.runs.add(new Doc.Run("\n"));
                }
            } else if (name.equals("hr")) {
                flush();
            } else if (name.equals("ul") || name.equals("ol")) {
                flush();
                lists.add(new int[]{name.equals("ol") ? Doc.LIST_NUMBER : Doc.LIST_BULLET, 0});
            } else if (name.equals("li")) {
                flush();
                if (!lists.isEmpty()) lists.get(lists.size() - 1)[1]++;
                ensure();
            } else if (name.equals("b") || name.equals("strong")) {
                bold++;
            } else if (name.equals("i") || name.equals("em") || name.equals("cite")) {
                italic++;
            } else if (name.equals("u") || name.equals("ins")) {
                underline++;
            } else if (name.equals("a")) {
                linkDepth++;
            } else if (name.equals("pre")) {
                flush();
                pre++;
            } else if (name.equals("table")) {
                flush();
                table = new Doc.Table();
            } else if (name.equals("tr") && table != null) {
                row = new ArrayList<String>();
            } else if ((name.equals("td") || name.equals("th")) && table != null) {
                if (row == null) row = new ArrayList<String>();
                cell = new StringBuilder();
                if (name.equals("th") && table.rows.isEmpty()) table.header = true;
            } else if (name.equals("img")) {
                String src = htmlAttr(tag, "src");
                byte[] bytes = null;
                if (src != null && src.startsWith("data:")) {
                    int comma = src.indexOf(',');
                    if (comma > 0 && src.substring(0, comma).contains("base64")) {
                        try {
                            bytes = Base64.decode(src.substring(comma + 1));
                        } catch (Exception ignored) {
                        }
                    }
                } else if (src != null && resolver != null) {
                    bytes = resolver.get(src);
                }
                Doc.Image img = Util.image(bytes);
                if (img != null) {
                    String w = htmlAttr(tag, "width");
                    if (w != null) try {
                        img.widthPt = Float.parseFloat(w.replace("px", "")) * 0.75f;
                    } catch (Exception ignored) {
                    }
                    if (cell == null) {
                        flush();
                        doc.add(img);
                    }
                }
            }
            if (selfClosing && !name.equals("br") && !name.equals("img") && !name.equals("hr")) close(name);
        }

        void close(String name) {
            if (name.matches("h[1-6]") || name.equals("p") || name.equals("div") || name.equals("li")
                    || name.equals("section") || name.equals("article") || name.equals("blockquote")
                    || name.equals("figcaption") || name.equals("dt") || name.equals("dd")) {
                if (cell == null) flush();
            } else if (name.equals("ul") || name.equals("ol")) {
                flush();
                if (!lists.isEmpty()) lists.remove(lists.size() - 1);
            } else if (name.equals("b") || name.equals("strong")) {
                bold = Math.max(0, bold - 1);
            } else if (name.equals("i") || name.equals("em") || name.equals("cite")) {
                italic = Math.max(0, italic - 1);
            } else if (name.equals("u") || name.equals("ins")) {
                underline = Math.max(0, underline - 1);
            } else if (name.equals("a")) {
                linkDepth = Math.max(0, linkDepth - 1);
            } else if (name.equals("pre")) {
                flush();
                pre = Math.max(0, pre - 1);
            } else if ((name.equals("td") || name.equals("th")) && cell != null && row != null) {
                row.add(cell.toString().trim());
                cell = null;
            } else if (name.equals("tr") && row != null && table != null) {
                if (cell != null) {
                    row.add(cell.toString().trim());
                    cell = null;
                }
                table.rows.add(row);
                row = null;
            } else if (name.equals("table") && table != null) {
                if (row != null) {
                    table.rows.add(row);
                    row = null;
                }
                table.trim();
                if (!table.rows.isEmpty()) doc.add(table);
                table = null;
                cell = null;
            }
        }

        static String alignOf(String tag) {
            String a = htmlAttr(tag, "align");
            if (a != null) return a.toLowerCase(Locale.ROOT);
            String style = htmlAttr(tag, "style");
            if (style != null) {
                Matcher m = Pattern.compile("text-align\\s*:\\s*(\\w+)").matcher(style);
                if (m.find()) return m.group(1).toLowerCase(Locale.ROOT);
            }
            return null;
        }

        static int alignCode(String a) {
            if (a.equals("center")) return Doc.ALIGN_CENTER;
            if (a.equals("right")) return Doc.ALIGN_RIGHT;
            if (a.equals("justify")) return Doc.ALIGN_JUSTIFY;
            return Doc.ALIGN_LEFT;
        }

        static String ltrim(String s) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == ' ') i++;
            return s.substring(i);
        }

        static String rtrim(String s) {
            int i = s.length();
            while (i > 0 && s.charAt(i - 1) == ' ') i--;
            return s.substring(0, i);
        }
    }

    // ------------------------------------------------------------------ EPUB

    public static Doc epub(byte[] data) throws Exception {
        final Map<String, byte[]> zip = Util.unzip(data);
        String opf = null;
        byte[] container = zip.get("META-INF/container.xml");
        if (container != null) {
            Matcher m = Pattern.compile("full-path\\s*=\\s*\"([^\"]+)\"").matcher(new String(container, Util.UTF8));
            if (m.find()) opf = m.group(1);
        }
        if (opf == null) for (String k : zip.keySet()) if (k.endsWith(".opf")) opf = k;
        Doc doc = new Doc();
        List<String> spine = new ArrayList<String>();
        if (opf != null && zip.containsKey(opf)) {
            String o = new String(zip.get(opf), Util.UTF8);
            Matcher tm = Pattern.compile("<dc:title[^>]*>([^<]+)</dc:title>").matcher(o);
            if (tm.find()) doc.title = decodeEntities(tm.group(1)).trim();
            Map<String, String> manifest = new HashMap<String, String>();
            Matcher im = Pattern.compile("<item\\b[^>]*>").matcher(o);
            while (im.find()) {
                String tag = im.group();
                String id = htmlAttr(tag, "id"), href = htmlAttr(tag, "href");
                if (id != null && href != null) manifest.put(id, Util.resolve(Util.dirOf(opf), href));
            }
            Matcher sm = Pattern.compile("<itemref\\b[^>]*>").matcher(o);
            while (sm.find()) {
                String path = manifest.get(htmlAttr(sm.group(), "idref"));
                if (path != null) spine.add(path);
            }
        }
        if (spine.isEmpty()) {
            for (String k : zip.keySet()) if (k.endsWith(".xhtml") || k.endsWith(".html") || k.endsWith(".htm")) spine.add(k);
        }
        String title = doc.title;
        boolean first = true;
        for (final String path : spine) {
            byte[] html = zip.get(decodeUrl(path));
            if (html == null) continue;
            if (!first) doc.add(new Doc.PageBreak());
            first = false;
            htmlInto(Util.decodeText(html), new Resolver() {
                @Override
                public byte[] get(String href) {
                    return zip.get(Util.resolve(Util.dirOf(path), decodeUrl(href)));
                }
            }, doc);
        }
        doc.title = title;
        return doc;
    }

    private static String decodeUrl(String s) {
        try {
            return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ------------------------------------------------------------------ RTF

    public static Doc rtf(byte[] data) {
        Doc doc = new Doc();
        String s = new String(data, java.nio.charset.Charset.forName("ISO-8859-1"));
        Doc.Para cur = new Doc.Para();
        // pilha de estado: {bold, italic, underline, skip}
        List<boolean[]> stack = new ArrayList<boolean[]>();
        boolean[] st = new boolean[4];
        int uc = 1;
        int skipChars = 0;
        StringBuilder text = new StringBuilder();
        java.io.ByteArrayOutputStream hexBytes = new java.io.ByteArrayOutputStream();
        int i = 0, n = s.length();
        Doc.Table table = null;
        List<String> row = null;
        boolean intbl = false;
        while (i < n) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= n || s.charAt(i + 1) != '\'') flushHex(hexBytes, text);
            if (c == '{') {
                stack.add(st.clone());
                i++;
                // destino ignorável
                if (s.startsWith("\\*", i)) {
                    st[3] = true;
                }
            } else if (c == '}') {
                flushRtf(cur, text, st);
                if (!stack.isEmpty()) st = stack.remove(stack.size() - 1);
                i++;
            } else if (c == '\\') {
                i++;
                if (i >= n) break;
                char d = s.charAt(i);
                if (d == '\\' || d == '{' || d == '}') {
                    if (!st[3]) text.append(d);
                    i++;
                } else if (d == '\'') {
                    if (i + 2 < n) {
                        try {
                            int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
                            if (skipChars > 0) skipChars--;
                            else if (!st[3]) hexBytes.write(v);
                        } catch (Exception ignored) {
                        }
                    }
                    i += 3;
                } else if (Character.isLetter(d)) {
                    int start = i;
                    while (i < n && Character.isLetter(s.charAt(i))) i++;
                    String word = s.substring(start, i);
                    int numStart = i;
                    if (i < n && (s.charAt(i) == '-' || Character.isDigit(s.charAt(i)))) {
                        i++;
                        while (i < n && Character.isDigit(s.charAt(i))) i++;
                    }
                    String num = s.substring(numStart, i);
                    if (i < n && s.charAt(i) == ' ') i++;
                    int arg = num.isEmpty() ? -1 : safeInt(num);
                    if (word.equals("fonttbl") || word.equals("colortbl") || word.equals("stylesheet")
                            || word.equals("info") || word.equals("pict") || word.equals("header")
                            || word.equals("footer") || word.equals("listtable") || word.equals("listoverridetable")
                            || word.equals("themedata") || word.equals("datastore") || word.equals("latentstyles")
                            || word.equals("object") || word.equals("fldinst") || word.equals("rsidtbl")
                            || word.equals("generator") || word.equals("xmlnstbl") || word.equals("mmathPr")) {
                        st[3] = true;
                    } else if (st[3]) {
                        // ignora
                    } else if (word.equals("par") || word.equals("sect")) {
                        flushRtf(cur, text, st);
                        DocxReader.mergeRuns(cur);
                        if (intbl) {
                            text.append('\n');
                            continue;
                        }
                        table = null;
                        doc.add(cur);
                        cur = new Doc.Para();
                    } else if (word.equals("intbl")) {
                        intbl = true;
                    } else if (word.equals("line")) {
                        text.append('\n');
                    } else if (word.equals("tab")) {
                        text.append('\t');
                    } else if (word.equals("page")) {
                        flushRtf(cur, text, st);
                        doc.add(cur);
                        doc.add(new Doc.PageBreak());
                        cur = new Doc.Para();
                    } else if (word.equals("b")) {
                        flushRtf(cur, text, st);
                        st[0] = arg != 0;
                    } else if (word.equals("i")) {
                        flushRtf(cur, text, st);
                        st[1] = arg != 0;
                    } else if (word.equals("ul")) {
                        flushRtf(cur, text, st);
                        st[2] = arg != 0;
                    } else if (word.equals("ulnone")) {
                        flushRtf(cur, text, st);
                        st[2] = false;
                    } else if (word.equals("plain")) {
                        flushRtf(cur, text, st);
                        st[0] = st[1] = st[2] = false;
                    } else if (word.equals("qc")) {
                        cur.align = Doc.ALIGN_CENTER;
                    } else if (word.equals("qr")) {
                        cur.align = Doc.ALIGN_RIGHT;
                    } else if (word.equals("qj")) {
                        cur.align = Doc.ALIGN_JUSTIFY;
                    } else if (word.equals("uc")) {
                        uc = Math.max(0, arg);
                    } else if (word.equals("u")) {
                        int v = arg < 0 ? arg + 65536 : arg;
                        text.append((char) v);
                        skipChars = uc;
                    } else if (word.equals("emdash")) {
                        text.append('—');
                    } else if (word.equals("endash")) {
                        text.append('–');
                    } else if (word.equals("bullet")) {
                        text.append('•');
                    } else if (word.equals("lquote")) {
                        text.append('‘');
                    } else if (word.equals("rquote")) {
                        text.append('’');
                    } else if (word.equals("ldblquote")) {
                        text.append('“');
                    } else if (word.equals("rdblquote")) {
                        text.append('”');
                    } else if (word.equals("trowd")) {
                        if (table == null) {
                            flushRtf(cur, text, st);
                            if (!cur.runs.isEmpty()) doc.add(cur);
                            cur = new Doc.Para();
                            table = new Doc.Table();
                            doc.add(table);
                        }
                        row = new ArrayList<String>();
                    } else if (word.equals("cell")) {
                        flushRtf(cur, text, st);
                        if (row == null) row = new ArrayList<String>();
                        if (table == null) {
                            table = new Doc.Table();
                            doc.add(table);
                        }
                        row.add(cur.text().trim());
                        cur = new Doc.Para();
                    } else if (word.equals("row")) {
                        if (table != null && row != null) table.rows.add(row);
                        row = null;
                    } else if (word.equals("pard")) {
                        cur.align = Doc.ALIGN_LEFT;
                        intbl = false;
                    }
                } else {
                    // símbolos de controle: \~ \- \_ etc.
                    if (d == '~') text.append('\u00A0');
                    else if (d == '_') text.append('-');
                    i++;
                }
            } else if (c == '\r' || c == '\n') {
                i++;
            } else {
                if (skipChars > 0) skipChars--;
                else if (!st[3]) text.append(c);
                i++;
            }
        }
        flushHex(hexBytes, text);
        flushRtf(cur, text, st);
        if (!cur.isBlank()) doc.add(cur);
        return doc;
    }

    private static void flushHex(java.io.ByteArrayOutputStream hex, StringBuilder text) {
        if (hex.size() == 0) return;
        text.append(new String(hex.toByteArray(), java.nio.charset.Charset.forName("windows-1252")));
        hex.reset();
    }

    private static void flushRtf(Doc.Para p, StringBuilder text, boolean[] st) {
        if (text.length() == 0) return;
        Doc.Run r = new Doc.Run(text.toString());
        r.bold = st[0];
        r.italic = st[1];
        r.underline = st[2];
        p.runs.add(r);
        text.setLength(0);
    }

    private static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
