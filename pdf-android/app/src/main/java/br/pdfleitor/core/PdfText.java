package br.pdfleitor.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Extrator de texto de PDF escrito do zero (sem bibliotecas externas). Suporta
 * xref comprimido (object streams), filtros comuns, fontes simples e compostas,
 * CMaps ToUnicode, codificações com Differences e Form XObjects. Reconstrói
 * linhas e parágrafos a partir das posições dos glifos.
 */
public class PdfText {

    // ================================================================== objetos

    static final class Name {
        final String v;

        Name(String v) {
            this.v = v;
        }

        @Override
        public String toString() {
            return "/" + v;
        }
    }

    static final class Str {
        final byte[] b;

        Str(byte[] b) {
            this.b = b;
        }
    }

    static final class Ref {
        final int num;

        Ref(int num) {
            this.num = num;
        }
    }

    static final class Op {
        final String v;

        Op(String v) {
            this.v = v;
        }
    }

    static final class Stream {
        Map<String, Object> dict;
        byte[] src;
        int start, len;
    }

    static final Object NULL = new Object();
    static final Object EOF = new Object();
    static final Object ARR_END = new Object();
    static final Object DICT_END = new Object();

    // ================================================================== lexer

    static final class Lexer {
        final byte[] b;
        int pos;
        final int end;

        Lexer(byte[] b, int pos, int end) {
            this.b = b;
            this.pos = pos;
            this.end = Math.min(end, b.length);
        }

        static boolean ws(int c) {
            return c == 0 || c == 9 || c == 10 || c == 12 || c == 13 || c == 32;
        }

        static boolean delim(int c) {
            return c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}'
                    || c == '/' || c == '%';
        }

        void skipWs() {
            while (pos < end) {
                int c = b[pos] & 0xFF;
                if (ws(c)) pos++;
                else if (c == '%') {
                    while (pos < end && b[pos] != '\n' && b[pos] != '\r') pos++;
                } else break;
            }
        }

        /** Próximo token primitivo. */
        Object token() {
            skipWs();
            if (pos >= end) return EOF;
            int c = b[pos] & 0xFF;
            if (c == '[') {
                pos++;
                return Op.class; // marcador de início de array
            }
            if (c == ']') {
                pos++;
                return ARR_END;
            }
            if (c == '<') {
                if (pos + 1 < end && b[pos + 1] == '<') {
                    pos += 2;
                    return Map.class; // início de dicionário
                }
                return hexString();
            }
            if (c == '>') {
                if (pos + 1 < end && b[pos + 1] == '>') {
                    pos += 2;
                    return DICT_END;
                }
                pos++;
                return token();
            }
            if (c == '(') return literalString();
            if (c == '/') return name();
            if (c == '{' || c == '}' || c == ')') {
                pos++;
                return new Op(String.valueOf((char) c));
            }
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') {
                int s = pos;
                pos++;
                while (pos < end) {
                    int d = b[pos] & 0xFF;
                    if ((d >= '0' && d <= '9') || d == '.') pos++;
                    else break;
                }
                String t = new String(b, s, pos - s, ISO);
                try {
                    if (t.indexOf('.') >= 0) return Double.parseDouble(t);
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    return 0L;
                }
            }
            int s = pos;
            while (pos < end) {
                int d = b[pos] & 0xFF;
                if (ws(d) || delim(d)) break;
                pos++;
            }
            if (pos == s) {
                pos++;
                return token();
            }
            String t = new String(b, s, pos - s, ISO);
            if (t.equals("true")) return Boolean.TRUE;
            if (t.equals("false")) return Boolean.FALSE;
            if (t.equals("null")) return NULL;
            return new Op(t);
        }

        Name name() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < end) {
                int d = b[pos] & 0xFF;
                if (ws(d) || delim(d)) break;
                if (d == '#' && pos + 2 < end) {
                    try {
                        sb.append((char) Integer.parseInt(new String(b, pos + 1, 2, ISO), 16));
                        pos += 3;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                sb.append((char) d);
                pos++;
            }
            return new Name(sb.toString());
        }

        Str hexString() {
            pos++;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int hi = -1;
            while (pos < end) {
                int d = b[pos++] & 0xFF;
                if (d == '>') break;
                int v = Character.digit(d, 16);
                if (v < 0) continue;
                if (hi < 0) hi = v;
                else {
                    out.write(hi << 4 | v);
                    hi = -1;
                }
            }
            if (hi >= 0) out.write(hi << 4);
            return new Str(out.toByteArray());
        }

        Str literalString() {
            pos++;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int depth = 1;
            while (pos < end) {
                int d = b[pos++] & 0xFF;
                if (d == '\\') {
                    if (pos >= end) break;
                    int e = b[pos++] & 0xFF;
                    switch (e) {
                        case 'n': out.write('\n'); break;
                        case 'r': out.write('\r'); break;
                        case 't': out.write('\t'); break;
                        case 'b': out.write('\b'); break;
                        case 'f': out.write('\f'); break;
                        case '\r':
                            if (pos < end && b[pos] == '\n') pos++;
                            break;
                        case '\n': break;
                        default:
                            if (e >= '0' && e <= '7') {
                                int v = e - '0';
                                for (int k = 0; k < 2 && pos < end && b[pos] >= '0' && b[pos] <= '7'; k++)
                                    v = v * 8 + (b[pos++] - '0');
                                out.write(v & 0xFF);
                            } else out.write(e);
                    }
                } else if (d == '(') {
                    depth++;
                    out.write(d);
                } else if (d == ')') {
                    if (--depth == 0) break;
                    out.write(d);
                } else out.write(d);
            }
            return new Str(out.toByteArray());
        }

        /** Objeto completo (arrays/dicionários/referências). */
        Object object(boolean refs) {
            Object t = token();
            return complete(t, refs);
        }

        Object complete(Object t, boolean refs) {
            if (t == Op.class) {
                List<Object> list = new ArrayList<Object>();
                while (true) {
                    Object x = token();
                    if (x == ARR_END || x == EOF) break;
                    list.add(complete(x, refs));
                }
                return refs ? collapseRefs(list) : list;
            }
            if (t == Map.class) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                while (true) {
                    Object k = token();
                    if (k == DICT_END || k == EOF) break;
                    if (!(k instanceof Name)) continue;
                    Object v = token();
                    if (v == DICT_END || v == EOF) break;
                    v = complete(v, refs);
                    if (refs && v instanceof Long) {
                        // possível referência "n g R"
                        int save = pos;
                        Object g = token();
                        if (g instanceof Long) {
                            Object r = token();
                            if (r instanceof Op && ((Op) r).v.equals("R")) {
                                m.put(((Name) k).v, new Ref(((Long) v).intValue()));
                                continue;
                            }
                        }
                        pos = save;
                    }
                    m.put(((Name) k).v, v);
                }
                return m;
            }
            if (refs && t instanceof Long) {
                int save = pos;
                Object g = token();
                if (g instanceof Long) {
                    Object r = token();
                    if (r instanceof Op && ((Op) r).v.equals("R")) return new Ref(((Long) t).intValue());
                }
                pos = save;
            }
            return t;
        }

        private static List<Object> collapseRefs(List<Object> l) {
            List<Object> out = new ArrayList<Object>(l.size());
            for (int i = 0; i < l.size(); i++) {
                Object o = l.get(i);
                if (o instanceof Long && i + 2 < l.size() && l.get(i + 1) instanceof Long && l.get(i + 2) instanceof Op
                        && ((Op) l.get(i + 2)).v.equals("R")) {
                    out.add(new Ref(((Long) o).intValue()));
                    i += 2;
                } else out.add(o);
            }
            return out;
        }
    }

    static final Charset ISO = Charset.forName("ISO-8859-1");
    static final Charset CP1252 = Charset.forName("windows-1252");

    // ================================================================== arquivo

    private final byte[] data;
    private final Map<Integer, Integer> offsets = new HashMap<Integer, Integer>();
    private final Map<Integer, int[]> compressed = new HashMap<Integer, int[]>();
    private final Map<Integer, Object> cache = new HashMap<Integer, Object>();
    private final Map<Integer, byte[]> objStmCache = new HashMap<Integer, byte[]>();
    private final List<Map<String, Object>> pages = new ArrayList<Map<String, Object>>();
    public boolean encrypted;

    public PdfText(byte[] data) {
        this.data = data;
        scanObjects();
        encrypted = indexOf(data, "/Encrypt".getBytes(ISO), 0) >= 0 && findTrailerHas("/Encrypt");
        try {
            loadPages();
        } catch (Exception ignored) {
        }
    }

    public int pageCount() {
        return pages.size();
    }

    private void scanObjects() {
        byte[] pat = "obj".getBytes(ISO);
        int i = 0;
        List<Integer> streamsToCheck = new ArrayList<Integer>();
        while ((i = indexOf(data, pat, i)) >= 0) {
            int after = i + 3;
            if (after < data.length && !Lexer.ws(data[after] & 0xFF) && !Lexer.delim(data[after] & 0xFF)) {
                i = after;
                continue;
            }
            int j = i - 1;
            while (j >= 0 && Lexer.ws(data[j] & 0xFF)) j--;
            int genEnd = j;
            while (j >= 0 && data[j] >= '0' && data[j] <= '9') j--;
            if (j == genEnd) {
                i = after;
                continue;
            }
            int k = j;
            while (k >= 0 && Lexer.ws(data[k] & 0xFF)) k--;
            if (k == j) {
                i = after;
                continue;
            }
            int numEnd = k;
            while (k >= 0 && data[k] >= '0' && data[k] <= '9') k--;
            if (k == numEnd || numEnd - k > 10) {
                i = after;
                continue;
            }
            if (k >= 0 && !Lexer.ws(data[k] & 0xFF) && !Lexer.delim(data[k] & 0xFF)) {
                i = after;
                continue;
            }
            try {
                int num = Integer.parseInt(new String(data, k + 1, numEnd - k, ISO));
                offsets.put(num, k + 1);
                // object streams
                int limit = Math.min(data.length, after + 400);
                if (indexOf(data, "/ObjStm".getBytes(ISO), after, limit) >= 0) streamsToCheck.add(num);
            } catch (NumberFormatException ignored) {
            }
            i = after;
        }
        for (int num : streamsToCheck) {
            try {
                Object o = getObject(num);
                if (!(o instanceof Stream)) continue;
                Stream s = (Stream) o;
                if (!"ObjStm".equals(nameOf(s.dict.get("Type")))) continue;
                byte[] dec = decode(s);
                objStmCache.put(num, dec);
                int n = intOf(s.dict.get("N"), 0);
                Lexer lx = new Lexer(dec, 0, dec.length);
                for (int idx = 0; idx < n; idx++) {
                    Object a = lx.token(), b = lx.token();
                    if (!(a instanceof Long) || !(b instanceof Long)) break;
                    int objNum = ((Long) a).intValue();
                    if (!offsets.containsKey(objNum) || compressed.containsKey(objNum))
                        compressed.put(objNum, new int[]{num, ((Long) b).intValue()});
                }
            } catch (Exception ignored) {
            }
        }
        // objetos comprimidos têm prioridade quando não houver definição direta
        for (Integer k2 : compressed.keySet()) {
            if (offsets.containsKey(k2)) {
                // se ambos existem, mantém o direto
                continue;
            }
        }
    }

    private boolean findTrailerHas(String key) {
        // procura no trailer clássico ou no dicionário do xref stream
        int t = lastIndexOf(data, "trailer".getBytes(ISO));
        if (t >= 0) {
            int e = Math.min(data.length, t + 2000);
            if (indexOf(data, key.getBytes(ISO), t, e) >= 0) return true;
        }
        for (Map.Entry<Integer, Integer> en : offsets.entrySet()) {
            int off = en.getValue();
            int lim = Math.min(data.length, off + 600);
            if (indexOf(data, "/XRef".getBytes(ISO), off, lim) >= 0 && indexOf(data, key.getBytes(ISO), off, lim) >= 0)
                return true;
        }
        return false;
    }

    Object getObject(int num) {
        if (cache.containsKey(num)) return cache.get(num);
        cache.put(num, NULL); // evita recursão infinita
        Object result = NULL;
        try {
            Integer off = offsets.get(num);
            if (off != null) {
                Lexer lx = new Lexer(data, off, data.length);
                lx.token();
                lx.token();
                lx.token(); // "n g obj"
                Object o = lx.object(true);
                if (o instanceof Map) {
                    int save = lx.pos;
                    Object kw = lx.token();
                    if (kw instanceof Op && ((Op) kw).v.equals("stream")) {
                        int p = lx.pos;
                        if (p < data.length && data[p] == '\r') p++;
                        if (p < data.length && data[p] == '\n') p++;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dict = (Map<String, Object>) o;
                        Stream s = new Stream();
                        s.dict = dict;
                        s.src = data;
                        s.start = p;
                        Object lenObj = dict.get("Length");
                        int len = -1;
                        if (lenObj instanceof Long) len = ((Long) lenObj).intValue();
                        else if (lenObj instanceof Ref) {
                            Object lv = getObject(((Ref) lenObj).num);
                            if (lv instanceof Long) len = ((Long) lv).intValue();
                        }
                        int es = indexOf(data, "endstream".getBytes(ISO), p);
                        if (len < 0 || p + len > data.length || (es >= 0 && p + len > es + 1)) {
                            len = es < 0 ? data.length - p : es - p;
                            while (len > 0 && (data[p + len - 1] == '\n' || data[p + len - 1] == '\r')) len--;
                        }
                        s.len = Math.max(0, len);
                        result = s;
                    } else {
                        lx.pos = save;
                        result = o;
                    }
                } else result = o;
            } else {
                int[] c = compressed.get(num);
                if (c != null) {
                    byte[] dec = objStmCache.get(c[0]);
                    Object so = getObject(c[0]);
                    if (dec == null && so instanceof Stream) {
                        dec = decode((Stream) so);
                        objStmCache.put(c[0], dec);
                    }
                    if (dec != null && so instanceof Stream) {
                        int first = intOf(((Stream) so).dict.get("First"), 0);
                        int n = intOf(((Stream) so).dict.get("N"), 0);
                        Lexer lx = new Lexer(dec, 0, dec.length);
                        int rel = -1;
                        for (int idx = 0; idx < n; idx++) {
                            Object a = lx.token(), b = lx.token();
                            if (!(a instanceof Long) || !(b instanceof Long)) break;
                            if (((Long) a).intValue() == num) {
                                rel = ((Long) b).intValue();
                                break;
                            }
                        }
                        if (rel >= 0) {
                            Lexer ox = new Lexer(dec, first + rel, dec.length);
                            result = ox.object(true);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        cache.put(num, result);
        return result;
    }

    Object resolve(Object o) {
        int guard = 0;
        while (o instanceof Ref && guard++ < 32) o = getObject(((Ref) o).num);
        return o;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> dict(Object o) {
        o = resolve(o);
        if (o instanceof Map) return (Map<String, Object>) o;
        if (o instanceof Stream) return ((Stream) o).dict;
        return null;
    }

    @SuppressWarnings("unchecked")
    List<Object> array(Object o) {
        o = resolve(o);
        return o instanceof List ? (List<Object>) o : null;
    }

    String nameOf(Object o) {
        o = resolve(o);
        return o instanceof Name ? ((Name) o).v : null;
    }

    int intOf(Object o, int def) {
        o = resolve(o);
        if (o instanceof Long) return ((Long) o).intValue();
        if (o instanceof Double) return ((Double) o).intValue();
        return def;
    }

    static double num(Object o) {
        if (o instanceof Long) return (Long) o;
        if (o instanceof Double) return (Double) o;
        return 0;
    }

    double numR(Object o, double def) {
        o = resolve(o);
        if (o instanceof Long) return (Long) o;
        if (o instanceof Double) return (Double) o;
        return def;
    }

    // ================================================================== páginas

    private void loadPages() {
        Map<String, Object> catalog = null;
        // procura a última referência /Root
        byte[] rootKey = "/Root".getBytes(ISO);
        int idx = data.length;
        while (catalog == null && (idx = lastIndexOf(data, rootKey, idx - 1)) >= 0) {
            Lexer lx = new Lexer(data, idx + 5, Math.min(data.length, idx + 40));
            Object o = lx.object(true);
            if (o instanceof Ref) {
                Map<String, Object> d = dict(o);
                if (d != null && d.containsKey("Pages")) catalog = d;
            }
        }
        if (catalog == null) {
            for (Integer num : new ArrayList<Integer>(offsets.keySet())) {
                Map<String, Object> d = dict(getObject(num));
                if (d != null && "Catalog".equals(nameOf(d.get("Type")))) {
                    catalog = d;
                    break;
                }
            }
        }
        if (catalog == null) {
            for (Integer num : new ArrayList<Integer>(compressed.keySet())) {
                Map<String, Object> d = dict(getObject(num));
                if (d != null && "Catalog".equals(nameOf(d.get("Type")))) {
                    catalog = d;
                    break;
                }
            }
        }
        if (catalog == null) return;
        walkPages(dict(catalog.get("Pages")), null, 0);
    }

    private void walkPages(Map<String, Object> node, Object inheritedRes, int depth) {
        if (node == null || depth > 64 || pages.size() > 100000) return;
        Object res = node.containsKey("Resources") ? node.get("Resources") : inheritedRes;
        List<Object> kids = array(node.get("Kids"));
        String type = nameOf(node.get("Type"));
        if (kids != null && !"Page".equals(type)) {
            for (Object k : kids) walkPages(dict(k), res, depth + 1);
        } else {
            Map<String, Object> page = new HashMap<String, Object>(node);
            if (res != null) page.put("Resources", res);
            pages.add(page);
        }
    }

    // ================================================================== filtros

    byte[] decode(Stream s) {
        byte[] raw = new byte[s.len];
        System.arraycopy(s.src, s.start, raw, 0, Math.min(s.len, s.src.length - s.start));
        Object f = resolve(s.dict.get("Filter"));
        Object parms = resolve(s.dict.get("DecodeParms"));
        if (f == null) return raw;
        List<Object> filters = new ArrayList<Object>();
        List<Object> params = new ArrayList<Object>();
        if (f instanceof List) {
            filters.addAll(array(f));
            List<Object> pl = array(parms);
            if (pl != null) params.addAll(pl);
        } else {
            filters.add(f);
            params.add(parms);
        }
        byte[] cur = raw;
        for (int i = 0; i < filters.size(); i++) {
            String name = nameOf(filters.get(i));
            Map<String, Object> p = i < params.size() ? dict(params.get(i)) : null;
            if (name == null) continue;
            if (name.equals("FlateDecode") || name.equals("Fl")) cur = predictor(inflate(cur), p);
            else if (name.equals("LZWDecode") || name.equals("LZW")) cur = predictor(lzw(cur), p);
            else if (name.equals("ASCIIHexDecode") || name.equals("AHx")) cur = asciiHex(cur);
            else if (name.equals("ASCII85Decode") || name.equals("A85")) cur = ascii85(cur);
            else if (name.equals("RunLengthDecode") || name.equals("RL")) cur = runLength(cur);
            else return cur; // filtros de imagem: não precisamos do conteúdo
        }
        return cur;
    }

    static byte[] inflate(byte[] in) {
        Inflater inf = new Inflater();
        inf.setInput(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 3);
        byte[] buf = new byte[16384];
        try {
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break;
                }
                out.write(buf, 0, n);
                if (out.size() > 256 * 1024 * 1024) break;
            }
        } catch (Exception e) {
            // dados parcialmente corrompidos: devolve o que conseguiu
            if (out.size() == 0) {
                // tenta sem cabeçalho zlib
                Inflater raw = new Inflater(true);
                try {
                    raw.setInput(in, 2, Math.max(0, in.length - 2));
                    while (!raw.finished()) {
                        int n = raw.inflate(buf);
                        if (n == 0) break;
                        out.write(buf, 0, n);
                    }
                } catch (Exception ignored) {
                } finally {
                    raw.end();
                }
            }
        } finally {
            inf.end();
        }
        return out.toByteArray();
    }

    byte[] predictor(byte[] in, Map<String, Object> p) {
        if (p == null) return in;
        int pred = intOf(p.get("Predictor"), 1);
        if (pred < 10) return in;
        int colors = intOf(p.get("Colors"), 1), bpc = intOf(p.get("BitsPerComponent"), 8);
        int cols = intOf(p.get("Columns"), 1);
        int bpp = Math.max(1, colors * bpc / 8);
        int rowLen = (cols * colors * bpc + 7) / 8;
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        byte[] prev = new byte[rowLen];
        byte[] row = new byte[rowLen];
        int i = 0;
        while (i + rowLen < in.length + 1 && i < in.length) {
            int type = in[i++] & 0xFF;
            int n = Math.min(rowLen, in.length - i);
            System.arraycopy(in, i, row, 0, n);
            i += n;
            for (int x = 0; x < rowLen; x++) {
                int a = x >= bpp ? row[x - bpp] & 0xFF : 0;
                int b = prev[x] & 0xFF;
                int c = x >= bpp ? prev[x - bpp] & 0xFF : 0;
                int v = row[x] & 0xFF;
                switch (type) {
                    case 1: v += a; break;
                    case 2: v += b; break;
                    case 3: v += (a + b) / 2; break;
                    case 4: {
                        int pp = a + b - c, pa = Math.abs(pp - a), pb = Math.abs(pp - b), pc = Math.abs(pp - c);
                        v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
                        break;
                    }
                    default: break;
                }
                row[x] = (byte) v;
            }
            out.write(row, 0, rowLen);
            byte[] t = prev;
            prev = row;
            row = t;
        }
        return out.toByteArray();
    }

    static byte[] asciiHex(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length / 2);
        int hi = -1;
        for (byte b : in) {
            if (b == '>') break;
            int v = Character.digit(b, 16);
            if (v < 0) continue;
            if (hi < 0) hi = v;
            else {
                out.write(hi << 4 | v);
                hi = -1;
            }
        }
        if (hi >= 0) out.write(hi << 4);
        return out.toByteArray();
    }

    static byte[] ascii85(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        long tuple = 0;
        int count = 0;
        for (int i = 0; i < in.length; i++) {
            int c = in[i] & 0xFF;
            if (c == '~') break;
            if (Lexer.ws(c)) continue;
            if (c == 'z' && count == 0) {
                out.write(0);
                out.write(0);
                out.write(0);
                out.write(0);
                continue;
            }
            if (c < '!' || c > 'u') continue;
            tuple = tuple * 85 + (c - '!');
            if (++count == 5) {
                out.write((int) (tuple >> 24));
                out.write((int) (tuple >> 16));
                out.write((int) (tuple >> 8));
                out.write((int) tuple);
                tuple = 0;
                count = 0;
            }
        }
        if (count > 1) {
            for (int k = count; k < 5; k++) tuple = tuple * 85 + 84;
            for (int k = 0; k < count - 1; k++) out.write((int) (tuple >> (24 - 8 * k)));
        }
        return out.toByteArray();
    }

    static byte[] runLength(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 2);
        int i = 0;
        while (i < in.length) {
            int len = in[i++] & 0xFF;
            if (len == 128) break;
            if (len < 128) {
                int n = Math.min(len + 1, in.length - i);
                out.write(in, i, n);
                i += n;
            } else if (i < in.length) {
                byte b = in[i++];
                for (int k = 0; k < 257 - len; k++) out.write(b);
            }
        }
        return out.toByteArray();
    }

    static byte[] lzw(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length * 3);
        List<byte[]> table = new ArrayList<byte[]>();
        int codeLen = 9;
        long bitBuf = 0;
        int bits = 0;
        byte[] prev = null;
        int i = 0;
        resetLzw(table);
        while (true) {
            while (bits < codeLen && i < in.length) {
                bitBuf = (bitBuf << 8) | (in[i++] & 0xFF);
                bits += 8;
            }
            if (bits < codeLen) break;
            int code = (int) ((bitBuf >> (bits - codeLen)) & ((1 << codeLen) - 1));
            bits -= codeLen;
            if (code == 256) {
                resetLzw(table);
                codeLen = 9;
                prev = null;
                continue;
            }
            if (code == 257) break;
            byte[] entry;
            if (code < table.size()) entry = table.get(code);
            else if (prev != null) {
                entry = new byte[prev.length + 1];
                System.arraycopy(prev, 0, entry, 0, prev.length);
                entry[prev.length] = prev[0];
            } else break;
            out.write(entry, 0, entry.length);
            if (prev != null) {
                byte[] n = new byte[prev.length + 1];
                System.arraycopy(prev, 0, n, 0, prev.length);
                n[prev.length] = entry[0];
                table.add(n);
            }
            prev = entry;
            int size = table.size();
            if (size >= 511 && codeLen == 9) codeLen = 10;
            else if (size >= 1023 && codeLen == 10) codeLen = 11;
            else if (size >= 2047 && codeLen == 11) codeLen = 12;
        }
        return out.toByteArray();
    }

    private static void resetLzw(List<byte[]> table) {
        table.clear();
        for (int k = 0; k < 256; k++) table.add(new byte[]{(byte) k});
        table.add(new byte[0]);
        table.add(new byte[0]);
    }

    // ================================================================== fontes

    final class Font {
        Map<Integer, String> toUni;
        String[] codeToName; // Differences
        int bytes = 1;
        double[] widths;
        int firstChar;
        double missingWidth = 500;
        Map<Integer, Double> cidWidths;
        double defaultCid = 1000;
        boolean bold;
        boolean symbolic;
        boolean unicodeCodes; // códigos já são Unicode (CMaps UCS2)
        boolean type3;
        double t3scale = 0.001;
        boolean noUnicode; // Type0 sem ToUnicode

        String decode(int code) {
            if (toUni != null) {
                String s = toUni.get(code);
                if (s != null) return s;
            }
            if (bytes == 2) {
                if (unicodeCodes && code > 31) return String.valueOf((char) code);
                return "";
            }
            if (codeToName != null && code < 256 && codeToName[code] != null) {
                String u = Glyphs.toUnicode(codeToName[code]);
                if (u != null) return u;
            }
            if (symbolic && toUni == null && codeToName == null) {
                if (code >= 0xF020 && code <= 0xF0FF) code -= 0xF000;
            }
            if (code < 32) return code == 9 ? " " : "";
            return new String(new byte[]{(byte) code}, CP1252);
        }

        double width(int code) {
            if (bytes == 2) {
                if (cidWidths != null) {
                    Double w = cidWidths.get(code);
                    if (w != null) return w / 1000.0;
                }
                return defaultCid / 1000.0;
            }
            if (widths != null) {
                int i = code - firstChar;
                if (i >= 0 && i < widths.length) {
                    double w = widths[i] * (type3 ? t3scale : 0.001);
                    if (w > 0) return w;
                }
            }
            return missingWidth / 1000.0;
        }
    }

    private final Map<Object, Font> fontCache = new HashMap<Object, Font>();

    Font loadFont(Object ref) {
        Object key = ref instanceof Ref ? (Object) ((Ref) ref).num : ref;
        if (key != null && fontCache.containsKey(key)) return fontCache.get(key);
        Font f = new Font();
        Map<String, Object> d = dict(ref);
        if (d != null) {
            try {
                buildFont(f, d);
            } catch (Exception ignored) {
            }
        }
        if (key != null) fontCache.put(key, f);
        return f;
    }

    private void buildFont(Font f, Map<String, Object> d) {
        String subtype = nameOf(d.get("Subtype"));
        String base = nameOf(d.get("BaseFont"));
        if (base != null) {
            String lb = base.toLowerCase();
            f.bold = lb.contains("bold") || lb.contains("black") || lb.contains("heavy") || lb.contains("semibold");
            if (lb.contains("symbol") || lb.contains("dingbat") || lb.contains("wingding")) f.symbolic = true;
        }
        Object tu = resolve(d.get("ToUnicode"));
        if (tu instanceof Stream) f.toUni = parseCMap(decode((Stream) tu), f);
        if ("Type0".equals(subtype)) {
            f.bytes = 2;
            Object enc = resolve(d.get("Encoding"));
            if (enc instanceof Name) {
                String e = ((Name) enc).v;
                if (e.contains("UCS2") || e.contains("UTF16")) f.unicodeCodes = true;
            } else if (enc instanceof Stream) {
                int[] cs = new int[]{2};
                parseCodespace(decode((Stream) enc), cs);
                f.bytes = cs[0];
            }
            List<Object> desc = array(d.get("DescendantFonts"));
            if (desc != null && !desc.isEmpty()) {
                Map<String, Object> cid = dict(desc.get(0));
                if (cid != null) {
                    f.defaultCid = numR(cid.get("DW"), 1000);
                    List<Object> w = array(cid.get("W"));
                    if (w != null) {
                        f.cidWidths = new HashMap<Integer, Double>();
                        int i = 0;
                        while (i < w.size()) {
                            Object a = resolve(w.get(i));
                            if (i + 1 >= w.size()) break;
                            Object b = resolve(w.get(i + 1));
                            if (b instanceof List) {
                                int c0 = (int) num(a);
                                List<Object> ws = array(b);
                                for (int k = 0; k < ws.size(); k++) f.cidWidths.put(c0 + k, numR(ws.get(k), 1000));
                                i += 2;
                            } else {
                                if (i + 2 >= w.size()) break;
                                int c0 = (int) num(a), c1 = (int) num(b);
                                double wv = numR(w.get(i + 2), 1000);
                                for (int c = c0; c <= c1 && c - c0 < 65536; c++) f.cidWidths.put(c, wv);
                                i += 3;
                            }
                        }
                    }
                    Map<String, Object> fd = dict(cid.get("FontDescriptor"));
                    if (fd != null) checkDescriptor(f, fd);
                }
            }
            if (f.toUni == null && !f.unicodeCodes) f.noUnicode = true;
            return;
        }
        f.type3 = "Type3".equals(subtype);
        if (f.type3) {
            List<Object> fm = array(d.get("FontMatrix"));
            if (fm != null && !fm.isEmpty()) f.t3scale = numR(fm.get(0), 0.001);
        }
        f.firstChar = intOf(d.get("FirstChar"), 0);
        List<Object> w = array(d.get("Widths"));
        if (w != null) {
            f.widths = new double[w.size()];
            for (int i = 0; i < w.size(); i++) f.widths[i] = numR(w.get(i), 0);
        } else if (base != null) {
            f.missingWidth = base.toLowerCase().contains("courier") ? 600 : 520;
        }
        Map<String, Object> fd = dict(d.get("FontDescriptor"));
        if (fd != null) {
            checkDescriptor(f, fd);
            if (fd.containsKey("MissingWidth")) f.missingWidth = numR(fd.get("MissingWidth"), f.missingWidth);
        }
        Object enc = resolve(d.get("Encoding"));
        String baseEnc = null;
        List<Object> diffs = null;
        if (enc instanceof Name) baseEnc = ((Name) enc).v;
        else if (enc instanceof Map) {
            Map<String, Object> ed = dict(enc);
            baseEnc = nameOf(ed.get("BaseEncoding"));
            diffs = array(ed.get("Differences"));
        }
        if ("MacRomanEncoding".equals(baseEnc)) {
            f.codeToName = new String[256];
            for (int c = 128; c < 256; c++) f.codeToName[c] = Glyphs.MAC_ROMAN_HIGH[c - 128];
        }
        if (diffs != null) {
            if (f.codeToName == null) f.codeToName = new String[256];
            int code = 0;
            for (Object o : diffs) {
                o = resolve(o);
                if (o instanceof Long || o instanceof Double) code = (int) num(o);
                else if (o instanceof Name) {
                    if (code >= 0 && code < 256) f.codeToName[code] = ((Name) o).v;
                    code++;
                }
            }
        }
    }

    private void checkDescriptor(Font f, Map<String, Object> fd) {
        int flags = intOf(fd.get("Flags"), 0);
        if ((flags & (1 << 18)) != 0) f.bold = true;
        double weight = numR(fd.get("FontWeight"), 0);
        if (weight >= 600) f.bold = true;
        String fn = nameOf(fd.get("FontName"));
        if (fn != null && fn.toLowerCase().contains("bold")) f.bold = true;
        if ((flags & 4) != 0 && (flags & 32) == 0) f.symbolic = true;
    }

    private void parseCodespace(byte[] cmap, int[] bytesOut) {
        Lexer lx = new Lexer(cmap, 0, cmap.length);
        boolean in = false;
        while (true) {
            Object t = lx.token();
            if (t == EOF) break;
            if (t instanceof Op) {
                String v = ((Op) t).v;
                if (v.equals("begincodespacerange")) in = true;
                else if (v.equals("endcodespacerange")) break;
            } else if (in && t instanceof Str) {
                bytesOut[0] = Math.max(1, Math.min(4, ((Str) t).b.length));
                return;
            }
        }
    }

    private Map<Integer, String> parseCMap(byte[] cmap, Font f) {
        Map<Integer, String> m = new HashMap<Integer, String>();
        Lexer lx = new Lexer(cmap, 0, cmap.length);
        List<Object> buf = new ArrayList<Object>();
        int mode = 0; // 1 = bfchar, 2 = bfrange, 3 = codespace
        int codeBytes = 0;
        while (true) {
            Object t = lx.token();
            if (t == EOF) break;
            if (t == Op.class) {
                t = lx.complete(t, false);
            }
            if (t instanceof Op) {
                String v = ((Op) t).v;
                if (v.equals("beginbfchar")) {
                    mode = 1;
                    buf.clear();
                } else if (v.equals("beginbfrange")) {
                    mode = 2;
                    buf.clear();
                } else if (v.equals("begincodespacerange")) {
                    mode = 3;
                    buf.clear();
                } else if (v.equals("endbfchar")) {
                    for (int i = 0; i + 1 < buf.size(); i += 2) {
                        if (buf.get(i) instanceof Str && buf.get(i + 1) instanceof Str) {
                            Str src = (Str) buf.get(i);
                            if (codeBytes == 0) codeBytes = src.b.length;
                            m.put(bytesToInt(src.b), utf16(((Str) buf.get(i + 1)).b));
                        } else if (buf.get(i) instanceof Str && buf.get(i + 1) instanceof Name) {
                            String u = Glyphs.toUnicode(((Name) buf.get(i + 1)).v);
                            if (u != null) m.put(bytesToInt(((Str) buf.get(i)).b), u);
                        }
                    }
                    mode = 0;
                } else if (v.equals("endbfrange")) {
                    for (int i = 0; i + 2 < buf.size(); i += 3) {
                        if (!(buf.get(i) instanceof Str) || !(buf.get(i + 1) instanceof Str)) continue;
                        Str lo = (Str) buf.get(i);
                        if (codeBytes == 0) codeBytes = lo.b.length;
                        int a = bytesToInt(lo.b), b = bytesToInt(((Str) buf.get(i + 1)).b);
                        Object dst = buf.get(i + 2);
                        if (b - a > 65536 || b < a) continue;
                        if (dst instanceof Str) {
                            byte[] base = ((Str) dst).b;
                            for (int c = a; c <= b; c++) {
                                byte[] cur = base.clone();
                                int add = c - a;
                                // incrementa o último byte (com transporte)
                                for (int k = cur.length - 1; k >= 0 && add > 0; k--) {
                                    int v2 = (cur[k] & 0xFF) + add;
                                    cur[k] = (byte) v2;
                                    add = v2 >> 8;
                                }
                                m.put(c, utf16(cur));
                            }
                        } else if (dst instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> l = (List<Object>) dst;
                            for (int c = a; c <= b && c - a < l.size(); c++) {
                                Object o = l.get(c - a);
                                if (o instanceof Str) m.put(c, utf16(((Str) o).b));
                            }
                        }
                    }
                    mode = 0;
                } else if (v.equals("endcodespacerange")) {
                    for (Object o : buf) {
                        if (o instanceof Str) {
                            codeBytes = ((Str) o).b.length;
                            break;
                        }
                    }
                    mode = 0;
                }
            } else if (mode != 0) {
                buf.add(t);
            }
        }
        if (f.bytes == 2 && codeBytes == 1) f.bytes = 1;
        return m;
    }

    static int bytesToInt(byte[] b) {
        int v = 0;
        for (byte x : b) v = (v << 8) | (x & 0xFF);
        return v;
    }

    static String utf16(byte[] b) {
        if (b.length == 1) return String.valueOf((char) (b[0] & 0xFF));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < b.length; i += 2) sb.append((char) (((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF)));
        return sb.toString();
    }

    // ================================================================== conteúdo

    /** Fragmento de texto posicionado. */
    static final class Chunk {
        double x, y, endX, size;
        final StringBuilder text = new StringBuilder();
        boolean bold;
        int order;
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[0] * b[0] + a[1] * b[2], a[0] * b[1] + a[1] * b[3],
                a[2] * b[0] + a[3] * b[2], a[2] * b[1] + a[3] * b[3],
                a[4] * b[0] + a[5] * b[2] + b[4], a[4] * b[1] + a[5] * b[3] + b[5]};
    }

    private final class Gs {
        double[] ctm = {1, 0, 0, 1, 0, 0};
        Font font;
        double fs = 12, tc, tw, th = 1, tl, rise;

        Gs copy() {
            Gs g = new Gs();
            g.ctm = ctm.clone();
            g.font = font;
            g.fs = fs;
            g.tc = tc;
            g.tw = tw;
            g.th = th;
            g.tl = tl;
            g.rise = rise;
            return g;
        }
    }

    private int chunkOrder;

    private void runContent(byte[] content, Map<String, Object> res, Gs gs0, List<Chunk> out, int depth) {
        if (depth > 6 || content == null) return;
        Lexer lx = new Lexer(content, 0, content.length);
        List<Object> ops = new ArrayList<Object>();
        List<Gs> stack = new ArrayList<Gs>();
        Gs gs = gs0;
        double[] tm = {1, 0, 0, 1, 0, 0}, tlm = {1, 0, 0, 1, 0, 0};
        Chunk cur = null;
        Map<String, Object> fonts = res == null ? null : dict(res.get("Font"));
        Map<String, Object> xobjs = res == null ? null : dict(res.get("XObject"));
        int guard = 0;
        while (guard++ < 5000000) {
            Object t = lx.token();
            if (t == EOF) break;
            if (t == Op.class || t == Map.class) {
                ops.add(lx.complete(t, false));
                continue;
            }
            if (!(t instanceof Op)) {
                ops.add(t);
                continue;
            }
            String op = ((Op) t).v;
            try {
                switch (op) {
                    case "q":
                        stack.add(gs.copy());
                        break;
                    case "Q":
                        if (!stack.isEmpty()) gs = stack.remove(stack.size() - 1);
                        break;
                    case "cm":
                        if (ops.size() >= 6) {
                            int o = ops.size() - 6;
                            double[] m = {num(ops.get(o)), num(ops.get(o + 1)), num(ops.get(o + 2)),
                                    num(ops.get(o + 3)), num(ops.get(o + 4)), num(ops.get(o + 5))};
                            gs.ctm = mul(m, gs.ctm);
                        }
                        break;
                    case "BT":
                        tm = new double[]{1, 0, 0, 1, 0, 0};
                        tlm = tm.clone();
                        cur = null;
                        break;
                    case "ET":
                        cur = null;
                        break;
                    case "Tf":
                        if (ops.size() >= 2) {
                            Object fn = ops.get(ops.size() - 2);
                            gs.fs = num(ops.get(ops.size() - 1));
                            if (fn instanceof Name && fonts != null) gs.font = loadFont(fonts.get(((Name) fn).v));
                            cur = null;
                        }
                        break;
                    case "Tc":
                        if (!ops.isEmpty()) gs.tc = num(ops.get(ops.size() - 1));
                        break;
                    case "Tw":
                        if (!ops.isEmpty()) gs.tw = num(ops.get(ops.size() - 1));
                        break;
                    case "Tz":
                        if (!ops.isEmpty()) gs.th = num(ops.get(ops.size() - 1)) / 100.0;
                        break;
                    case "TL":
                        if (!ops.isEmpty()) gs.tl = num(ops.get(ops.size() - 1));
                        break;
                    case "Ts":
                        if (!ops.isEmpty()) gs.rise = num(ops.get(ops.size() - 1));
                        break;
                    case "Td":
                    case "TD":
                        if (ops.size() >= 2) {
                            double tx = num(ops.get(ops.size() - 2)), ty = num(ops.get(ops.size() - 1));
                            if (op.equals("TD")) gs.tl = -ty;
                            tlm = mul(new double[]{1, 0, 0, 1, tx, ty}, tlm);
                            tm = tlm.clone();
                            cur = null;
                        }
                        break;
                    case "Tm":
                        if (ops.size() >= 6) {
                            int o = ops.size() - 6;
                            tlm = new double[]{num(ops.get(o)), num(ops.get(o + 1)), num(ops.get(o + 2)),
                                    num(ops.get(o + 3)), num(ops.get(o + 4)), num(ops.get(o + 5))};
                            tm = tlm.clone();
                            cur = null;
                        }
                        break;
                    case "T*":
                        tlm = mul(new double[]{1, 0, 0, 1, 0, -gs.tl}, tlm);
                        tm = tlm.clone();
                        cur = null;
                        break;
                    case "Tj":
                    case "'":
                    case "\"":
                        if (op.equals("'") || op.equals("\"")) {
                            if (op.equals("\"") && ops.size() >= 3) {
                                gs.tw = num(ops.get(ops.size() - 3));
                                gs.tc = num(ops.get(ops.size() - 2));
                            }
                            tlm = mul(new double[]{1, 0, 0, 1, 0, -gs.tl}, tlm);
                            tm = tlm.clone();
                            cur = null;
                        }
                        if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof Str) {
                            cur = show(((Str) ops.get(ops.size() - 1)).b, gs, tm, cur, out);
                        }
                        break;
                    case "TJ":
                        if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> arr = (List<Object>) ops.get(ops.size() - 1);
                            for (Object o : arr) {
                                if (o instanceof Str) cur = show(((Str) o).b, gs, tm, cur, out);
                                else if (o instanceof Long || o instanceof Double) {
                                    double adj = num(o);
                                    double tx = -adj / 1000.0 * gs.fs * gs.th;
                                    double[] nt = mul(new double[]{1, 0, 0, 1, tx, 0}, tm);
                                    System.arraycopy(nt, 0, tm, 0, 6);
                                    if (cur != null && -adj > 180 && cur.text.length() > 0
                                            && cur.text.charAt(cur.text.length() - 1) != ' ') {
                                        cur.text.append(' ');
                                    }
                                    if (cur != null) cur.endX = devX(tm, gs);
                                }
                            }
                        }
                        break;
                    case "Do":
                        if (!ops.isEmpty() && ops.get(ops.size() - 1) instanceof Name && xobjs != null) {
                            Object xo = resolve(xobjs.get(((Name) ops.get(ops.size() - 1)).v));
                            if (xo instanceof Stream && "Form".equals(nameOf(((Stream) xo).dict.get("Subtype")))) {
                                Stream xs = (Stream) xo;
                                Gs g2 = gs.copy();
                                List<Object> mat = array(xs.dict.get("Matrix"));
                                if (mat != null && mat.size() == 6) {
                                    double[] m = new double[6];
                                    for (int k = 0; k < 6; k++) m[k] = numR(mat.get(k), 0);
                                    g2.ctm = mul(m, g2.ctm);
                                }
                                Map<String, Object> r2 = dict(xs.dict.get("Resources"));
                                runContent(decode(xs), r2 != null ? r2 : res, g2, out, depth + 1);
                            }
                        }
                        break;
                    case "BI":
                        // imagem embutida: pula até EI
                        int ei = lx.pos;
                        while (ei + 2 < content.length) {
                            if (content[ei] == 'E' && content[ei + 1] == 'I' && (ei == 0 || Lexer.ws(content[ei - 1] & 0xFF))
                                    && (ei + 2 >= content.length || Lexer.ws(content[ei + 2] & 0xFF))) break;
                            ei++;
                        }
                        lx.pos = Math.min(content.length, ei + 2);
                        break;
                    default:
                        break;
                }
            } catch (Exception ignored) {
            }
            ops.clear();
        }
    }

    private double devX(double[] tm, Gs gs) {
        double[] m = mul(tm, gs.ctm);
        return m[4];
    }

    private Chunk show(byte[] s, Gs gs, double[] tm, Chunk cur, List<Chunk> out) {
        Font f = gs.font != null ? gs.font : new Font();
        int i = 0;
        while (i < s.length) {
            int code;
            int nb = f.bytes;
            if (nb >= 2 && i + nb <= s.length) {
                code = 0;
                for (int k = 0; k < nb; k++) code = (code << 8) | (s[i + k] & 0xFF);
                i += nb;
            } else {
                code = s[i] & 0xFF;
                i++;
            }
            String u = f.decode(code);
            double w0 = f.width(code);
            double[] trm = mul(new double[]{gs.fs * gs.th, 0, 0, gs.fs, 0, gs.rise}, mul(tm, gs.ctm));
            double size = Math.sqrt(trm[2] * trm[2] + trm[3] * trm[3]);
            double x = trm[4], y = trm[5];
            if (u.length() > 0) {
                boolean sameLine = cur != null && Math.abs(cur.y - y) < Math.max(1, size * 0.3)
                        && x >= cur.endX - size * 0.6 && x - cur.endX < size * 0.12;
                if (!sameLine) {
                    cur = new Chunk();
                    cur.x = x;
                    cur.y = y;
                    cur.size = size;
                    cur.bold = f.bold;
                    cur.order = chunkOrder++;
                    out.add(cur);
                }
                cur.text.append(u);
            }
            double tx = (w0 * gs.fs + gs.tc + (code == 32 && nb == 1 ? gs.tw : 0)) * gs.th;
            double[] nt = mul(new double[]{1, 0, 0, 1, tx, 0}, tm);
            System.arraycopy(nt, 0, tm, 0, 6);
            if (cur != null && u.length() > 0) {
                double[] e = mul(tm, gs.ctm);
                cur.endX = e[4];
            }
        }
        return cur;
    }

    // ================================================================== layout

    public static final class Line {
        public String text;
        public double y, size, x;
        public boolean bold;
    }

    /** Linhas de texto de uma página (em ordem de leitura). */
    public List<Line> lines(int pageIndex) {
        List<Line> result = new ArrayList<Line>();
        if (encrypted || pageIndex < 0 || pageIndex >= pages.size()) return result;
        Map<String, Object> page = pages.get(pageIndex);
        Map<String, Object> res = dict(page.get("Resources"));
        List<Chunk> chunks = new ArrayList<Chunk>();
        chunkOrder = 0;
        Object contents = resolve(page.get("Contents"));
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        try {
            if (contents instanceof Stream) {
                byte[] b = decode((Stream) contents);
                all.write(b, 0, b.length);
            } else if (contents instanceof List) {
                for (Object o : array(contents)) {
                    Object so = resolve(o);
                    if (so instanceof Stream) {
                        byte[] b = decode((Stream) so);
                        all.write(b, 0, b.length);
                        all.write('\n');
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // rotação de página
        int rotate = intOf(page.get("Rotate"), 0);
        Gs gs = new Gs();
        if (rotate == 90 || rotate == -270) gs.ctm = new double[]{0, -1, 1, 0, 0, 0};
        else if (rotate == 270 || rotate == -90) gs.ctm = new double[]{0, 1, -1, 0, 0, 0};
        else if (rotate == 180) gs.ctm = new double[]{-1, 0, 0, -1, 0, 0};
        runContent(all.toByteArray(), res, gs, chunks, 0);
        if (chunks.isEmpty()) return result;

        // remove duplicatas (negrito simulado desenhando o texto duas vezes)
        // e agrupa por linha de base
        Collections.sort(chunks, new Comparator<Chunk>() {
            @Override
            public int compare(Chunk a, Chunk b) {
                if (Math.abs(a.y - b.y) > Math.min(a.size, b.size) * 0.35) return a.y > b.y ? -1 : 1;
                return Double.compare(a.x, b.x);
            }
        });
        List<List<Chunk>> lines = new ArrayList<List<Chunk>>();
        List<Chunk> line = null;
        double lineY = 0, lineSize = 0;
        for (Chunk c : chunks) {
            if (line == null || Math.abs(c.y - lineY) > Math.max(1.0, Math.min(c.size, lineSize) * 0.4)) {
                line = new ArrayList<Chunk>();
                lines.add(line);
                lineY = c.y;
                lineSize = c.size;
            }
            line.add(c);
        }
        for (List<Chunk> l : lines) {
            Collections.sort(l, new Comparator<Chunk>() {
                @Override
                public int compare(Chunk a, Chunk b) {
                    return Double.compare(a.x, b.x);
                }
            });
            StringBuilder sb = new StringBuilder();
            Chunk prev = null;
            double maxSize = 0;
            int boldChars = 0, chars = 0;
            for (Chunk c : l) {
                String t = c.text.toString();
                if (prev != null) {
                    if (t.equals(prev.text.toString()) && Math.abs(c.x - prev.x) < c.size * 0.3) continue;
                    double gap = c.x - prev.endX;
                    boolean hasSpace = sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ' || t.startsWith(" ");
                    if (gap > c.size * 1.6) {
                        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
                        sb.append("    ");
                        t = t.replaceFirst("^ +", "");
                    } else if (!hasSpace && gap > c.size * 0.15) sb.append(' ');
                }
                sb.append(t);
                maxSize = Math.max(maxSize, c.size);
                int n = t.trim().length();
                chars += n;
                if (c.bold) boldChars += n;
                prev = c;
            }
            String text = clean(sb.toString());
            if (text.trim().isEmpty()) continue;
            Line ln = new Line();
            ln.text = text;
            ln.y = l.get(0).y;
            ln.x = l.get(0).x;
            ln.size = maxSize;
            ln.bold = chars > 0 && boldChars * 10 >= chars * 6;
            result.add(ln);
        }
        return result;
    }

    static String clean(String s) {
        s = s.replace(' ', ' ').replace("­", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFB00 && c <= 0xFB06) {
                sb.append(Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFKC));
            } else if (c == '\t') sb.append(' ');
            else if (c >= 32 || c == '\n') {
                if (c == 0xF0B7 || c == 0xF0A7 || c == 0xF0FC || c == 0xF0D8 || c == 0xF076 || c == 0xF0A8 || c == 0xF06E) {
                    sb.append('•');
                    continue;
                }
                if (c == 0xFFFD || (c >= 0xE000 && c <= 0xF8FF)) continue;
                sb.append(c);
            }
        }
        return sb.toString().replaceAll("[ ]{5,}", "    ").trim();
    }

    /** Texto plano de uma página. */
    public String pageText(int pageIndex) {
        StringBuilder sb = new StringBuilder();
        for (Line l : lines(pageIndex)) sb.append(l.text).append('\n');
        return sb.toString();
    }

    /**
     * Converte as linhas de uma página em parágrafos/títulos. bodySize é o
     * tamanho de fonte predominante no documento (0 para calcular pela página).
     */
    public static List<Doc.Block> paragraphs(List<Line> lines, double bodySize) {
        List<Doc.Block> out = new ArrayList<Doc.Block>();
        if (lines.isEmpty()) return out;
        if (bodySize <= 0) bodySize = predominantSize(lines);
        StringBuilder para = null;
        Line first = null, prev = null;
        Doc.Table table = null;
        for (int li = 0; li < lines.size(); li++) {
            Line l = lines.get(li);
            // linhas com colunas (separadas por grandes espaços) viram tabela
            String[] cells = l.text.split(" {4,}");
            boolean tabular = cells.length >= 2 && (table != null
                    || (li + 1 < lines.size() && lines.get(li + 1).text.split(" {4,}").length >= 2));
            if (tabular) {
                flushPara(out, para, first, bodySize);
                para = null;
                if (table == null) {
                    table = new Doc.Table();
                    table.header = l.bold || l.size > bodySize * 1.05;
                    out.add(table);
                }
                List<String> row = new ArrayList<String>();
                for (String c : cells) row.add(c.trim());
                table.rows.add(row);
                prev = l;
                continue;
            }
            if (table != null) {
                table = null;
                para = null;
            }
            boolean newPara = para == null;
            if (!newPara) {
                double gap = prev.y - l.y;
                double lead = Math.max(prev.size, l.size) * 1.65;
                boolean sizeChange = Math.abs(l.size - prev.size) > Math.max(1.0, bodySize * 0.15);
                boolean bullet = l.text.matches("^([•\\-–·▪◦●○■□*]|\\d{1,3}[.)]|[a-zA-Z][.)])\\s.*");
                boolean prevEndsSentence = prev.text.matches(".*[.:!?]$");
                boolean indent = Math.abs(l.x - first.x) > bodySize * 2 && prevEndsSentence;
                boolean shortPrev = prevEndsSentence && prev.text.length() < 45;
                if (gap > lead || gap < -bodySize || sizeChange || bullet || prev.bold != l.bold || indent || shortPrev)
                    newPara = true;
            }
            if (newPara) {
                flushPara(out, para, first, bodySize);
                para = new StringBuilder(l.text);
                first = l;
            } else {
                String pt = para.toString();
                if (pt.endsWith("-") && l.text.length() > 0 && Character.isLowerCase(l.text.charAt(0))) {
                    para.setLength(para.length() - 1);
                    para.append(l.text);
                } else para.append(' ').append(l.text);
            }
            prev = l;
        }
        flushPara(out, para, first, bodySize);
        return out;
    }

    private static void flushPara(List<Doc.Block> out, StringBuilder para, Line first, double body) {
        if (para == null || first == null) return;
        String text = para.toString().trim();
        if (text.isEmpty()) return;
        double ratio = first.size / Math.max(1, body);
        if (ratio >= 1.25 && text.length() < 200) {
            int level = ratio >= 1.8 ? 1 : ratio >= 1.45 ? 2 : 3;
            out.add(new Doc.Heading(level, text));
            return;
        }
        Doc.Para p = new Doc.Para();
        Doc.Run r = new Doc.Run(text);
        r.bold = first.bold;
        p.runs.add(r);
        if (text.matches("^[•▪◦●○■□]\\s.*")) {
            p.list = Doc.LIST_BULLET;
            r.text = text.substring(1).trim();
        }
        out.add(p);
    }

    public static double predominantSize(List<Line> lines) {
        Map<Long, Integer> hist = new HashMap<Long, Integer>();
        for (Line l : lines) {
            long k = Math.round(l.size * 2);
            Integer c = hist.get(k);
            hist.put(k, (c == null ? 0 : c) + l.text.length());
        }
        long best = 24;
        int bc = -1;
        for (Map.Entry<Long, Integer> e : hist.entrySet()) {
            if (e.getValue() > bc) {
                bc = e.getValue();
                best = e.getKey();
            }
        }
        return best / 2.0;
    }

    // ================================================================== util

    static int indexOf(byte[] d, byte[] p, int from) {
        return indexOf(d, p, from, d.length);
    }

    static int indexOf(byte[] d, byte[] p, int from, int to) {
        outer:
        for (int i = Math.max(0, from); i <= to - p.length; i++) {
            for (int k = 0; k < p.length; k++) if (d[i + k] != p[k]) continue outer;
            return i;
        }
        return -1;
    }

    static int lastIndexOf(byte[] d, byte[] p) {
        return lastIndexOf(d, p, d.length - p.length);
    }

    static int lastIndexOf(byte[] d, byte[] p, int from) {
        outer:
        for (int i = Math.min(from, d.length - p.length); i >= 0; i--) {
            for (int k = 0; k < p.length; k++) if (d[i + k] != p[k]) continue outer;
            return i;
        }
        return -1;
    }
}
