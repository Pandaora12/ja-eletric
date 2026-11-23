package br.pdfleitor.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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

/** Aba "Converter": conversão entre PDF, Word, PowerPoint, Excel, imagens e mais. */
public class ConvertPage extends ScrollView {

    /** Atalho de conversão. */
    private static final class Tool {
        final String title, sub;
        final String[] mimes;
        final Format target;
        final boolean multi;
        final int color;

        Tool(String title, String sub, String[] mimes, Format target, boolean multi, int color) {
            this.title = title;
            this.sub = sub;
            this.mimes = mimes;
            this.target = target;
            this.multi = multi;
            this.color = color;
        }
    }

    private static final String M_PDF = "application/pdf";
    private static final String[] WORD = {Format.DOCX.mime, Format.ODT.mime, Format.RTF.mime, "text/rtf", "application/msword"};
    private static final String[] PPT = {Format.PPTX.mime, Format.ODP.mime, "application/vnd.ms-powerpoint"};
    private static final String[] XLS = {Format.XLSX.mime, Format.ODS.mime, "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"};
    private static final String[] IMG = {"image/*"};
    private static final String[] WEB = {"text/html", "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream"};
    private static final String[] EBOOK = {Format.EPUB.mime};

    private static final int C_WORD = 0xFF2B579A, C_PDF = 0xFFD93025, C_PPT = 0xFFD24726, C_XLS = 0xFF217346,
            C_IMG = 0xFF8E44AD, C_TXT = 0xFF5F6368, C_WEB = 0xFF0B8043;

    private final Tool[] tools = {
            new Tool("Word → PDF", "DOCX, ODT, RTF", WORD, Format.PDF, false, C_WORD),
            new Tool("PDF → Word", "Texto editável", new String[]{M_PDF}, Format.DOCX, false, C_PDF),
            new Tool("PowerPoint → PDF", "PPTX, ODP", PPT, Format.PDF, false, C_PPT),
            new Tool("PDF → PowerPoint", "Um slide por página", new String[]{M_PDF}, Format.PPTX, false, C_PDF),
            new Tool("Excel → PDF", "XLSX, ODS, CSV", XLS, Format.PDF, false, C_XLS),
            new Tool("PDF → Excel", "Tabelas e texto", new String[]{M_PDF}, Format.XLSX, false, C_PDF),
            new Tool("Imagens → PDF", "JPG, PNG, WEBP…", IMG, Format.PDF, true, C_IMG),
            new Tool("PDF → JPG", "Uma imagem por página", new String[]{M_PDF}, Format.JPG, false, C_PDF),
            new Tool("PowerPoint → Word", "Slides em documento", PPT, Format.DOCX, false, C_PPT),
            new Tool("Excel → Word", "Planilha em tabela", XLS, Format.DOCX, false, C_XLS),
            new Tool("Word → PowerPoint", "Títulos viram slides", WORD, Format.PPTX, false, C_WORD),
            new Tool("Word → Excel", "Tabelas do documento", WORD, Format.XLSX, false, C_WORD),
            new Tool("PowerPoint → Imagens", "PNG de cada slide", PPT, Format.PNG, false, C_PPT),
            new Tool("PDF → Texto", "TXT simples", new String[]{M_PDF}, Format.TXT, false, C_PDF),
            new Tool("HTML / Markdown → PDF", "Páginas e notas", WEB, Format.PDF, false, C_WEB),
            new Tool("EPUB → PDF", "eBooks", EBOOK, Format.PDF, false, C_TXT),
    };

    private final MainActivity act;
    private final LinearLayout col;
    private final LinearLayout sourceCard, sourceInfo, progressBox, resultBox, outputsList;
    private final FlowLayout targetChips;
    private final TextView convertBtn, progressText, chooseBtn, outputsEmpty;
    private final List<Converter.Source> sources = new ArrayList<Converter.Source>();
    private Format target;
    private Tool pendingTool;
    private Converter running;
    private File lastResult, saveAsFile;

    public ConvertPage(MainActivity a) {
        super(a);
        act = a;
        setBackgroundColor(Ui.BG);
        setFillViewport(true);
        col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 20), side, Ui.dp(a, 32));
        addView(col, new LayoutParams(-1, -2));

        TextView title = Ui.title(a, "Converter");
        title.setPadding(Ui.dp(a, 4), 0, 0, 0);
        col.addView(title);
        TextView subtitle = Ui.text(a, "Tudo acontece no seu aparelho, sem internet e sem cadastro.", 14, Ui.TEXT2, false);
        subtitle.setPadding(Ui.dp(a, 4), 0, 0, Ui.dp(a, 6));
        col.addView(subtitle);

        // cartão de conversão livre
        sourceCard = Ui.card(a);
        TextView ct = Ui.text(a, "Qualquer formato para qualquer formato", 16, Ui.TEXT, true);
        sourceCard.addView(ct);
        TextView cs = Ui.text(a, "PDF, Word, PowerPoint, Excel, OpenDocument, imagens, HTML, Markdown, TXT, CSV, RTF e EPUB.", 13.5f, Ui.TEXT2, false);
        cs.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 12));
        sourceCard.addView(cs);
        sourceInfo = new LinearLayout(a);
        sourceInfo.setOrientation(LinearLayout.VERTICAL);
        sourceCard.addView(sourceInfo);
        targetChips = new FlowLayout(a, Ui.dp(a, 8));
        targetChips.setVisibility(GONE);
        sourceCard.addView(targetChips, Ui.lpMargins(-1, -2, 0, Ui.dp(a, 4), 0, Ui.dp(a, 12)));
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        chooseBtn = Ui.button(a, "Escolher arquivo", false);
        chooseBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingTool = null;
                pick(new String[]{"*/*"}, true);
            }
        });
        btns.addView(chooseBtn, new LinearLayout.LayoutParams(0, -2, 1));
        convertBtn = Ui.button(a, "Converter", true);
        convertBtn.setVisibility(GONE);
        convertBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startConversion();
            }
        });
        btns.addView(convertBtn, Ui.lpMargins(0, -2, Ui.dp(a, 10), 0, 0, 0));
        ((LinearLayout.LayoutParams) convertBtn.getLayoutParams()).weight = 1;
        sourceCard.addView(btns);

        progressBox = new LinearLayout(a);
        progressBox.setOrientation(LinearLayout.HORIZONTAL);
        progressBox.setGravity(Gravity.CENTER_VERTICAL);
        progressBox.setPadding(0, Ui.dp(a, 14), 0, 0);
        progressBox.setVisibility(GONE);
        ProgressBar pb = new ProgressBar(a);
        progressBox.addView(pb, new LinearLayout.LayoutParams(Ui.dp(a, 28), Ui.dp(a, 28)));
        progressText = Ui.text(a, "", 14, Ui.TEXT, false);
        progressText.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
        progressBox.addView(progressText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView cancel = Ui.text(a, "Cancelar", 14, Ui.PRIMARY, true);
        cancel.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running != null) running.cancel();
            }
        });
        progressBox.addView(cancel);
        sourceCard.addView(progressBox);

        resultBox = new LinearLayout(a);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setVisibility(GONE);
        sourceCard.addView(resultBox);
        col.addView(sourceCard, Ui.lpMargins(-1, -2, 0, Ui.dp(a, 12), 0, 0));

        // atalhos
        col.addView(Ui.section(a, "Conversões rápidas"));
        LinearLayout row = null;
        for (int i = 0; i < tools.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                col.addView(row, Ui.lpMargins(-1, -2, 0, 0, 0, Ui.dp(a, 10)));
            }
            View card = toolCard(tools[i]);
            row.addView(card, Ui.lpMargins(0, -1, i % 2 == 1 ? Ui.dp(a, 10) : 0, 0, 0, 0));
            ((LinearLayout.LayoutParams) card.getLayoutParams()).weight = 1;
        }

        // arquivos convertidos
        col.addView(Ui.section(a, "Arquivos convertidos"));
        outputsList = new LinearLayout(a);
        outputsList.setOrientation(LinearLayout.VERTICAL);
        col.addView(outputsList);
        outputsEmpty = Ui.text(a, "Os arquivos que você converter aparecem aqui.", 14, Ui.TEXT2, false);
        outputsEmpty.setPadding(Ui.dp(a, 4), 0, 0, 0);
        col.addView(outputsEmpty);
        refreshOutputs();
    }

    private View toolCard(final Tool t) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        int r = Ui.dp(act, 16);
        c.setBackground(Ui.ripple(Ui.rounded(Ui.SURFACE, r), r));
        if (!Ui.dark) c.setElevation(Ui.dp(act, 1));
        c.setPadding(Ui.dp(act, 14), Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14));
        c.setClickable(true);
        LinearLayout badges = new LinearLayout(act);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        String[] parts = t.title.split(" → ");
        badges.addView(badge(parts[0], t.color));
        ImageView arrow = new ImageView(act);
        arrow.setImageDrawable(Ui.icon(act, R.drawable.ic_arrow, Ui.TEXT2));
        badges.addView(arrow, Ui.lpMargins(Ui.dp(act, 16), Ui.dp(act, 16), Ui.dp(act, 4), 0, Ui.dp(act, 4), 0));
        badges.addView(badge(parts.length > 1 ? parts[1] : "", colorFor(t.target)));
        c.addView(badges);
        TextView tt = Ui.text(act, t.title, 14.5f, Ui.TEXT, true);
        tt.setPadding(0, Ui.dp(act, 10), 0, 0);
        tt.setMaxLines(2);
        c.addView(tt);
        TextView ts = Ui.text(act, t.sub, 12.5f, Ui.TEXT2, false);
        ts.setSingleLine();
        ts.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(ts);
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
        return c;
    }

    private static int colorFor(Format f) {
        switch (f) {
            case PDF: return C_PDF;
            case DOCX: case ODT: case RTF: return C_WORD;
            case PPTX: return C_PPT;
            case XLSX: case ODS: case CSV: return C_XLS;
            case JPG: case PNG: case WEBP: return C_IMG;
            case HTML: case MD: return C_WEB;
            default: return C_TXT;
        }
    }

    private TextView badge(String label, int color) {
        String s = label;
        if (s.startsWith("PowerPoint")) s = "PPT";
        else if (s.startsWith("Word")) s = "DOC";
        else if (s.startsWith("Excel")) s = "XLS";
        else if (s.startsWith("Imagens")) s = "IMG";
        else if (s.startsWith("Texto")) s = "TXT";
        else if (s.startsWith("HTML")) s = "WEB";
        else if (s.length() > 4) s = s.substring(0, 4);
        TextView b = Ui.text(act, s.toUpperCase(), 11, 0xFFFFFFFF, true);
        b.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(color, Ui.dp(act, 8)));
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(act, 40), Ui.dp(act, 30)));
        return b;
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
            showTargets();
            startConversion();
        }
    }

    /** Arquivos vindos de outros apps (Compartilhar/Abrir com) ou do leitor. */
    void setExternalSources(List<Uri> uris, boolean fromApp) {
        pendingTool = null;
        setSources(uris);
        smoothScrollTo(0, 0);
    }

    private void setSources(List<Uri> uris) {
        sources.clear();
        resultBox.setVisibility(GONE);
        List<Converter.Source> list = new ArrayList<Converter.Source>();
        for (Uri u : uris) list.add(Converter.describe(act, u));
        boolean allImages = true;
        for (Converter.Source s : list) if (s.format == null || !s.format.isImage()) allImages = false;
        if (list.size() > 1 && !allImages) {
            Ui.toast(act, "Vários arquivos só podem ser combinados quando todos são imagens. Usando o primeiro.");
            sources.add(list.get(0));
        } else sources.addAll(list);
        Converter.Source first = sources.get(0);
        if (first.format == null || !first.format.canRead) {
            Ui.toast(act, "Formato não suportado: " + first.name);
            sources.clear();
            showSourceInfo();
            return;
        }
        target = first.format == Format.PDF ? Format.DOCX : Format.PDF;
        if (allImages && sources.size() > 0) target = Format.PDF;
        showSourceInfo();
        showTargets();
    }

    private void showSourceInfo() {
        sourceInfo.removeAllViews();
        if (sources.isEmpty()) {
            targetChips.setVisibility(GONE);
            convertBtn.setVisibility(GONE);
            chooseBtn.setText("Escolher arquivo");
            return;
        }
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(Ui.SURFACE2, Ui.dp(act, 12)));
        row.setPadding(Ui.dp(act, 10), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
        Converter.Source first = sources.get(0);
        row.addView(badge(first.format.ext, colorFor(first.format)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView n = Ui.text(act, sources.size() == 1 ? first.name : sources.size() + " imagens selecionadas", 14.5f, Ui.TEXT, true);
        n.setSingleLine();
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        long total = 0;
        for (Converter.Source s : sources) total += Math.max(0, s.size);
        t.addView(Ui.text(act, first.format.label + (total > 0 ? "  ·  " + Ui.size(total) : ""), 12.5f, Ui.TEXT2, false));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        sourceInfo.addView(row);
        TextView to = Ui.text(act, "Converter para", 13, Ui.TEXT2, true);
        to.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 2));
        sourceInfo.addView(to);
        chooseBtn.setText("Trocar arquivo");
        convertBtn.setVisibility(VISIBLE);
    }

    private void showTargets() {
        targetChips.removeAllViews();
        if (sources.isEmpty()) return;
        targetChips.setVisibility(VISIBLE);
        Format src = sources.get(0).format;
        for (final Format f : Format.targetsFor(src)) {
            TextView chip = Ui.text(act, f.label, 13.5f, Ui.TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0);
            chip.setMinHeight(Ui.dp(act, 36));
            int r = Ui.dp(act, 18);
            boolean sel = f == target;
            chip.setBackground(Ui.ripple(sel ? Ui.rounded(Ui.PRIMARY, r) : Ui.stroke(0, Ui.DIVIDER, Ui.dp(act, 1), r), r));
            chip.setTextColor(sel ? Ui.ON_PRIMARY : Ui.TEXT);
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    target = f;
                    showTargets();
                }
            });
            targetChips.addView(chip);
        }
        convertBtn.setText("Converter para " + target.label);
    }

    // ------------------------------------------------------------------ conversão

    private void startConversion() {
        if (sources.isEmpty() || target == null || running != null) return;
        final List<Converter.Source> src = new ArrayList<Converter.Source>(sources);
        final Format tgt = target;
        final Converter conv = new Converter(act);
        running = conv;
        resultBox.setVisibility(GONE);
        progressBox.setVisibility(VISIBLE);
        progressText.setText("Preparando…");
        convertBtn.setEnabled(false);
        convertBtn.setAlpha(0.5f);
        chooseBtn.setEnabled(false);
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
                                    progressText.setText(msg);
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
                        progressBox.setVisibility(GONE);
                        convertBtn.setEnabled(true);
                        convertBtn.setAlpha(1f);
                        chooseBtn.setEnabled(true);
                        if (err != null) {
                            new AlertDialog.Builder(act).setTitle("Não foi possível converter").setMessage(err)
                                    .setPositiveButton("OK", null).show();
                            return;
                        }
                        showResult(r);
                        refreshOutputs();
                    }
                });
            }
        }).start();
    }

    private void showResult(Converter.Result r) {
        lastResult = r.file;
        if (r.file.getName().toLowerCase().endsWith(".pdf")) {
            History.touch(act, Uri.fromFile(r.file).toString(), r.file.getName(), r.file.length());
        }
        resultBox.removeAllViews();
        resultBox.setVisibility(VISIBLE);
        resultBox.setPadding(0, Ui.dp(act, 16), 0, 0);
        resultBox.addView(Ui.divider(act));
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 12));
        ImageView ok = new ImageView(act);
        ok.setImageDrawable(Ui.icon(act, R.drawable.ic_check, 0xFFFFFFFF));
        ok.setBackground(Ui.rounded(0xFF1E8E3E, Ui.dp(act, 18)));
        int p = Ui.dp(act, 7);
        ok.setPadding(p, p, p, p);
        row.addView(ok, new LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 36)));
        LinearLayout t = new LinearLayout(act);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView n = Ui.text(act, r.file.getName(), 14.5f, Ui.TEXT, true);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.addView(n);
        String info = Ui.size(r.file.length());
        if (r.publicUri != null) info += "  ·  salvo em Downloads/PDF Leitor";
        t.addView(Ui.text(act, info, 12.5f, Ui.TEXT2, false));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        resultBox.addView(row);
        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final File f = r.file;
        TextView open = Ui.button(act, "Abrir", true);
        open.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Files.open(act, f);
            }
        });
        actions.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
        TextView share = Ui.button(act, "Enviar", false);
        share.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Files.share(act, Files.shareUri(act, f), Files.mimeOf(f.getName()), f.getName());
            }
        });
        actions.addView(share, Ui.lpMargins(0, -2, Ui.dp(act, 8), 0, 0, 0));
        ((LinearLayout.LayoutParams) share.getLayoutParams()).weight = 1;
        TextView save = Ui.button(act, "Salvar…", false);
        save.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAs(f);
            }
        });
        actions.addView(save, Ui.lpMargins(0, -2, Ui.dp(act, 8), 0, 0, 0));
        ((LinearLayout.LayoutParams) save.getLayoutParams()).weight = 1;
        resultBox.addView(actions);
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
            if (shown++ >= 40) break;
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int r = Ui.dp(act, 14);
            row.setBackground(Ui.ripple(Ui.rounded(Ui.SURFACE, r), r));
            row.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10));
            Format fmt = Format.fromName(f.getName(), null);
            row.addView(badge(Util.extension(f.getName()), fmt != null ? colorFor(fmt) : C_TXT));
            LinearLayout t = new LinearLayout(act);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(Ui.dp(act, 12), 0, 0, 0);
            TextView n = Ui.text(act, f.getName(), 14.5f, Ui.TEXT, true);
            n.setSingleLine();
            n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            t.addView(n);
            t.addView(Ui.text(act, Ui.size(f.length()) + "  ·  " + Ui.ago(f.lastModified()), 12.5f, Ui.TEXT2, false));
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            final ImageView more = Ui.iconButton(act, R.drawable.ic_more, Ui.TEXT2, "Opções");
            more.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    outputMenu(more, f);
                }
            });
            row.addView(more);
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Files.open(act, f);
                }
            });
            outputsList.addView(row, Ui.lpMargins(-1, -2, 0, 0, 0, Ui.dp(act, 8)));
        }
        outputsEmpty.setVisibility(shown == 0 ? VISIBLE : GONE);
    }

    private void outputMenu(View anchor, final File f) {
        Ui.menu(act, f.getName(), new String[]{"Abrir", "Compartilhar", "Salvar em…", "Converter novamente", "Excluir"}, new Ui.MenuCallback() {
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
                    new AlertDialog.Builder(act).setTitle("Excluir arquivo?").setMessage(f.getName())
                            .setPositiveButton("Excluir", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    History.remove(act, Uri.fromFile(f).toString());
                                    f.delete();
                                    refreshOutputs();
                                }
                            }).setNegativeButton("Cancelar", null).show();
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

    // ------------------------------------------------------------------ FlowLayout

    /** Layout que quebra linhas automaticamente (para os chips de formato). */
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
