package br.pdfleitor.app;

import android.content.pm.PackageInfo;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 20);
        col.setPadding(side, Ui.dp(a, 28), side, Ui.dp(a, 130));
        addView(col, new LayoutParams(-1, -2));
        build();
    }

    void refresh() {
        refresh(false);
    }

    void refresh(boolean animate) {
        col.removeAllViews();
        build();
        if (animate) Ui.stagger(col, 0);
    }

    private void build() {
        col.addView(Ui.header(act, "03 — Ajustes", "Do seu\n*jeito.*", "Tudo fica salvo só neste aparelho."));

        // aparência
        col.addView(Ui.section(act, "Aparência"));
        LinearLayout c1 = card();
        String[] themes = {"system", "light", "dark"};
        c1.addView(segmentRow("Tema", null, new String[]{"Automático", "Claro", "Escuro"}, indexOf(themes, Prefs.theme(act)),
                new Widgets.Segmented.Listener() {
                    @Override
                    public void onChange(final int i) {
                        Prefs.put(act, "theme", new String[]{"system", "light", "dark"}[i]);
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                act.recreate();
                            }
                        }, 320);
                    }
                }));
        col.addView(c1);

        // leitura
        col.addView(Ui.section(act, "Leitura"));
        LinearLayout c2 = card();
        c2.addView(toggle("Modo noturno", "Inverte as cores das páginas para ler no escuro.", "night_reading", Prefs.nightReading(act)));
        c2.addView(div());
        c2.addView(toggle("Tela sempre ligada", "Enquanto um PDF estiver aberto.", "keep_screen_on", Prefs.keepScreenOn(act)));
        c2.addView(div());
        c2.addView(toggle("Continuar de onde parou", "Abre na última página lida.", "resume_last_page", Prefs.resumeLastPage(act)));
        col.addView(c2);

        // conversão
        col.addView(Ui.section(act, "Conversão"));
        LinearLayout c3 = card();
        final String[] sizes = {"a4", "letter"};
        c3.addView(segmentRow("Papel", "Tamanho das páginas dos PDFs gerados.", new String[]{"A4", "Carta"},
                indexOf(sizes, Prefs.pageSize(act)), saver("page_size", sizes)));
        c3.addView(div());
        final String[] margins = {"narrow", "normal", "wide"};
        c3.addView(segmentRow("Margens", null, new String[]{"Estreitas", "Normais", "Largas"},
                indexOf(margins, Prefs.margins(act)), saver("margins", margins)));
        c3.addView(div());
        final int[] dpis = {100, 150, 200, 300};
        int di = 1;
        for (int i = 0; i < dpis.length; i++) if (dpis[i] == Prefs.imageDpi(act)) di = i;
        c3.addView(segmentRow("Qualidade das imagens", "Resolução ao transformar páginas em JPG/PNG (dpi).",
                new String[]{"100", "150", "200", "300"}, di, new Widgets.Segmented.Listener() {
                    @Override
                    public void onChange(int i) {
                        Prefs.put(act, "image_dpi", dpis[i]);
                    }
                }));
        c3.addView(div());
        final String[] pimg = {"auto", "always", "never"};
        c3.addView(segmentRow("Imagem das páginas (PDF → Word)", "No automático, só páginas digitalizadas viram imagem.",
                new String[]{"Auto", "Sempre", "Nunca"}, indexOf(pimg, Prefs.pdfPageImages(act)), saver("pdf_page_images", pimg)));
        if (Build.VERSION.SDK_INT >= 29) {
            c3.addView(div());
            c3.addView(toggle("Cópia em Downloads", "Salva também em Downloads/PDF Leitor.", "save_downloads", Prefs.saveToDownloads(act)));
        }
        col.addView(c3);

        // armazenamento
        col.addView(Ui.section(act, "Armazenamento"));
        LinearLayout c4 = card();
        c4.addView(action("Limpar biblioteca", History.all(act).size() + " itens", new Runnable() {
            @Override
            public void run() {
                Sheet.confirm(act, "Limpar a\n*biblioteca?*", "A lista fica vazia. Seus arquivos continuam onde estão.", "Limpar", true,
                        new Runnable() {
                            @Override
                            public void run() {
                                History.clear(act);
                                Thumbs.clear(act);
                                refresh(false);
                                Ui.toast(act, "Biblioteca limpa");
                            }
                        });
            }
        }));
        c4.addView(div());
        final File out = Files.outputDir(act);
        c4.addView(action("Apagar convertidos", Ui.size(Files.dirSize(out)), new Runnable() {
            @Override
            public void run() {
                Sheet.confirm(act, "Apagar\n*convertidos?*", "Os arquivos guardados pelo app serão apagados. Cópias em Downloads não são afetadas.",
                        "Apagar", true, new Runnable() {
                            @Override
                            public void run() {
                                Files.deleteRecursive(out);
                                refresh(false);
                                act.convertPage.refreshOutputs();
                                Ui.toast(act, "Arquivos convertidos apagados");
                            }
                        });
            }
        }));
        c4.addView(div());
        final File cache = act.getCacheDir();
        c4.addView(action("Limpar cache", Ui.size(Files.dirSize(cache)), new Runnable() {
            @Override
            public void run() {
                Thumbs.clear(act);
                Files.deleteRecursive(cache);
                refresh(false);
                Ui.toast(act, "Cache limpo");
            }
        }));
        col.addView(c4);

        // sobre
        col.addView(Ui.section(act, "Sobre"));
        col.addView(about());
    }

    // ------------------------------------------------------------------ peças

    private LinearLayout card() {
        LinearLayout c = Ui.card(act);
        c.setPadding(Ui.dp(act, 20), Ui.dp(act, 6), Ui.dp(act, 20), Ui.dp(act, 6));
        return c;
    }

    private View div() {
        return Ui.divider(act);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private Widgets.Segmented.Listener saver(final String key, final String[] values) {
        return new Widgets.Segmented.Listener() {
            @Override
            public void onChange(int i) {
                Prefs.put(act, key, values[i]);
            }
        };
    }

    private View segmentRow(String title, String sub, String[] labels, int sel, Widgets.Segmented.Listener l) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Ui.dp(act, 16), 0, Ui.dp(act, 16));
        r.addView(Ui.text(act, title, 16, Ui.INK, true));
        if (sub != null) {
            TextView s = Ui.text(act, sub, 13.5f, Ui.INK2, false);
            s.setPadding(0, Ui.dp(act, 5), 0, 0);
            r.addView(s);
        }
        Widgets.Segmented seg = new Widgets.Segmented(act, labels, sel);
        seg.setListener(l);
        r.addView(seg, Ui.lpMargins(-1, -2, 0, Ui.dp(act, 12), 0, 0));
        return r;
    }

    private View toggle(String title, String sub, final String key, boolean value) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(act, 16), 0, Ui.dp(act, 16));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(Ui.text(act, title, 16, Ui.INK, true));
        TextView s = Ui.text(act, sub, 13.5f, Ui.INK2, false);
        s.setPadding(0, Ui.dp(act, 5), Ui.dp(act, 12), 0);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        final Widgets.Toggle tg = new Widgets.Toggle(act, value);
        tg.setListener(new Widgets.Toggle.Listener() {
            @Override
            public void onChange(boolean on) {
                Prefs.put(act, key, on);
            }
        });
        r.addView(tg);
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                tg.toggle();
            }
        });
        return r;
    }

    private View action(String title, String value, final Runnable run) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(act, 18), 0, Ui.dp(act, 18));
        r.setBackground(Ui.ripple(null, Ui.dp(act, 12)));
        r.addView(Ui.text(act, title, 16, Ui.INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(Ui.label(act, value, Ui.INK2));
        TextView arrow = Ui.text(act, "→", 17, Ui.INK3, false);
        arrow.setPadding(Ui.dp(act, 12), 0, 0, 0);
        r.addView(arrow);
        r.setClickable(true);
        r.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        return r;
    }

    private View about() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(Ui.DOCK, Ui.dp(act, 28)));
        c.setPadding(Ui.dp(act, 24), Ui.dp(act, 26), Ui.dp(act, 24), Ui.dp(act, 26));
        String version = "1.0";
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }
        TextView v = Ui.label(act, "Versão " + version, Ui.DOCK_MUTED);
        c.addView(v);
        TextView name = Ui.display(act, "PDF *Leitor*", 44);
        name.setTextColor(Ui.DOCK_INK);
        name.setPadding(0, Ui.dp(act, 14), 0, 0);
        c.addView(name);
        TextView m = Ui.text(act, "Ler e converter documentos não deveria exigir conta, internet nem anúncio. "
                + "Tudo acontece aqui, no seu aparelho — seus arquivos nunca saem dele.", 15, Ui.DOCK_INK, false);
        m.setAlpha(0.82f);
        m.setPadding(0, Ui.dp(act, 14), 0, 0);
        c.addView(m);
        ConvertPage.FlowLayout badges = new ConvertPage.FlowLayout(act, Ui.dp(act, 8));
        for (String s : new String[]{"100% offline", "Sem login", "Sem anúncios", "Sem rastreio"}) {
            TextView b = Ui.label(act, s, Ui.DOCK_INK);
            b.setBackground(Ui.stroke(0, Ui.DOCK_MUTED, Math.max(1, Ui.dp(act, 1)), Ui.dp(act, 14)));
            b.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
            badges.addView(b);
        }
        c.addView(badges, Ui.lpMargins(-1, -2, 0, Ui.dp(act, 20), 0, Ui.dp(act, 22)));
        c.addView(Ui.label(act, "Lê", Ui.ACCENT));
        TextView reads = Ui.text(act, "PDF, DOCX, PPTX, XLSX, ODT, ODS, ODP, EPUB, HTML, Markdown, TXT, CSV, RTF, JPG, PNG, WEBP, GIF, BMP", 13.5f, Ui.DOCK_INK, false);
        reads.setAlpha(0.82f);
        reads.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 14));
        c.addView(reads);
        c.addView(Ui.label(act, "Gera", Ui.ACCENT));
        TextView writes = Ui.text(act, "PDF, DOCX, PPTX, XLSX, ODT, ODS, EPUB, HTML, Markdown, TXT, CSV, RTF, JPG, PNG, WEBP", 13.5f, Ui.DOCK_INK, false);
        writes.setAlpha(0.82f);
        writes.setPadding(0, Ui.dp(act, 6), 0, 0);
        c.addView(writes);
        return c;
    }
}
