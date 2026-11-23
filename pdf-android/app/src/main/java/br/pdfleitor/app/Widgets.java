package br.pdfleitor.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Componentes visuais próprios do sistema "Papel & Tinta". */
public final class Widgets {

    private Widgets() {
    }

    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static int blend(int a, int b, float t) {
        return Color.argb((int) lerp(Color.alpha(a), Color.alpha(b), t), (int) lerp(Color.red(a), Color.red(b), t),
                (int) lerp(Color.green(a), Color.green(b), t), (int) lerp(Color.blue(a), Color.blue(b), t));
    }

    // ================================================================== Dock

    /** Barra inferior flutuante em cápsula, com pílula que desliza e se expande. */
    public static class Dock extends LinearLayout {
        public interface Listener {
            void onSelect(int index);
        }

        private final LinearLayout[] items;
        private final ImageView[] icons;
        private final TextView[] labels;
        private final int[] iconRes;
        private final float[] weights;
        private int selected = -1;
        private final Paint pill = Ui.paint(Ui.ACCENT);
        private final RectF r = new RectF();
        private Listener listener;
        private ValueAnimator anim;

        public Dock(Context c, String[] names, int[] res) {
            super(c);
            setOrientation(HORIZONTAL);
            setWillNotDraw(false);
            setBackground(Ui.rounded(Ui.DOCK, Ui.dp(c, 34)));
            setElevation(Ui.dp(c, 14));
            int p = Ui.dp(c, 6);
            setPadding(p, p, p, p);
            items = new LinearLayout[names.length];
            icons = new ImageView[names.length];
            labels = new TextView[names.length];
            weights = new float[names.length];
            iconRes = res;
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                LinearLayout it = new LinearLayout(c);
                it.setOrientation(HORIZONTAL);
                it.setGravity(Gravity.CENTER);
                it.setClickable(true);
                it.setContentDescription(names[i]);
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(Ui.icon(c, res[i], Ui.DOCK_MUTED));
                it.addView(iv, new LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22)));
                TextView tv = Ui.text(c, names[i], 14, Ui.ON_ACCENT, true);
                tv.setSingleLine();
                tv.setPadding(Ui.dp(c, 9), 0, 0, 0);
                tv.setVisibility(GONE);
                it.addView(tv);
                it.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) listener.onSelect(idx);
                    }
                });
                weights[i] = 1f;
                addView(it, new LayoutParams(0, Ui.dp(c, 56), 1f));
                items[i] = it;
                icons[i] = iv;
                labels[i] = tv;
            }
        }

        public void setListener(Listener l) {
            listener = l;
        }

        public void select(int idx, boolean animate) {
            if (idx == selected) return;
            final int prev = selected;
            selected = idx;
            final float[] from = weights.clone();
            final float[] to = new float[weights.length];
            for (int i = 0; i < to.length; i++) to[i] = i == idx ? 2.15f : 1f;
            for (int i = 0; i < labels.length; i++) {
                boolean sel = i == idx;
                labels[i].setVisibility(sel ? VISIBLE : GONE);
                icons[i].setImageDrawable(Ui.icon(getContext(), iconRes[i], sel ? Ui.ON_ACCENT : Ui.DOCK_MUTED));
            }
            if (anim != null) anim.cancel();
            if (!animate || prev < 0) {
                for (int i = 0; i < to.length; i++) setWeight(i, to[i]);
                return;
            }
            labels[idx].setAlpha(0f);
            labels[idx].animate().alpha(1f).setStartDelay(120).setDuration(260).start();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(460);
            anim.setInterpolator(Ui.EASE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    float f = (Float) a.getAnimatedValue();
                    for (int i = 0; i < to.length; i++) setWeight(i, lerp(from[i], to[i], f));
                }
            });
            anim.start();
        }

        private void setWeight(int i, float w) {
            weights[i] = w;
            LayoutParams lp = (LayoutParams) items[i].getLayoutParams();
            lp.weight = w;
            items[i].setLayoutParams(lp);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (selected >= 0) {
                View v = items[selected];
                r.set(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                float rad = r.height() / 2f;
                canvas.drawRoundRect(r, rad, rad, pill);
            }
            super.dispatchDraw(canvas);
        }
    }

    // ================================================================== Segmented

    /** Controle segmentado com indicador deslizante. */
    public static class Segmented extends LinearLayout {
        public interface Listener {
            void onChange(int index);
        }

        private final TextView[] opts;
        private int selected, from;
        private float frac = 1f;
        private final Paint pill = Ui.paint(Ui.CARD);
        private final Paint shadow = Ui.paint(Ui.dark ? 0x33000000 : 0x14000000);
        private final RectF r = new RectF();
        private Listener listener;

        public Segmented(Context c, String[] labels, int initial) {
            super(c);
            setOrientation(HORIZONTAL);
            setWillNotDraw(false);
            setBackground(Ui.rounded(Ui.CARD2, Ui.dp(c, 18)));
            int p = Ui.dp(c, 4);
            setPadding(p, p, p, p);
            opts = new TextView[labels.length];
            selected = from = Math.max(0, initial);
            if (Ui.dark) pill.setColor(0xFF34332F);
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                TextView t = Ui.text(c, labels[i], 14, i == selected ? Ui.INK : Ui.INK2, true);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine();
                t.setClickable(true);
                t.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        set(idx, true);
                        if (listener != null) listener.onChange(idx);
                    }
                });
                addView(t, new LayoutParams(0, Ui.dp(c, 42), 1));
                opts[i] = t;
            }
        }

        public void setListener(Listener l) {
            listener = l;
        }

        public void set(int idx, boolean animate) {
            if (idx == selected) return;
            from = selected;
            selected = idx;
            for (int i = 0; i < opts.length; i++) opts[i].setTextColor(i == idx ? Ui.INK : Ui.INK2);
            if (!animate) {
                frac = 1f;
                invalidate();
                return;
            }
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(360);
            a.setInterpolator(Ui.EASE);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    frac = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            View a = opts[from], b = opts[selected];
            r.set(lerp(a.getLeft(), b.getLeft(), frac), b.getTop(), lerp(a.getRight(), b.getRight(), frac), b.getBottom());
            float rad = Ui.dp(getContext(), 14);
            r.offset(0, Ui.dp(getContext(), 1));
            canvas.drawRoundRect(r, rad, rad, shadow);
            r.offset(0, -Ui.dp(getContext(), 1));
            canvas.drawRoundRect(r, rad, rad, pill);
            super.dispatchDraw(canvas);
        }
    }

    // ================================================================== Toggle

    /** Interruptor desenhado à mão, com animação. */
    public static class Toggle extends View {
        public interface Listener {
            void onChange(boolean on);
        }

        private boolean on;
        private float pos;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Listener listener;

        public Toggle(Context c, boolean value) {
            super(c);
            on = value;
            pos = value ? 1 : 0;
            thumb.setColor(Color.WHITE);
            thumb.setShadowLayer(Ui.dp(c, 2), 0, Ui.dp(c, 1), 0x33000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            setClickable(true);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        public void setListener(Listener l) {
            listener = l;
        }

        public void toggle() {
            on = !on;
            ValueAnimator a = ValueAnimator.ofFloat(pos, on ? 1 : 0);
            a.setDuration(320);
            a.setInterpolator(Ui.EASE);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    pos = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
            if (listener != null) listener.onChange(on);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Ui.dp(getContext(), 54), Ui.dp(getContext(), 32));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            track.setColor(blend(Ui.CARD2, Ui.ACCENT, pos));
            c.drawRoundRect(0, 0, w, h, h / 2, h / 2, track);
            float pad = Ui.dp(getContext(), 3.5f);
            float rad = h / 2 - pad;
            float cx = lerp(pad + rad, w - pad - rad, pos);
            c.drawCircle(cx, h / 2, rad, thumb);
        }
    }

    // ================================================================== Loaders

    /** Linha de progresso indeterminada (varredura). */
    public static class LineLoader extends View {
        private final Paint track = Ui.paint(Ui.LINE), bar = Ui.paint(Ui.ACCENT);
        private float t;
        private ValueAnimator anim;

        public LineLoader(Context c) {
            super(c);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1300);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(Ui.EASE_IN_OUT);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    t = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawRoundRect(0, 0, w, h, h / 2, h / 2, track);
            float len = w * 0.38f;
            float x = lerp(-len, w, t);
            c.save();
            c.clipRect(0, 0, w, h);
            c.drawRoundRect(x, 0, x + len, h, h / 2, h / 2, bar);
            c.restore();
        }
    }

    /** Barra fina de progresso determinado. */
    public static class ProgressLine extends View {
        private final Paint track = Ui.paint(Ui.LINE), bar = Ui.paint(Ui.ACCENT);
        private float value;

        public ProgressLine(Context c) {
            super(c);
        }

        public void set(float v) {
            value = Math.max(0, Math.min(1, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            c.drawRoundRect(0, 0, w, h, h / 2, h / 2, track);
            if (value > 0) c.drawRoundRect(0, 0, Math.max(h, w * value), h, h / 2, h / 2, bar);
        }
    }

    /** Selo de sucesso: círculo que surge e um traço de "check" desenhado. */
    public static class CheckMark extends View {
        private final Paint circle = Ui.paint(Ui.ACCENT), stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float t;

        public CheckMark(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(Ui.ON_ACCENT);
            stroke.setStrokeWidth(Ui.dp(c, 3));
        }

        public void play() {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(700);
            a.setInterpolator(Ui.EASE);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    t = (Float) va.getAnimatedValue();
                    invalidate();
                }
            });
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(1f, t * 1.6f);
            c.drawCircle(w / 2, h / 2, Math.min(w, h) / 2 * s, circle);
            float ct = Math.max(0f, (t - 0.35f) / 0.65f);
            if (ct <= 0) return;
            Path p = new Path();
            p.moveTo(w * 0.30f, h * 0.52f);
            p.lineTo(w * 0.44f, h * 0.66f);
            p.lineTo(w * 0.71f, h * 0.38f);
            PathMeasure pm = new PathMeasure(p, false);
            Path seg = new Path();
            pm.getSegment(0, pm.getLength() * ct, seg, true);
            c.drawPath(seg, stroke);
        }
    }

    // ================================================================== Ilustração

    /** Pilha de folhas (estado vazio), com leve flutuação. */
    public static class PaperArt extends View {
        private final Paint sheet = Ui.paint(Ui.CARD), edge = new Paint(Paint.ANTI_ALIAS_FLAG), line = Ui.paint(Ui.CARD2),
                accent = Ui.paint(Ui.ACCENT), fold = Ui.paint(Ui.ACCENT_SOFT), shadow = Ui.paint(Ui.dark ? 0x55000000 : 0x18000000);
        private float t;
        private ValueAnimator anim;

        public PaperArt(Context c) {
            super(c);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Ui.dp(c, 1.2f));
            edge.setColor(Ui.LINE);
            if (Ui.dark) line.setColor(0xFF2E2D2A);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
            anim.setDuration(5200);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(null);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    t = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        private void drawSheet(Canvas c, float cx, float cy, float w, float h, float rot, boolean top) {
            c.save();
            c.rotate(rot, cx, cy);
            float l = cx - w / 2, tp = cy - h / 2, r = Ui.dp(getContext(), 10);
            c.drawRoundRect(l + 2, tp + Ui.dp(getContext(), 6), l + w + 2, tp + h + Ui.dp(getContext(), 6), r, r, shadow);
            c.drawRoundRect(l, tp, l + w, tp + h, r, r, sheet);
            c.drawRoundRect(l, tp, l + w, tp + h, r, r, edge);
            if (top) {
                float f = w * 0.24f;
                Path p = new Path();
                p.moveTo(l + w - f, tp);
                p.lineTo(l + w, tp + f);
                p.lineTo(l + w - f, tp + f);
                p.close();
                c.drawPath(p, fold);
                float lh = Ui.dp(getContext(), 7), gap = Ui.dp(getContext(), 13);
                float y = tp + h * 0.36f;
                float[] lens = {0.62f, 0.7f, 0.48f, 0.66f};
                for (int i = 0; i < lens.length; i++) {
                    c.drawRoundRect(l + w * 0.14f, y, l + w * (0.14f + lens[i]), y + lh, lh / 2, lh / 2, i == 0 ? accent : line);
                    y += gap;
                }
            }
            c.restore();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float sw = Math.min(w * 0.42f, h * 0.62f), sh = sw * 1.3f;
            float bob = (float) Math.sin(t) * Ui.dp(getContext(), 4);
            drawSheet(c, w / 2 - sw * 0.22f, h / 2 + Ui.dp(getContext(), 6), sw, sh, -11 + (float) Math.sin(t) * 1.2f, false);
            drawSheet(c, w / 2 + sw * 0.2f, h / 2 + Ui.dp(getContext(), 2), sw, sh, 8 - (float) Math.sin(t) * 1.2f, false);
            drawSheet(c, w / 2, h / 2 - Ui.dp(getContext(), 4) + bob, sw, sh, -2 + (float) Math.sin(t + 1) * 1.5f, true);
            // selo circular
            float r = Ui.dp(getContext(), 18);
            c.drawCircle(w / 2 + sw * 0.42f, h / 2 + sh * 0.36f + bob, r, accent);
        }
    }

    // ================================================================== Etiqueta de formato

    /** Etiqueta em forma de arquivo com canto dobrado e extensão. */
    public static class FileTag extends View {
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), fold = new Paint(Paint.ANTI_ALIAS_FLAG),
                text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String ext;

        public FileTag(Context c, String ext) {
            super(c);
            set(ext);
            text.setTypeface(Fonts.mono);
            text.setTextAlign(Paint.Align.CENTER);
            text.setLetterSpacing(0.04f);
        }

        public void set(String e) {
            ext = e == null ? "" : e.toUpperCase();
            if (ext.equals("JPEG")) ext = "JPG";
            if (ext.length() > 4) ext = ext.substring(0, 4);
            int col = Ui.formatColor(e);
            body.setColor(col);
            fold.setColor(blend(col, Color.WHITE, 0.45f));
            text.setColor(col == 0xFFF0A23B ? 0xFF2A1A00 : Color.WHITE);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) * 0.14f, f = w * 0.32f;
            Path p = new Path();
            p.moveTo(r, 0);
            p.lineTo(w - f, 0);
            p.lineTo(w, f);
            p.lineTo(w, h - r);
            p.quadTo(w, h, w - r, h);
            p.lineTo(r, h);
            p.quadTo(0, h, 0, h - r);
            p.lineTo(0, r);
            p.quadTo(0, 0, r, 0);
            p.close();
            c.drawPath(p, body);
            Path q = new Path();
            q.moveTo(w - f, 0);
            q.lineTo(w - f, f * 0.7f);
            q.quadTo(w - f, f, w - f * 0.7f, f);
            q.lineTo(w, f);
            q.close();
            c.drawPath(q, fold);
            text.setTextSize(Math.min(w * (ext.length() > 3 ? 0.24f : 0.3f), h * 0.24f));
            c.drawText(ext, w / 2, h * 0.78f, text);
        }
    }
}
