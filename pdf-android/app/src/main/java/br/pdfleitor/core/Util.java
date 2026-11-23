package br.pdfleitor.core;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class Util {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    /** Limite de segurança por entrada de ZIP (evita "zip bombs"). */
    private static final long MAX_ENTRY = 200L * 1024 * 1024;

    private Util() {
    }

    // ------------------------------------------------------------------ IO

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(8192, in.available()));
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    public static Map<String, byte[]> unzip(byte[] data) throws IOException {
        Map<String, byte[]> map = new LinkedHashMap<String, byte[]>();
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data));
        ZipEntry e;
        byte[] buf = new byte[16384];
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int n;
            long total = 0;
            while ((n = zin.read(buf)) > 0) {
                total += n;
                if (total > MAX_ENTRY) throw new IOException("Arquivo compactado muito grande");
                bos.write(buf, 0, n);
            }
            String name = e.getName();
            if (name.startsWith("/")) name = name.substring(1);
            map.put(name, bos.toByteArray());
        }
        zin.close();
        if (map.isEmpty()) throw new IOException("Arquivo inválido ou corrompido");
        return map;
    }

    /** Escreve uma entrada de ZIP. Use stored=true para o "mimetype" do ODF. */
    public static void zipPut(ZipOutputStream zos, String name, byte[] data, boolean stored) throws IOException {
        ZipEntry e = new ZipEntry(name);
        if (stored) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            CRC32 crc = new CRC32();
            crc.update(data);
            e.setCrc(crc.getValue());
        }
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    public static void zipPut(ZipOutputStream zos, String name, String text) throws IOException {
        zipPut(zos, name, text.getBytes(UTF8), false);
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    // ------------------------------------------------------------------ XML

    public static XmlPullParser parser(byte[] xml) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(false);
        XmlPullParser p = f.newPullParser();
        p.setInput(new ByteArrayInputStream(xml), null);
        return p;
    }

    /** Nome local (sem prefixo de namespace). */
    public static String ln(String name) {
        if (name == null) return "";
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    /** Valor de atributo procurando pelo nome local. */
    public static String attr(XmlPullParser p, String local) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (ln(p.getAttributeName(i)).equals(local)) return p.getAttributeValue(i);
        }
        return null;
    }

    /** Atributo com prefixo exato (ex.: "r:id"), com fallback para o nome local. */
    public static String attrQ(XmlPullParser p, String qname) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            if (p.getAttributeName(i).equals(qname)) return p.getAttributeValue(i);
        }
        return attr(p, ln(qname));
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default:
                    // remove caracteres de controle inválidos em XML 1.0
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') continue;
                    if (c == 0xFFFE || c == 0xFFFF) continue;
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String htmlEsc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ caminhos

    /** Resolve um caminho relativo (de um .rels) em relação a um diretório. */
    public static String resolve(String baseDir, String target) {
        if (target == null) return null;
        if (target.startsWith("/")) return target.substring(1);
        String path = baseDir.isEmpty() ? target : baseDir + "/" + target;
        String[] parts = path.split("/");
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        for (String s : parts) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else out.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(out.get(i));
        }
        return sb.toString();
    }

    public static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    /** Lê um arquivo .rels e devolve id -> caminho resolvido. */
    public static Map<String, String> rels(Map<String, byte[]> zip, String partPath) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        String dir = dirOf(partPath);
        String file = partPath.substring(partPath.lastIndexOf('/') + 1);
        String relPath = (dir.isEmpty() ? "" : dir + "/") + "_rels/" + file + ".rels";
        byte[] data = zip.get(relPath);
        if (data == null) return m;
        try {
            XmlPullParser p = parser(data);
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && ln(p.getName()).equals("Relationship")) {
                    String id = attr(p, "Id");
                    String target = attr(p, "Target");
                    String mode = attr(p, "TargetMode");
                    if (id != null && target != null && !"External".equals(mode)) {
                        m.put(id, resolve(dir, target));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    // ------------------------------------------------------------------ imagens

    /** Detecta tipo e dimensões de imagem a partir do cabeçalho. Retorna {w,h} ou null. */
    public static int[] imageSize(byte[] d) {
        if (d == null || d.length < 24) return null;
        try {
            // PNG
            if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
                return new int[]{be32(d, 16), be32(d, 20)};
            }
            // GIF
            if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') {
                return new int[]{(d[6] & 0xFF) | (d[7] & 0xFF) << 8, (d[8] & 0xFF) | (d[9] & 0xFF) << 8};
            }
            // BMP
            if (d[0] == 'B' && d[1] == 'M') {
                return new int[]{le32(d, 18), Math.abs(le32(d, 22))};
            }
            // JPEG
            if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
                int i = 2;
                while (i + 9 < d.length) {
                    if ((d[i] & 0xFF) != 0xFF) {
                        i++;
                        continue;
                    }
                    int marker = d[i + 1] & 0xFF;
                    if (marker == 0xFF) {
                        i++;
                        continue;
                    }
                    if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                        i += 2;
                        continue;
                    }
                    int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
                    if ((marker >= 0xC0 && marker <= 0xCF) && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                        int h = ((d[i + 5] & 0xFF) << 8) | (d[i + 6] & 0xFF);
                        int w = ((d[i + 7] & 0xFF) << 8) | (d[i + 8] & 0xFF);
                        return new int[]{w, h};
                    }
                    i += 2 + len;
                }
            }
            // WEBP (VP8X / VP8 / VP8L)
            if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E') {
                String fmt = new String(d, 12, 4, "ISO-8859-1");
                if (fmt.equals("VP8X") && d.length >= 30) {
                    int w = 1 + ((d[24] & 0xFF) | (d[25] & 0xFF) << 8 | (d[26] & 0xFF) << 16);
                    int h = 1 + ((d[27] & 0xFF) | (d[28] & 0xFF) << 8 | (d[29] & 0xFF) << 16);
                    return new int[]{w, h};
                } else if (fmt.equals("VP8 ") && d.length >= 30) {
                    return new int[]{((d[26] & 0xFF) | (d[27] & 0xFF) << 8) & 0x3FFF, ((d[28] & 0xFF) | (d[29] & 0xFF) << 8) & 0x3FFF};
                } else if (fmt.equals("VP8L") && d.length >= 25) {
                    int b0 = d[21] & 0xFF, b1 = d[22] & 0xFF, b2 = d[23] & 0xFF, b3 = d[24] & 0xFF;
                    int w = 1 + (((b1 & 0x3F) << 8) | b0);
                    int h = 1 + (((b3 & 0xF) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
                    return new int[]{w, h};
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String imageMime(byte[] d) {
        if (d == null || d.length < 12) return "application/octet-stream";
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P') return "image/png";
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return "image/gif";
        if (d[0] == 'B' && d[1] == 'M') return "image/bmp";
        if (d[0] == 'R' && d[1] == 'I' && d[8] == 'W') return "image/webp";
        return "application/octet-stream";
    }

    public static String imageExt(String mime) {
        if ("image/png".equals(mime)) return "png";
        if ("image/jpeg".equals(mime)) return "jpeg";
        if ("image/gif".equals(mime)) return "gif";
        if ("image/bmp".equals(mime)) return "bmp";
        if ("image/webp".equals(mime)) return "webp";
        return "bin";
    }

    /** Cria um bloco de imagem a partir de bytes, ou null se não reconhecido. */
    public static Doc.Image image(byte[] data) {
        int[] sz = imageSize(data);
        if (sz == null || sz[0] <= 0 || sz[1] <= 0) return null;
        return new Doc.Image(data, imageMime(data), sz[0], sz[1]);
    }

    private static int be32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    private static int le32(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16) | ((d[o + 3] & 0xFF) << 24);
    }

    // ------------------------------------------------------------------ texto

    /** Decodifica texto tentando UTF-8 (com BOM), UTF-16 e por fim Windows-1252. */
    public static String decodeText(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xEF && (d[1] & 0xFF) == 0xBB && (d[2] & 0xFF) == 0xBF)
            return new String(d, 3, d.length - 3, UTF8);
        if (d.length >= 2 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xFE)
            return new String(d, 2, d.length - 2, Charset.forName("UTF-16LE"));
        if (d.length >= 2 && (d[0] & 0xFF) == 0xFE && (d[1] & 0xFF) == 0xFF)
            return new String(d, 2, d.length - 2, Charset.forName("UTF-16BE"));
        if (isValidUtf8(d)) return new String(d, UTF8);
        return new String(d, Charset.forName("windows-1252"));
    }

    private static boolean isValidUtf8(byte[] d) {
        int i = 0;
        while (i < d.length) {
            int b = d[i] & 0xFF;
            int n;
            if (b < 0x80) n = 0;
            else if (b >= 0xC2 && b <= 0xDF) n = 1;
            else if (b >= 0xE0 && b <= 0xEF) n = 2;
            else if (b >= 0xF0 && b <= 0xF4) n = 3;
            else return false;
            if (i + n >= d.length && n > 0) return false;
            for (int k = 1; k <= n; k++) if ((d[i + k] & 0xC0) != 0x80) return false;
            i += n + 1;
        }
        return true;
    }

    public static int parseColor(String hex) {
        if (hex == null) return 0;
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String hex6(int argb) {
        String s = Integer.toHexString(argb & 0xFFFFFF).toUpperCase();
        while (s.length() < 6) s = "0" + s;
        return s;
    }

    public static String baseName(String name) {
        if (name == null || name.isEmpty()) return "documento";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.isEmpty() ? "documento" : name;
    }

    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
