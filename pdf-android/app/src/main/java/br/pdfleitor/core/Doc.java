package br.pdfleitor.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo intermediário de documento. Todos os leitores produzem um Doc e todos
 * os escritores consomem um Doc, permitindo converter de qualquer formato para
 * qualquer outro.
 */
public class Doc {

    public String title = "Documento";
    public final List<Block> blocks = new ArrayList<Block>();
    /** Presente quando a origem é uma apresentação (usado para fidelidade visual). */
    public List<Slide> slides;
    /** Sugestão de orientação paisagem (planilhas largas, slides). */
    public boolean landscape;

    // ------------------------------------------------------------------ blocos

    public abstract static class Block {
    }

    public static class Run {
        public String text;
        public boolean bold, italic, underline;
        /** Tamanho em pontos; 0 = padrão. */
        public float size;
        /** Cor ARGB; 0 = padrão. */
        public int color;

        public Run(String text) {
            this.text = text;
        }

        public Run(String text, boolean bold, boolean italic) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
        }

        public Run copyStyle(String t) {
            Run r = new Run(t);
            r.bold = bold;
            r.italic = italic;
            r.underline = underline;
            r.size = size;
            r.color = color;
            return r;
        }
    }

    public static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2, ALIGN_JUSTIFY = 3;
    public static final int LIST_NONE = 0, LIST_BULLET = 1, LIST_NUMBER = 2;

    public static class Para extends Block {
        public final List<Run> runs = new ArrayList<Run>();
        public int align = ALIGN_LEFT;
        public int list = LIST_NONE;
        public int listLevel;
        /** Número sequencial do item em listas numeradas (1..n). */
        public int listIndex;

        public Para() {
        }

        public Para(String text) {
            runs.add(new Run(text));
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) sb.append(r.text);
            return sb.toString();
        }

        public boolean isBlank() {
            return text().trim().length() == 0;
        }
    }

    public static class Heading extends Para {
        public int level;

        public Heading(int level, String text) {
            super(text);
            this.level = Math.max(1, Math.min(6, level));
        }

        public Heading(int level) {
            this.level = Math.max(1, Math.min(6, level));
        }
    }

    public static class Table extends Block {
        public String name;
        public final List<List<String>> rows = new ArrayList<List<String>>();
        public boolean header;

        public int columnCount() {
            int c = 0;
            for (List<String> r : rows) c = Math.max(c, r.size());
            return c;
        }

        /** Remove linhas/colunas finais vazias. */
        public void trim() {
            while (!rows.isEmpty() && isEmptyRow(rows.get(rows.size() - 1))) rows.remove(rows.size() - 1);
            for (List<String> r : rows) {
                while (!r.isEmpty() && (r.get(r.size() - 1) == null || r.get(r.size() - 1).trim().isEmpty()))
                    r.remove(r.size() - 1);
            }
        }

        private static boolean isEmptyRow(List<String> r) {
            for (String s : r) if (s != null && s.trim().length() > 0) return false;
            return true;
        }
    }

    public static class Image extends Block {
        public byte[] data;
        public String mime;
        public int width, height; // pixels
        /** Largura desejada em pontos (0 = automática). */
        public float widthPt;
        /** true para imagens que representam uma página inteira (PDF rasterizado). */
        public boolean fullPage;

        public Image(byte[] data, String mime, int w, int h) {
            this.data = data;
            this.mime = mime;
            this.width = w;
            this.height = h;
        }
    }

    public static class PageBreak extends Block {
    }

    // ------------------------------------------------------------------ slides

    public static class Slide {
        /** Dimensões em EMU (914400 por polegada). */
        public long cx = 12192000L, cy = 6858000L;
        public int background = 0xFFFFFFFF;
        public final List<Shape> shapes = new ArrayList<Shape>();
    }

    public static class Shape {
        public static final int TEXT = 0, PICTURE = 1, TABLE = 2;
        public int kind = TEXT;
        public long x, y, w, h;
        public int fill; // 0 = sem preenchimento
        public int line; // 0 = sem borda
        public boolean isTitle;
        public boolean ellipse;
        /** 0 = topo, 1 = centro, 2 = base */
        public int anchor;
        public final List<Para> paras = new ArrayList<Para>();
        public Image image;
        public Table table;
    }

    // ------------------------------------------------------------------ util

    public void add(Block b) {
        blocks.add(b);
    }

    public List<Table> tables() {
        List<Table> out = new ArrayList<Table>();
        for (Block b : blocks) if (b instanceof Table) out.add((Table) b);
        return out;
    }

    public boolean hasContent() {
        for (Block b : blocks) {
            if (b instanceof Para && !((Para) b).isBlank()) return true;
            if (b instanceof Table && !((Table) b).rows.isEmpty()) return true;
            if (b instanceof Image) return true;
        }
        return slides != null && !slides.isEmpty();
    }

    /** Texto plano do documento inteiro. */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b instanceof Para) {
                sb.append(((Para) b).text()).append('\n');
            } else if (b instanceof Table) {
                for (List<String> r : ((Table) b).rows) {
                    for (int i = 0; i < r.size(); i++) {
                        if (i > 0) sb.append('\t');
                        sb.append(r.get(i) == null ? "" : r.get(i));
                    }
                    sb.append('\n');
                }
                sb.append('\n');
            } else if (b instanceof PageBreak) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
