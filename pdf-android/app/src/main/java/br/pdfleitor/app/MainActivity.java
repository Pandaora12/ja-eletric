package br.pdfleitor.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/** Tela principal com a barra inferior: PDFs, Converter e Configurações. */
public class MainActivity extends Activity {

    public static final String ACTION_CONVERT = "br.pdfleitor.app.CONVERT";
    static final int REQ_OPEN_PDF = 11, REQ_PICK_CONVERT = 12, REQ_SAVE_AS = 13;

    private FrameLayout content;
    private final View[] pages = new View[3];
    private int current = -1;
    PdfListPage pdfPage;
    ConvertPage convertPage;
    SettingsPage settingsPage;

    private Widgets.Dock dock;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applyTheme(this);
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        dock = new Widgets.Dock(this, new String[]{"PDFs", "Converter", "Configurações"},
                new int[]{R.drawable.ic_doc, R.drawable.ic_convert, R.drawable.ic_settings});
        dock.setListener(new Widgets.Dock.Listener() {
            @Override
            public void onSelect(int index) {
                select(index);
            }
        });
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int m = Ui.dp(this, 16);
        dlp.setMargins(m, 0, m, Ui.dp(this, 14));
        root.addView(dock, dlp);
        Snack.bottomOffsetDp = 96;
        setContentView(root);

        pdfPage = new PdfListPage(this);
        convertPage = new ConvertPage(this);
        settingsPage = new SettingsPage(this);
        pages[0] = pdfPage;
        pages[1] = convertPage;
        pages[2] = settingsPage;
        for (View v : pages) {
            v.setVisibility(View.GONE);
            content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        int tab = state != null ? state.getInt("tab", 0) : 0;
        select(tab);
        if (state == null) {
            dock.setTranslationY(Ui.dp(this, 120));
            dock.animate().translationY(0).setStartDelay(250).setDuration(700).setInterpolator(Ui.EASE).start();
            handleIntent(getIntent());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", current);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** Arquivos enviados por outros apps vão direto para o conversor. */
    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<Uri>();
        if (Intent.ACTION_VIEW.equals(action) || ACTION_CONVERT.equals(action)) {
            if (intent.getData() != null) uris.add(intent.getData());
        } else if (Intent.ACTION_SEND.equals(action)) {
            Parcelable p = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) uris.add((Uri) p);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Parcelable> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) for (Parcelable p : list) if (p instanceof Uri) uris.add((Uri) p);
        }
        if (uris.isEmpty()) return;
        // um PDF enviado sozinho via "Compartilhar" abre no leitor
        if (Intent.ACTION_SEND.equals(action) && uris.size() == 1) {
            String type = intent.getType();
            String name = Files.displayName(this, uris.get(0));
            if ("application/pdf".equals(type) || name.toLowerCase().endsWith(".pdf")) {
                Intent v = new Intent(this, ViewerActivity.class);
                v.setData(uris.get(0));
                v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(v);
                return;
            }
        }
        select(1);
        convertPage.setExternalSources(uris, ACTION_CONVERT.equals(action));
    }

    void select(int idx) {
        if (idx == current) return;
        int prev = current;
        current = idx;
        dock.select(idx, prev >= 0);
        for (int i = 0; i < 3; i++) {
            final View p = pages[i];
            if (p == null) continue;
            if (i == idx) {
                p.setVisibility(View.VISIBLE);
                p.setAlpha(0f);
                p.setTranslationX(prev < 0 ? 0 : (idx > prev ? 1 : -1) * Ui.dp(this, 28));
                p.animate().alpha(1f).translationX(0).setDuration(420).setInterpolator(Ui.EASE).start();
            } else if (i == prev) {
                p.animate().alpha(0f).translationX((idx > prev ? -1 : 1) * Ui.dp(this, 20)).setDuration(160)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (pages[current] != p) p.setVisibility(View.GONE);
                            }
                        }).start();
            } else {
                p.setVisibility(View.GONE);
            }
        }
        if (idx == 0 && pdfPage != null) pdfPage.refresh(true);
        if (idx == 1 && convertPage != null) convertPage.onShown();
        if (idx == 2 && settingsPage != null) settingsPage.refresh(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Snack.bottomOffsetDp = 96;
        if (current == 0 && pdfPage != null) pdfPage.refresh(false);
    }

    @Override
    public void onBackPressed() {
        if (current != 0) select(0);
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_OPEN_PDF) pdfPage.onPicked(data);
        else if (req == REQ_PICK_CONVERT) convertPage.onPicked(data);
        else if (req == REQ_SAVE_AS) convertPage.onSaveAs(data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (convertPage != null) convertPage.onDestroy();
    }
}
