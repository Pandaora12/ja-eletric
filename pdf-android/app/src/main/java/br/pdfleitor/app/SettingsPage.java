package br.pdfleitor.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;

/** Aba "Configurações". */
public class SettingsPage extends ScrollView {

    private final MainActivity act;
    private final LinearLayout col;

    public SettingsPage(MainActivity a) {
        super(a);
        act = a;
        setBackgroundColor(Ui.BG);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 20), side, Ui.dp(a, 32));
        addView(col, new LayoutParams(-1, -2));
        build();
    }

    void refresh() {
        col.removeAllViews();
        build();
    }

    private void build() {
        TextView title = Ui.title(act, "Configurações");
        title.setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 4));
        col.addView(title);

        // aparência
        col.addView(Ui.section(act, "Aparência"));
        LinearLayout c1 = Ui.card(act);
        c1.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        c1.addView(choice("Tema", new String[]{"Automático (sistema)", "Claro", "Escuro"}, new String[]{"system", "light", "dark"},
                Prefs.theme(act), "theme", true));
        col.addView(c1);

        // leitura
        col.addView(Ui.section(act, "Leitura"));
        LinearLayout c2 = Ui.card(act);
        c2.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        c2.addView(toggle("Modo noturno", "Inverte as cores das páginas para ler no escuro", "night_reading", Prefs.nightReading(act)));
        c2.addView(inset(Ui.divider(act)));
        c2.addView(toggle("Manter a tela ligada", "Enquanto um PDF estiver aberto", "keep_screen_on", Prefs.keepScreenOn(act)));
        c2.addView(inset(Ui.divider(act)));
        c2.addView(toggle("Continuar de onde parou", "Abre o PDF na última página lida", "resume_last_page", Prefs.resumeLastPage(act)));
        col.addView(c2);

        // conversão
        col.addView(Ui.section(act, "Conversão"));
        LinearLayout c3 = Ui.card(act);
        c3.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        c3.addView(choice("Tamanho da página do PDF", new String[]{"A4 (210 × 297 mm)", "Carta (216 × 279 mm)"},
                new String[]{"a4", "letter"}, Prefs.pageSize(act), "page_size", false));
        c3.addView(inset(Ui.divider(act)));
        c3.addView(choice("Margens", new String[]{"Estreitas", "Normais", "Largas"}, new String[]{"narrow", "normal", "wide"},
                Prefs.margins(act), "margins", false));
        c3.addView(inset(Ui.divider(act)));
        c3.addView(choiceInt("Qualidade das imagens (PDF → imagem)", new String[]{"Rascunho (100 dpi)", "Padrão (150 dpi)", "Alta (200 dpi)", "Impressão (300 dpi)"},
                new int[]{100, 150, 200, 300}, Prefs.imageDpi(act), "image_dpi"));
        c3.addView(inset(Ui.divider(act)));
        c3.addView(choice("PDF → Word: imagem das páginas", new String[]{"Automático (só páginas digitalizadas)", "Sempre incluir", "Nunca incluir"},
                new String[]{"auto", "always", "never"}, Prefs.pdfPageImages(act), "pdf_page_images", false));
        if (Build.VERSION.SDK_INT >= 29) {
            c3.addView(inset(Ui.divider(act)));
            c3.addView(toggle("Salvar também em Downloads", "Cópia em Downloads/PDF Leitor, visível no app Arquivos", "save_downloads", Prefs.saveToDownloads(act)));
        }
        col.addView(c3);

        // armazenamento
        col.addView(Ui.section(act, "Armazenamento"));
        LinearLayout c4 = Ui.card(act);
        c4.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        c4.addView(action("Limpar lista de PDFs", History.all(act).size() + " itens no histórico (os arquivos não são apagados)", new Runnable() {
            @Override
            public void run() {
                confirm("Limpar a lista de PDFs?", "A lista fica vazia. Seus arquivos continuam onde estão.", new Runnable() {
                    @Override
                    public void run() {
                        History.clear(act);
                        Thumbs.clear(act);
                        refresh();
                    }
                });
            }
        }));
        c4.addView(inset(Ui.divider(act)));
        final File out = Files.outputDir(act);
        c4.addView(action("Apagar arquivos convertidos", Ui.size(Files.dirSize(out)) + " na pasta do app", new Runnable() {
            @Override
            public void run() {
                confirm("Apagar arquivos convertidos?", "Os arquivos convertidos guardados pelo app serão apagados. "
                        + "Cópias salvas em Downloads não são afetadas.", new Runnable() {
                    @Override
                    public void run() {
                        Files.deleteRecursive(out);
                        refresh();
                        act.convertPage.refreshOutputs();
                    }
                });
            }
        }));
        c4.addView(inset(Ui.divider(act)));
        final File cache = act.getCacheDir();
        final File lib = Files.libraryDir(act);
        c4.addView(action("Limpar cache", Ui.size(Files.dirSize(cache)) + " de miniaturas e temporários  ·  "
                + Ui.size(Files.dirSize(lib)) + " de PDFs recebidos de outros apps", new Runnable() {
            @Override
            public void run() {
                Thumbs.clear(act);
                Files.deleteRecursive(cache);
                Ui.toast(act, "Cache limpo");
                refresh();
            }
        }));
        col.addView(c4);

        // sobre
        col.addView(Ui.section(act, "Sobre"));
        LinearLayout c5 = Ui.card(act);
        String version = "1.0";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }
        c5.addView(Ui.text(act, "PDF Leitor  " + version, 16, Ui.TEXT, true));
        TextView about = Ui.text(act, "Funciona 100% offline: não pede internet, login nem cadastro. "
                + "Seus arquivos nunca saem do aparelho.\n\n"
                + "Lê: PDF, Word (DOCX), PowerPoint (PPTX), Excel (XLSX), ODT, ODS, ODP, EPUB, HTML, Markdown, TXT, CSV, RTF e imagens.\n"
                + "Gera: PDF, DOCX, PPTX, XLSX, ODT, ODS, EPUB, HTML, Markdown, TXT, CSV, RTF, JPG, PNG e WEBP.\n\n"
                + "Formatos antigos do Office 97-2003 (.doc, .xls, .ppt) não são suportados.", 13.5f, Ui.TEXT2, false);
        about.setPadding(0, Ui.dp(act, 8), 0, 0);
        about.setLineSpacing(0, 1.15f);
        c5.addView(about);
        col.addView(c5);
    }

    private View inset(View v) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.setMargins(Ui.dp(act, 16), 0, Ui.dp(act, 16), 0);
        return v;
    }

    private LinearLayout row(String title, String sub) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(act, 16), Ui.dp(act, 12), Ui.dp(act, 16), Ui.dp(act, 12));
        r.setBackground(Ui.ripple(null, 0));
        r.setMinimumHeight(Ui.dp(act, 56));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(act, title, 15.5f, Ui.TEXT, false));
        if (sub != null) {
            TextView s = Ui.text(act, sub, 13, Ui.TEXT2, false);
            s.setPadding(0, Ui.dp(act, 2), 0, 0);
            s.setMaxLines(3);
            s.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(s);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return r;
    }

    private View toggle(String title, String sub, final String key, boolean value) {
        LinearLayout r = row(title, sub);
        final Switch sw = new Switch(act);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Prefs.put(act, key, checked);
            }
        });
        r.addView(sw);
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return r;
    }

    private View choice(final String title, final String[] labels, final String[] values, String current, final String key,
                        final boolean recreate) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) idx = i;
        LinearLayout r = row(title, labels[idx]);
        final int sel = idx;
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(act).setTitle(title).setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.put(act, key, values[which]);
                        d.dismiss();
                        if (recreate) act.recreate();
                        else refresh();
                    }
                }).setNegativeButton("Cancelar", null).show();
            }
        });
        return r;
    }

    private View choiceInt(final String title, final String[] labels, final int[] values, int current, final String key) {
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == current) idx = i;
        LinearLayout r = row(title, labels[idx]);
        final int sel = idx;
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(act).setTitle(title).setSingleChoiceItems(labels, sel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Prefs.put(act, key, values[which]);
                        d.dismiss();
                        refresh();
                    }
                }).setNegativeButton("Cancelar", null).show();
            }
        });
        return r;
    }

    private View action(String title, String sub, final Runnable run) {
        LinearLayout r = row(title, sub);
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        return r;
    }

    private void confirm(String title, String msg, final Runnable ok) {
        new AlertDialog.Builder(act).setTitle(title).setMessage(msg)
                .setPositiveButton("Confirmar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ok.run();
                    }
                }).setNegativeButton("Cancelar", null).show();
    }
}
