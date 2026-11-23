package br.pdfleitor.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurações do aplicativo (armazenadas localmente). */
public final class Prefs {

    private Prefs() {
    }

    public static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public static String theme(Context c) {
        return sp(c).getString("theme", "system");
    }

    public static boolean nightReading(Context c) {
        return sp(c).getBoolean("night_reading", false);
    }

    public static boolean keepScreenOn(Context c) {
        return sp(c).getBoolean("keep_screen_on", true);
    }

    public static boolean resumeLastPage(Context c) {
        return sp(c).getBoolean("resume_last_page", true);
    }

    public static String pageSize(Context c) {
        return sp(c).getString("page_size", "a4");
    }

    public static String margins(Context c) {
        return sp(c).getString("margins", "normal");
    }

    public static int imageDpi(Context c) {
        return sp(c).getInt("image_dpi", 150);
    }

    /** "auto", "always" ou "never" */
    public static String pdfPageImages(Context c) {
        return sp(c).getString("pdf_page_images", "auto");
    }

    public static boolean saveToDownloads(Context c) {
        return sp(c).getBoolean("save_downloads", true);
    }

    public static int jpegQuality(Context c) {
        return sp(c).getInt("jpeg_quality", 90);
    }

    public static void put(Context c, String key, Object value) {
        SharedPreferences.Editor e = sp(c).edit();
        if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) e.putInt(key, (Integer) value);
        else e.putString(key, String.valueOf(value));
        e.apply();
    }
}
