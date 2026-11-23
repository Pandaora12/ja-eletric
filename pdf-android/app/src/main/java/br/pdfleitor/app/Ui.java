package br.pdfleitor.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

/** Cores, tema e utilitários de interface (UI feita em código, sem bibliotecas). */
public final class Ui {

    public static int BG, SURFACE, SURFACE2, TEXT, TEXT2, PRIMARY, PRIMARY_SOFT, ON_PRIMARY, DIVIDER;
    public static boolean dark;

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
        a.setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        if (dark) {
            BG = 0xFF111214;
            SURFACE = 0xFF1C1D21;
            SURFACE2 = 0xFF26282D;
            TEXT = 0xFFECEDEF;
            TEXT2 = 0xFFA0A4AA;
            PRIMARY = 0xFFFF7A6E;
            PRIMARY_SOFT = 0xFF3B2220;
            ON_PRIMARY = 0xFF2A0C09;
            DIVIDER = 0xFF2C2E33;
        } else {
            BG = 0xFFF4F5F7;
            SURFACE = 0xFFFFFFFF;
            SURFACE2 = 0xFFEDEEF1;
            TEXT = 0xFF1C1D21;
            TEXT2 = 0xFF5F6368;
            PRIMARY = 0xFFD93025;
            PRIMARY_SOFT = 0xFFFCE8E6;
            ON_PRIMARY = 0xFFFFFFFF;
            DIVIDER = 0xFFE2E4E8;
        }
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(SURFACE);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= 0x00000010; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

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

    /** Fundo com efeito de toque (ripple). */
    public static Drawable ripple(Drawable content, float radiusPx) {
        int rc = dark ? 0x33FFFFFF : 0x22000000;
        Drawable mask = rounded(Color.WHITE, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rc), content, mask);
    }

    public static Drawable rippleBorderless() {
        int rc = dark ? 0x33FFFFFF : 0x22000000;
        return new RippleDrawable(ColorStateList.valueOf(rc), null, null);
    }

    public static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setTint(color);
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 26, TEXT, false);
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return t;
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s, 13, TEXT2, true);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 4), dp(c, 20), 0, dp(c, 8));
        return t;
    }

    public static ImageView iconButton(Context c, int res, int color, String desc) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon(c, res, color));
        int p = dp(c, 10);
        iv.setPadding(p, p, p, p);
        iv.setBackground(rippleBorderless());
        iv.setContentDescription(desc);
        iv.setClickable(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        return iv;
    }

    /** Botão preenchido (primário) ou contornado. */
    public static TextView button(Context c, String label, boolean filled) {
        TextView b = text(c, label, 15, filled ? ON_PRIMARY : PRIMARY, true);
        b.setGravity(Gravity.CENTER);
        int r = dp(c, 24);
        b.setBackground(ripple(filled ? rounded(PRIMARY, r) : stroke(Color.TRANSPARENT, PRIMARY, dp(c, 1.5f), r), r));
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setMinHeight(dp(c, 48));
        b.setClickable(true);
        b.setSingleLine();
        b.setEllipsize(TextUtils.TruncateAt.END);
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(SURFACE, dp(c, 18)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        if (!dark) l.setElevation(dp(c, 1));
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
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

    public interface MenuCallback {
        void onItem(int id);
    }

    /** Menu de opções (ids começam em 1, na ordem dos itens). */
    public static void menu(Context c, String title, String[] items, final MenuCallback cb) {
        new android.app.AlertDialog.Builder(c).setTitle(title)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        cb.onItem(which + 1);
                    }
                }).show();
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

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
}
