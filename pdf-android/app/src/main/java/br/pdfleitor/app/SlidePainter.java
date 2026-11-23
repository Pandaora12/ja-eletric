package br.pdfleitor.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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

import java.util.List;

import br.pdfleitor.core.Doc;
import br.pdfleitor.core.DocxWriter;

/** Desenha um slide (PowerPoint/ODP) em um Canvas, respeitando posições. */
final class SlidePainter {

    private SlidePainter() {
    }

    /** scale converte EMU para unidades do canvas (pontos: 1/12700). */
    static void draw(Canvas c, Doc.Slide s, float scale) {
        float W = s.cx * scale, H = s.cy * scale;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(s.background != 0 ? s.background : 0xFFFFFFFF);
        c.drawRect(0, 0, W, H, fill);
        boolean darkBg = PdfDocWriter.luminance(s.background != 0 ? s.background : 0xFFFFFFFF) < 110;
        for (Doc.Shape sh : s.shapes) {
            RectF r = new RectF(sh.x * scale, sh.y * scale, (sh.x + sh.w) * scale, (sh.y + sh.h) * scale);
            try {
                if (sh.kind == Doc.Shape.PICTURE && sh.image != null) {
                    Bitmap bmp = PdfDocWriter.decode(sh.image.data, 2000);
                    if (bmp != null) {
                        c.drawBitmap(bmp, null, r, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
                        bmp.recycle();
                    }
                } else if (sh.kind == Doc.Shape.TABLE && sh.table != null) {
                    drawTable(c, sh.table, r, darkBg);
                } else {
                    if (sh.fill != 0) {
                        fill.setColor(sh.fill);
                        fill.setStyle(Paint.Style.FILL);
                        if (sh.ellipse) c.drawOval(r, fill);
                        else c.drawRect(r, fill);
                    }
                    if (sh.line != 0) {
                        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
                        ln.setStyle(Paint.Style.STROKE);
                        ln.setStrokeWidth(1f);
                        ln.setColor(sh.line);
                        if (sh.ellipse) c.drawOval(r, ln);
                        else c.drawRect(r, ln);
                    }
                    boolean darkShape = sh.fill != 0 ? PdfDocWriter.luminance(sh.fill) < 110 : darkBg;
                    drawText(c, sh, r, darkShape ? 0xFFFFFFFF : 0xFF202124);
                }
            } catch (Exception ignored) {
                // um elemento com problema não impede o resto do slide
            }
        }
    }

    private static void drawText(Canvas c, Doc.Shape sh, RectF r, int defColor) {
        if (sh.paras.isEmpty()) return;
        float insetX = 7.2f, insetY = 3.6f;
        int width = (int) Math.max(10, r.width() - 2 * insetX);
        float factor = 1f;
        StaticLayout layout = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            layout = build(sh, width, factor, defColor);
            if (layout.getHeight() <= r.height() - 2 * insetY || factor < 0.45f) break;
            factor *= 0.88f;
        }
        float top = r.top + insetY;
        float free = r.height() - 2 * insetY - layout.getHeight();
        if (free > 0) {
            if (sh.anchor == 1) top += free / 2;
            else if (sh.anchor == 2) top += free;
        }
        c.save();
        c.translate(r.left + insetX, top);
        layout.draw(c);
        c.restore();
    }

    private static StaticLayout build(Doc.Shape sh, int width, float factor, int defColor) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.SANS_SERIF);
        tp.setColor(defColor);
        float baseSize = (sh.isTitle ? 32 : 18) * factor;
        tp.setTextSize(baseSize);
        Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
        int num = 0;
        for (int i = 0; i < sh.paras.size(); i++) {
            Doc.Para p = sh.paras.get(i);
            if (i > 0) sb.append('\n');
            int pStart = sb.length();
            String prefix = null;
            if (p.list == Doc.LIST_BULLET) prefix = "•  ";
            else if (p.list == Doc.LIST_NUMBER) prefix = (++num) + ".  ";
            float firstSize = baseSize;
            for (Doc.Run r : p.runs) if (r.size > 0) {
                firstSize = r.size * factor;
                break;
            }
            if (prefix != null) {
                sb.append(prefix);
                sb.setSpan(new AbsoluteSizeSpan(Math.round(firstSize)), pStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            for (Doc.Run r : p.runs) {
                if (r.text == null || r.text.isEmpty()) continue;
                int s = sb.length();
                sb.append(r.text.replace('\t', ' '));
                int e = sb.length();
                float size = (r.size > 0 ? r.size : (sh.isTitle ? 32 : 18)) * factor;
                sb.setSpan(new AbsoluteSizeSpan(Math.max(4, Math.round(size))), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int style = (r.bold ? Typeface.BOLD : 0) | (r.italic ? Typeface.ITALIC : 0);
                if (style != 0) sb.setSpan(new StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (r.underline) sb.setSpan(new UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (r.color != 0) sb.setSpan(new ForegroundColorSpan(r.color), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (prefix != null) {
                tp.setTextSize(firstSize);
                int indent = (int) (p.listLevel * 24 * factor);
                int hang = indent + (int) tp.measureText(prefix);
                sb.setSpan(new LeadingMarginSpan.Standard(indent, hang), pStart, sb.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                tp.setTextSize(baseSize);
            }
            if (i == 0) {
                if (p.align == Doc.ALIGN_CENTER) align = Layout.Alignment.ALIGN_CENTER;
                else if (p.align == Doc.ALIGN_RIGHT) align = Layout.Alignment.ALIGN_OPPOSITE;
            }
        }
        if (sb.length() == 0) sb.append(" ");
        return new StaticLayout(sb, tp, width, align, 1.0f, 0, false);
    }

    private static void drawTable(Canvas c, Doc.Table t, RectF r, boolean darkBg) {
        int cols = Math.max(1, t.columnCount());
        int rows = t.rows.size();
        if (rows == 0) return;
        int[] widths = DocxWriter.columnWidths(t, cols, (int) Math.max(10, r.width()));
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Typeface.SANS_SERIF);
        float size = cols > 8 ? 9 : cols > 5 ? 11 : 13;
        float pad = 4;
        // altura de cada linha conforme o conteúdo
        StaticLayout[][] ls = new StaticLayout[rows][cols];
        float[] heights = new float[rows];
        float total = 0;
        for (int i = 0; i < rows; i++) {
            List<String> row = t.rows.get(i);
            TextPaint p = new TextPaint(tp);
            p.setTextSize(size);
            boolean header = t.header && i == 0;
            p.setColor(header ? 0xFFFFFFFF : 0xFF202124);
            if (header) p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            for (int k = 0; k < cols; k++) {
                String v = k < row.size() && row.get(k) != null ? row.get(k) : "";
                ls[i][k] = new StaticLayout(v, p, (int) Math.max(4, widths[k] - 2 * pad), Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
                heights[i] = Math.max(heights[i], ls[i][k].getHeight() + 2 * pad);
            }
            heights[i] = Math.max(heights[i], r.height() / rows);
            total += heights[i];
        }
        Paint fill = new Paint();
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xFFBFBFBF);
        line.setStrokeWidth(0.8f);
        float y = r.top;
        for (int i = 0; i < rows; i++) {
            float x = r.left;
            boolean header = t.header && i == 0;
            for (int k = 0; k < cols; k++) {
                fill.setColor(header ? 0xFFC62828 : (i % 2 == 0 ? 0xFFF5F5F5 : 0xFFFFFFFF));
                c.drawRect(x, y, x + widths[k], y + heights[i], fill);
                c.drawRect(x, y, x + widths[k], y + heights[i], line);
                c.save();
                c.clipRect(x, y, x + widths[k], y + heights[i]);
                c.translate(x + pad, y + pad);
                ls[i][k].draw(c);
                c.restore();
                x += widths[k];
            }
            y += heights[i];
        }
    }
}
