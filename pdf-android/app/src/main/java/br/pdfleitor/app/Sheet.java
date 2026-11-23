package br.pdfleitor.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Painel que sobe da parte inferior (substitui os diálogos padrão), com
 * cantos arredondados, alça, título em serifa e animação.
 */
public class Sheet {

    public interface TextCallback {
        void onText(String s);
    }

    private final Dialog dialog;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final Context ctx;
    private boolean closing;

    public Sheet(Context c, String title, String subtitle) {
        ctx = c;
        dialog = new Dialog(c, android.R.style.Theme_Material_Dialog_NoActionBar);
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(c, 30);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.CARD);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        int side = Ui.dp(c, 22);
        panel.setPadding(side, Ui.dp(c, 12), side, Ui.dp(c, 26));
        View handle = new View(c);
        handle.setBackground(Ui.rounded(Ui.LINE, Ui.dp(c, 3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = Ui.dp(c, 20);
        panel.addView(handle, hl);
        if (title != null) {
            TextView t = Ui.display(c, title, 32);
            panel.addView(t);
        }
        if (subtitle != null) {
            TextView s = Ui.text(c, subtitle, 14.5f, Ui.INK2, false);
            s.setPadding(0, Ui.dp(c, 10), 0, 0);
            panel.addView(s);
        }
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        panel.addView(body, Ui.lpMargins(-1, -2, 0, Ui.dp(c, 18), 0, 0));
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.getDecorView().setPadding(0, 0, 0, 0);
            w.setDimAmount(Ui.dark ? 0.6f : 0.38f);
            w.setWindowAnimations(0);
        }
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                panel.setTranslationY(panel.getHeight() + Ui.dp(ctx, 40));
                panel.animate().translationY(0).setDuration(420).setInterpolator(Ui.EASE).start();
                for (int i = 0; i < body.getChildCount(); i++) {
                    View v = body.getChildAt(i);
                    v.setAlpha(0f);
                    v.animate().alpha(1f).setStartDelay(120 + i * 35L).setDuration(300).start();
                }
            }
        });
    }

    public LinearLayout body() {
        return body;
    }

    public Sheet show() {
        if (ctx instanceof Activity && ((Activity) ctx).isFinishing()) return this;
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return this;
    }

    public void dismiss() {
        dismiss(null);
    }

    /** Fecha com animação e executa a ação depois. */
    public void dismiss(final Runnable after) {
        if (closing) return;
        closing = true;
        panel.animate().translationY(panel.getHeight() + Ui.dp(ctx, 40)).setDuration(240).setInterpolator(Ui.EASE_IN_OUT)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {
                        }
                        if (after != null) after.run();
                    }
                }).start();
    }

    public Dialog dialog() {
        return dialog;
    }

    // ------------------------------------------------------------------ fábricas

    /** Linha de opção com índice numérico em mono. */
    public View option(int index, String label, boolean destructive, final Runnable action) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int r = Ui.dp(ctx, 18);
        row.setBackground(Ui.ripple(null, r));
        row.setPadding(Ui.dp(ctx, 4), 0, Ui.dp(ctx, 8), 0);
        row.setMinimumHeight(Ui.dp(ctx, 56));
        TextView idx = Ui.label(ctx, String.format("%02d", index), Ui.INK3);
        row.addView(idx, new LinearLayout.LayoutParams(Ui.dp(ctx, 34), -2));
        TextView t = Ui.text(ctx, label, 17, destructive ? Ui.ACCENT : Ui.INK, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = Ui.text(ctx, "→", 17, Ui.INK3, false);
        row.addView(arrow);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss(action);
            }
        });
        return row;
    }

    public static void menu(Context c, String title, String[] items, final Ui.MenuCallback cb) {
        Sheet s = new Sheet(c, title, null);
        for (int i = 0; i < items.length; i++) {
            final int id = i + 1;
            boolean destructive = items[i].startsWith("Remover") || items[i].startsWith("Excluir");
            if (i > 0) s.body.addView(Ui.divider(c));
            s.body.addView(s.option(id, items[i], destructive, new Runnable() {
                @Override
                public void run() {
                    cb.onItem(id);
                }
            }));
        }
        s.show();
    }

    public static void confirm(Context c, String title, String msg, String ok, boolean destructive, final Runnable action) {
        final Sheet s = new Sheet(c, title, msg);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.button(c, "Cancelar", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss();
            }
        });
        row.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView go = Ui.button(c, ok, true, destructive);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss(action);
            }
        });
        row.addView(go, Ui.lpMargins(0, -2, Ui.dp(c, 10), 0, 0, 0));
        ((LinearLayout.LayoutParams) go.getLayoutParams()).weight = 1;
        s.body.addView(row, Ui.lpMargins(-1, -2, 0, Ui.dp(c, 8), 0, 0));
        s.show();
    }

    public static void info(Context c, String title, String msg) {
        final Sheet s = new Sheet(c, title, msg);
        TextView ok = Ui.button(c, "Entendi", true);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss();
            }
        });
        s.body.addView(ok, Ui.lpMargins(-1, -2, 0, Ui.dp(c, 8), 0, 0));
        s.show();
    }

    /** Painel com pares rótulo/valor (ex.: informações do arquivo). */
    public static void facts(Context c, String title, String[][] rows) {
        final Sheet s = new Sheet(c, title, null);
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) s.body.addView(Ui.divider(c));
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(Ui.dp(c, 52));
            r.addView(Ui.label(c, rows[i][0], Ui.INK2), new LinearLayout.LayoutParams(Ui.dp(c, 110), -2));
            TextView v = Ui.text(c, rows[i][1], 15.5f, Ui.INK, false);
            v.setGravity(Gravity.END);
            r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
            s.body.addView(r);
        }
        TextView ok = Ui.button(c, "Fechar", false);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.dismiss();
            }
        });
        s.body.addView(ok, Ui.lpMargins(-1, -2, 0, Ui.dp(c, 16), 0, 0));
        s.show();
    }

    public static void input(final Context c, String title, String hint, boolean number, String ok, final TextCallback cb) {
        final Sheet s = new Sheet(c, title, null);
        final EditText et = new EditText(c);
        et.setHint(hint);
        et.setHintTextColor(Ui.INK3);
        et.setTextColor(Ui.INK);
        et.setTypeface(Fonts.sans);
        et.setTextSize(19);
        et.setSingleLine();
        et.setBackground(Ui.rounded(Ui.CARD2, Ui.dp(c, 18)));
        et.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        et.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        s.body.addView(et, new LinearLayout.LayoutParams(-1, Ui.dp(c, 58)));
        final Runnable submit = new Runnable() {
            @Override
            public void run() {
                final String v = et.getText().toString().trim();
                if (v.isEmpty()) return;
                InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
                s.dismiss(new Runnable() {
                    @Override
                    public void run() {
                        cb.onText(v);
                    }
                });
            }
        };
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, android.view.KeyEvent e) {
                submit.run();
                return true;
            }
        });
        TextView go = Ui.button(c, ok, true, true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit.run();
            }
        });
        s.body.addView(go, Ui.lpMargins(-1, -2, 0, Ui.dp(c, 12), 0, 0));
        Window w = s.dialog.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        s.show();
        et.requestFocus();
    }

    /** Lista rolável (ex.: resultados de busca). */
    public static void list(Context c, String title, String subtitle, String[] heads, String[] items, final Ui.MenuCallback cb) {
        final Sheet s = new Sheet(c, title, subtitle);
        ScrollView sv = new ScrollView(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col);
        for (int i = 0; i < items.length; i++) {
            final int id = i + 1;
            if (i > 0) col.addView(Ui.divider(c));
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14));
            r.setBackground(Ui.ripple(null, Ui.dp(c, 14)));
            r.addView(Ui.label(c, heads[i], Ui.ACCENT));
            TextView t = Ui.text(c, items[i], 15, Ui.INK, false);
            t.setPadding(0, Ui.dp(c, 6), 0, 0);
            t.setMaxLines(3);
            r.addView(t);
            r.setClickable(true);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    s.dismiss(new Runnable() {
                        @Override
                        public void run() {
                            cb.onItem(id);
                        }
                    });
                }
            });
            col.addView(r);
        }
        int max = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f);
        s.body.addView(new MaxHeight(c, sv, max));
        s.show();
    }

    /** Painel de carregamento com indicador animado. */
    public static Sheet loading(Context c, String msg) {
        Sheet s = new Sheet(c, null, null);
        s.dialog.setCancelable(false);
        TextView t = Ui.text(c, msg, 16, Ui.INK, false);
        s.body.addView(t);
        Widgets.LineLoader l = new Widgets.LineLoader(c);
        s.body.addView(l, Ui.lpMargins(-1, Ui.dp(c, 4), 0, Ui.dp(c, 18), 0, Ui.dp(c, 6)));
        return s.show();
    }

    /** Contêiner que limita a altura do filho. */
    static final class MaxHeight extends LinearLayout {
        private final int max;

        MaxHeight(Context c, View child, int max) {
            super(c);
            this.max = max;
            addView(child, new LayoutParams(-1, -2));
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }
}
