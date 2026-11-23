package br.pdfleitor.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Miniaturas da primeira página dos PDFs (cache em memória e em disco). */
public final class Thumbs {

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap v) {
            return v.getByteCount();
        }
    };
    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<String> FAILED = new HashSet<String>();

    public interface PagesCallback {
        void onPages(String uri, int pages);
    }

    private Thumbs() {
    }

    public static File dir(Context c) {
        File d = new File(c.getCacheDir(), "thumbs");
        d.mkdirs();
        return d;
    }

    public static void load(final Context ctx, final String uri, final ImageView target, final int widthPx, final PagesCallback pc) {
        target.setTag(uri);
        Bitmap b = MEM.get(uri);
        if (b != null) {
            target.setImageBitmap(b);
            return;
        }
        target.setImageDrawable(null);
        if (FAILED.contains(uri)) return;
        final Context c = ctx.getApplicationContext();
        EX.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                int pages = -1;
                File f = new File(dir(c), Files.sha1(uri) + ".png");
                if (f.exists()) bmp = BitmapFactory.decodeFile(f.getPath());
                if (bmp == null) {
                    ParcelFileDescriptor pfd = null;
                    PdfRenderer r = null;
                    try {
                        pfd = c.getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
                        r = new PdfRenderer(pfd);
                        pages = r.getPageCount();
                        PdfRenderer.Page p = r.openPage(0);
                        int w = widthPx, h = Math.round(widthPx * (float) p.getHeight() / p.getWidth());
                        h = Math.min(h, widthPx * 2);
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bmp.eraseColor(Color.WHITE);
                        Matrix m = new Matrix();
                        m.setScale((float) w / p.getWidth(), (float) w / p.getWidth());
                        p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        p.close();
                        FileOutputStream fos = new FileOutputStream(f);
                        bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
                        fos.close();
                    } catch (Throwable t) {
                        bmp = null;
                    } finally {
                        try {
                            if (r != null) r.close();
                            if (pfd != null) pfd.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                final Bitmap result = bmp;
                final int pg = pages;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            FAILED.add(uri);
                            return;
                        }
                        MEM.put(uri, result);
                        if (uri.equals(target.getTag())) target.setImageBitmap(result);
                        if (pg > 0 && pc != null) pc.onPages(uri, pg);
                    }
                });
            }
        });
    }

    public static void clear(Context c) {
        MEM.evictAll();
        FAILED.clear();
        Files.deleteRecursive(dir(c));
    }
}
