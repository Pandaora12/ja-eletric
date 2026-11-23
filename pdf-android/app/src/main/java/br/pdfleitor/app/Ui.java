package br.pdfleitor.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

/**
 * Sistema de design "Papel & Tinta": tokens de cor, tipografia editorial
 * (Instrument Serif + Inter Tight + JetBrains Mono), formas e movimento.
 */
public final class Ui {

    // ------------------------------------------------------------------ cores

    public static int BG, CARD, CARD2, INK, INK2, INK3, LINE, ACCENT, ACCENT_SOFT, ON_ACCENT, DOCK, DOCK_INK, DOCK_MUTED, VIEWER_BG;
    /** Compatibilidade com nomes antigos. */
    public static int SURFACE, SURFACE2, TEXT, TEXT2, PRIMARY, PRIMARY_SOFT, ON_PRIMARY, DIVIDER;
    public static boolean dark;

    /** Curva de movimento padrão (ease-out expressivo). */
    public static final Interpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator EASE_IN_OUT = new PathInterpolator(0.65f, 0f, 0.35f, 1f);

    private Ui() {
    }

    public static boolean isDark(Context c) {
        String t = Prefs.theme(c);
        if (t.equals("dark")) return true;
        if (t.equals("light")) return false;
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Aplica o tema na Activity. Chamar antes de super.onCreate(). */
    public static void applyTheme(Activity a) {
        dark = isDark(a);
        Fonts.init(a);
        a.setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        if (dark) {
            BG = 0xFF0F0F0E;
            CARD = 0xFF191917;
            CARD2 = 0xFF232220;
            INK = 0xFFF1EEE7;
            INK2 = 0xFF9C978D;
            INK3 = 0xFF6A665F;
            LINE = 0xFF2B2A27;
            ACCENT = 0xFFFF6A3D;
            ACCENT_SOFT = 0xFF3A1E13;
            ON_ACCENT = 0xFF140A06;
            DOCK = 0xFFF1EEE7;
            DOCK_INK = 0xFF141412;
            DOCK_MUTED = 0xFF7D786F;
            VIEWER_BG = 0xFF0A0A09;
        } else {
            BG = 0xFFF3F0E9;
            CARD = 0xFFFBF9F5;
            CARD2 = 0xFFE9E5DC;
            INK = 0xFF141412;
            INK2 = 0xFF6E6A62;
            INK3 = 0xFFA6A196;
            LINE = 0xFFE0DBD0;
            ACCENT = 0xFFFF4F1F;
            ACCENT_SOFT = 0xFFFFE3D7;
            ON_ACCENT = 0xFFFFFFFF;
            DOCK = 0xFF141412;
            DOCK_INK = 0xFFF3F0E9;
            DOCK_MUTED = 0xFF8D887E;
            VIEWER_BG = 0xFFE4DFD4;
        }
        SURFACE = CARD;
        SURFACE2 = CARD2;
        TEXT = INK;
        TEXT2 = INK2;
        PRIMARY = ACCENT;
        PRIMARY_SOFT = ACCENT_SOFT;
        ON_PRIMARY = ON_ACCENT;
        DIVIDER = LINE;
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= 0x00000010; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** Cores das etiquetas de formato. */
    public static int formatColor(String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        if (e.equals("pdf")) return 0xFFFF4F1F;
        if (e.startsWith("doc") || e.equals("odt") || e.equals("rtf")) return 0xFF2D5BFF;
        if (e.startsWith("ppt") || e.equals("odp")) return 0xFFF0A23B;
        if (e.startsWith("xls") || e.equals("ods") || e.equals("csv")) return 0xFF1F9D61;
        if (e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("webp") || e.equals("gif") || e.equals("bmp")
                || e.equals("img") || e.equals("zip")) return 0xFF9B5DE5;
        if (e.equals("html") || e.equals("md") || e.equals("web")) return 0xFF0E9AA7;
        if (e.equals("epub")) return 0xFFD6336C;
        return 0xFF8A857B;
    }

    // ------------------------------------------------------------------ medidas

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------------------ formas

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, int strokePx, float radiusPx) {
        GradientDrawable g = rounded(fill, radiusPx);
        g.setStroke(strokePx, strokeColor);
        return g;
    }

    public static GradientDrawable dashed(int fill, int strokeColor, int strokePx, float dash, float gap, float radiusPx) {
        GradientDrawable g = rounded(fill, radiusPx);
        g.setStroke(strokePx, strokeColor, dash, gap);
        return g;
    }

    public static Drawable ripple(Drawable content, float radiusPx) {
        int rc = dark ? 0x22FFFFFF : 0x14000000;
        Drawable mask = rounded(Color.WHITE, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rc), content, mask);
    }

    public static Drawable rippleBorderless() {
        int rc = dark ? 0x22FFFFFF : 0x14000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), null, null);
    }

    public static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setTint(color);
        return d;
    }

    // ------------------------------------------------------------------ tipografia

    /** Fonte que aplica um Typeface (TypefaceSpan(Typeface) só existe no Android 9+). */
    public static final class FontSpan extends MetricAffectingSpan {
        private final Typeface tf;

        public FontSpan(Typeface tf) {
            this.tf = tf;
        }

        @Override
        public void updateDrawState(TextPaint p) {
            p.setTypeface(tf);
        }

        @Override
        public void updateMeasureState(TextPaint p) {
            p.setTypeface(tf);
        }
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(medium ? Fonts.sansSemi : Fonts.sans);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.18f);
        return t;
    }

    /** Rótulo monoespaçado em caixa alta (estilo índice editorial). */
    public static TextView label(Context c, String s, int color) {
        TextView t = new TextView(c);
        t.setText(s.toUpperCase());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        t.setTextColor(color);
        t.setTypeface(Fonts.mono);
        t.setLetterSpacing(0.12f);
        t.setIncludeFontPadding(false);
        t.setSingleLine();
        return t;
    }

    /**
     * Título editorial em serifa. Trechos entre *asteriscos* ficam em itálico
     * na cor de destaque. Ex.: "Seus\n*PDFs.*"
     */
    public static TextView display(Context c, String s, float sp) {
        TextView t = new TextView(c);
        t.setText(rich(s));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(INK);
        t.setTypeface(Fonts.serif);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 0.92f);
        t.setLetterSpacing(-0.015f);
        return t;
    }

    public static CharSequence rich(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        boolean em = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '*') {
                if (em) {
                    sb.setSpan(new FontSpan(Fonts.serifItalic), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new ForegroundColorSpan(ACCENT), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else start = sb.length();
                em = !em;
            } else sb.append(ch);
        }
        return sb;
    }

    /** Cabeçalho de página: rótulo índice + título editorial + subtítulo. */
    public static LinearLayout header(Context c, String index, String title, String subtitle) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(rounded(ACCENT, dp(c, 4)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(c, 7), dp(c, 7)));
        TextView l = label(c, index, INK2);
        l.setPadding(dp(c, 8), 0, 0, 0);
        row.addView(l);
        h.addView(row);
        TextView d = display(c, title, 54);
        d.setPadding(0, dp(c, 18), 0, 0);
        h.addView(d);
        if (subtitle != null) {
            TextView s = text(c, subtitle, 15, INK2, false);
            s.setPadding(0, dp(c, 14), dp(c, 24), 0);
            h.addView(s);
        }
        return h;
    }

    public static TextView section(Context c, String s) {
        TextView t = label(c, s, INK2);
        t.setPadding(dp(c, 4), dp(c, 30), 0, dp(c, 12));
        return t;
    }

    // ------------------------------------------------------------------ componentes

    public static ImageView iconButton(Context c, int res, int color, String desc) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon(c, res, color));
        int p = dp(c, 11);
        iv.setPadding(p, p, p, p);
        iv.setBackground(rippleBorderless());
        iv.setContentDescription(desc);
        iv.setClickable(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return iv;
    }

    /** Botão circular com fundo. */
    public static ImageView circleButton(Context c, int res, int bg, int fg, int sizeDp, String desc) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon(c, res, fg));
        int p = dp(c, sizeDp * 0.28f);
        iv.setPadding(p, p, p, p);
        iv.setBackground(ripple(rounded(bg, dp(c, sizeDp / 2f)), dp(c, sizeDp / 2f)));
        iv.setContentDescription(desc);
        iv.setClickable(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        press(iv);
        return iv;
    }

    /** Botão em cápsula: "filled" usa tinta (ou acento se accent=true). */
    public static TextView button(Context c, String label, boolean filled) {
        return button(c, label, filled, false);
    }

    public static TextView button(Context c, String label, boolean filled, boolean accent) {
        int bg = accent ? ACCENT : INK;
        int fg = accent ? ON_ACCENT : BG;
        TextView b = text(c, label, 15, filled ? fg : INK, true);
        b.setGravity(Gravity.CENTER);
        int r = dp(c, 28);
        b.setBackground(ripple(filled ? rounded(bg, r) : stroke(Color.TRANSPARENT, LINE, dp(c, 1.2f), r), r));
        b.setSingleLine();
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        b.setMinHeight(dp(c, 54));
        b.setMinimumHeight(dp(c, 54));
        b.setClickable(true);
        press(b);
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(stroke(CARD, LINE, Math.max(1, dp(c, 0.8f)), dp(c, 26)));
        int p = dp(c, 20);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.8f))));
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpMargins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    // ------------------------------------------------------------------ movimento

    /** Efeito tátil: encolhe levemente ao tocar. */
    public static void press(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.965f).scaleY(0.965f).setDuration(140).setInterpolator(EASE).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(EASE).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Entrada escalonada dos filhos (fade + subida). */
    public static void stagger(ViewGroup g, long startDelay) {
        float dy = dp(g.getContext(), 22);
        int n = 0;
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.getVisibility() != View.VISIBLE) continue;
            v.setAlpha(0f);
            v.setTranslationY(dy);
            v.animate().alpha(1f).translationY(0).setStartDelay(startDelay + n * 55L).setDuration(560).setInterpolator(EASE).start();
            n++;
        }
    }

    public static void enter(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 18));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(520).setInterpolator(EASE).start();
    }

    /** Anima um número inteiro em um TextView. */
    public static void countUp(final TextView t, final int to) {
        ValueAnimator a = ValueAnimator.ofInt(0, to);
        a.setDuration(Math.min(1200, 500 + to * 20L));
        a.setInterpolator(EASE);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                t.setText(String.valueOf(va.getAnimatedValue()));
            }
        });
        a.start();
    }

    // ------------------------------------------------------------------ feedback

    public interface MenuCallback {
        void onItem(int id);
    }

    /** Menu de opções em painel inferior (ids começam em 1). */
    public static void menu(Context c, String title, String[] items, MenuCallback cb) {
        Sheet.menu(c, title, items, cb);
    }

    public static void toast(Context c, String s) {
        if (c instanceof Activity && !((Activity) c).isFinishing()) Snack.show((Activity) c, s);
        else Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ formatação

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#,##0.#");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return df.format(bytes / (1024.0 * 1024)) + " MB";
        return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
    }

    public static String ago(long when) {
        long d = (System.currentTimeMillis() - when) / 1000;
        if (d < 60) return "agora";
        if (d < 3600) return "há " + (d / 60) + " min";
        if (d < 86400) return "há " + (d / 3600) + " h";
        if (d < 86400 * 2) return "ontem";
        if (d < 86400 * 30) return "há " + (d / 86400) + " dias";
        return new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(new java.util.Date(when));
    }

    static Paint paint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        return p;
    }
}
