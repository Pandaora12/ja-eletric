package br.pdfleitor.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Provedor de arquivos mínimo (equivalente ao FileProvider) para compartilhar
 * arquivos internos com outros apps, somente leitura.
 */
public class FilesProvider extends ContentProvider {

    public static String authority(Context c) {
        return c.getPackageName() + ".files";
    }

    private static File[] roots(Context c) {
        return new File[]{Files.outputDir(c), Files.libraryDir(c), Files.shareDir(c), Files.tempDir(c)};
    }

    private static final String[] NAMES = {"out", "lib", "share", "tmp"};

    public static Uri uriFor(Context c, File f) {
        try {
            String path = f.getCanonicalPath();
            File[] roots = roots(c);
            for (int i = 0; i < roots.length; i++) {
                String root = roots[i].getCanonicalPath();
                if (path.startsWith(root + File.separator)) {
                    String rel = path.substring(root.length() + 1);
                    Uri.Builder b = new Uri.Builder().scheme("content").authority(authority(c)).appendPath(NAMES[i]);
                    for (String seg : rel.split(File.separator)) b.appendPath(seg);
                    return b.build();
                }
            }
        } catch (IOException ignored) {
        }
        throw new IllegalArgumentException("Arquivo fora das pastas compartilháveis: " + f);
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        java.util.List<String> segs = uri.getPathSegments();
        if (segs.size() < 2) throw new FileNotFoundException();
        File[] roots = roots(getContext());
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(segs.get(0))) {
                File f = roots[i];
                for (int k = 1; k < segs.size(); k++) f = new File(f, segs.get(k));
                try {
                    String canon = f.getCanonicalPath();
                    if (!canon.startsWith(roots[i].getCanonicalPath() + File.separator)) throw new FileNotFoundException();
                } catch (IOException e) {
                    throw new FileNotFoundException();
                }
                return f;
            }
        }
        throw new FileNotFoundException();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) throw new SecurityException("Somente leitura");
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (projection == null) projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        java.util.List<String> cols = new java.util.ArrayList<String>();
        java.util.List<Object> vals = new java.util.ArrayList<Object>();
        for (String p : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(p)) {
                cols.add(p);
                vals.add(f.getName());
            } else if (OpenableColumns.SIZE.equals(p)) {
                cols.add(p);
                vals.add(f.length());
            }
        }
        MatrixCursor c = new MatrixCursor(cols.toArray(new String[0]), 1);
        c.addRow(vals.toArray());
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String last = uri.getLastPathSegment();
        return Files.mimeOf(last == null ? "" : last);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
