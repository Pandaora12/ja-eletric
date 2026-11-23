package br.pdfleitor.app;

import android.content.Context;
import android.graphics.Typeface;

/** Fontes embutidas (OFL): Instrument Serif, Inter Tight e JetBrains Mono. */
public final class Fonts {

    public static Typeface serif = Typeface.SERIF, serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC),
            sans = Typeface.SANS_SERIF, sansMedium = Typeface.SANS_SERIF, sansSemi = Typeface.DEFAULT_BOLD,
            mono = Typeface.MONOSPACE;
    private static boolean loaded;

    private Fonts() {
    }

    public static synchronized void init(Context c) {
        if (loaded) return;
        loaded = true;
        serif = load(c, "serif.ttf", serif);
        serifItalic = load(c, "serif_italic.ttf", serifItalic);
        sans = load(c, "sans.ttf", sans);
        sansMedium = load(c, "sans_medium.ttf", sansMedium);
        sansSemi = load(c, "sans_semibold.ttf", sansSemi);
        mono = load(c, "mono.ttf", mono);
    }

    private static Typeface load(Context c, String name, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getApplicationContext().getAssets(), "fonts/" + name);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
