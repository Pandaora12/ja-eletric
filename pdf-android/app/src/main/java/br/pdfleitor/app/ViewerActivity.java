package br.pdfleitor.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import br.pdfleitor.core.PdfText;

/** Tela de leitura de PDF. */
public class ViewerActivity extends Activity {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_INTERNAL = "internal";

    private PdfView pdfView;
    private PdfDoc doc;
    private LinearLayout topBar, bottomBar;
    private TextView titleView, subtitleView, pageChip;
    private SeekBar seek;
    private View loading;
    private LinearLayout loadingBox;
    private FrameLayout root;
    private boolean barsVisible = true;
    private String storedUri, name;
    private Uri openUri;
    private History.Entry entry;
    private PdfText textIndex;
    private boolean seeking;

    public static void open(Activity a, Uri uri, String name) {
        Intent i = new Intent(a, ViewerActivity.class);
        i.setData(uri);
        i.putExtra(EXTRA_NAME, name);
        i.putExtra(EXTRA_INTERNAL, true);
        a.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle state) {
        Ui.applyTheme(this);
        super.onCreate(state);
        buildUi();
        if (Prefs.keepScreenOn(this)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final Uri uri = getIntent().getData();
        if (uri == null) {
            showError("Nenhum arquivo informado.");
            return;
        }
        name = getIntent().getStringExtra(EXTRA_NAME);
        boolean internal = getIntent().getBooleanExtra(EXTRA_INTERNAL, false);
        if (internal) {
            if (name == null) name = Files.displayName(this, uri);
            start(uri, uri.toString());
        } else {
            // aberto por outro app: copia para a biblioteca para poder reabrir depois
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final String n = Files.displayName(ViewerActivity.this, uri);
                        String fname = n.toLowerCase().endsWith(".pdf") ? n : n + ".pdf";
                        final File f = Files.importToLibrary(ViewerActivity.this, uri, fname);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                name = f.getName();
                                Uri fu = Uri.fromFile(f);
                                start(fu, fu.toString());
                            }
                        });
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // tenta abrir diretamente
                                name = Files.displayName(ViewerActivity.this, uri);
                                start(uri, null);
                            }
                        });
                    }
                }
            }).start();
        }
    }

    private void start(Uri uri, String stored) {
        openUri = uri;
        storedUri = stored;
        titleView.setText(name);
        PdfDoc.open(this, uri, new PdfDoc.OpenCallback() {
            @Override
            public void onOpened(PdfDoc d) {
                if (isFinishing()) {
                    d.close();
                    return;
                }
                doc = d;
                loading.setVisibility(View.GONE);
                int startPage = 0;
                if (storedUri != null) {
                    long size = "file".equals(openUri.getScheme()) ? new File(openUri.getPath()).length() : Files.size(ViewerActivity.this, openUri);
                    entry = History.touch(ViewerActivity.this, storedUri, name, size);
                    entry.pages = d.count;
                    History.update(ViewerActivity.this, entry);
                    if (Prefs.resumeLastPage(ViewerActivity.this) && entry.lastPage < d.count) startPage = entry.lastPage;
                }
                seek.setMax(Math.max(0, d.count - 1));
                pdfView.setDocument(d, startPage);
                updatePage(startPage, d.count);
                if (startPage > 0) Ui.toast(ViewerActivity.this, "Continuando da página " + (startPage + 1));
            }

            @Override
            public void onError(String message) {
                showError(message);
            }
        });
    }

    private LinearLayout capsule() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBackground(Ui.stroke(Ui.CARD, Ui.LINE, Math.max(1, Ui.dp(this, 0.8f)), Ui.dp(this, 30)));
        l.setElevation(Ui.dp(this, 10));
        return l;
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Ui.VIEWER_BG);
        getWindow().setNavigationBarColor(Ui.VIEWER_BG);
        Snack.bottomOffsetDp = 110;
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.VIEWER_BG);
        pdfView = new PdfView(this);
        pdfView.setNight(Prefs.nightReading(this));
        root.addView(pdfView, new FrameLayout.LayoutParams(-1, -1));

        // cápsula superior
        topBar = capsule();
        topBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        ImageView back = Ui.circleButton(this, R.drawable.ic_back, Ui.CARD2, Ui.INK, 44, "Voltar");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
        titleView = Ui.text(this, "", 15.5f, Ui.INK, true);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtitleView = Ui.label(this, "Abrindo…", Ui.INK2);
        subtitleView.setPadding(0, Ui.dp(this, 5), 0, 0);
        titles.addView(titleView);
        titles.addView(subtitleView);
        topBar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView search = Ui.iconButton(this, R.drawable.ic_search, Ui.INK, "Buscar");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchDialog();
            }
        });
        topBar.addView(search);
        final ImageView nightBtn = Ui.iconButton(this, R.drawable.ic_night, Ui.INK, "Modo noturno");
        nightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean n = !pdfView.isNight();
                pdfView.setNight(n);
                Prefs.put(ViewerActivity.this, "night_reading", n);
                nightBtn.animate().rotationBy(360).setDuration(600).setInterpolator(Ui.EASE).start();
                Ui.toast(ViewerActivity.this, n ? "Modo noturno ligado" : "Modo noturno desligado");
            }
        });
        topBar.addView(nightBtn);
        final ImageView more = Ui.iconButton(this, R.drawable.ic_more, Ui.INK, "Mais opções");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moreMenu(more);
            }
        });
        topBar.addView(more);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        int m = Ui.dp(this, 12);
        tlp.setMargins(m, m, m, 0);
        root.addView(topBar, tlp);
        pdfView.setTopInset(Ui.dp(this, 80));

        // cápsula inferior: página + régua
        bottomBar = capsule();
        bottomBar.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        pageChip = Ui.display(this, "", 26);
        pageChip.setMinWidth(Ui.dp(this, 60));
        pageChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToDialog();
            }
        });
        bottomBar.addView(pageChip);
        seek = new SeekBar(this);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.INK));
        seek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.INK3));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && doc != null) {
                    pageChip.setText(Ui.rich((p + 1) + "*/" + doc.count + "*"));
                    pdfView.goToPage(p);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
            }
        });
        bottomBar.addView(seek, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        blp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        root.addView(bottomBar, blp);

        loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        TextView lt = Ui.display(this, "Abrindo…", 30);
        loadingBox.addView(lt);
        loadingBox.addView(new Widgets.LineLoader(this), Ui.lpMargins(Ui.dp(this, 140), Ui.dp(this, 3), 0, Ui.dp(this, 18), 0, 0));
        root.addView(loadingBox, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        loading = loadingBox;

        pdfView.setListener(new PdfView.Listener() {
            @Override
            public void onPageChanged(int page, int count) {
                updatePage(page, count);
            }

            @Override
            public void onTap() {
                toggleBars();
            }
        });
        setContentView(root);
        topBar.setTranslationY(-Ui.dp(this, 100));
        topBar.animate().translationY(0).setDuration(600).setStartDelay(100).setInterpolator(Ui.EASE).start();
        bottomBar.setTranslationY(Ui.dp(this, 120));
        bottomBar.animate().translationY(0).setDuration(600).setStartDelay(180).setInterpolator(Ui.EASE).start();
    }

    private void updatePage(int page, int count) {
        subtitleView.setText(("Página " + (page + 1) + " de " + count).toUpperCase());
        pageChip.setText(Ui.rich((page + 1) + "*/" + count + "*"));
        if (!seeking) seek.setProgress(page);
        if (entry != null && entry.lastPage != page) {
            entry.lastPage = page;
        }
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        float dyTop = barsVisible ? 0 : -topBar.getHeight() - Ui.dp(this, 24);
        float dyBottom = barsVisible ? 0 : bottomBar.getHeight() + Ui.dp(this, 40);
        topBar.animate().translationY(dyTop).alpha(barsVisible ? 1f : 0f).setDuration(320).setInterpolator(Ui.EASE).start();
        bottomBar.animate().translationY(dyBottom).alpha(barsVisible ? 1f : 0f).setDuration(320).setInterpolator(Ui.EASE).start();
    }

    private void showError(String msg) {
        loading.setVisibility(View.GONE);
        subtitleView.setText("ERRO");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(this, 28), 0, Ui.dp(this, 28), 0);
        box.addView(Ui.label(this, "Não foi possível abrir", Ui.ACCENT));
        TextView h = Ui.display(this, "Algo deu\n*errado.*", 48);
        h.setPadding(0, Ui.dp(this, 14), 0, 0);
        box.addView(h);
        TextView t = Ui.text(this, msg, 16, Ui.INK2, false);
        t.setPadding(0, Ui.dp(this, 16), 0, 0);
        box.addView(t);
        TextView b = Ui.button(this, "Voltar", true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        box.addView(b, Ui.lpMargins(-2, -2, 0, Ui.dp(this, 28), 0, 0));
        box.setBackgroundColor(Ui.BG);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
        bottomBar.setVisibility(View.GONE);
        topBar.bringToFront();
        Ui.stagger(box, 80);
    }

    private void moreMenu(View anchor) {
        Ui.menu(this, name, new String[]{"Ir para página…", "Compartilhar", "Converter este PDF", "Abrir em outro app", "Informações"}, new Ui.MenuCallback() {
            @Override
            public void onItem(int id) {
            switch (id) {
                case 1:
                    goToDialog();
                    break;
                case 2:
                    if (openUri != null) Files.share(ViewerActivity.this, shareableUri(), "application/pdf", name);
                    break;
                case 3: {
                    Intent i = new Intent(ViewerActivity.this, MainActivity.class);
                    i.setAction(MainActivity.ACTION_CONVERT);
                    i.setData(openUri);
                    i.putExtra(EXTRA_NAME, name);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(i);
                    break;
                }
                case 4: {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(shareableUri(), "application/pdf");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(Intent.createChooser(i, "Abrir com"));
                    } catch (Exception e) {
                        Ui.toast(ViewerActivity.this, "Nenhum outro app disponível.");
                    }
                    break;
                }
                case 5:
                    info();
                    break;
                default:
                    break;
            }
            }
        });
    }

    private Uri shareableUri() {
        if (openUri != null && "file".equals(openUri.getScheme())) return Files.shareUri(this, new File(openUri.getPath()));
        return openUri;
    }

    private void info() {
        if (doc == null) return;
        long size = "file".equals(openUri.getScheme()) ? new File(openUri.getPath()).length() : Files.size(this, openUri);
        Sheet.facts(this, "Sobre este\n*documento*", new String[][]{
                {"Nome", name},
                {"Páginas", String.valueOf(doc.count)},
                {"Tamanho", size > 0 ? Ui.size(size) : "—"},
                {"Formato", Math.round(doc.w[0] / 72f * 25.4f) + " × " + Math.round(doc.h[0] / 72f * 25.4f) + " mm"},
                {"Lendo", "Página " + (pdfView.getCurrentPage() + 1)}});
    }

    private void goToDialog() {
        if (doc == null) return;
        Sheet.input(this, "Ir para a\n*página*", "1 – " + doc.count, true, "Ir", new Sheet.TextCallback() {
            @Override
            public void onText(String s) {
                try {
                    pdfView.goToPage(Integer.parseInt(s) - 1);
                } catch (Exception ignored) {
                }
            }
        });
    }

    // ------------------------------------------------------------------ busca

    private void searchDialog() {
        if (doc == null) return;
        Sheet.input(this, "Buscar no\n*documento*", "Palavra ou frase", false, "Buscar", new Sheet.TextCallback() {
            @Override
            public void onText(String q) {
                runSearch(q);
            }
        });
    }

    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private void runSearch(final String query) {
        final Sheet progress = Sheet.loading(this, "Procurando \u201c" + query + "\u201d…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<int[]> hits = new ArrayList<int[]>();
                final List<String> snippets = new ArrayList<String>();
                String error = null;
                try {
                    if (textIndex == null) textIndex = new PdfText(Files.read(ViewerActivity.this, openUri));
                    if (textIndex.encrypted) error = "Este PDF é protegido e não permite busca de texto.";
                    String q = fold(query);
                    int total = 0;
                    for (int i = 0; i < textIndex.pageCount() && snippets.size() < 300; i++) {
                        String text = textIndex.pageText(i).replace('\n', ' ');
                        String f = fold(text);
                        int idx = f.indexOf(q);
                        while (idx >= 0 && snippets.size() < 300) {
                            total++;
                            int s = Math.max(0, idx - 40), e = Math.min(text.length(), idx + q.length() + 50);
                            String snip = (s > 0 ? "…" : "") + text.substring(s, Math.min(e, text.length())).trim() + (e < text.length() ? "…" : "");
                            hits.add(new int[]{i});
                            snippets.add("Pág. " + (i + 1) + "  ·  " + snip);
                            idx = f.indexOf(q, idx + q.length());
                        }
                    }
                } catch (Throwable t) {
                    error = "Não foi possível buscar: " + t.getMessage();
                }
                final String err = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.dismiss();
                        if (isFinishing()) return;
                        if (err != null) {
                            Ui.toast(ViewerActivity.this, err);
                            return;
                        }
                        if (hits.isEmpty()) {
                            Sheet.info(ViewerActivity.this, "Nada\n*encontrado.*",
                                    "Nenhum resultado para \u201c" + query + "\u201d. Se o PDF for digitalizado (imagem), não há texto para buscar.");
                            return;
                        }
                        String[] heads = new String[snippets.size()];
                        String[] texts = new String[snippets.size()];
                        for (int k = 0; k < snippets.size(); k++) {
                            String sn = snippets.get(k);
                            int sep = sn.indexOf("  ·  ");
                            heads[k] = sn.substring(0, sep);
                            texts[k] = sn.substring(sep + 5);
                        }
                        Sheet.list(ViewerActivity.this, hits.size() + (hits.size() == 1 ? " resultado" : " resultados"),
                                "para \u201c" + query + "\u201d", heads, texts, new Ui.MenuCallback() {
                                    @Override
                                    public void onItem(int id) {
                                        pdfView.goToPage(hits.get(id - 1)[0]);
                                    }
                                });
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (entry != null) {
            entry.lastPage = pdfView.getCurrentPage();
            History.update(this, entry);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pdfView.release();
        if (doc != null) doc.close();
    }
}
