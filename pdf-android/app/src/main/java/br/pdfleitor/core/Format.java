package br.pdfleitor.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Formatos de arquivo suportados para leitura e/ou escrita. */
public enum Format {
    PDF("PDF", "pdf", "application/pdf", true, true),
    DOCX("Word", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", true, true),
    PPTX("PowerPoint", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", true, true),
    XLSX("Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true, true),
    ODT("Texto ODT", "odt", "application/vnd.oasis.opendocument.text", true, true),
    ODS("Planilha ODS", "ods", "application/vnd.oasis.opendocument.spreadsheet", true, true),
    ODP("Apresentação ODP", "odp", "application/vnd.oasis.opendocument.presentation", true, false),
    EPUB("eBook EPUB", "epub", "application/epub+zip", true, true),
    HTML("HTML", "html", "text/html", true, true),
    MD("Markdown", "md", "text/markdown", true, true),
    TXT("Texto", "txt", "text/plain", true, true),
    CSV("CSV", "csv", "text/csv", true, true),
    RTF("RTF", "rtf", "application/rtf", true, true),
    JPG("JPG", "jpg", "image/jpeg", true, true),
    PNG("PNG", "png", "image/png", true, true),
    WEBP("WEBP", "webp", "image/webp", true, true),
    GIF("GIF", "gif", "image/gif", true, false),
    BMP("BMP", "bmp", "image/bmp", true, false);

    public final String label, ext, mime;
    public final boolean canRead, canWrite;

    Format(String label, String ext, String mime, boolean canRead, boolean canWrite) {
        this.label = label;
        this.ext = ext;
        this.mime = mime;
        this.canRead = canRead;
        this.canWrite = canWrite;
    }

    public boolean isImage() {
        return this == JPG || this == PNG || this == WEBP || this == GIF || this == BMP;
    }

    public static Format fromName(String name, String mime) {
        String ext = Util.extension(name);
        if (ext.equals("jpeg") || ext.equals("jpe")) ext = "jpg";
        if (ext.equals("htm") || ext.equals("xhtml")) ext = "html";
        if (ext.equals("markdown")) ext = "md";
        if (ext.equals("text") || ext.equals("log") || ext.equals("json") || ext.equals("xml")
                || ext.equals("ini") || ext.equals("srt")) ext = "txt";
        if (ext.equals("tsv")) ext = "csv";
        if (ext.equals("docm") || ext.equals("dotx")) ext = "docx";
        if (ext.equals("pptm") || ext.equals("ppsx") || ext.equals("potx")) ext = "pptx";
        if (ext.equals("xlsm") || ext.equals("xltx")) ext = "xlsx";
        for (Format f : values()) if (f.ext.equals(ext)) return f;
        if (mime != null) {
            mime = mime.toLowerCase(Locale.ROOT);
            for (Format f : values()) if (f.mime.equals(mime)) return f;
            if (mime.startsWith("image/jpeg")) return JPG;
            if (mime.startsWith("text/html")) return HTML;
            if (mime.startsWith("text/")) return TXT;
        }
        return null;
    }

    /** Detecta o formato pelo conteúdo (assinatura), usado como reforço. */
    public static Format sniff(byte[] d, Format guess) {
        if (d == null || d.length < 8) return guess;
        if (d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F') return PDF;
        String m = Util.imageMime(d);
        if (m.equals("image/png")) return PNG;
        if (m.equals("image/jpeg")) return JPG;
        if (m.equals("image/gif")) return GIF;
        if (m.equals("image/webp")) return WEBP;
        if (m.equals("image/bmp") && (guess == null || guess == BMP)) return BMP;
        if (d[0] == '{' && d[1] == '\\' && d[2] == 'r' && d[3] == 't' && d[4] == 'f') return RTF;
        if (d[0] == 'P' && d[1] == 'K') {
            if (guess == DOCX || guess == PPTX || guess == XLSX || guess == ODT || guess == ODS || guess == ODP || guess == EPUB)
                return guess;
            String head = new String(d, 0, Math.min(d.length, 4000), Util.UTF8);
            if (head.contains("word/")) return DOCX;
            if (head.contains("ppt/")) return PPTX;
            if (head.contains("xl/")) return XLSX;
            if (head.contains("application/epub+zip")) return EPUB;
            if (head.contains("opendocument.text")) return ODT;
            if (head.contains("opendocument.spreadsheet")) return ODS;
            if (head.contains("opendocument.presentation")) return ODP;
        }
        // formatos binários antigos do Office (OLE2)
        if ((d[0] & 0xFF) == 0xD0 && (d[1] & 0xFF) == 0xCF && (d[2] & 0xFF) == 0x11 && (d[3] & 0xFF) == 0xE0)
            return null;
        return guess;
    }

    public static boolean isLegacyOffice(byte[] d) {
        return d != null && d.length > 4 && (d[0] & 0xFF) == 0xD0 && (d[1] & 0xFF) == 0xCF
                && (d[2] & 0xFF) == 0x11 && (d[3] & 0xFF) == 0xE0;
    }

    /** Destinos possíveis para um formato de origem. */
    public static List<Format> targetsFor(Format src) {
        List<Format> out = new ArrayList<Format>();
        for (Format f : values()) {
            if (!f.canWrite || f == src) continue;
            out.add(f);
        }
        if (src != null && src.isImage()) {
            // imagens não têm texto: remove destinos puramente textuais
            out.remove(TXT);
            out.remove(CSV);
            out.remove(XLSX);
            out.remove(ODS);
        }
        return out;
    }
}
