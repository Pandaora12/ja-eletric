package br.pdfleitor.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
    private ProgressBar loading;
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

    private void buildUi() {
        root = new FrameLayout(this);
        pdfView = new PdfView(this);
        pdfView.setNight(Prefs.nightReading(this));
        root.addView(pdfView, new FrameLayout.LayoutParams(-1, -1));

        // barra superior
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Ui.SURFACE);
        topBar.setElevation(Ui.dp(this, 3));
        topBar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, Ui.TEXT, "Voltar");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 6), 0, 0, 0);
        titleView = Ui.text(this, "", 16, Ui.TEXT, true);
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        subtitleView = Ui.text(this, "Abrindo…", 12.5f, Ui.TEXT2, false);
        titles.addView(titleView);
        titles.addView(subtitleView);
        topBar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView search = Ui.iconButton(this, R.drawable.ic_search, Ui.TEXT, "Buscar");
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchDialog();
            }
        });
        topBar.addView(search);
        final ImageView nightBtn = Ui.iconButton(this, R.drawable.ic_night, Ui.TEXT, "Modo noturno");
        nightBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean n = !pdfView.isNight();
                pdfView.setNight(n);
                Prefs.put(ViewerActivity.this, "night_reading", n);
                Ui.toast(ViewerActivity.this, n ? "Modo noturno ativado" : "Modo noturno desativado");
            }
        });
        topBar.addView(nightBtn);
        final ImageView more = Ui.iconButton(this, R.drawable.ic_more, Ui.TEXT, "Mais opções");
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moreMenu(more);
            }
        });
        topBar.addView(more);
        int barH = Ui.dp(this, 60);
        root.addView(topBar, new FrameLayout.LayoutParams(-1, barH, Gravity.TOP));
        pdfView.setTopInset(barH);

        // barra inferior com indicador de página
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(this, 28)));
        bottomBar.setElevation(Ui.dp(this, 4));
        bottomBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        pageChip = Ui.text(this, "", 14, Ui.TEXT, true);
        pageChip.setMinWidth(Ui.dp(this, 64));
        pageChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goToDialog();
            }
        });
        bottomBar.addView(pageChip);
        seek = new SeekBar(this);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && doc != null) {
                    pageChip.setText((p + 1) + " / " + doc.count);
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
        bottomBar.addView(seek, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int m = Ui.dp(this, 14);
        blp.setMargins(m, m, m, m);
        root.addView(bottomBar, blp);

        loading = new ProgressBar(this);
        root.addView(loading, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));

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
    }

    private void updatePage(int page, int count) {
        subtitleView.setText("Página " + (page + 1) + " de " + count);
        pageChip.setText((page + 1) + " / " + count);
        if (!seeking) seek.setProgress(page);
        if (entry != null && entry.lastPage != page) {
            entry.lastPage = page;
        }
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        float dyTop = barsVisible ? 0 : -topBar.getHeight() - Ui.dp(this, 8);
        float dyBottom = barsVisible ? 0 : bottomBar.getHeight() + Ui.dp(this, 32);
        topBar.animate().translationY(dyTop).setDuration(180).start();
        bottomBar.animate().translationY(dyBottom).setDuration(180).start();
    }

    private void showError(String msg) {
        loading.setVisibility(View.GONE);
        subtitleView.setText("Erro");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView t = Ui.text(this, msg, 16, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        TextView b = Ui.button(this, "Voltar", true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        box.addView(b, Ui.lpMargins(-2, -2, 0, Ui.dp(this, 24), 0, 0));
        box.setBackgroundColor(Ui.BG);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1));
        topBar.bringToFront();
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
        String msg = "Nome: " + name + "\nPáginas: " + doc.count + "\nTamanho: " + (size > 0 ? Ui.size(size) : "desconhecido")
                + "\nPágina 1: " + Math.round(doc.w[0] / 72f * 25.4f) + " × " + Math.round(doc.h[0] / 72f * 25.4f) + " mm";
        new AlertDialog.Builder(this).setTitle("Informações").setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void goToDialog() {
        if (doc == null) return;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("1 – " + doc.count);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Ir para a página").setView(wrap)
                .setPositiveButton("Ir", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            int n = Integer.parseInt(input.getText().toString().trim());
                            pdfView.goToPage(n - 1);
                        } catch (Exception ignored) {
                        }
                    }
                }).setNegativeButton("Cancelar", null).create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    // ------------------------------------------------------------------ busca

    private void searchDialog() {
        if (doc == null) return;
        final EditText input = new EditText(this);
        input.setHint("Palavra ou frase");
        input.setSingleLine();
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(input);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Buscar no documento").setView(wrap)
                .setPositiveButton("Buscar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String q = input.getText().toString().trim();
                        if (q.length() > 0) runSearch(q);
                    }
                }).setNegativeButton("Cancelar", null).create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
    }

    static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

    private void runSearch(final String query) {
        final AlertDialog progress = new AlertDialog.Builder(this).setMessage("Buscando \"" + query + "\"…").setCancelable(false).show();
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
                            new AlertDialog.Builder(ViewerActivity.this).setTitle("Nada encontrado")
                                    .setMessage("Nenhum resultado para \"" + query + "\".\n\nSe o PDF for digitalizado (imagem), não há texto para buscar.")
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        new AlertDialog.Builder(ViewerActivity.this)
                                .setTitle(hits.size() + (hits.size() == 1 ? " resultado" : " resultados"))
                                .setItems(snippets.toArray(new String[0]), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        pdfView.goToPage(hits.get(which)[0]);
                                    }
                                }).setNegativeButton("Fechar", null).show();
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
