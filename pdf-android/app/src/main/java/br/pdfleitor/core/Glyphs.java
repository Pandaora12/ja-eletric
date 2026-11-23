package br.pdfleitor.core;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Mapeamento de nomes de glifos (Adobe Glyph List, subconjunto) para Unicode. */
final class Glyphs {

    private static final Map<String, String> MAP = new HashMap<String, String>();
    static final String[] MAC_ROMAN_HIGH = new String[128];

    private static final String MAC_ROMAN =
            "ÄÅÇÉÑÖÜáàâäãåçéèêëíìîïñóòôöõúùûü†°¢£§•¶ß®©™´¨≠ÆØ∞±≤≥¥µ∂∑∏π∫ªºΩæø¿¡¬√ƒ≈∆«»… ÀÃÕŒœ–—“”‘’÷◊ÿŸ⁄€‹›ﬁﬂ‡·‚„‰ÂÊÁËÈÍÎÏÌÓÔÒÚÛÙıˆ˜¯˘˙˚¸˝˛ˇ";

    static {
        String[] pairs = {
                "space", " ", "exclam", "!", "quotedbl", "\"", "numbersign", "#", "dollar", "$", "percent", "%",
                "ampersand", "&", "quotesingle", "'", "parenleft", "(", "parenright", ")", "asterisk", "*",
                "plus", "+", "comma", ",", "hyphen", "-", "period", ".", "slash", "/", "zero", "0", "one", "1",
                "two", "2", "three", "3", "four", "4", "five", "5", "six", "6", "seven", "7", "eight", "8",
                "nine", "9", "colon", ":", "semicolon", ";", "less", "<", "equal", "=", "greater", ">",
                "question", "?", "at", "@", "bracketleft", "[", "backslash", "\\", "bracketright", "]",
                "asciicircum", "^", "underscore", "_", "grave", "`", "braceleft", "{", "bar", "|",
                "braceright", "}", "asciitilde", "~", "bullet", "•", "endash", "–", "emdash", "—",
                "quoteleft", "‘", "quoteright", "’", "quotedblleft", "“", "quotedblright", "”",
                "quotesinglbase", "‚", "quotedblbase", "„", "ellipsis", "…", "fi", "fi", "fl", "fl",
                "ff", "ff", "ffi", "ffi", "ffl", "ffl", "degree", "°", "copyright", "©", "registered", "®",
                "trademark", "™", "section", "§", "paragraph", "¶", "periodcentered", "·", "middot", "·",
                "guillemotleft", "«", "guillemotright", "»", "guilsinglleft", "‹", "guilsinglright", "›",
                "dagger", "†", "daggerdbl", "‡", "florin", "ƒ", "Euro", "€", "euro", "€", "nbspace", " ",
                "nonbreakingspace", " ", "minus", "−", "multiply", "×", "divide", "÷", "ordfeminine", "ª",
                "ordmasculine", "º", "exclamdown", "¡", "questiondown", "¿", "sterling", "£", "yen", "¥",
                "cent", "¢", "currency", "¤", "brokenbar", "¦", "dotlessi", "ı", "germandbls", "ß",
                "ae", "æ", "AE", "Æ", "oe", "œ", "OE", "Œ", "oslash", "ø", "Oslash", "Ø", "eth", "ð", "Eth", "Ð",
                "thorn", "þ", "Thorn", "Þ", "lslash", "ł", "Lslash", "Ł", "mu", "µ", "plusminus", "±",
                "onehalf", "½", "onequarter", "¼", "threequarters", "¾", "onesuperior", "¹", "twosuperior", "²",
                "threesuperior", "³", "logicalnot", "¬", "macron", "¯", "acute", "´", "cedilla", "¸",
                "dieresis", "¨", "circumflex", "ˆ", "tilde", "˜", "perthousand", "‰", "fraction", "⁄",
                "arrowright", "→", "arrowleft", "←", "arrowup", "↑", "arrowdown", "↓", "checkmark", "✓",
                "square", "■", "circle", "○", "lozenge", "◊", "infinity", "∞", "notequal", "≠",
                "lessequal", "≤", "greaterequal", "≥", "summation", "∑", "product", "∏", "radical", "√",
                "integral", "∫", "partialdiff", "∂", "Delta", "∆", "Omega", "Ω", "pi", "π", "alpha", "α",
                "beta", "β", "gamma", "γ", "delta", "δ", "epsilon", "ε", "lambda", "λ", "sigma", "σ",
                "theta", "θ", "omega", "ω", "phi", "φ", "approxequal", "≈", "uni00A0", " ",
        };
        for (int i = 0; i < pairs.length; i += 2) MAP.put(pairs[i], pairs[i + 1]);
        for (int i = 0; i < 128 && i < MAC_ROMAN.length(); i++) {
            MAC_ROMAN_HIGH[i] = String.format("uni%04X", (int) MAC_ROMAN.charAt(i));
        }
    }

    private static final String[][] ACCENTS = {
            {"acute", "́"}, {"grave", "̀"}, {"circumflex", "̂"}, {"tilde", "̃"},
            {"dieresis", "̈"}, {"ring", "̊"}, {"cedilla", "̧"}, {"caron", "̌"},
            {"macron", "̄"}, {"breve", "̆"}, {"ogonek", "̨"}, {"dotaccent", "̇"},
            {"hungarumlaut", "̋"}};

    static String toUnicode(String name) {
        if (name == null || name.isEmpty()) return null;
        String m = MAP.get(name);
        if (m != null) return m;
        int dot = name.indexOf('.');
        if (dot > 0) {
            String base = toUnicode(name.substring(0, dot));
            if (base != null) return base;
        }
        int us = name.indexOf('_');
        if (us > 0) {
            StringBuilder sb = new StringBuilder();
            for (String part : name.split("_")) {
                String u = toUnicode(part);
                if (u == null) return null;
                sb.append(u);
            }
            return sb.toString();
        }
        if (name.length() == 1) {
            char c = name.charAt(0);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return name;
        }
        try {
            if (name.startsWith("uni") && name.length() >= 7) {
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i + 4 <= name.length(); i += 4)
                    sb.append((char) Integer.parseInt(name.substring(i, i + 4), 16));
                return sb.toString();
            }
            if (name.startsWith("u") && name.length() >= 5 && name.length() <= 7) {
                return new String(Character.toChars(Integer.parseInt(name.substring(1), 16)));
            }
        } catch (Exception ignored) {
        }
        // letra + acento (ex.: "atilde", "Ccedilla")
        for (String[] acc : ACCENTS) {
            if (name.length() == acc[0].length() + 1 && name.endsWith(acc[0])) {
                String composed = Normalizer.normalize(name.charAt(0) + acc[1], Normalizer.Form.NFC);
                if (composed.length() == 1) return composed;
            }
        }
        // nomes numéricos (ex.: "a65", "g32") não são mapeáveis com segurança
        return null;
    }

    private Glyphs() {
    }
}
