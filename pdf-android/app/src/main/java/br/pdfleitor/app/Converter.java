package br.pdfleitor.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import br.pdfleitor.core.Doc;
import br.pdfleitor.core.Engine;
import br.pdfleitor.core.Format;
import br.pdfleitor.core.PdfText;
import br.pdfleitor.core.Util;

/** Executa conversões entre formatos, 100% no aparelho. */
public class Converter {

    public static class Source {
        public Uri uri;
        public String name;
        public long size;
        public Format format;
    }

    public static class Result {
        public File file;
        public Uri publicUri;
        public int pages;
    }

    public interface Listener {
        void progress(String msg);
    }

    private final Context ctx;
    private Listener listener;
    private volatile boolean cancelled;

    public Converter(Context c) {
        this.ctx = c.getApplicationContext();
    }

    public void cancel() {
        cancelled = true;
    }

    private void step(String msg) throws InterruptedException {
        if (cancelled) throw new InterruptedException("Conversão cancelada");
        if (listener != null) listener.progress(msg);
    }

    public static Source describe(Context c, Uri uri) {
        Source s = new Source();
        s.uri = uri;
        s.name = Files.displayName(c, uri);
        s.size = Files.size(c, uri);
        String mime = null;
        try {
            mime = c.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        s.format = Format.fromName(s.name, mime);
        return s;
    }

    public Result convert(List<Source> sources, Format target, Listener l) throws Exception {
        this.listener = l;
        if (sources.isEmpty()) throw new IllegalArgumentException("Nenhum arquivo selecionado");
        boolean allImages = true;
        for (Source s : sources) if (s.format == null || !s.format.isImage()) allImages = false;
        Source first = sources.get(0);
        String base = Util.baseName(first.name);
        Result res;
        if (allImages) {
            res = fromImages(sources, target, base);
        } else {
            step("Lendo " + first.name + "…");
            byte[] data = Files.read(ctx, first.uri);
            Format src = Format.sniff(data, first.format);
            if (Format.isLegacyOffice(data)) {
                throw new UnsupportedOperationException("Arquivos do Office 97-2003 (.doc, .xls, .ppt) usam um formato binário antigo "
                        + "que não é suportado. Abra no Office/WPS e salve como .docx, .xlsx ou .pptx.");
            }
            if (src == null) throw new UnsupportedOperationException("Não foi possível reconhecer o formato de \"" + first.name + "\".");
            if (src == Format.PDF) res = fromPdf(data, target, base);
            else res = fromDocument(data, src, first.name, target, base);
        }
        if (Prefs.saveToDownloads(ctx) && res.file != null) {
            step("Salvando em Downloads…");
            res.publicUri = Files.saveToDownloads(ctx, res.file);
        }
        return res;
    }

    // ------------------------------------------------------------------ imagens

    private Result fromImages(List<Source> sources, Format target, String base) throws Exception {
        if (target == Format.TXT || target == Format.CSV || target == Format.XLSX || target == Format.ODS) {
            throw new UnsupportedOperationException("Imagens não contêm texto editável. Escolha PDF, Word, PowerPoint ou outro formato de documento.");
        }
        Result res = new Result();
        if (target.isImage()) {
            List<File> outs = new ArrayList<File>();
            for (int i = 0; i < sources.size(); i++) {
                Source s = sources.get(i);
                step("Convertendo imagem " + (i + 1) + " de " + sources.size() + "…");
                byte[] d = Files.read(ctx, s.uri);
                Bitmap bmp = PdfDocWriter.decode(d, 4096);
                if (bmp == null) throw new IOException("Imagem inválida: " + s.name);
                File f = Files.uniqueFile(sources.size() == 1 ? Files.outputDir(ctx) : Files.tempDir(ctx), Util.baseName(s.name), target.ext);
                saveBitmap(bmp, target, f);
                bmp.recycle();
                outs.add(f);
            }
            res.file = outs.size() == 1 ? outs.get(0) : zip(outs, base + "_imagens");
            res.pages = outs.size();
            return res;
        }
        Doc doc = new Doc();
        doc.title = base;
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            step("Lendo imagem " + (i + 1) + " de " + sources.size() + "…");
            byte[] d = Files.read(ctx, s.uri);
            Doc.Image img = normalizedImage(d, 2400);
            if (img == null) throw new IOException("Imagem inválida: " + s.name);
            img.fullPage = true;
            if (i > 0) doc.add(new Doc.PageBreak());
            doc.add(img);
        }
        res.pages = sources.size();
        res.file = writeDoc(doc, target, sources.size() > 1 ? base + "_e_outras" : base);
        return res;
    }

    /** Decodifica e recomprime como JPEG/PNG compatível com todos os formatos. */
    private Doc.Image normalizedImage(byte[] d, int maxSide) {
        String mime = Util.imageMime(d);
        int[] sz = Util.imageSize(d);
        boolean ok = (mime.equals("image/png") || mime.equals("image/jpeg")) && sz != null && sz[0] <= maxSide && sz[1] <= maxSide;
        if (ok) return Util.image(d);
        Bitmap bmp = PdfDocWriter.decode(d, maxSide);
        if (bmp == null) return null;
        try {
            Bitmap.CompressFormat cf = bmp.hasAlpha() ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(cf, 88, bos);
            Doc.Image img = new Doc.Image(bos.toByteArray(), cf == Bitmap.CompressFormat.PNG ? "image/png" : "image/jpeg",
                    bmp.getWidth(), bmp.getHeight());
            return img;
        } finally {
            bmp.recycle();
        }
    }

    private void normalizeImages(Doc doc) {
        for (int i = 0; i < doc.blocks.size(); i++) {
            Doc.Block b = doc.blocks.get(i);
            if (b instanceof Doc.Image) {
                Doc.Image img = (Doc.Image) b;
                Doc.Image n = normalizedImage(img.data, 3000);
                if (n == null) {
                    doc.blocks.remove(i--);
                } else if (n != img && n.data != img.data) {
                    img.data = n.data;
                    img.mime = n.mime;
                    img.width = n.width;
                    img.height = n.height;
                }
            }
        }
        if (doc.slides != null) {
            for (Doc.Slide s : doc.slides) {
                for (Doc.Shape sh : s.shapes) {
                    if (sh.image != null && !(sh.image.mime.equals("image/png") || sh.image.mime.equals("image/jpeg"))) {
                        Doc.Image n = normalizedImage(sh.image.data, 3000);
                        if (n != null) {
                            sh.image.data = n.data;
                            sh.image.mime = n.mime;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ documentos

    private Result fromDocument(byte[] data, Format src, String name, Format target, String base) throws Exception {
        step("Interpretando o documento…");
        Doc doc = Engine.read(data, src, name);
        if (!doc.hasContent()) throw new IOException("O documento está vazio ou não pôde ser lido.");
        normalizeImages(doc);
        Result res = new Result();
        if (target.isImage()) {
            File pdf = new File(Files.tempDir(ctx), "conv_" + System.nanoTime() + ".pdf");
            writePdf(doc, pdf);
            List<File> imgs = renderPdf(pdf, target, base);
            pdf.delete();
            res.pages = imgs.size();
            res.file = imgs.size() == 1 ? moveToOutput(imgs.get(0), base, target.ext) : zip(imgs, base + "_paginas");
            return res;
        }
        res.file = writeDoc(doc, target, base);
        return res;
    }

    private File moveToOutput(File f, String base, String ext) throws IOException {
        File out = Files.uniqueFile(Files.outputDir(ctx), base, ext);
        if (!f.renameTo(out)) {
            Files.copyFile(f, out);
            f.delete();
        }
        return out;
    }

    private File writeDoc(Doc doc, Format target, String base) throws Exception {
        File out = Files.uniqueFile(Files.outputDir(ctx), base, target.ext);
        step("Gerando " + target.label + "…");
        if (target == Format.PDF) {
            writePdf(doc, out);
        } else {
            OutputStream os = new BufferedOutputStream(new FileOutputStream(out));
            try {
                Engine.write(doc, target, os);
            } catch (Exception e) {
                os.close();
                out.delete();
                throw e;
            }
            os.close();
        }
        return out;
    }

    private void writePdf(Doc doc, File out) throws Exception {
        PdfDocWriter w = new PdfDocWriter();
        w.setPaper(Prefs.pageSize(ctx), Prefs.margins(ctx), doc.landscape);
        OutputStream os = new BufferedOutputStream(new FileOutputStream(out));
        try {
            w.write(doc, os, new PdfDocWriter.Progress() {
                @Override
                public void onProgress(String msg) {
                    if (listener != null) listener.progress(msg);
                }
            });
        } finally {
            os.close();
        }
    }

    // ------------------------------------------------------------------ PDF

    private Result fromPdf(byte[] data, Format target, String base) throws Exception {
        File tmp = new File(Files.tempDir(ctx), "src_" + System.nanoTime() + ".pdf");
        FileOutputStream fos = new FileOutputStream(tmp);
        fos.write(data);
        fos.close();
        Result res = new Result();
        try {
            if (target.isImage()) {
                List<File> imgs = renderPdf(tmp, target, base);
                res.pages = imgs.size();
                res.file = imgs.size() == 1 ? moveToOutput(imgs.get(0), base, target.ext) : zip(imgs, base + "_paginas");
                return res;
            }
            if (target == Format.PPTX) {
                // cada página vira um slide com a imagem da página
                Doc doc = new Doc();
                doc.title = base;
                for (Doc.Image img : pageImages(tmp, Math.min(160, Prefs.imageDpi(ctx)))) {
                    doc.add(img);
                }
                res.file = writeDoc(doc, target, base);
                return res;
            }
            step("Extraindo texto do PDF…");
            PdfText pt = new PdfText(data);
            if (pt.encrypted) throw new IOException("Este PDF é protegido/criptografado e não permite extrair o conteúdo.");
            int n = pt.pageCount();
            List<List<PdfText.Line>> pages = new ArrayList<List<PdfText.Line>>();
            List<PdfText.Line> all = new ArrayList<PdfText.Line>();
            int chars = 0;
            boolean anyScanned = false;
            for (int i = 0; i < n; i++) {
                if (i % 5 == 0) step("Lendo texto da página " + (i + 1) + " de " + n + "…");
                List<PdfText.Line> lines = pt.lines(i);
                pages.add(lines);
                all.addAll(lines);
                int pc = 0;
                for (PdfText.Line ln : lines) pc += ln.text.length();
                chars += pc;
                if (pc < 20) anyScanned = true;
            }
            double body = PdfText.predominantSize(all);
            boolean textOnly = target == Format.TXT || target == Format.CSV || target == Format.XLSX || target == Format.ODS;
            String mode = Prefs.pdfPageImages(ctx);
            if (textOnly && chars == 0) {
                throw new IOException("Este PDF não tem texto selecionável (parece ser digitalizado/imagem). "
                        + "Converta para Word, PowerPoint ou imagens.");
            }
            List<Doc.Image> imgs = null;
            if (!textOnly && (mode.equals("always") || (mode.equals("auto") && anyScanned))) {
                imgs = pageImages(tmp, 110);
            }
            Doc doc = new Doc();
            doc.title = base;
            for (int i = 0; i < n; i++) {
                if (i > 0) doc.add(new Doc.PageBreak());
                List<Doc.Block> paras = PdfText.paragraphs(pages.get(i), body);
                int pc = 0;
                for (PdfText.Line ln : pages.get(i)) pc += ln.text.length();
                boolean addImg = imgs != null && i < imgs.size() && (mode.equals("always") || pc < 20);
                if (addImg) {
                    doc.add(imgs.get(i));
                    if (!paras.isEmpty()) doc.add(new Doc.PageBreak());
                }
                doc.blocks.addAll(paras);
            }
            if (!doc.hasContent()) throw new IOException("Não foi possível extrair conteúdo deste PDF.");
            res.pages = n;
            res.file = writeDoc(doc, target, base);
            return res;
        } finally {
            tmp.delete();
        }
    }

    /** Renderiza cada página como imagem JPEG (para Word/PowerPoint). */
    private List<Doc.Image> pageImages(File pdf, int dpi) throws Exception {
        List<Doc.Image> out = new ArrayList<Doc.Image>();
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer r = openRenderer(pfd);
        try {
            int n = r.getPageCount();
            for (int i = 0; i < n; i++) {
                step("Renderizando página " + (i + 1) + " de " + n + "…");
                Bitmap bmp = renderPage(r, i, dpi);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos);
                Doc.Image img = new Doc.Image(bos.toByteArray(), "image/jpeg", bmp.getWidth(), bmp.getHeight());
                img.fullPage = true;
                out.add(img);
                bmp.recycle();
            }
        } finally {
            r.close();
            pfd.close();
        }
        return out;
    }

    static PdfRenderer openRenderer(ParcelFileDescriptor pfd) throws IOException {
        try {
            return new PdfRenderer(pfd);
        } catch (SecurityException e) {
            throw new IOException("Este PDF é protegido por senha.");
        } catch (IOException e) {
            throw new IOException("Arquivo PDF inválido ou corrompido.");
        }
    }

    static Bitmap renderPage(PdfRenderer r, int index, int dpi) {
        PdfRenderer.Page p = r.openPage(index);
        try {
            float scale = dpi / 72f;
            int w = Math.max(1, Math.round(p.getWidth() * scale));
            int h = Math.max(1, Math.round(p.getHeight() * scale));
            // limita a ~16 megapixels
            double mp = (double) w * h;
            if (mp > 16e6) {
                double f = Math.sqrt(16e6 / mp);
                w = (int) (w * f);
                h = (int) (h * f);
                scale *= f;
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            Matrix m = new Matrix();
            m.setScale((float) w / p.getWidth(), (float) h / p.getHeight());
            p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
            return bmp;
        } finally {
            p.close();
        }
    }

    private List<File> renderPdf(File pdf, Format target, String base) throws Exception {
        List<File> out = new ArrayList<File>();
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer r = openRenderer(pfd);
        try {
            int n = r.getPageCount();
            int dpi = Prefs.imageDpi(ctx);
            for (int i = 0; i < n; i++) {
                step("Gerando imagem " + (i + 1) + " de " + n + "…");
                Bitmap bmp = renderPage(r, i, dpi);
                File f = new File(Files.tempDir(ctx), String.format(java.util.Locale.ROOT, "%s_pagina_%03d.%s", base, i + 1, target.ext));
                saveBitmap(bmp, target, f);
                bmp.recycle();
                out.add(f);
            }
        } finally {
            r.close();
            pfd.close();
        }
        return out;
    }

    private void saveBitmap(Bitmap bmp, Format target, File f) throws IOException {
        Bitmap toSave = bmp;
        if (target == Format.JPG && bmp.hasAlpha()) {
            // JPEG não tem transparência: aplica fundo branco
            toSave = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(toSave);
            c.drawColor(Color.WHITE);
            c.drawBitmap(bmp, 0, 0, null);
        }
        Bitmap.CompressFormat cf = target == Format.PNG ? Bitmap.CompressFormat.PNG
                : target == Format.WEBP ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG;
        OutputStream os = new BufferedOutputStream(new FileOutputStream(f));
        try {
            toSave.compress(cf, Prefs.jpegQuality(ctx), os);
        } finally {
            os.close();
            if (toSave != bmp) toSave.recycle();
        }
    }

    private File zip(List<File> files, String base) throws Exception {
        step("Compactando " + files.size() + " arquivos…");
        File out = Files.uniqueFile(Files.outputDir(ctx), base, "zip");
        ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        try {
            for (File f : files) {
                zos.putNextEntry(new ZipEntry(f.getName()));
                java.io.InputStream in = new java.io.FileInputStream(f);
                Util.copy(in, zos);
                in.close();
                zos.closeEntry();
                f.delete();
            }
        } finally {
            zos.close();
        }
        return out;
    }
}
