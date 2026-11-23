package br.pdfleitor.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import br.pdfleitor.core.Doc;
import br.pdfleitor.core.DocxWriter;

/**
 * Gera PDF a partir do modelo Doc usando a API nativa android.graphics.pdf.
 * Faz a paginação de parágrafos, títulos, listas, tabelas e imagens.
 */
public class PdfDocWriter {

    public float pageW = 595, pageH = 842; // A4 em pontos
    public float margin = 56;

    public interface Progress {
        void onProgress(String msg);
    }

    private PdfDocument pdf;
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo;
    private float y, curW, curH;
    private boolean pageHasContent;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Progress progress;

    private static final float BODY = 11f;
    private static final float[] HEAD = {20, 16, 14, 13, 12, 11.5f};

    public void setPaper(String size, String margins, boolean landscape) {
        if ("letter".equals(size)) {
            pageW = 612;
            pageH = 792;
        } else {
            pageW = 595;
            pageH = 842;
        }
        if (landscape) {
            float t = pageW;
            pageW = pageH;
            pageH = t;
        }
        margin = "narrow".equals(margins) ? 32 : "wide".equals(margins) ? 76 : 56;
    }

    public void write(Doc doc, OutputStream os, Progress p) throws IOException {
        this.progress = p;
        pdf = new PdfDocument();
        paint.setTypeface(Typeface.SANS_SERIF);
        paint.setColor(0xFF202124);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(0.6f);
        line.setColor(0xFFA6A6A6);
        try {
            if (doc.slides != null && !doc.slides.isEmpty()) {
                writeSlides(doc);
            } else {
                writeFlow(doc);
            }
            finishPage();
            if (pageNo == 0) {
                newPage(pageW, pageH);
                finishPage();
            }
            pdf.writeTo(os);
        } finally {
            pdf.close();
        }
    }

    // ------------------------------------------------------------------ páginas

    private void newPage(float w, float h) {
        finishPage();
        pageNo++;
        curW = w;
        curH = h;
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder((int) w, (int) h, pageNo).create();
        page = pdf.startPage(info);
        canvas = page.getCanvas();
        y = margin;
        pageHasContent = false;
        if (progress != null && pageNo % 5 == 1) progress.onProgress("Gerando página " + pageNo + "…");
    }

    private void finishPage() {
        if (page != null) {
            pdf.finishPage(page);
            page = null;
            canvas = null;
        }
    }

    private void ensurePage() {
        if (page == null) newPage(pageW, pageH);
    }

    private float contentW() {
        return curW - 2 * margin;
    }

    private float bottom() {
        return curH - margin;
    }

    // ------------------------------------------------------------------ fluxo

    private void writeFlow(Doc doc) {
        List<Doc.Block> blocks = doc.blocks;
        for (int i = 0; i < blocks.size(); i++) {
            Doc.Block b = blocks.get(i);
            if (b instanceof Doc.PageBreak) {
                if (page != null && pageHasContent) finishPage();
                continue;
            }
            if (b instanceof Doc.Image && ((Doc.Image) b).fullPage) {
                drawFullPageImage((Doc.Image) b);
                continue;
            }
            ensurePage();
            if (b instanceof Doc.Heading) {
                Doc.Heading h = (Doc.Heading) b;
                float before = pageHasContent ? (h.level <= 2 ? 14 : 10) : 0;
                StaticLayout l = layoutPara(h, contentW(), HEAD[h.level - 1], true, h.level <= 2 ? 0xFF1F3864 : 0xFF2F5496);
                // mantém o título junto do próximo bloco
                float needed = before + l.getHeight() + 40;
                if (y + needed > bottom() && pageHasContent) newPage(pageW, pageH);
                else y += before;
                drawLayout(l, margin);
                y += 6;
            } else if (b instanceof Doc.Para) {
                Doc.Para p = (Doc.Para) b;
                StaticLayout l = layoutPara(p, contentW(), BODY, false, 0);
                drawLayout(l, margin);
                boolean nextIsList = i + 1 < blocks.size() && blocks.get(i + 1) instanceof Doc.Para
                        && ((Doc.Para) blocks.get(i + 1)).list != Doc.LIST_NONE;
                y += p.list != Doc.LIST_NONE && nextIsList ? 2 : 7;
            } else if (b instanceof Doc.Table) {
                drawTable((Doc.Table) b);
                y += 10;
            } else if (b instanceof Doc.Image) {
                drawImage((Doc.Image) b);
                y += 8;
            }
        }
    }

    private StaticLayout layoutPara(Doc.Para p, float width, float baseSize, boolean bold, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String prefix = null;
        if (p.list == Doc.LIST_BULLET) prefix = p.listLevel % 2 == 0 ? "•  " : "◦  ";
        else if (p.list == Doc.LIST_NUMBER) prefix = Math.max(1, p.listIndex) + ".  ";
        if (prefix != null) sb.append(prefix);
        for (Doc.Run r : p.runs) {
            if (r.text == null || r.text.isEmpty()) continue;
            int start = sb.length();
            sb.append(r.text.replace('\t', ' '));
            int end = sb.length();
            int style = (r.bold || bold ? Typeface.BOLD : 0) | (r.italic ? Typeface.ITALIC : 0);
            if (style != 0) sb.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r.underline) sb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r.size > 0 && !bold) {
                float s = Math.max(6, Math.min(48, r.size));
                sb.setSpan(new AbsoluteSizeSpan(Math.round(s)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int c = r.color != 0 ? r.color : color;
            if (c != 0 && (c & 0xFFFFFF) != 0xFFFFFF) sb.setSpan(new ForegroundColorSpan(c), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (sb.length() == 0) sb.append(" ");
        if (bold && color != 0) sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        paint.setTextSize(baseSize);
        if (prefix != null) {
            int indent = 14 + p.listLevel * 16;
            int hang = indent + (int) paint.measureText(prefix);
            sb.setSpan(new LeadingMarginSpan.Standard(indent, hang), 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
        if (p.align == Doc.ALIGN_CENTER) align = Layout.Alignment.ALIGN_CENTER;
        else if (p.align == Doc.ALIGN_RIGHT) align = Layout.Alignment.ALIGN_OPPOSITE;
        return new StaticLayout(sb, new TextPaint(paint), Math.max(10, (int) width), align, 1.12f, 0, false);
    }

    /** Desenha um layout de texto quebrando entre páginas linha a linha. */
    private void drawLayout(StaticLayout l, float x) {
        int lines = l.getLineCount();
        int first = 0;
        while (first < lines) {
            float avail = bottom() - y;
            int last = first;
            float top = l.getLineTop(first);
            while (last < lines && l.getLineBottom(last) - top <= avail) last++;
            if (last == first) {
                if (pageHasContent) {
                    newPage(curW, curH);
                    continue;
                }
                last = first + 1; // linha maior que a página: desenha assim mesmo
            }
            float h = l.getLineBottom(last - 1) - top;
            canvas.save();
            canvas.clipRect(x - 2, y, x + l.getWidth() + 2, y + h);
            canvas.translate(x, y - top);
            l.draw(canvas);
            canvas.restore();
            y += h;
            pageHasContent = true;
            first = last;
            if (first < lines) newPage(curW, curH);
        }
    }

    // ------------------------------------------------------------------ tabelas

    private void drawTable(Doc.Table t) {
        int cols = Math.max(1, t.columnCount());
        if (t.rows.isEmpty()) return;
        float width = contentW();
        int[] widths = DocxWriter.columnWidths(t, cols, (int) width);
        float size = cols > 10 ? 7f : cols > 6 ? 8f : 9.5f;
        float pad = 4;
        List<String> header = t.header ? t.rows.get(0) : null;
        for (int r = 0; r < t.rows.size(); r++) {
            boolean isHeader = t.header && r == 0;
            StaticLayout[] cells = layoutRow(t.rows.get(r), cols, widths, size, isHeader, pad);
            float h = rowHeight(cells, pad);
            if (y + h > bottom() && pageHasContent) {
                newPage(curW, curH);
                if (header != null && !isHeader) {
                    StaticLayout[] hc = layoutRow(header, cols, widths, size, true, pad);
                    drawRow(hc, widths, rowHeight(hc, pad), true, pad);
                }
            }
            drawRow(cells, widths, h, isHeader, pad);
        }
    }

    private StaticLayout[] layoutRow(List<String> row, int cols, int[] widths, float size, boolean bold, float pad) {
        StaticLayout[] out = new StaticLayout[cols];
        TextPaint tp = new TextPaint(paint);
        tp.setTextSize(size);
        tp.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) : Typeface.SANS_SERIF);
        for (int c = 0; c < cols; c++) {
            String v = c < row.size() && row.get(c) != null ? row.get(c) : "";
            out[c] = new StaticLayout(v, tp, Math.max(4, (int) (widths[c] - 2 * pad)), Layout.Alignment.ALIGN_NORMAL, 1.05f, 0, false);
        }
        return out;
    }

    private float rowHeight(StaticLayout[] cells, float pad) {
        float h = 0;
        for (StaticLayout l : cells) h = Math.max(h, l.getHeight());
        return Math.min(h + 2 * pad, bottom() - margin);
    }

    private void drawRow(StaticLayout[] cells, int[] widths, float h, boolean header, float pad) {
        float x = margin;
        float total = 0;
        for (int w : widths) total += w;
        if (header) {
            fill.setColor(0xFFE8EAED);
            fill.setStyle(Paint.Style.FILL);
            canvas.drawRect(margin, y, margin + total, y + h, fill);
        }
        for (int c = 0; c < cells.length; c++) {
            canvas.save();
            canvas.clipRect(x, y, x + widths[c], y + h);
            canvas.translate(x + pad, y + pad);
            cells[c].draw(canvas);
            canvas.restore();
            canvas.drawRect(x, y, x + widths[c], y + h, line);
            x += widths[c];
        }
        y += h;
        pageHasContent = true;
    }

    // ------------------------------------------------------------------ imagens

    static Bitmap decode(byte[] data, int maxSide) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / sample > maxSide || o.outHeight / sample > maxSide) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (OutOfMemoryError e) {
            o2.inSampleSize = sample * 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        }
    }

    private void drawImage(Doc.Image img) {
        Bitmap bmp = decode(img.data, 2000);
        if (bmp == null) return;
        try {
            float w = img.widthPt > 0 ? img.widthPt : img.width * 0.75f;
            w = Math.min(w, contentW());
            float h = w * bmp.getHeight() / Math.max(1, bmp.getWidth());
            float maxH = bottom() - margin;
            if (h > maxH) {
                h = maxH;
                w = h * bmp.getWidth() / Math.max(1, bmp.getHeight());
            }
            if (y + h > bottom() && pageHasContent) newPage(curW, curH);
            float x = margin + (contentW() - w) / 2;
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(bmp, null, new RectF(x, y, x + w, y + h), p);
            y += h;
            pageHasContent = true;
        } finally {
            bmp.recycle();
        }
    }

    /** Imagem ocupando a página inteira (orientação conforme a imagem). */
    private void drawFullPageImage(Doc.Image img) {
        Bitmap bmp = decode(img.data, 2600);
        if (bmp == null) return;
        try {
            boolean land = bmp.getWidth() > bmp.getHeight();
            float pw = Math.min(pageW, pageH), ph = Math.max(pageW, pageH);
            if (land) {
                float t = pw;
                pw = ph;
                ph = t;
            }
            newPage(pw, ph);
            float m = Math.min(margin, 24);
            float aw = pw - 2 * m, ah = ph - 2 * m;
            float scale = Math.min(aw / bmp.getWidth(), ah / bmp.getHeight());
            float w = bmp.getWidth() * scale, h = bmp.getHeight() * scale;
            float x = (pw - w) / 2, yy = (ph - h) / 2;
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            canvas.drawBitmap(bmp, null, new RectF(x, yy, x + w, yy + h), p);
            pageHasContent = true;
            finishPage();
        } finally {
            bmp.recycle();
        }
    }

    // ------------------------------------------------------------------ slides

    private void writeSlides(Doc doc) {
        for (Doc.Slide s : doc.slides) {
            float w = s.cx / 12700f, h = s.cy / 12700f;
            newPage(w, h);
            SlidePainter.draw(canvas, s, 1f / 12700f);
            pageHasContent = true;
            finishPage();
        }
    }

    static int luminance(int c) {
        return (int) (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c));
    }
}
