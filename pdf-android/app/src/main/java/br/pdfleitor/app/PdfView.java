package br.pdfleitor.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import java.util.HashSet;
import java.util.Set;

/**
 * Visualizador de PDF com rolagem vertical contínua, zoom por pinça e toque
 * duplo. Usa bitmaps de baixa resolução por página e, quando há zoom, uma
 * renderização nítida apenas da área visível.
 */
public class PdfView extends View {

    public interface Listener {
        void onPageChanged(int page, int count);

        void onTap();
    }

    private PdfDoc doc;
    private Listener listener;
    private float[] top, dispH;
    private float dispW, totalH, pad, gap, topInset;
    private float zoom = 1f, sx, sy;
    private int currentPage = -1;
    private int pendingPage = -1;
    private boolean night;

    private final LruCache<Integer, Bitmap> cache;
    private final Set<Integer> pending = new HashSet<Integer>();
    private int renderWidth;
    private volatile int visFirst, visLast;

    private Bitmap hq;
    private float hqSx, hqSy, hqZoom;
    private int hqGen;
    private volatile int stateGen;

    private final OverScroller scroller;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;
    private ValueAnimator zoomAnim;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint nightPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pagePaint = new Paint();
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int bgColor = 0xFFE8EAED;

    private final Runnable hqTask = new Runnable() {
        @Override
        public void run() {
            requestHq();
        }
    };

    public PdfView(Context c) {
        super(c);
        int mem = (int) Math.min(Integer.MAX_VALUE, Runtime.getRuntime().maxMemory() / 4);
        cache = new LruCache<Integer, Bitmap>(mem) {
            @Override
            protected int sizeOf(Integer key, Bitmap value) {
                return value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, Integer key, Bitmap oldValue, Bitmap newValue) {
                // não reciclamos manualmente: o bitmap pode estar sendo desenhado
            }
        };
        scroller = new OverScroller(c);
        pad = Ui.dp(c, 6);
        gap = Ui.dp(c, 8);
        ColorMatrix inv = new ColorMatrix(new float[]{
                -1, 0, 0, 0, 255,
                0, -1, 0, 0, 255,
                0, 0, -1, 0, 255,
                0, 0, 0, 1, 0});
        nightPaint.setColorFilter(new ColorMatrixColorFilter(inv));
        shadowPaint.setColor(0x1A000000);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                if (zoomAnim != null) zoomAnim.cancel();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaler.isInProgress() || e2.getPointerCount() > 1) return true;
                scrollToPos(sx + dx, sy + dy);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (scaler.isInProgress()) return true;
                scroller.fling((int) sx, (int) sy, (int) -vx, (int) -vy, 0, (int) maxX(), 0, (int) maxY());
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (listener != null) listener.onTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                animateZoom(zoom < 1.6f ? 2.5f : 1f, e.getX(), e.getY());
                return true;
            }
        });
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAround(zoom * d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setNight(boolean n) {
        night = n;
        bgColor = n ? 0xFF050505 : Ui.VIEWER_BG;
        invalidate();
    }

    public boolean isNight() {
        return night;
    }

    public void setTopInset(float px) {
        topInset = px;
        relayout();
    }

    public void setDocument(PdfDoc d, int startPage) {
        doc = d;
        pendingPage = startPage;
        relayout();
    }

    public int getCurrentPage() {
        return Math.max(0, currentPage);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int keep = currentPage;
        if (keep >= 0 && pendingPage < 0) pendingPage = keep;
        relayout();
    }

    private void relayout() {
        if (doc == null || getWidth() == 0) return;
        int n = doc.count;
        top = new float[n];
        dispH = new float[n];
        dispW = getWidth() - 2 * pad;
        float y = topInset + gap;
        for (int i = 0; i < n; i++) {
            top[i] = y;
            dispH[i] = dispW * doc.h[i] / doc.w[i];
            y += dispH[i] + gap;
        }
        totalH = y + Ui.dp(getContext(), 64);
        int newWidth = (int) dispW;
        if (newWidth != renderWidth) {
            renderWidth = newWidth;
            cache.evictAll();
        }
        dropHq();
        if (pendingPage >= 0) {
            int p = pendingPage;
            pendingPage = -1;
            goToPage(p);
        } else {
            scrollToPos(sx, sy);
        }
        invalidate();
    }

    private float maxX() {
        return Math.max(0, getWidth() * zoom - getWidth());
    }

    private float maxY() {
        return Math.max(0, totalH * zoom - getHeight());
    }

    private void scrollToPos(float x, float y) {
        sx = Math.max(0, Math.min(maxX(), x));
        sy = Math.max(0, Math.min(maxY(), y));
        stateGen++;
        updatePage();
        invalidate();
        scheduleHq();
    }

    public void goToPage(int page) {
        if (doc == null || top == null) {
            pendingPage = page;
            return;
        }
        page = Math.max(0, Math.min(doc.count - 1, page));
        scroller.forceFinished(true);
        scrollToPos(sx, (top[page] - gap) * zoom);
    }

    private void zoomAround(float nz, float fx, float fy) {
        nz = Math.max(1f, Math.min(6f, nz));
        float cx = (sx + fx) / zoom, cy = (sy + fy) / zoom;
        zoom = nz;
        scrollToPos(cx * zoom - fx, cy * zoom - fy);
    }

    private void animateZoom(final float target, final float fx, final float fy) {
        if (zoomAnim != null) zoomAnim.cancel();
        zoomAnim = ValueAnimator.ofFloat(zoom, target);
        zoomAnim.setDuration(220);
        zoomAnim.setInterpolator(new DecelerateInterpolator());
        zoomAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                zoomAround((Float) a.getAnimatedValue(), fx, fy);
            }
        });
        zoomAnim.start();
    }

    private void updatePage() {
        if (doc == null || top == null || doc.count == 0) return;
        float center = (sy + getHeight() / 2f) / zoom;
        int p = indexAt(center);
        if (p != currentPage) {
            currentPage = p;
            if (listener != null) listener.onPageChanged(p, doc.count);
        }
    }

    private int indexAt(float y) {
        int lo = 0, hi = top.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (top[mid] <= y) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            scheduleHq();
        }
        return true;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollToPos(scroller.getCurrX(), scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------------ desenho

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(bgColor);
        if (doc == null || top == null) return;
        float vTop = sy / zoom, vBottom = (sy + getHeight()) / zoom;
        int first = indexAt(vTop);
        int last = first;
        while (last + 1 < doc.count && top[last + 1] < vBottom) last++;
        visFirst = first;
        visLast = last;
        canvas.save();
        canvas.translate(-sx, -sy);
        canvas.scale(zoom, zoom);
        pagePaint.setColor(night ? 0xFF1E1E1E : 0xFFFFFFFF);
        RectF r = new RectF();
        for (int i = first; i <= last; i++) {
            r.set(pad, top[i], pad + dispW, top[i] + dispH[i]);
            if (!night) canvas.drawRect(r.left - 0.5f, r.top + 1f, r.right + 0.5f, r.bottom + 2f, shadowPaint);
            Bitmap b = cache.get(i);
            if (b != null && !b.isRecycled()) {
                canvas.drawBitmap(b, null, r, night ? nightPaint : bmpPaint);
            } else {
                canvas.drawRect(r, pagePaint);
                request(i);
            }
        }
        canvas.restore();
        // pré-carrega vizinhas
        if (first > 0) request(first - 1);
        if (last + 1 < doc.count) request(last + 1);
        if (hq != null && !hq.isRecycled() && hqZoom == zoom && hqSx == sx && hqSy == sy) {
            canvas.drawBitmap(hq, 0, 0, night ? nightPaint : bmpPaint);
        }
    }

    private void request(final int i) {
        if (doc == null || renderWidth <= 0 || pending.contains(i) || cache.get(i) != null) return;
        pending.add(i);
        final int width = renderWidth;
        doc.renderPage(i, width, new PdfDoc.BitmapCallback() {
            @Override
            public void onBitmap(Bitmap bmp) {
                pending.remove(i);
                if (bmp == null) return;
                if (width != renderWidth) {
                    bmp.recycle();
                    return;
                }
                cache.put(i, bmp);
                invalidate();
            }
        }, new PdfDoc.Check() {
            @Override
            public boolean stillNeeded() {
                return i >= visFirst - 2 && i <= visLast + 2;
            }
        });
    }

    // ------------------------------------------------------------------ alta resolução

    private void scheduleHq() {
        removeCallbacks(hqTask);
        if (zoom > 1.05f) postDelayed(hqTask, 160);
    }

    private void dropHq() {
        // sem recycle(): o bitmap pode estar em um frame ainda não desenhado
        hq = null;
    }

    private void requestHq() {
        if (doc == null || top == null || zoom <= 1.05f || scroller.isFinished() == false) return;
        if (hq != null && hqZoom == zoom && hqSx == sx && hqSy == sy) return;
        final float z = zoom, x = sx, y = sy;
        final int gen = stateGen;
        final int vw = getWidth(), vh = getHeight();
        float vTop = sy / zoom, vBottom = (sy + vh) / zoom;
        int first = indexAt(vTop);
        int last = first;
        while (last + 1 < doc.count && top[last + 1] < vBottom) last++;
        java.util.List<PdfDoc.Region> regions = new java.util.ArrayList<PdfDoc.Region>();
        for (int i = first; i <= last; i++) {
            PdfDoc.Region rg = new PdfDoc.Region();
            rg.page = i;
            rg.left = pad * z - x;
            rg.top = top[i] * z - y;
            rg.width = dispW * z;
            rg.height = dispH[i] * z;
            regions.add(rg);
        }
        final int myGen = ++hqGen;
        doc.renderViewport(vw, vh, regions.toArray(new PdfDoc.Region[0]), new PdfDoc.Check() {
            @Override
            public boolean stillNeeded() {
                return stateGen == gen;
            }
        }, new PdfDoc.BitmapCallback() {
            @Override
            public void onBitmap(Bitmap bmp) {
                if (bmp == null) return;
                if (myGen != hqGen || stateGen != gen) {
                    bmp.recycle();
                    return;
                }
                hq = bmp;
                hqZoom = z;
                hqSx = x;
                hqSy = y;
                invalidate();
            }
        });
    }

    public void release() {
        removeCallbacks(hqTask);
        cache.evictAll();
        hq = null;
    }
}
