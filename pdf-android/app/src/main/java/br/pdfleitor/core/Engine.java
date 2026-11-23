package br.pdfleitor.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada independente de Android: lê qualquer formato suportado para
 * o modelo Doc e escreve o Doc em qualquer formato de texto/escritório. PDF e
 * imagens são tratados pela camada Android (renderização gráfica).
 */
public final class Engine {

    private Engine() {
    }

    public static Doc read(byte[] data, Format f, String name) throws Exception {
        if (Format.isLegacyOffice(data)) {
            throw new UnsupportedOperationException("Arquivos .doc/.xls/.ppt antigos (Office 97-2003) não são suportados. "
                    + "Salve como .docx/.xlsx/.pptx e tente novamente.");
        }
        f = Format.sniff(data, f);
        if (f == null) throw new UnsupportedOperationException("Formato de arquivo não reconhecido.");
        Doc doc;
        switch (f) {
            case DOCX: doc = new DocxReader().read(data); break;
            case PPTX: doc = new PptxReader().read(data); break;
            case XLSX: doc = new XlsxReader().read(data); break;
            case ODT:
            case ODS:
            case ODP: doc = new OdfReader().read(data); break;
            case EPUB: doc = TextReaders.epub(data); break;
            case HTML: doc = TextReaders.html(data, null); break;
            case MD: doc = TextReaders.markdown(data); break;
            case CSV: doc = TextReaders.csv(data); break;
            case RTF: doc = TextReaders.rtf(data); break;
            case TXT: doc = TextReaders.txt(data); break;
            case PDF: doc = readPdfText(data); break;
            default:
                if (f.isImage()) {
                    doc = new Doc();
                    Doc.Image img = Util.image(data);
                    if (img == null) throw new Exception("Imagem inválida");
                    doc.add(img);
                    break;
                }
                throw new UnsupportedOperationException("Formato não suportado");
        }
        if (name != null && (doc.title == null || doc.title.equals("Documento") || doc.title.trim().isEmpty()))
            doc.title = Util.baseName(name);
        return doc;
    }

    /** Leitura de PDF somente texto (sem imagens de página). */
    public static Doc readPdfText(byte[] data) {
        PdfText pt = new PdfText(data);
        Doc doc = new Doc();
        List<List<PdfText.Line>> pages = new ArrayList<List<PdfText.Line>>();
        List<PdfText.Line> all = new ArrayList<PdfText.Line>();
        for (int i = 0; i < pt.pageCount(); i++) {
            List<PdfText.Line> l = pt.lines(i);
            pages.add(l);
            all.addAll(l);
        }
        double body = PdfText.predominantSize(all);
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) doc.add(new Doc.PageBreak());
            doc.blocks.addAll(PdfText.paragraphs(pages.get(i), body));
        }
        return doc;
    }

    public static void write(Doc doc, Format f, OutputStream os) throws IOException {
        switch (f) {
            case DOCX: {
                new DocxWriter().write(doc, os);
                break;
            }
            case PPTX: new PptxWriter().write(doc, os); break;
            case XLSX: new XlsxWriter().write(doc, os); break;
            case ODT: new OdfWriter().writeOdt(doc, os); break;
            case ODS: new OdfWriter().writeOds(doc, os); break;
            case EPUB: TextWriters.epub(doc, os); break;
            case HTML: TextWriters.html(doc, os); break;
            case MD: TextWriters.markdown(doc, os); break;
            case CSV: TextWriters.csv(doc, os); break;
            case RTF: TextWriters.rtf(doc, os); break;
            case TXT: TextWriters.txt(doc, os); break;
            default: throw new UnsupportedOperationException("Escrita em " + f.label + " requer a camada gráfica");
        }
    }
}
