package br.pdfleitor.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Aba "PDFs": todos os PDFs já abertos, com busca e favoritos. */
public class PdfListPage extends FrameLayout {

    private final MainActivity act;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private final List<History.Entry> items = new ArrayList<History.Entry>();
    private final TextView countView;
    private final LinearLayout empty;
    private final TextView chipAll, chipFav;
    private boolean favOnly;
    private String filter = "";

    public PdfListPage(MainActivity a) {
        super(a);
        act = a;
        setBackgroundColor(Ui.BG);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(a, 16);

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(side + Ui.dp(a, 4), Ui.dp(a, 20), side, Ui.dp(a, 8));
        header.addView(Ui.title(a, "Meus PDFs"));
        countView = Ui.text(a, "", 14, Ui.TEXT2, false);
        header.addView(countView);
        col.addView(header);

        // busca
        LinearLayout search = new LinearLayout(a);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.rounded(Ui.SURFACE2, Ui.dp(a, 24)));
        search.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 8), 0);
        ImageView si = new ImageView(a);
        si.setImageDrawable(Ui.icon(a, R.drawable.ic_search, Ui.TEXT2));
        search.addView(si, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
        EditText et = new EditText(a);
        et.setHint("Buscar PDFs");
        et.setHintTextColor(Ui.TEXT2);
        et.setTextColor(Ui.TEXT);
        et.setBackground(null);
        et.setSingleLine();
        et.setTextSize(15);
        et.setPadding(Ui.dp(a, 10), 0, 0, 0);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                filter = s.toString().trim();
                refresh();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        search.addView(et, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
        col.addView(search, Ui.lpMargins(-1, -2, side, Ui.dp(a, 4), side, 0));

        // filtros
        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(side, Ui.dp(a, 12), side, Ui.dp(a, 4));
        chipAll = chip("Recentes");
        chipFav = chip("Favoritos");
        chipAll.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                favOnly = false;
                refresh();
            }
        });
        chipFav.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                favOnly = true;
                refresh();
            }
        });
        chips.addView(chipAll);
        chips.addView(chipFav, Ui.lpMargins(-2, -2, Ui.dp(a, 8), 0, 0, 0));
        col.addView(chips);

        list = new ListView(a);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0));
        list.setDividerHeight(Ui.dp(a, 8));
        list.setClipToPadding(false);
        list.setPadding(side - Ui.dp(a, 4), Ui.dp(a, 4), side - Ui.dp(a, 4), Ui.dp(a, 96));
        list.setSelector(android.R.color.transparent);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                openEntry(items.get(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                menu(v, items.get(pos));
                return true;
            }
        });
        FrameLayout listWrap = new FrameLayout(a);
        listWrap.addView(list, new FrameLayout.LayoutParams(-1, -1));

        empty = new LinearLayout(a);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(a, 40), 0, Ui.dp(a, 40), Ui.dp(a, 60));
        ImageView ei = new ImageView(a);
        ei.setImageDrawable(Ui.icon(a, R.drawable.ic_doc, Ui.PRIMARY));
        ei.setBackground(Ui.rounded(Ui.PRIMARY_SOFT, Ui.dp(a, 40)));
        int ep = Ui.dp(a, 22);
        ei.setPadding(ep, ep, ep, ep);
        empty.addView(ei, new LinearLayout.LayoutParams(Ui.dp(a, 80), Ui.dp(a, 80)));
        TextView et1 = Ui.text(a, "Nenhum PDF por aqui", 18, Ui.TEXT, true);
        et1.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 6));
        empty.addView(et1);
        TextView et2 = Ui.text(a, "Toque em \"Abrir PDF\" para escolher um arquivo. Tudo o que você abrir aparece nesta lista.", 14, Ui.TEXT2, false);
        et2.setGravity(Gravity.CENTER);
        empty.addView(et2);
        listWrap.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(listWrap, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(col, new LayoutParams(-1, -1));

        // botão flutuante
        LinearLayout fab = new LinearLayout(a);
        fab.setOrientation(LinearLayout.HORIZONTAL);
        fab.setGravity(Gravity.CENTER_VERTICAL);
        int r = Ui.dp(a, 18);
        fab.setBackground(Ui.ripple(Ui.rounded(Ui.PRIMARY, r), r));
        fab.setElevation(Ui.dp(a, 6));
        fab.setPadding(Ui.dp(a, 18), 0, Ui.dp(a, 22), 0);
        fab.setClickable(true);
        ImageView fi = new ImageView(a);
        fi.setImageDrawable(Ui.icon(a, R.drawable.ic_add, Ui.ON_PRIMARY));
        fab.addView(fi, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
        TextView ft = Ui.text(a, "Abrir PDF", 15, Ui.ON_PRIMARY, true);
        ft.setPadding(Ui.dp(a, 10), 0, 0, 0);
        fab.addView(ft);
        fab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pick();
            }
        });
        LayoutParams flp = new LayoutParams(-2, Ui.dp(a, 56), Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, Ui.dp(a, 18), Ui.dp(a, 18));
        addView(fab, flp);
        refresh();
    }

    private TextView chip(String label) {
        TextView t = Ui.text(act, label, 14, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(act, 16), 0, Ui.dp(act, 16), 0);
        t.setMinHeight(Ui.dp(act, 36));
        t.setClickable(true);
        return t;
    }

    private void styleChip(TextView t, boolean sel) {
        int r = Ui.dp(act, 18);
        t.setBackground(Ui.ripple(sel ? Ui.rounded(Ui.PRIMARY_SOFT, r) : Ui.stroke(0, Ui.DIVIDER, Ui.dp(act, 1), r), r));
        t.setTextColor(sel ? Ui.PRIMARY : Ui.TEXT2);
    }

    public void refresh() {
        items.clear();
        List<History.Entry> all = History.all(act);
        String f = ViewerActivity.fold(filter);
        int favs = 0;
        for (History.Entry e : all) {
            if (e.fav) favs++;
            if (favOnly && !e.fav) continue;
            if (f.length() > 0 && !ViewerActivity.fold(e.name).contains(f)) continue;
            items.add(e);
        }
        styleChip(chipAll, !favOnly);
        styleChip(chipFav, favOnly);
        chipFav.setText(favs > 0 ? "Favoritos · " + favs : "Favoritos");
        countView.setText(all.isEmpty() ? "Leia e organize seus documentos" : all.size() + (all.size() == 1 ? " documento" : " documentos"));
        empty.setVisibility(items.isEmpty() ? VISIBLE : GONE);
        if (items.isEmpty() && !all.isEmpty()) {
            ((TextView) empty.getChildAt(1)).setText(favOnly ? "Nenhum favorito" : "Nada encontrado");
            ((TextView) empty.getChildAt(2)).setText(favOnly ? "Toque na estrela de um PDF para marcá-lo como favorito." : "Tente outra busca.");
        } else if (all.isEmpty()) {
            ((TextView) empty.getChildAt(1)).setText("Nenhum PDF por aqui");
            ((TextView) empty.getChildAt(2)).setText("Toque em \"Abrir PDF\" para escolher um arquivo. Tudo o que você abrir aparece nesta lista.");
        }
        adapter.notifyDataSetChanged();
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
        } else {
            for (Uri u : uris) History.touch(act, u.toString(), Files.displayName(act, u), Files.size(act, u));
            Ui.toast(act, uris.size() + " PDFs adicionados à lista");
            refresh();
        }
    }

    private void openEntry(History.Entry e) {
        ViewerActivity.open(act, Uri.parse(e.uri), e.name);
    }

    private void menu(View anchor, final History.Entry e) {
        Ui.menu(act, e.name, new String[]{"Abrir", e.fav ? "Remover dos favoritos" : "Adicionar aos favoritos", "Compartilhar", "Converter", "Remover da lista"}, new Ui.MenuCallback() {
            @Override
            public void onItem(int id) {
            switch (id) {
                case 1:
                    openEntry(e);
                    break;
                case 2:
                    e.fav = !e.fav;
                    History.update(act, e);
                    refresh();
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
        new AlertDialog.Builder(act).setTitle("Remover da lista?")
                .setMessage("\"" + e.name + "\" sai da lista. O arquivo original não é apagado.")
                .setPositiveButton("Remover", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        History.remove(act, e.uri);
                        Uri u = Uri.parse(e.uri);
                        // cópias internas (PDFs recebidos de outros apps) são apagadas
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
                        refresh();
                    }
                }).setNegativeButton("Cancelar", null).show();
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
        final ImageView thumb, star, more;
        final TextView name, meta, sub;
        History.Entry entry;

        Row() {
            super(act);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int r = Ui.dp(act, 16);
            LinearLayout inner = this;
            inner.setBackground(Ui.ripple(Ui.rounded(Ui.SURFACE, r), r));
            setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10));
            AbsLp lp = new AbsLp();
            setLayoutParams(lp);

            FrameLayout tf = new FrameLayout(act);
            tf.setBackground(Ui.stroke(Ui.dark ? 0xFF2A2B2F : 0xFFF1F2F4, Ui.DIVIDER, Ui.dp(act, 1), Ui.dp(act, 6)));
            tf.setPadding(1, 1, 1, 1);
            ImageView ph = new ImageView(act);
            ph.setImageDrawable(Ui.icon(act, R.drawable.ic_doc, Ui.PRIMARY));
            int pp = Ui.dp(act, 14);
            ph.setPadding(pp, pp, pp, pp);
            tf.addView(ph, new FrameLayout.LayoutParams(-1, -1));
            thumb = new ImageView(act);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tf.addView(thumb, new FrameLayout.LayoutParams(-1, -1));
            tf.setClipToOutline(true);
            addView(tf, new LayoutParams(Ui.dp(act, 50), Ui.dp(act, 66)));

            LinearLayout texts = new LinearLayout(act);
            texts.setOrientation(VERTICAL);
            texts.setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 4), 0);
            name = Ui.text(act, "", 15.5f, Ui.TEXT, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            meta = Ui.text(act, "", 13, Ui.TEXT2, false);
            meta.setPadding(0, Ui.dp(act, 3), 0, 0);
            sub = Ui.text(act, "", 12.5f, Ui.TEXT2, false);
            texts.addView(name);
            texts.addView(meta);
            texts.addView(sub);
            addView(texts, new LayoutParams(0, -2, 1));

            star = Ui.iconButton(act, R.drawable.ic_star_border, Ui.TEXT2, "Favorito");
            star.setFocusable(false);
            star.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    entry.fav = !entry.fav;
                    History.update(act, entry);
                    refresh();
                }
            });
            addView(star);
            more = Ui.iconButton(act, R.drawable.ic_more, Ui.TEXT2, "Opções");
            more.setFocusable(false);
            more.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    menu(more, entry);
                }
            });
            addView(more);
        }

        void bind(History.Entry e) {
            entry = e;
            name.setText(e.name);
            StringBuilder m = new StringBuilder();
            if (e.pages > 0) m.append(e.pages).append(e.pages == 1 ? " página" : " páginas");
            if (e.size > 0) m.append(m.length() > 0 ? "  ·  " : "").append(Ui.size(e.size));
            meta.setText(m.length() > 0 ? m : "PDF");
            String s = "Aberto " + Ui.ago(e.opened);
            if (e.pages > 1 && e.lastPage > 0) s += "  ·  parou na pág. " + (e.lastPage + 1);
            sub.setText(s);
            star.setImageDrawable(Ui.icon(act, e.fav ? R.drawable.ic_star : R.drawable.ic_star_border, e.fav ? 0xFFF4B400 : Ui.TEXT2));
            Thumbs.load(act, e.uri, thumb, Ui.dp(act, 50), new Thumbs.PagesCallback() {
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

    /** Espaçamento entre itens da lista. */
    private class AbsLp extends android.widget.AbsListView.LayoutParams {
        AbsLp() {
            super(-1, -2);
        }
    }
}
