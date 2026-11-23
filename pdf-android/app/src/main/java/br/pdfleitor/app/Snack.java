package br.pdfleitor.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aviso curto em cápsula, acima da barra inferior. */
public final class Snack {

    private static View current;
    /** Distância extra da base (ex.: altura da barra flutuante). */
    public static int bottomOffsetDp = 24;

    private Snack() {
    }

    public static void show(Activity a, String msg) {
        ViewGroup root = (ViewGroup) a.findViewById(android.R.id.content);
        if (root == null) return;
        if (current != null && current.getParent() != null) ((ViewGroup) current.getParent()).removeView(current);
        LinearLayout pill = new LinearLayout(a);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.rounded(Ui.DOCK, Ui.dp(a, 24)));
        pill.setElevation(Ui.dp(a, 8));
        pill.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 20), Ui.dp(a, 14));
        View dot = new View(a);
        dot.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(a, 4)));
        pill.addView(dot, new LinearLayout.LayoutParams(Ui.dp(a, 8), Ui.dp(a, 8)));
        TextView t = Ui.text(a, msg, 14.5f, Ui.DOCK_INK, false);
        t.setPadding(Ui.dp(a, 12), 0, 0, 0);
        t.setMaxLines(3);
        pill.addView(t);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int m = Ui.dp(a, 20);
        lp.setMargins(m, 0, m, Ui.dp(a, bottomOffsetDp));
        root.addView(pill, lp);
        current = pill;
        final View v = pill;
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(a, 30));
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(Ui.EASE).start();
        v.postDelayed(new Runnable() {
            @Override
            public void run() {
                v.animate().alpha(0f).translationY(Ui.dp(v.getContext(), 16)).setDuration(260).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
                    }
                }).start();
            }
        }, 2800);
    }
}
