package br.pdfleitor.app;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Aba "PDFs": biblioteca de PDFs já abertos. */
public class PdfListPage extends FrameLayout {

    private final MainActivity act;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final List<History.Entry> items = new ArrayList<History.Entry>();
    private final TextView statDocs, statPages, statFavs;
    private final LinearLayout empty;
    private final TextView emptyTitle, emptyText, emptyBtn;
    private final Widgets.Segmented seg;
    private final ImageView clearSearch;
    private final EditText search;
    private boolean favOnly;
    private String filter = "";

    public PdfListPage(MainActivity a) {
        super(a);
        act = a;
        setBackgroundColor(Ui.BG);
        int side = Ui.dp(a, 20);

        // ---------------------------------------------------------- cabeçalho
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(side, Ui.dp(a, 28), side, Ui.dp(a, 8));
        header.setLayoutParams(new AbsListView.LayoutParams(-1, -2));

        FrameLayout top = new FrameLayout(a);
        top.addView(Ui.header(a, "01 — Biblioteca", "Seus\n*PDFs.*", null));
        ImageView add = Ui.circleButton(a, R.drawable.ic_add, Ui.INK, Ui.BG, 52, "Abrir PDF");
        add.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pick();
            }
        });
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52), Gravity.END | Gravity.BOTTOM);
        alp.bottomMargin = Ui.dp(a, 6);
        top.addView(add, alp);
        header.addView(top);

        // estatísticas
        LinearLayout stats = new LinearLayout(a);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(0, Ui.dp(a, 26), 0, Ui.dp(a, 22));
        statDocs = stat(stats, "Documentos", true);
        statPages = stat(stats, "Páginas", false);
        statFavs = stat(stats, "Favoritos", false);
        header.addView(stats);
        header.addView(Ui.divider(a));

        // busca
        LinearLayout sbox = new LinearLayout(a);
        sbox.setOrientation(LinearLayout.HORIZONTAL);
        sbox.setGravity(Gravity.CENTER_VERTICAL);
        sbox.setBackground(Ui.stroke(Ui.CARD, Ui.LINE, Math.max(1, Ui.dp(a, 0.8f)), Ui.dp(a, 22)));
        sbox.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 6), 0);
        ImageView si = new ImageView(a);
        si.setImageDrawable(Ui.icon(a, R.drawable.ic_search, Ui.INK2));
        sbox.addView(si, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
        search = new EditText(a);
        search.setHint("Buscar na biblioteca");
        search.setHintTextColor(Ui.INK3);
        search.setTextColor(Ui.INK);
        search.setTypeface(Fonts.sans);
        search.setBackground(null);
        search.setSingleLine();
        search.setTextSize(15.5f);
        search.setPadding(Ui.dp(a, 12), 0, 0, 0);
        clearSearch = Ui.iconButton(a, R.drawable.ic_close, Ui.INK2, "Limpar busca");
        clearSearch.setVisibility(GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filter = s.toString().trim();
                clearSearch.setVisibility(filter.isEmpty() ? GONE : VISIBLE);
                refresh(false);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        sbox.addView(search, new LinearLayout.LayoutParams(0, Ui.dp(a, 54), 1));
        clearSearch.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                search.setText("");
            }
        });
        sbox.addView(clearSearch);
        header.addView(sbox, Ui.lpMargins(-1, -2, 0, Ui.dp(a, 22), 0, 0));

        seg = new Widgets.Segmented(a, new String[]{"Recentes", "Favoritos"}, 0);
        seg.setListener(new Widgets.Segmented.Listener() {
            @Override
            public void onChange(int index) {
                favOnly = index == 1;
                refresh(true);
            }
        });
        header.addView(seg, Ui.lpMargins(-1, -2, 0, Ui.dp(a, 12), 0, Ui.dp(a, 6)));

        // ---------------------------------------------------------- vazio
        empty = new LinearLayout(a);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(side, Ui.dp(a, 8), side, Ui.dp(a, 40));
        empty.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
        empty.addView(new Widgets.PaperArt(a), new LinearLayout.LayoutParams(-1, Ui.dp(a, 220)));
        emptyTitle = Ui.display(a, "", 34);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, Ui.lpMargins(-2, -2, 0, Ui.dp(a, 8), 0, 0));
        emptyText = Ui.text(a, "", 15, Ui.INK2, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), 0);
        empty.addView(emptyText);
        emptyBtn = Ui.button(a, "Abrir um PDF", true, true);
        emptyBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pick();
            }
        });
        empty.addView(emptyBtn, Ui.lpMargins(-2, -2, 0, Ui.dp(a, 24), 0, 0));

        // ---------------------------------------------------------- lista
        list = new ListView(a);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0));
        list.setDividerHeight(Ui.dp(a, 10));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(a, 120));
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.addHeaderView(header, null, false);
        list.addFooterView(empty, null, false);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                int i = pos - list.getHeaderViewsCount();
                if (i >= 0 && i < items.size()) openEntry(items.get(i));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                int i = pos - list.getHeaderViewsCount();
                if (i >= 0 && i < items.size()) menu(items.get(i));
                return true;
            }
        });
        addView(list, new LayoutParams(-1, -1));

        refresh(false);
    }

    private TextView stat(LinearLayout parent, String label, boolean first) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        if (!first) c.setPadding(Ui.dp(act, 16), 0, 0, 0);
        TextView n = Ui.display(act, "0", 40);
        c.addView(n);
        TextView l = Ui.label(act, label, Ui.INK2);
        l.setPadding(0, Ui.dp(act, 8), 0, 0);
        c.addView(l);
        parent.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
        return n;
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(boolean animate) {
        items.clear();
        List<History.Entry> all = History.all(act);
        String f = ViewerActivity.fold(filter);
        int favs = 0, pages = 0;
        for (History.Entry e : all) {
            if (e.fav) favs++;
            pages += Math.max(0, e.pages);
            if (favOnly && !e.fav) continue;
            if (f.length() > 0 && !ViewerActivity.fold(e.name).contains(f)) continue;
            items.add(e);
        }
        if (animate) {
            Ui.countUp(statDocs, all.size());
            Ui.countUp(statPages, pages);
            Ui.countUp(statFavs, favs);
        } else {
            statDocs.setText(String.valueOf(all.size()));
            statPages.setText(String.valueOf(pages));
            statFavs.setText(String.valueOf(favs));
        }
        boolean isEmpty = items.isEmpty();
        for (int i = 0; i < empty.getChildCount(); i++) empty.getChildAt(i).setVisibility(isEmpty ? VISIBLE : GONE);
        empty.setPadding(empty.getPaddingLeft(), isEmpty ? Ui.dp(act, 8) : 0, empty.getPaddingRight(), isEmpty ? Ui.dp(act, 40) : 0);
        if (all.isEmpty()) {
            emptyTitle.setText(Ui.rich("Sua estante\nestá *vazia.*"));
            emptyText.setText("Abra um PDF e ele fica guardado aqui, com a página onde você parou. Nada sai do seu aparelho.");
        } else if (favOnly && filter.isEmpty()) {
            emptyTitle.setText(Ui.rich("Nenhum\n*favorito* ainda."));
            emptyText.setText("Toque na estrela de um documento para mantê-lo sempre à mão.");
        } else {
            emptyTitle.setText(Ui.rich("Nada *encontrado.*"));
            emptyText.setText("Nenhum documento corresponde a \"" + filter + "\".");
        }
        emptyBtn.setVisibility(isEmpty && all.isEmpty() ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
        if (animate) {
            list.post(new Runnable() {
                @Override
                public void run() {
                    int n = 0;
                    for (int i = 0; i < list.getChildCount(); i++) {
                        View v = list.getChildAt(i);
                        if (!(v instanceof Row)) continue;
                        v.setAlpha(0f);
                        v.setTranslationY(Ui.dp(act, 28));
                        v.animate().alpha(1f).translationY(0).setStartDelay(60 + n * 60L).setDuration(560).setInterpolator(Ui.EASE).start();
                        n++;
                    }
                }
            });
        }
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            act.startActivityForResult(i, MainActivity.REQ_OPEN_PDF);
        } catch (Exception e) {
            Ui.toast(act, "Não há gerenciador de arquivos disponível.");
        }
    }

    void onPicked(Intent data) {
        List<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        for (Uri u : uris) {
            try {
                act.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
        if (uris.size() == 1) {
            ViewerActivity.open(act, uris.get(0), Files.displayName(act, uris.get(0)));
        } else if (uris.size() > 1) {
            for (Uri u : uris) History.touch(act, u.toString(), Files.displayName(act, u), Files.size(act, u));
            Ui.toast(act, uris.size() + " PDFs adicionados à biblioteca");
            refresh(true);
        }
    }

    private void openEntry(History.Entry e) {
        ViewerActivity.open(act, Uri.parse(e.uri), e.name);
    }

    private void menu(final History.Entry e) {
        Ui.menu(act, e.name.replaceAll("(?i)\\.pdf$", ""), new String[]{"Abrir", e.fav ? "Tirar dos favoritos" : "Adicionar aos favoritos",
                "Compartilhar", "Converter", "Remover da biblioteca"}, new Ui.MenuCallback() {
            @Override
            public void onItem(int id) {
                switch (id) {
                    case 1:
                        openEntry(e);
                        break;
                    case 2:
                        e.fav = !e.fav;
                        History.update(act, e);
                        refresh(false);
                        break;
                    case 3:
                        try {
                            Files.share(act, Files.externalUri(act, e.uri), "application/pdf", e.name);
                        } catch (Exception ex) {
                            Ui.toast(act, "Não foi possível compartilhar este arquivo.");
                        }
                        break;
                    case 4: {
                        List<Uri> l = new ArrayList<Uri>();
                        l.add(Uri.parse(e.uri));
                        act.select(1);
                        act.convertPage.setExternalSources(l, true);
                        break;
                    }
                    case 5:
                        confirmRemove(e);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void confirmRemove(final History.Entry e) {
        Sheet.confirm(act, "Remover da\n*biblioteca?*", "\"" + e.name + "\" sai da lista. O arquivo original continua no aparelho.",
                "Remover", true, new Runnable() {
                    @Override
                    public void run() {
                        History.remove(act, e.uri);
                        Uri u = Uri.parse(e.uri);
                        if ("file".equals(u.getScheme()) && u.getPath() != null
                                && u.getPath().startsWith(Files.libraryDir(act).getPath())) {
                            File f = new File(u.getPath());
                            f.delete();
                            File parent = f.getParentFile();
                            if (parent != null) parent.delete();
                        } else {
                            try {
                                act.getContentResolver().releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {
                            }
                        }
                        refresh(false);
                        Ui.toast(act, "Removido da biblioteca");
                    }
                });
    }

    // ------------------------------------------------------------------ lista

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int p) {
            return items.get(p);
        }

        @Override
        public long getItemId(int p) {
            return p;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Row row = convert instanceof Row ? (Row) convert : new Row();
            row.bind(items.get(pos));
            return row;
        }
    }

    private class Row extends LinearLayout {
        final ImageView thumb, star;
        final TextView name, meta, pct;
        final Widgets.ProgressLine progress;
        final LinearLayout progRow;
        History.Entry entry;

        Row() {
            super(act);
            setOrientation(VERTICAL);
            int side = Ui.dp(act, 20);
            setPadding(side, 0, side, 0);
            setLayoutParams(new AbsListView.LayoutParams(-1, -2));

            LinearLayout card = new LinearLayout(act);
            card.setOrientation(HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            int r = Ui.dp(act, 24);
            card.setBackground(Ui.ripple(Ui.stroke(Ui.CARD, Ui.LINE, Math.max(1, Ui.dp(act, 0.8f)), r), r));
            card.setPadding(Ui.dp(act, 12), Ui.dp(act, 12), Ui.dp(act, 6), Ui.dp(act, 12));
            card.setDuplicateParentStateEnabled(true);
            addView(card, new LayoutParams(-1, -2));

            FrameLayout tf = new FrameLayout(act);
            tf.setBackground(Ui.stroke(Ui.CARD2, Ui.LINE, Math.max(1, Ui.dp(act, 0.8f)), Ui.dp(act, 10)));
            Widgets.FileTag ph = new Widgets.FileTag(act, "pdf");
            tf.addView(ph, new FrameLayout.LayoutParams(Ui.dp(act, 26), Ui.dp(act, 32), Gravity.CENTER));
            thumb = new ImageView(act);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tf.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
            tf.setClipToOutline(true);
            card.addView(tf, new LayoutParams(Ui.dp(act, 60), Ui.dp(act, 80)));

            LinearLayout texts = new LinearLayout(act);
            texts.setOrientation(VERTICAL);
            texts.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 6), 0);
            name = Ui.text(act, "", 16.5f, Ui.INK, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            meta = Ui.label(act, "", Ui.INK2);
            meta.setPadding(0, Ui.dp(act, 9), 0, 0);
            texts.addView(meta);
            progRow = new LinearLayout(act);
            progRow.setOrientation(HORIZONTAL);
            progRow.setGravity(Gravity.CENTER_VERTICAL);
            progRow.setPadding(0, Ui.dp(act, 12), 0, 0);
            progress = new Widgets.ProgressLine(act);
            progRow.addView(progress, new LayoutParams(0, Ui.dp(act, 3), 1));
            pct = Ui.label(act, "", Ui.INK2);
            pct.setPadding(Ui.dp(act, 10), 0, 0, 0);
            progRow.addView(pct);
            texts.addView(progRow);
            card.addView(texts, new LayoutParams(0, -2, 1));

            LinearLayout side2 = new LinearLayout(act);
            side2.setOrientation(VERTICAL);
            star = Ui.iconButton(act, R.drawable.ic_star_border, Ui.INK3, "Favorito");
            star.setFocusable(false);
            star.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    entry.fav = !entry.fav;
                    History.update(act, entry);
                    star.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            star.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Ui.EASE).start();
                        }
                    }).start();
                    refresh(false);
                }
            });
            side2.addView(star);
            ImageView more = Ui.iconButton(act, R.drawable.ic_more, Ui.INK3, "Opções");
            more.setFocusable(false);
            more.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    menu(entry);
                }
            });
            side2.addView(more);
            card.addView(side2);
        }

        void bind(History.Entry e) {
            entry = e;
            name.setText(e.name.replaceAll("(?i)\\.pdf$", ""));
            StringBuilder m = new StringBuilder();
            if (e.pages > 0) m.append(e.pages).append(e.pages == 1 ? " pág" : " págs");
            if (e.size > 0) m.append(m.length() > 0 ? "  ·  " : "").append(Ui.size(e.size));
            m.append(m.length() > 0 ? "  ·  " : "").append(Ui.ago(e.opened));
            meta.setText(m.toString().toUpperCase());
            if (e.pages > 1) {
                progRow.setVisibility(VISIBLE);
                float f = (e.lastPage + 1) / (float) e.pages;
                progress.set(f);
                pct.setText(Math.round(f * 100) + "%");
            } else progRow.setVisibility(GONE);
            star.setImageDrawable(Ui.icon(act, e.fav ? R.drawable.ic_star : R.drawable.ic_star_border, e.fav ? Ui.ACCENT : Ui.INK3));
            Thumbs.load(act, e.uri, thumb, Ui.dp(act, 60), new Thumbs.PagesCallback() {
                @Override
                public void onPages(String uri, int pages) {
                    History.Entry he = History.find(act, uri);
                    if (he != null && he.pages != pages) {
                        he.pages = pages;
                        History.update(act, he);
                        adapter.notifyDataSetChanged();
                    }
                }
            });
        }
    }
}
