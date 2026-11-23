package br.pdfleitor.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Histórico de PDFs abertos (guardado localmente, sem conta nem nuvem). */
public final class History {

    public static final class Entry {
        public String uri;
        public String name;
        public long size;
        public int pages;
        public int lastPage;
        public long opened;
        public boolean fav;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("uri", uri);
            o.put("name", name);
            o.put("size", size);
            o.put("pages", pages);
            o.put("last", lastPage);
            o.put("opened", opened);
            o.put("fav", fav);
            return o;
        }

        static Entry fromJson(JSONObject o) {
            Entry e = new Entry();
            e.uri = o.optString("uri");
            e.name = o.optString("name", "documento.pdf");
            e.size = o.optLong("size");
            e.pages = o.optInt("pages");
            e.lastPage = o.optInt("last");
            e.opened = o.optLong("opened");
            e.fav = o.optBoolean("fav");
            return e;
        }
    }

    private static final int MAX = 500;
    private static List<Entry> cache;

    private History() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    public static synchronized List<Entry> all(Context c) {
        if (cache == null) {
            cache = new ArrayList<Entry>();
            try {
                JSONArray a = new JSONArray(sp(c).getString("items", "[]"));
                for (int i = 0; i < a.length(); i++) cache.add(Entry.fromJson(a.getJSONObject(i)));
            } catch (Exception ignored) {
            }
        }
        List<Entry> copy = new ArrayList<Entry>(cache);
        Collections.sort(copy, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.opened, a.opened);
            }
        });
        return copy;
    }

    public static synchronized Entry find(Context c, String uri) {
        all(c);
        for (Entry e : cache) if (e.uri.equals(uri)) return e;
        return null;
    }

    /** Registra/atualiza a abertura de um PDF. */
    public static synchronized Entry touch(Context c, String uri, String name, long size) {
        all(c);
        Entry e = find(c, uri);
        if (e == null) {
            // mesmo arquivo com outro endereço (nome + tamanho iguais)
            for (Entry x : cache) {
                if (x.name.equals(name) && x.size == size && size > 0) {
                    x.uri = uri;
                    e = x;
                    break;
                }
            }
        }
        if (e == null) {
            e = new Entry();
            e.uri = uri;
            cache.add(e);
        }
        if (name != null) e.name = name;
        if (size > 0) e.size = size;
        e.opened = System.currentTimeMillis();
        trim();
        save(c);
        return e;
    }

    public static synchronized void update(Context c, Entry e) {
        save(c);
    }

    public static synchronized void remove(Context c, String uri) {
        all(c);
        for (int i = cache.size() - 1; i >= 0; i--) if (cache.get(i).uri.equals(uri)) cache.remove(i);
        save(c);
    }

    public static synchronized void clear(Context c) {
        all(c);
        cache.clear();
        save(c);
    }

    private static void trim() {
        if (cache.size() <= MAX) return;
        Collections.sort(cache, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.fav != b.fav) return a.fav ? -1 : 1;
                return Long.compare(b.opened, a.opened);
            }
        });
        while (cache.size() > MAX) cache.remove(cache.size() - 1);
    }

    private static void save(Context c) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : cache) a.put(e.toJson());
            sp(c).edit().putString("items", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
