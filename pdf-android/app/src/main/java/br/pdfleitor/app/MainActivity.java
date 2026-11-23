package br.pdfleitor.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Tela principal com a barra inferior: PDFs, Converter e Configurações. */
public class MainActivity extends Activity {

    public static final String ACTION_CONVERT = "br.pdfleitor.app.CONVERT";
    static final int REQ_OPEN_PDF = 11, REQ_PICK_CONVERT = 12, REQ_SAVE_AS = 13;

    private FrameLayout content;
    private final View[] pages = new View[3];
    private final LinearLayout[] tabs = new LinearLayout[3];
    private int current = -1;
    PdfListPage pdfPage;
    ConvertPage convertPage;
    SettingsPage settingsPage;

    @Override
    protected void onCreate(Bundle state) {
        Ui.applyTheme(this);
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Ui.divider(this));
        root.addView(buildNav());
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
        if (state == null) handleIntent(getIntent());
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

    private View buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Ui.SURFACE);
        nav.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        String[] labels = {"PDFs", "Converter", "Configurações"};
        int[] icons = {R.drawable.ic_doc, R.drawable.ic_convert, R.drawable.ic_settings};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setClickable(true);
            t.setBackground(Ui.rippleBorderless());
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(Ui.icon(this, icons[i], Ui.TEXT2));
            iv.setPadding(Ui.dp(this, 18), Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4));
            t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 32)));
            TextView tv = Ui.text(this, labels[i], 12.5f, Ui.TEXT2, true);
            tv.setPadding(0, Ui.dp(this, 4), 0, 0);
            t.addView(tv);
            t.setContentDescription(labels[i]);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            tabs[i] = t;
            nav.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return nav;
    }

    void select(int idx) {
        if (idx == current) return;
        current = idx;
        int[] icons = {R.drawable.ic_doc, R.drawable.ic_convert, R.drawable.ic_settings};
        for (int i = 0; i < 3; i++) {
            boolean sel = i == idx;
            ImageView iv = (ImageView) tabs[i].getChildAt(0);
            TextView tv = (TextView) tabs[i].getChildAt(1);
            iv.setImageDrawable(Ui.icon(this, icons[i], sel ? Ui.PRIMARY : Ui.TEXT2));
            iv.setBackground(sel ? Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(this, 16)) : null);
            tv.setTextColor(sel ? Ui.TEXT : Ui.TEXT2);
            if (pages[i] != null) pages[i].setVisibility(sel ? View.VISIBLE : View.GONE);
        }
        if (idx == 0 && pdfPage != null) pdfPage.refresh();
        if (idx == 1 && convertPage != null) convertPage.refreshOutputs();
        if (idx == 2 && settingsPage != null) settingsPage.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current == 0 && pdfPage != null) pdfPage.refresh();
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
