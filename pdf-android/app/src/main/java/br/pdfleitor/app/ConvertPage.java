package br.pdfleitor.app;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import br.pdfleitor.core.Format;
import br.pdfleitor.core.Util;

/** Aba "Converter". */
public class ConvertPage extends ScrollView {

    private static final class Tool {
        final String from, to, title, sub;
        final String[] mimes;
        final Format target;
        final boolean multi;

        Tool(String from, String to, String title, String sub, String[] mimes, Format target, boolean multi) {
            this.from = from;
            this.to = to;
            this.title = title;
            this.sub = sub;
            this.mimes = mimes;
            this.target = target;
            this.multi = multi;
        }
    }

    private static final String M_PDF = "application/pdf";
    private static final String[] WORD = {Format.DOCX.mime, Format.ODT.mime, Format.RTF.mime, "text/rtf", "application/msword"};
    private static final String[] PPT = {Format.PPTX.mime, Format.ODP.mime, "application/vnd.ms-powerpoint"};
    private static final String[] XLS = {Format.XLSX.mime, Format.ODS.mime, "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"};
    private static final String[] IMG = {"image/*"};
    private static final String[] WEB = {"text/html", "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream"};
    private static final String[] EBOOK = {Format.EPUB.mime};
    private static final String[] PDFM = {M_PDF};

    private final Tool[] tools = {
            new Tool("doc", "pdf", "Word → PDF", "DOCX · ODT · RTF", WORD, Format.PDF, false),
            new Tool("pdf", "doc", "PDF → Word", "Texto editável", PDFM, Format.DOCX, false),
            new Tool("img", "pdf", "Imagens → PDF", "Junte várias fotos", IMG, Format.PDF, true),
            new Tool("ppt", "pdf", "PowerPoint → PDF", "PPTX · ODP", PPT, Format.PDF, false),
            new Tool("pdf", "ppt", "PDF → PowerPoint", "Página a página", PDFM, Format.PPTX, false),
            new Tool("xls", "pdf", "Excel → PDF", "XLSX · ODS · CSV", XLS, Format.PDF, false),
            new Tool("pdf", "xls", "PDF → Excel", "Tabelas e texto", PDFM, Format.XLSX, false),
            new Tool("pdf", "jpg", "PDF → JPG", "Página a página", PDFM, Format.JPG, false),
            new Tool("ppt", "doc", "PowerPoint → Word", "Slides em texto", PPT, Format.DOCX, false),
            new Tool("xls", "doc", "Excel → Word", "Planilha em tabela", XLS, Format.DOCX, false),
            new Tool("doc", "ppt", "Word → PowerPoint", "Títulos → slides", WORD, Format.PPTX, false),
            new Tool("doc", "xls", "Word → Excel", "Só as tabelas", WORD, Format.XLSX, false),
            new Tool("ppt", "png", "PowerPoint → PNG", "Slide a slide", PPT, Format.PNG, false),
            new Tool("pdf", "txt", "PDF → Texto", "Somente o texto", PDFM, Format.TXT, false),
            new Tool("web", "pdf", "HTML/MD → PDF", "Páginas e notas", WEB, Format.PDF, false),
            new Tool("epub", "pdf", "EPUB → PDF", "eBooks", EBOOK, Format.PDF, false),
    };

    private final MainActivity act;
    private final FrameLayout stage;
    private final LinearLayout outputsList;
    private final TextView outputsEmpty;
    private final List<Converter.Source> sources = new ArrayList<Converter.Source>();
    private Format target;
    private Tool pendingTool;
    private Converter running;
    private File saveAsFile;
    private TextView progressText, convertBtn;
    private View progressBox;
    private FlowLayout chips;

    public ConvertPage(MainActivity a) {
        super(a);
        act = a;
        setBackgroundColor(Ui.BG);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 20);
        col.setPadding(side, Ui.dp(a, 28), side, Ui.dp(a, 130));
        addView(col, new LayoutParams(-1, -2));

        col.addView(Ui.header(a, "02 — Conversor", "Converta\n*qualquer* coisa.",
                "Word, PDF, PowerPoint, Excel, imagens e mais. Tudo acontece no aparelho — sem internet, sem cadastro."));

        stage = new FrameLayout(a);
        col.addView(stage, Ui.lpMargins(-1, -2, 0, Ui.dp(a, 26), 0, 0));
        showIdle();

        // atalhos em grade "bento"
        col.addView(Ui.section(a, "Atalhos"));
        col.addView(bigTool(tools[0]), Ui.lpMargins(-1, -2, 0, 0, 0, Ui.dp(a, 10)));
        LinearLayout row = null;
        for (int i = 1; i < tools.length; i++) {
            int k = i - 1;
            if (k % 2 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, Ui.lpMargins(-1, -2, 0, 0, 0, Ui.dp(a, 10)));
            }
            View card = smallTool(tools[i], k < 2);
            LinearLayout.LayoutParams lp = Ui.lpMargins(0, -1, k % 2 == 1 ? Ui.dp(a, 10) : 0, 0, 0, 0);
            lp.weight = 1;
            row.addView(card, lp);
        }

        col.addView(Ui.section(a, "Convertidos recentemente"));
        outputsList = new LinearLayout(a);
        outputsList.setOrientation(LinearLayout.VERTICAL);
        col.addView(outputsList);
        outputsEmpty = Ui.text(a, "Seus arquivos convertidos aparecem aqui.", 15, Ui.INK2, false);
        outputsEmpty.setPadding(Ui.dp(a, 4), 0, 0, 0);
        col.addView(outputsEmpty);
        refreshOutputs();
    }

    void onShown() {
        refreshOutputs();
    }

    // ------------------------------------------------------------------ atalhos

    private View bigTool(final Tool t) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        int r = Ui.dp(act, 28);
        c.setBackground(Ui.ripple(Ui.rounded(Ui.DOCK, r), r));
        c.setPadding(Ui.dp(act, 22), Ui.dp(act, 22), Ui.dp(act, 22), Ui.dp(act, 22));
        c.setClickable(true);
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new Widgets.FileTag(act, t.from), new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 54)));
        TextView arrow = Ui.text(act, "→", 26, Ui.DOCK_MUTED, false);
        arrow.setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 12), 0);
        top.addView(arrow);
        top.addView(new Widgets.FileTag(act, t.to), new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 54)));
        View sp = new View(act);
        top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView badge = Ui.label(act, "Mais usado", Ui.ON_ACCENT);
        badge.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 12)));
        badge.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 10), Ui.dp(act, 6));
        top.addView(badge);
        c.addView(top);
        TextView title = Ui.display(act, t.title, 40);
        title.setTextColor(Ui.DOCK_INK);
        title.setPadding(0, Ui.dp(act, 26), 0, 0);
        c.addView(title);
        TextView sub = Ui.label(act, t.sub, Ui.DOCK_MUTED);
        sub.setPadding(0, Ui.dp(act, 10), 0, 0);
        c.addView(sub);
        bindTool(c, t);
        Ui.press(c);
        return c;
    }

    private View smallTool(final Tool t, boolean tall) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        int r = Ui.dp(act, 24);
        c.setBackground(Ui.ripple(Ui.stroke(Ui.CARD, Ui.LINE, Math.max(1, Ui.dp(act, 0.8f)), r), r));
        c.setPadding(Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16));
        c.setClickable(true);
        FrameLayout tags = new FrameLayout(act);
        int tw = Ui.dp(act, tall ? 34 : 28), th = Ui.dp(act, tall ? 42 : 34);
        Widgets.FileTag a = new Widgets.FileTag(act, t.from);
        tags.addView(a, new FrameLayout.LayoutParams(tw, th));
        Widgets.FileTag b = new Widgets.FileTag(act, t.to);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(tw, th);
        blp.leftMargin = (int) (tw * 0.92f);
        blp.topMargin = (int) (th * 0.18f);
        b.setRotation(8);
        a.setRotation(-6);
        tags.addView(b, blp);
        c.addView(tags, new LinearLayout.LayoutParams(-2, (int) (th * 1.3f)));
        TextView title = Ui.text(act, t.title, tall ? 17 : 15, Ui.INK, true);
        title.setPadding(0, Ui.dp(act, tall ? 22 : 16), 0, 0);
        title.setMaxLines(2);
        c.addView(title);
        TextView sub = Ui.label(act, t.sub, Ui.INK2);
        sub.setPadding(0, Ui.dp(act, 8), 0, 0);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(sub);
        bindTool(c, t);
        Ui.press(c);
        return c;
    }

    private void bindTool(View c, final Tool t) {
        c.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running != null) {
                    Ui.toast(act, "Aguarde a conversão atual terminar.");
                    return;
                }
                pendingTool = t;
                pick(t.mimes, t.multi);
            }
        });
    }

    // ------------------------------------------------------------------ palco

    private void swapStage(View v) {
        stage.removeAllViews();
        stage.addView(v, new FrameLayout.LayoutParams(-1, -2));
        Ui.enter(v, 0);
    }

    private void showIdle() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int r = Ui.dp(act, 28);
        c.setBackground(Ui.ripple(Ui.dashed(Ui.CARD, Ui.INK3, Ui.dp(act, 1.4f), Ui.dp(act, 7), Ui.dp(act, 6), r), r));
        c.setPadding(Ui.dp(act, 24), Ui.dp(act, 34), Ui.dp(act, 24), Ui.dp(act, 30));
        c.setClickable(true);
        ImageView plus = new ImageView(act);
        plus.setImageDrawable(Ui.icon(act, R.drawable.ic_add, Ui.ON_ACCENT));
        plus.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(act, 32)));
        int p = Ui.dp(act, 18);
        plus.setPadding(p, p, p, p);
        plus.setElevation(Ui.dp(act, 6));
        c.addView(plus, new LinearLayout.LayoutParams(Ui.dp(act, 64), Ui.dp(act, 64)));
        TextView t = Ui.display(act, "Escolha um *arquivo*", 30);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(act, 20), 0, 0);
        c.addView(t);
        TextView s = Ui.text(act, "Qualquer formato para qualquer formato. Para juntar fotos em um PDF, selecione várias imagens.", 14.5f, Ui.INK2, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8), 0);
        c.addView(s);
        LinearLayout fmts = new LinearLayout(act);
        fmts.setOrientation(LinearLayout.HORIZONTAL);
        fmts.setPadding(0, Ui.dp(act, 22), 0, 0);
        for (String e : new String[]{"pdf", "doc", "ppt", "xls", "img", "epub"}) {
            Widgets.FileTag ft = new Widgets.FileTag(act, e);
            fmts.addView(ft, Ui.lpMargins(Ui.dp(act, 30), Ui.dp(act, 37), Ui.dp(act, 4), 0, Ui.dp(act, 4), 0));
        }
        c.addView(fmts);
        c.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingTool = null;
                pick(new String[]{"*/*"}, true);
            }
        });
        Ui.press(c);
        swapStage(c);
    }

    private LinearLayout fileRow(Converter.Source first, int count) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new Widgets.FileTag(act, first.format.ext), new LinearLayout.LayoutParams(Ui.dp(act, 44), Ui.dp(act, 54)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 4), 0);
        TextView n = Ui.text(act, count == 1 ? first.name : count + " imagens", 16.5f, Ui.INK, true);
        n.setSingleLine();
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        long total = 0;
        for (Converter.Source s : sources) total += Math.max(0, s.size);
        TextView m = Ui.label(act, first.format.label + (total > 0 ? "  ·  " + Ui.size(total) : ""), Ui.INK2);
        m.setPadding(0, Ui.dp(act, 8), 0, 0);
        t.addView(m);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private void showSelected() {
        if (sources.isEmpty()) {
            showIdle();
            return;
        }
        LinearLayout c = Ui.card(act);
        LinearLayout row = fileRow(sources.get(0), sources.size());
        ImageView clear = Ui.circleButton(act, R.drawable.ic_close, Ui.CARD2, Ui.INK, 40, "Trocar arquivo");
        clear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running != null) return;
                sources.clear();
                showIdle();
            }
        });
        row.addView(clear);
        c.addView(row);
        c.addView(Ui.divider(act), Ui.lpMargins(-1, Math.max(1, Ui.dp(act, 0.8f)), 0, Ui.dp(act, 18), 0, Ui.dp(act, 16)));
        c.addView(Ui.label(act, "Converter para", Ui.INK2));
        chips = new FlowLayout(act, Ui.dp(act, 8));
        c.addView(chips, Ui.lpMargins(-1, -2, 0, Ui.dp(act, 14), 0, Ui.dp(act, 20)));
        fillChips();
        convertBtn = Ui.button(act, "", true, true);
        convertBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startConversion();
            }
        });
        c.addView(convertBtn, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout pb = new LinearLayout(act);
        pb.setOrientation(LinearLayout.VERTICAL);
        pb.setVisibility(GONE);
        pb.addView(new Widgets.LineLoader(act), Ui.lpMargins(-1, Ui.dp(act, 4), 0, Ui.dp(act, 8), 0, Ui.dp(act, 14)));
        LinearLayout pr = new LinearLayout(act);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        progressText = Ui.text(act, "Preparando…", 15, Ui.INK, false);
        pr.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cancel = Ui.label(act, "Cancelar", Ui.ACCENT);
        cancel.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), 0, Ui.dp(act, 10));
        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running != null) running.cancel();
            }
        });
        pr.addView(cancel);
        pb.addView(pr);
        progressBox = pb;
        c.addView(pb);
        updateButton();
        swapStage(c);
    }

    private void fillChips() {
        chips.removeAllViews();
        Format src = sources.get(0).format;
        for (final Format f : Format.targetsFor(src)) {
            TextView chip = Ui.text(act, f.label, 14, Ui.INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 16), 0);
            chip.setMinHeight(Ui.dp(act, 40));
            int r = Ui.dp(act, 20);
            boolean sel = f == target;
            chip.setBackground(Ui.ripple(sel ? Ui.rounded(Ui.INK, r) : Ui.stroke(0, Ui.LINE, Math.max(1, Ui.dp(act, 1)), r), r));
            chip.setTextColor(sel ? Ui.BG : Ui.INK);
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (running != null) return;
                    target = f;
                    fillChips();
                    updateButton();
                }
            });
            chips.addView(chip);
        }
    }

    private void updateButton() {
        if (convertBtn != null && target != null) convertBtn.setText("Converter para " + target.label + "   →");
    }

    private void showResult(final Converter.Result r) {
        final File f = r.file;
        LinearLayout c = Ui.card(act);
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        final Widgets.CheckMark check = new Widgets.CheckMark(act);
        head.addView(check, new LinearLayout.LayoutParams(Ui.dp(act, 46), Ui.dp(act, 46)));
        TextView done = Ui.display(act, "Pronto.", 36);
        done.setPadding(Ui.dp(act, 14), 0, 0, 0);
        head.addView(done);
        c.addView(head);
        LinearLayout file = new LinearLayout(act);
        file.setOrientation(LinearLayout.HORIZONTAL);
        file.setGravity(Gravity.CENTER_VERTICAL);
        file.setBackground(Ui.rounded(Ui.CARD2, Ui.dp(act, 18)));
        file.setPadding(Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12));
        file.addView(new Widgets.FileTag(act, Util.extension(f.getName())), new LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 44)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView n = Ui.text(act, f.getName(), 15.5f, Ui.INK, true);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        String info = Ui.size(f.length()) + (r.publicUri != null ? "  ·  também em Downloads" : "");
        TextView m = Ui.label(act, info, Ui.INK2);
        m.setPadding(0, Ui.dp(act, 7), 0, 0);
        t.addView(m);
        file.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(file, Ui.lpMargins(-1, -2, 0, Ui.dp(act, 18), 0, Ui.dp(act, 16)));
        TextView open = Ui.button(act, "Abrir", true);
        open.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Files.open(act, f);
            }
        });
        c.addView(open, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView share = Ui.button(act, "Enviar", false);
        share.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Files.share(act, Files.shareUri(act, f), Files.mimeOf(f.getName()), f.getName());
            }
        });
        actions.addView(share, new LinearLayout.LayoutParams(0, -2, 1));
        TextView save = Ui.button(act, "Salvar em…", false);
        save.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAs(f);
            }
        });
        LinearLayout.LayoutParams slp = Ui.lpMargins(0, -2, Ui.dp(act, 10), 0, 0, 0);
        slp.weight = 1;
        actions.addView(save, slp);
        c.addView(actions, Ui.lpMargins(-1, -2, 0, Ui.dp(act, 10), 0, 0));
        TextView again = Ui.label(act, "Nova conversão  →", Ui.ACCENT);
        again.setGravity(Gravity.CENTER);
        again.setPadding(0, Ui.dp(act, 18), 0, Ui.dp(act, 4));
        again.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sources.clear();
                showIdle();
            }
        });
        c.addView(again, new LinearLayout.LayoutParams(-1, -2));
        swapStage(c);
        check.postDelayed(new Runnable() {
            @Override
            public void run() {
                check.play();
            }
        }, 180);
    }

    // ------------------------------------------------------------------ seleção

    private void pick(String[] mimes, boolean multi) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (mimes.length == 1) i.setType(mimes[0]);
        else {
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        }
        if (multi) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            act.startActivityForResult(i, MainActivity.REQ_PICK_CONVERT);
        } catch (Exception e) {
            Ui.toast(act, "Não há gerenciador de arquivos disponível.");
        }
    }

    void onPicked(Intent data) {
        List<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        if (uris.isEmpty()) return;
        setSources(uris);
        if (pendingTool != null && !sources.isEmpty()) {
            target = pendingTool.target;
            pendingTool = null;
            showSelected();
            smoothScrollTo(0, 0);
            startConversion();
        }
    }

    void setExternalSources(List<Uri> uris, boolean fromApp) {
        pendingTool = null;
        setSources(uris);
        smoothScrollTo(0, 0);
    }

    private void setSources(List<Uri> uris) {
        if (running != null) {
            Ui.toast(act, "Aguarde a conversão atual terminar.");
            return;
        }
        sources.clear();
        List<Converter.Source> list = new ArrayList<Converter.Source>();
        for (Uri u : uris) list.add(Converter.describe(act, u));
        boolean allImages = true;
        for (Converter.Source s : list) if (s.format == null || !s.format.isImage()) allImages = false;
        if (list.size() > 1 && !allImages) {
            Ui.toast(act, "Só imagens podem ser combinadas. Usando o primeiro arquivo.");
            sources.add(list.get(0));
        } else sources.addAll(list);
        Converter.Source first = sources.get(0);
        if (first.format == null || !first.format.canRead) {
            Ui.toast(act, "Formato não suportado: " + first.name);
            sources.clear();
            showIdle();
            return;
        }
        target = first.format == Format.PDF ? Format.DOCX : Format.PDF;
        showSelected();
    }

    // ------------------------------------------------------------------ conversão

    private void startConversion() {
        if (sources.isEmpty() || target == null || running != null) return;
        final List<Converter.Source> src = new ArrayList<Converter.Source>(sources);
        final Format tgt = target;
        final Converter conv = new Converter(act);
        running = conv;
        if (convertBtn != null) convertBtn.setVisibility(GONE);
        if (progressBox != null) {
            progressBox.setVisibility(VISIBLE);
            Ui.enter(progressBox, 0);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Converter.Result res = null;
                String error = null;
                try {
                    res = conv.convert(src, tgt, new Converter.Listener() {
                        @Override
                        public void progress(final String msg) {
                            act.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (progressText != null) progressText.setText(msg);
                                }
                            });
                        }
                    });
                } catch (InterruptedException e) {
                    error = "Conversão cancelada.";
                } catch (OutOfMemoryError e) {
                    error = "O arquivo é grande demais para a memória deste aparelho.";
                } catch (Throwable t) {
                    error = t.getMessage() != null ? t.getMessage() : "Falha na conversão (" + t.getClass().getSimpleName() + ").";
                }
                final Converter.Result r = res;
                final String err = error;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        running = null;
                        if (err != null) {
                            showSelected();
                            Sheet.info(act, "Não deu\n*certo.*", err);
                            return;
                        }
                        if (r.file.getName().toLowerCase().endsWith(".pdf")) {
                            History.touch(act, Uri.fromFile(r.file).toString(), r.file.getName(), r.file.length());
                        }
                        showResult(r);
                        refreshOutputs();
                    }
                });
            }
        }).start();
    }

    private void saveAs(File f) {
        saveAsFile = f;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(Files.mimeOf(f.getName()));
        i.putExtra(Intent.EXTRA_TITLE, f.getName());
        try {
            act.startActivityForResult(i, MainActivity.REQ_SAVE_AS);
        } catch (Exception e) {
            Ui.toast(act, "Não há gerenciador de arquivos disponível.");
        }
    }

    void onSaveAs(Intent data) {
        final Uri dest = data.getData();
        final File src = saveAsFile;
        if (dest == null || src == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    OutputStream os = act.getContentResolver().openOutputStream(dest);
                    InputStream in = new FileInputStream(src);
                    try {
                        Util.copy(in, os);
                    } finally {
                        in.close();
                        if (os != null) os.close();
                    }
                    msg = "Arquivo salvo.";
                } catch (Exception e) {
                    msg = "Não foi possível salvar: " + e.getMessage();
                }
                final String m = msg;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Ui.toast(act, m);
                    }
                });
            }
        }).start();
    }

    // ------------------------------------------------------------------ convertidos

    void refreshOutputs() {
        outputsList.removeAllViews();
        File[] files = Files.outputDir(act).listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        int shown = 0;
        for (final File f : files) {
            if (f.isDirectory()) continue;
            if (shown++ >= 30) break;
            if (shown > 1) outputsList.addView(Ui.divider(act));
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.ripple(null, Ui.dp(act, 14)));
            row.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 14));
            row.addView(new Widgets.FileTag(act, Util.extension(f.getName())), new LinearLayout.LayoutParams(Ui.dp(act, 34), Ui.dp(act, 42)));
            LinearLayout t = new LinearLayout(act);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(Ui.dp(act, 14), 0, 0, 0);
            TextView n = Ui.text(act, f.getName(), 15.5f, Ui.INK, true);
            n.setSingleLine();
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            t.addView(n);
            TextView m = Ui.label(act, Ui.size(f.length()) + "  ·  " + Ui.ago(f.lastModified()), Ui.INK2);
            m.setPadding(0, Ui.dp(act, 7), 0, 0);
            t.addView(m);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView more = Ui.iconButton(act, R.drawable.ic_more, Ui.INK3, "Opções");
            more.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    outputMenu(f);
                }
            });
            row.addView(more);
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Files.open(act, f);
                }
            });
            outputsList.addView(row);
        }
        outputsEmpty.setVisibility(shown == 0 ? VISIBLE : GONE);
    }

    private void outputMenu(final File f) {
        Ui.menu(act, f.getName(), new String[]{"Abrir", "Compartilhar", "Salvar em…", "Converter de novo", "Excluir"},
                new Ui.MenuCallback() {
                    @Override
                    public void onItem(int id) {
                        switch (id) {
                            case 1:
                                Files.open(act, f);
                                break;
                            case 2:
                                Files.share(act, Files.shareUri(act, f), Files.mimeOf(f.getName()), f.getName());
                                break;
                            case 3:
                                saveAs(f);
                                break;
                            case 4: {
                                List<Uri> l = new ArrayList<Uri>();
                                l.add(Uri.fromFile(f));
                                setExternalSources(l, true);
                                break;
                            }
                            case 5:
                                Sheet.confirm(act, "Excluir\n*arquivo?*", f.getName(), "Excluir", true, new Runnable() {
                                    @Override
                                    public void run() {
                                        History.remove(act, Uri.fromFile(f).toString());
                                        f.delete();
                                        refreshOutputs();
                                        Ui.toast(act, "Arquivo excluído");
                                    }
                                });
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    void onDestroy() {
        if (running != null) running.cancel();
    }

    /** Layout que quebra linhas automaticamente (chips de formato). */
    static class FlowLayout extends ViewGroup {
        private final int spacing;

        FlowLayout(Context c, int spacing) {
            super(c);
            this.spacing = spacing;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec);
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + c.getMeasuredWidth() > maxW) {
                    x = 0;
                    y += rowH + spacing;
                    rowH = 0;
                }
                x += c.getMeasuredWidth() + spacing;
                rowH = Math.max(rowH, c.getMeasuredHeight());
            }
            setMeasuredDimension(maxW, y + rowH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l;
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                int w = c.getMeasuredWidth(), h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + spacing;
                    rowH = 0;
                }
                c.layout(x, y, x + w, y + h);
                x += w + spacing;
                rowH = Math.max(rowH, h);
            }
        }
    }
}
