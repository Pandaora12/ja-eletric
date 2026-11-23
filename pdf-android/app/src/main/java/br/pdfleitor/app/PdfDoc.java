package br.pdfleitor.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Documento PDF aberto com o PdfRenderer nativo. Todo acesso ao renderer
 * acontece em uma única thread dedicada (o PdfRenderer não é thread-safe).
 */
public class PdfDoc {

    public interface OpenCallback {
        void onOpened(PdfDoc doc);

        void onError(String message);
    }

    public interface BitmapCallback {
        void onBitmap(Bitmap bmp);
    }

    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    public int count;
    public float[] w, h;
    private volatile boolean closed;

    private PdfDoc() {
    }

    public static void open(final Context c, final Uri uri, final OpenCallback cb) {
        final PdfDoc d = new PdfDoc();
        d.ex.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    d.pfd = c.getContentResolver().openFileDescriptor(uri, "r");
                    if (d.pfd == null) throw new IOException("Não foi possível abrir o arquivo.");
                    d.renderer = Converter.openRenderer(d.pfd);
                    d.count = d.renderer.getPageCount();
                    d.w = new float[d.count];
                    d.h = new float[d.count];
                    for (int i = 0; i < d.count; i++) {
                        PdfRenderer.Page p = d.renderer.openPage(i);
                        d.w[i] = p.getWidth();
                        d.h[i] = p.getHeight();
                        p.close();
                    }
                    d.main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onOpened(d);
                        }
                    });
                } catch (final Exception e) {
                    d.closeNow();
                    final String msg = e instanceof java.io.FileNotFoundException
                            ? "Arquivo não encontrado. Ele pode ter sido movido, renomeado ou apagado."
                            : e instanceof SecurityException
                            ? "Sem permissão para abrir este arquivo. Abra-o novamente pelo botão \"+\"."
                            : (e.getMessage() != null ? e.getMessage() : "Não foi possível abrir o PDF.");
                    d.main.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.onError(msg);
                        }
                    });
                }
            }
        });
    }

    /** Renderiza a página inteira com a largura informada (em pixels). */
    public void renderPage(final int index, final int width, final BitmapCallback cb, final Check check) {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                if (closed) return;
                if (check != null && !check.stillNeeded()) {
                    post(cb, null);
                    return;
                }
                Bitmap bmp = null;
                try {
                    int height = Math.max(1, Math.round(width * h[index] / w[index]));
                    bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    PdfRenderer.Page p = renderer.openPage(index);
                    Matrix m = new Matrix();
                    m.setScale(width / w[index], height / h[index]);
                    p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    p.close();
                } catch (Throwable t) {
                    if (bmp != null) bmp.recycle();
                    bmp = null;
                }
                post(cb, bmp);
            }
        });
    }

    public interface Check {
        boolean stillNeeded();
    }

    /** Uma região de página para a renderização de alta resolução. */
    public static class Region {
        public int page;
        public float left, top, width, height; // posição da página na tela (px)
    }

    /** Renderiza o recorte visível da tela em alta resolução (zoom). */
    public void renderViewport(final int vw, final int vh, final Region[] regions, final Check check, final BitmapCallback cb) {
        ex.execute(new Runnable() {
            @Override
            public void run() {
                if (closed) return;
                if (!check.stillNeeded()) {
                    post(cb, null);
                    return;
                }
                Bitmap bmp = null;
                try {
                    bmp = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    Paint white = new Paint();
                    white.setColor(Color.WHITE);
                    for (Region r : regions) {
                        if (!check.stillNeeded()) {
                            bmp.recycle();
                            post(cb, null);
                            return;
                        }
                        Rect clip = new Rect(Math.max(0, (int) Math.floor(r.left)), Math.max(0, (int) Math.floor(r.top)),
                                Math.min(vw, (int) Math.ceil(r.left + r.width)), Math.min(vh, (int) Math.ceil(r.top + r.height)));
                        if (clip.width() <= 0 || clip.height() <= 0) continue;
                        c.drawRect(clip, white);
                        PdfRenderer.Page p = renderer.openPage(r.page);
                        Matrix m = new Matrix();
                        m.setScale(r.width / w[r.page], r.height / h[r.page]);
                        m.postTranslate(r.left, r.top);
                        p.render(bmp, clip, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        p.close();
                    }
                } catch (Throwable t) {
                    if (bmp != null) bmp.recycle();
                    bmp = null;
                }
                post(cb, bmp);
            }
        });
    }

    private void post(final BitmapCallback cb, final Bitmap bmp) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (closed) {
                    if (bmp != null) bmp.recycle();
                    return;
                }
                cb.onBitmap(bmp);
            }
        });
    }

    public void close() {
        closed = true;
        ex.execute(new Runnable() {
            @Override
            public void run() {
                closeNow();
            }
        });
        ex.shutdown();
    }

    private void closeNow() {
        try {
            if (renderer != null) renderer.close();
        } catch (Exception ignored) {
        }
        try {
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
        renderer = null;
        pfd = null;
    }
}
