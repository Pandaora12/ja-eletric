package br.pdfleitor.core;

/** Base64 simples (java.util.Base64 só existe a partir do Android 8). */
public final class Base64 {
    private static final char[] ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] REV = new int[128];

    static {
        java.util.Arrays.fill(REV, -1);
        for (int i = 0; i < ALPHA.length; i++) REV[ALPHA[i]] = i;
        REV['-'] = 62;
        REV['_'] = 63;
    }

    private Base64() {
    }

    public static String encode(byte[] d) {
        StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
        int i = 0;
        for (; i + 2 < d.length; i += 3) {
            int v = (d[i] & 0xFF) << 16 | (d[i + 1] & 0xFF) << 8 | (d[i + 2] & 0xFF);
            sb.append(ALPHA[v >> 18 & 63]).append(ALPHA[v >> 12 & 63]).append(ALPHA[v >> 6 & 63]).append(ALPHA[v & 63]);
        }
        int rem = d.length - i;
        if (rem == 1) {
            int v = (d[i] & 0xFF) << 16;
            sb.append(ALPHA[v >> 18 & 63]).append(ALPHA[v >> 12 & 63]).append("==");
        } else if (rem == 2) {
            int v = (d[i] & 0xFF) << 16 | (d[i + 1] & 0xFF) << 8;
            sb.append(ALPHA[v >> 18 & 63]).append(ALPHA[v >> 12 & 63]).append(ALPHA[v >> 6 & 63]).append('=');
        }
        return sb.toString();
    }

    public static byte[] decode(String s) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length() * 3 / 4);
        int buf = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=') break;
            if (c >= 128 || REV[c] < 0) continue;
            buf = buf << 6 | REV[c];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write(buf >> bits & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
