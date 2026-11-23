package br.pdfleitor.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

import br.pdfleitor.core.Format;
import br.pdfleitor.core.Util;

/** Utilitários de arquivos: nomes, cópias, compartilhamento e pasta Downloads. */
public final class Files {

    public static final long MAX_INPUT = 300L * 1024 * 1024;

    private Files() {
    }

    public static String displayName(Context c, Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cur = null;
            try {
                cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cur != null && cur.moveToFirst() && !cur.isNull(0)) name = cur.getString(0);
            } catch (Exception ignored) {
            } finally {
                if (cur != null) cur.close();
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
            if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name == null || name.isEmpty() ? "arquivo" : name;
    }

    public static long size(Context c, Uri uri) {
        if ("file".equals(uri.getScheme())) return new File(uri.getPath()).length();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null);
            if (cur != null && cur.moveToFirst() && !cur.isNull(0)) return cur.getLong(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return -1;
    }

    public static byte[] read(Context c, Uri uri) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Não foi possível abrir o arquivo");
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_INPUT) throw new IOException("Arquivo muito grande (limite de 300 MB)");
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    public static void copy(Context c, Uri uri, File dest) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Não foi possível abrir o arquivo");
        OutputStream out = new FileOutputStream(dest);
        try {
            Util.copy(in, out);
        } finally {
            in.close();
            out.close();
        }
    }

    public static File libraryDir(Context c) {
        File d = new File(c.getFilesDir(), "biblioteca");
        d.mkdirs();
        return d;
    }

    public static File outputDir(Context c) {
        File base = c.getExternalFilesDir(null);
        File d = new File(base != null ? base : c.getFilesDir(), "Convertidos");
        d.mkdirs();
        return d;
    }

    public static File tempDir(Context c) {
        File d = new File(c.getCacheDir(), "tmp");
        d.mkdirs();
        return d;
    }

    public static File shareDir(Context c) {
        File d = new File(c.getCacheDir(), "share");
        d.mkdirs();
        return d;
    }

    /** Copia um PDF recebido de outro app para a biblioteca interna (acesso permanente). */
    public static File importToLibrary(Context c, Uri uri, String name) throws IOException {
        File tmp = new File(tempDir(c), "import_" + System.nanoTime());
        copy(c, uri, tmp);
        String hash = sha1(tmp);
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        File dir = new File(libraryDir(c), hash.substring(0, 12));
        dir.mkdirs();
        File dest = new File(dir, safe);
        if (dest.exists() && dest.length() == tmp.length()) {
            tmp.delete();
            return dest;
        }
        if (!tmp.renameTo(dest)) {
            copyFile(tmp, dest);
            tmp.delete();
        }
        return dest;
    }

    public static void copyFile(File a, File b) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        try {
            Util.copy(in, out);
        } finally {
            in.close();
            out.close();
        }
    }

    static String sha1(File f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            InputStream in = new FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Long.toHexString(f.length()) + Long.toHexString(System.nanoTime());
        }
    }

    public static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(s.getBytes("UTF-8"))) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    public static File uniqueFile(File dir, String base, String ext) {
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (base.isEmpty()) base = "documento";
        if (base.length() > 80) base = base.substring(0, 80);
        File f = new File(dir, base + "." + ext);
        int i = 2;
        while (f.exists()) f = new File(dir, base + " (" + (i++) + ")." + ext);
        return f;
    }

    public static String mimeOf(String name) {
        String ext = Util.extension(name);
        if (ext.equals("zip")) return "application/zip";
        Format f = Format.fromName(name, null);
        return f != null ? f.mime : "application/octet-stream";
    }

    // ------------------------------------------------------------------ provider

    /** URI compartilhável para arquivos internos do app. */
    public static Uri shareUri(Context c, File f) {
        return FilesProvider.uriFor(c, f);
    }

    /** URI utilizável para abrir/compartilhar, a partir do texto salvo no histórico. */
    public static Uri externalUri(Context c, String stored) {
        Uri u = Uri.parse(stored);
        if ("file".equals(u.getScheme())) return shareUri(c, new File(u.getPath()));
        return u;
    }

    public static void share(Activity a, Uri uri, String mime, String name) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, name);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, "Compartilhar"));
        } catch (ActivityNotFoundException e) {
            Ui.toast(a, "Nenhum app disponível para compartilhar.");
        }
    }

    /** Abre um arquivo: PDFs no próprio leitor, outros formatos em apps instalados. */
    public static void open(Activity a, File f) {
        String mime = mimeOf(f.getName());
        if (mime.equals("application/pdf")) {
            ViewerActivity.open(a, Uri.fromFile(f), f.getName());
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(shareUri(a, f), mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(a, "Nenhum app instalado abre arquivos ." + Util.extension(f.getName())
                    + ". Use \"Compartilhar\" ou \"Salvar\".");
        }
    }

    // ------------------------------------------------------------------ Downloads

    /** Copia para Downloads/PDF Leitor (Android 10+, sem permissões). */
    public static Uri saveToDownloads(Context c, File f) {
        if (Build.VERSION.SDK_INT < 29) return null;
        ContentResolver cr = c.getContentResolver();
        try {
            ContentValues v = new ContentValues();
            v.put("_display_name", f.getName());
            v.put("mime_type", mimeOf(f.getName()));
            v.put("relative_path", "Download/PDF Leitor");
            v.put("is_pending", 1);
            Uri uri = cr.insert(Uri.parse("content://media/external/downloads"), v);
            if (uri == null) return null;
            OutputStream out = cr.openOutputStream(uri);
            InputStream in = new FileInputStream(f);
            try {
                Util.copy(in, out);
            } finally {
                in.close();
                if (out != null) out.close();
            }
            ContentValues done = new ContentValues();
            done.put("is_pending", 0);
            cr.update(uri, done, null, null);
            return uri;
        } catch (Exception e) {
            return null;
        }
    }

    public static long dirSize(File d) {
        long s = 0;
        File[] list = d.listFiles();
        if (list == null) return 0;
        for (File f : list) s += f.isDirectory() ? dirSize(f) : f.length();
        return s;
    }

    public static void deleteRecursive(File d) {
        File[] list = d.listFiles();
        if (list != null) for (File f : list) {
            if (f.isDirectory()) deleteRecursive(f);
            f.delete();
        }
    }
}
