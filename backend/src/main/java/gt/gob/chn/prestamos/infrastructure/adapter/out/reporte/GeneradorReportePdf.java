package gt.gob.chn.prestamos.infrastructure.adapter.out.reporte;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.ColumnaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.DocumentoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FilaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.ParDato;
import gt.gob.chn.prestamos.domain.model.reporte.ValorCelda;
import gt.gob.chn.prestamos.domain.port.out.GeneradorReportePort;

@Component
public class GeneradorReportePdf implements GeneradorReportePort {

    // Con más de seis columnas A4 vertical deja las cifras ilegibles (historial de pagos).
    private static final int COLUMNAS_PARA_GIRAR = 6;

    private static final float MARGEN_LATERAL = 36f;

    private static final float MARGEN_SUPERIOR = 86f;

    // Holgado a propósito: ColumnText recorta en silencio una nota al pie de tres renglones.
    private static final float MARGEN_INFERIOR = 62f;

    private static final float ANCHO_RECUADRO_SIGLAS = 42f;
    private static final float ALTO_RECUADRO_SIGLAS = 28f;
    private static final float SEPARACION_FRANJA = 36f;

    // Font es inmutable tras crearse, así que se comparte entre hilos. WinAnsi es necesario para tildes y eñe.
    private static final Font FUENTE_SIGLAS = fuente(true, PaletaReporte.TAM_SIGLAS, PaletaReporte.BLANCO);
    private static final Font FUENTE_ENTIDAD = fuente(true, PaletaReporte.TAM_ENTIDAD, PaletaReporte.AZUL_INSTITUCIONAL);
    private static final Font FUENTE_SISTEMA = fuente(false, PaletaReporte.TAM_SISTEMA, PaletaReporte.GRIS_TEXTO);
    private static final Font FUENTE_TITULO = fuente(true, PaletaReporte.TAM_TITULO, PaletaReporte.NEGRO_TEXTO);
    private static final Font FUENTE_SUBTITULO = fuente(false, PaletaReporte.TAM_SUBTITULO, PaletaReporte.GRIS_TEXTO);
    private static final Font FUENTE_ETIQUETA = fuente(true, PaletaReporte.TAM_DATO, PaletaReporte.GRIS_TEXTO);
    private static final Font FUENTE_VALOR = fuente(false, PaletaReporte.TAM_DATO, PaletaReporte.NEGRO_TEXTO);
    private static final Font FUENTE_CABECERA_TABLA = fuente(true, PaletaReporte.TAM_TABLA, PaletaReporte.BLANCO);
    private static final Font FUENTE_CELDA = fuente(false, PaletaReporte.TAM_TABLA, PaletaReporte.NEGRO_TEXTO);
    private static final Font FUENTE_TOTAL = fuente(true, PaletaReporte.TAM_TABLA, PaletaReporte.NEGRO_TEXTO);
    private static final Font FUENTE_VACIO = fuente(false, PaletaReporte.TAM_TABLA, PaletaReporte.GRIS_TEXTO);
    private static final Font FUENTE_PIE = fuente(false, PaletaReporte.TAM_PIE, PaletaReporte.GRIS_TEXTO);

    @Override
    public FormatoReporte formato() {
        return FormatoReporte.PDF;
    }

    @Override
    public ArchivoGenerado generar(DocumentoReporte documento) {
        // Un plan de 360 cuotas ronda los 64 kB: el búfer casi no necesita crecer.
        ByteArrayOutputStream salida = new ByteArrayOutputStream(64 * 1024);
        Document pdf = new Document(
                tamanoPagina(documento),
                MARGEN_LATERAL, MARGEN_LATERAL, MARGEN_SUPERIOR, MARGEN_INFERIOR);
        try {
            PdfWriter escritor = PdfWriter.getInstance(pdf, salida);
            escritor.setPageEvent(new EncabezadoYPie(documento));

            pdf.addTitle(documento.titulo() + " - " + documento.subtitulo());
            pdf.addAuthor(PaletaReporte.ENTIDAD);
            pdf.addCreator(PaletaReporte.SISTEMA);

            pdf.open();
            pdf.add(bloqueDatos(documento));
            pdf.add(tabla(documento));
            pdf.close();
        } catch (DocumentException e) {
            throw new IllegalStateException(
                    "No se pudo generar el PDF del reporte '" + documento.titulo() + "'", e);
        }

        return ArchivoGenerado.de(documento, formato(), salida.toByteArray());
    }

    private static Rectangle tamanoPagina(DocumentoReporte documento) {
        return documento.columnas().size() > COLUMNAS_PARA_GIRAR
                ? PageSize.A4.rotate()
                : PageSize.A4;
    }

    // Va como contenido y no en el evento de página porque solo debe salir en la primera.
    private static PdfPTable bloqueDatos(DocumentoReporte documento) {
        List<ParDato> datos = documento.datosEncabezado();
        // La etiqueta más larga ("Fecha de desembolso:") debe caber en un renglón o las dos
        // columnas de la ficha se desalinean.
        PdfPTable bloque = new PdfPTable(new float[] { 1.4f, 1.85f, 1.4f, 1.85f });
        bloque.setWidthPercentage(100);
        bloque.setSpacingAfter(10f);

        if (datos.isEmpty()) {
            return bloque;
        }
        for (int i = 0; i < datos.size(); i += 2) {
            agregarPar(bloque, datos.get(i));
            agregarPar(bloque, i + 1 < datos.size() ? datos.get(i + 1) : null);
        }
        return bloque;
    }

    private static void agregarPar(PdfPTable bloque, ParDato par) {
        bloque.addCell(celdaDato(par == null ? "" : par.etiqueta() + ":", FUENTE_ETIQUETA, Element.ALIGN_LEFT));
        bloque.addCell(celdaDato(par == null ? "" : par.valor(), FUENTE_VALOR, Element.ALIGN_LEFT));
    }

    private static PdfPCell celdaDato(String texto, Font fuente, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setBackgroundColor(PaletaReporte.GRIS_BLOQUE_DATOS);
        celda.setHorizontalAlignment(alineacion);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setPaddingTop(3f);
        celda.setPaddingBottom(3f);
        celda.setPaddingLeft(5f);
        celda.setPaddingRight(5f);
        return celda;
    }

    private static PdfPTable tabla(DocumentoReporte documento) {
        List<ColumnaReporte> columnas = documento.columnas();
        PdfPTable tabla = new PdfPTable(pesos(columnas));
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);

        for (ColumnaReporte columna : columnas) {
            tabla.addCell(celdaCabecera(columna));
        }

        List<FilaReporte> filas = documento.filas();
        if (filas.isEmpty()) {
            PdfPCell aviso = new PdfPCell(new Phrase("Sin registros que mostrar.", FUENTE_VACIO));
            aviso.setColspan(columnas.size());
            aviso.setHorizontalAlignment(Element.ALIGN_CENTER);
            aviso.setPadding(10f);
            aviso.setBorderColor(PaletaReporte.GRIS_SEPARADOR);
            tabla.addCell(aviso);
            return tabla;
        }

        for (int i = 0; i < filas.size(); i++) {
            Color fondo = i % 2 == 1 ? PaletaReporte.GRIS_FILA_ALTERNA : PaletaReporte.BLANCO;
            List<ValorCelda> celdas = filas.get(i).celdas();
            for (int c = 0; c < columnas.size(); c++) {
                // Por columna y no por celda: una fila corta se rellena en vez de descuadrar la tabla.
                ValorCelda valor = c < celdas.size() ? celdas.get(c) : null;
                tabla.addCell(celdaDatos(valor, columnas.get(c), fondo));
            }
        }

        if (!documento.totales().isEmpty()) {
            List<ValorCelda> totales = documento.totales();
            for (int c = 0; c < columnas.size(); c++) {
                tabla.addCell(celdaTotal(totales.get(c), columnas.get(c)));
            }
        }
        return tabla;
    }

    // La alineación delata el tipo de dato: derecha = importes, centro = contadores, izquierda = texto.
    private static float[] pesos(List<ColumnaReporte> columnas) {
        float[] pesos = new float[columnas.size()];
        for (int i = 0; i < columnas.size(); i++) {
            ColumnaReporte columna = columnas.get(i);
            pesos[i] = switch (columna.alineacion()) {
                case CENTRO -> 5f;
                case DERECHA -> 12f;
                case IZQUIERDA -> Math.max(columna.titulo().length() + 4f, 14f);
            };
        }
        return pesos;
    }

    private static PdfPCell celdaCabecera(ColumnaReporte columna) {
        PdfPCell celda = new PdfPCell(new Phrase(columna.titulo(), FUENTE_CABECERA_TABLA));
        celda.setBackgroundColor(PaletaReporte.AZUL_INSTITUCIONAL);
        celda.setBorderColor(PaletaReporte.AZUL_INSTITUCIONAL);
        celda.setHorizontalAlignment(alineacion(columna));
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setPadding(4f);
        return celda;
    }

    private static PdfPCell celdaDatos(ValorCelda valor, ColumnaReporte columna, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(FormatoValores.comoTexto(valor), FUENTE_CELDA));
        celda.setBackgroundColor(fondo);
        celda.setBorderColor(PaletaReporte.GRIS_FILA_ALTERNA);
        celda.setHorizontalAlignment(alineacion(columna));
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setPaddingTop(2.5f);
        celda.setPaddingBottom(2.5f);
        celda.setPaddingLeft(4f);
        celda.setPaddingRight(4f);
        return celda;
    }

    private static PdfPCell celdaTotal(ValorCelda valor, ColumnaReporte columna) {
        PdfPCell celda = new PdfPCell(new Phrase(FormatoValores.comoTexto(valor), FUENTE_TOTAL));
        celda.setBorder(Rectangle.TOP);
        celda.setBorderWidthTop(1f);
        celda.setBorderColorTop(PaletaReporte.AZUL_INSTITUCIONAL);
        celda.setHorizontalAlignment(alineacion(columna));
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celda.setPaddingTop(4f);
        celda.setPaddingBottom(4f);
        celda.setPaddingLeft(4f);
        celda.setPaddingRight(4f);
        return celda;
    }

    private static int alineacion(ColumnaReporte columna) {
        return switch (columna.alineacion()) {
            case IZQUIERDA -> Element.ALIGN_LEFT;
            case CENTRO -> Element.ALIGN_CENTER;
            case DERECHA -> Element.ALIGN_RIGHT;
        };
    }

    private static Font fuente(boolean negrita, float tamano, Color color) {
        return FontFactory.getFont(
                negrita ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
                BaseFont.WINANSI,
                tamano,
                Font.NORMAL,
                color);
    }

    // Encabezado y pie van en el evento de página para repetirse en todas las hojas. El total de
    // "Página X de Y" se escribe al cerrar en un PdfTemplate compartido, sin generar el PDF dos veces.
    private static final class EncabezadoYPie extends PdfPageEventHelper {

        private static final float ANCHO_PLANTILLA = 15f;
        private static final float ALTO_PLANTILLA = 9f;

        // OpenPDF recorta el espacio final al alinear a la derecha; sin esto sale "Página 1 de7".
        private static final float ESPACIO_ANTES_DEL_TOTAL = 2.5f;

        private static final float INTERLINEA_PIE = 7.6f;

        private static final float ALTURA_MINIMA_PIE = 5f;

        private static final float BANDA_NOTA = 0.46f;

        private final DocumentoReporte documento;
        private final String textoGeneracion;

        private PdfTemplate huecoTotalPaginas;
        private BaseFont fuenteBase;

        private int totalPaginas;

        private EncabezadoYPie(DocumentoReporte documento) {
            this.documento = documento;
            this.textoGeneracion = "Generado por " + documento.generadoPor()
                    + " el " + FormatoValores.fechaHora(documento.generadoEn());
        }

        @Override
        public void onOpenDocument(PdfWriter escritor, Document pdf) {
            huecoTotalPaginas = escritor.getDirectContent()
                    .createTemplate(ANCHO_PLANTILLA, ALTO_PLANTILLA);
            fuenteBase = FUENTE_PIE.getBaseFont();
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document pdf) {
            totalPaginas = escritor.getPageNumber();
            PdfContentByte lienzo = escritor.getDirectContent();
            dibujarEncabezado(lienzo, pdf);
            dibujarPie(lienzo, pdf, escritor.getPageNumber());
        }

        @Override
        public void onCloseDocument(PdfWriter escritor, Document pdf) {
            huecoTotalPaginas.beginText();
            huecoTotalPaginas.setFontAndSize(fuenteBase, PaletaReporte.TAM_PIE);
            huecoTotalPaginas.setColorFill(PaletaReporte.GRIS_TEXTO);
            huecoTotalPaginas.setTextMatrix(0, 0);
            huecoTotalPaginas.showText(String.valueOf(totalPaginas));
            huecoTotalPaginas.endText();
        }

        private void dibujarEncabezado(PdfContentByte lienzo, Document pdf) {
            float izquierda = pdf.left();
            float derecha = pdf.right();
            float tope = pdf.getPageSize().getHeight() - MARGEN_LATERAL;

            lienzo.saveState();

            lienzo.setColorFill(PaletaReporte.AZUL_OSCURO);
            lienzo.rectangle(izquierda, tope - ALTO_RECUADRO_SIGLAS,
                    ANCHO_RECUADRO_SIGLAS, ALTO_RECUADRO_SIGLAS);
            lienzo.fill();
            ColumnText.showTextAligned(lienzo, Element.ALIGN_CENTER,
                    new Phrase(PaletaReporte.SIGLAS, FUENTE_SIGLAS),
                    izquierda + ANCHO_RECUADRO_SIGLAS / 2f, tope - 20f, 0);

            float xTexto = izquierda + ANCHO_RECUADRO_SIGLAS + 10f;
            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                    new Phrase(PaletaReporte.ENTIDAD, FUENTE_ENTIDAD), xTexto, tope - 10f, 0);
            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                    new Phrase(PaletaReporte.SISTEMA, FUENTE_SISTEMA), xTexto, tope - 22f, 0);

            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                    new Phrase(documento.titulo(), FUENTE_TITULO), derecha, tope - 10f, 0);
            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                    new Phrase(documento.subtitulo(), FUENTE_SUBTITULO), derecha, tope - 23f, 0);

            float ySeparador = tope - SEPARACION_FRANJA;
            lienzo.setColorStroke(PaletaReporte.AZUL_INSTITUCIONAL);
            lienzo.setLineWidth(1.2f);
            lienzo.moveTo(izquierda, ySeparador);
            lienzo.lineTo(derecha, ySeparador);
            lienzo.stroke();

            lienzo.restoreState();
        }

        private void dibujarPie(PdfContentByte lienzo, Document pdf, int pagina) {
            float izquierda = pdf.left();
            float derecha = pdf.right();
            float ancho = derecha - izquierda;
            float ySeparador = pdf.bottom() - 22f;
            float yPrimerRenglon = ySeparador - INTERLINEA_PIE;

            lienzo.saveState();

            lienzo.setColorStroke(PaletaReporte.GRIS_SEPARADOR);
            lienzo.setLineWidth(0.6f);
            lienzo.moveTo(izquierda, ySeparador);
            lienzo.lineTo(derecha, ySeparador);
            lienzo.stroke();

            // Bandas sin solape; nota y generación usan ColumnText porque pueden ocupar varios renglones.
            escribirEnBanda(lienzo, documento.notaPie(), Element.ALIGN_LEFT,
                    izquierda, izquierda + ancho * BANDA_NOTA, ySeparador);
            escribirEnBanda(lienzo, textoGeneracion, Element.ALIGN_CENTER,
                    izquierda + ancho * (BANDA_NOTA + 0.02f),
                    izquierda + ancho * 0.80f, ySeparador);

            float xHueco = derecha - ANCHO_PLANTILLA;
            ColumnText.showTextAligned(lienzo, Element.ALIGN_RIGHT,
                    new Phrase("Página " + pagina + " de", FUENTE_PIE),
                    xHueco - ESPACIO_ANTES_DEL_TOTAL, yPrimerRenglon, 0);
            lienzo.addTemplate(huecoTotalPaginas, xHueco, yPrimerRenglon);

            lienzo.restoreState();
        }

        private void escribirEnBanda(PdfContentByte lienzo, String texto, int alineacion,
                float x0, float x1, float yTope) {
            if (texto == null || texto.isBlank()) {
                return;
            }
            try {
                ColumnText banda = new ColumnText(lienzo);
                // La caja baja casi al borde del papel: ColumnText descarta en silencio lo que no cabe.
                banda.setSimpleColumn(x0, ALTURA_MINIMA_PIE, x1, yTope);
                banda.setAlignment(alineacion);
                banda.setLeading(INTERLINEA_PIE);
                banda.setText(new Phrase(texto, FUENTE_PIE));
                banda.go();
            } catch (DocumentException e) {
                // onEndPage no admite checked exceptions; ExceptionConverter es el envoltorio de la librería.
                throw new ExceptionConverter(e);
            }
        }
    }
}
