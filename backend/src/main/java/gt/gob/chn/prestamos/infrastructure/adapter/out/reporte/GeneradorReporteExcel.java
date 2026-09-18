package gt.gob.chn.prestamos.infrastructure.adapter.out.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.ColumnaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.DocumentoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FilaReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.ParDato;
import gt.gob.chn.prestamos.domain.model.reporte.ValorCelda;
import gt.gob.chn.prestamos.domain.port.out.GeneradorReportePort;

@Component
public class GeneradorReporteExcel implements GeneradorReportePort {

    private static final Logger LOG = LoggerFactory.getLogger(GeneradorReporteExcel.class);

    // Solo formato de presentación: el valor de la celda sigue siendo numérico para poder sumar y filtrar.
    private static final String FORMATO_GENERAL = "General";
    private static final String FORMATO_ENTERO = "0";
    private static final String FORMATO_MONEDA = "\"Q\"#,##0.00";
    private static final String FORMATO_PORCENTAJE = "0.00\"%\"";
    private static final String FORMATO_FECHA = "dd/mm/yyyy";
    private static final String FORMATO_FECHA_HORA = "dd/mm/yyyy hh:mm";

    private static final int COLUMNAS_PARA_GIRAR = 6;

    private static final int COLUMNAS_MINIMAS_FRANJA = 4;

    // Excel mide el ancho de columna en 1/256 de carácter.
    private static final int UNIDAD_ANCHO = 256;
    private static final int HOLGURA_ANCHO = 2 * UNIDAD_ANCHO;
    private static final int ANCHO_MAXIMO = 60 * UNIDAD_ANCHO;

    @Override
    public FormatoReporte formato() {
        return FormatoReporte.EXCEL;
    }

    @Override
    public ArchivoGenerado generar(DocumentoReporte documento) {
        try (XSSFWorkbook libro = new XSSFWorkbook();
                ByteArrayOutputStream salida = new ByteArrayOutputStream(64 * 1024)) {

            Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(documento.titulo()));
            Estilos estilos = new Estilos(libro);

            int fila = escribirFranjaInstitucional(hoja, estilos, documento);
            fila = escribirDatosEncabezado(hoja, estilos, documento, fila);
            int filaCabecera = fila + 1;
            int ultimaFilaDatos = escribirTabla(hoja, estilos, documento, filaCabecera);

            configurarVista(hoja, documento, filaCabecera, ultimaFilaDatos);
            configurarImpresion(hoja, documento, filaCabecera);
            ajustarAnchos(hoja, documento);

            libro.write(salida);
            return ArchivoGenerado.de(documento, formato(), salida.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo generar el Excel del reporte '" + documento.titulo() + "'", e);
        }
    }

    // Excel solo muestra encabezado y pie al imprimir, así que la franja también va como filas visibles.
    private static int escribirFranjaInstitucional(Sheet hoja, Estilos estilos, DocumentoReporte documento) {
        int ancho = anchoFranja(documento);

        Row primera = hoja.createRow(0);
        primera.setHeightInPoints(18f);
        celda(primera, 0, estilos.siglas).setCellValue(PaletaReporte.SIGLAS);
        celda(primera, 1, estilos.entidad).setCellValue(PaletaReporte.ENTIDAD);
        rellenar(primera, 2, ancho, estilos.entidad);
        combinar(hoja, 0, 0, 1, ancho - 1);

        Row segunda = hoja.createRow(1);
        segunda.setHeightInPoints(14f);
        celda(segunda, 0, estilos.siglas);
        celda(segunda, 1, estilos.sistema).setCellValue(PaletaReporte.SISTEMA);
        rellenar(segunda, 2, ancho, estilos.sistema);
        combinar(hoja, 0, 1, 0, 0);
        combinar(hoja, 1, 1, 1, ancho - 1);

        Row tercera = hoja.createRow(2);
        tercera.setHeightInPoints(20f);
        celda(tercera, 0, estilos.titulo)
                .setCellValue(documento.titulo() + " · " + documento.subtitulo());
        rellenar(tercera, 1, ancho, estilos.titulo);
        combinar(hoja, 2, 2, 0, ancho - 1);

        hoja.createRow(3).setHeightInPoints(6f);
        return 4;
    }

    private static int escribirDatosEncabezado(Sheet hoja, Estilos estilos,
            DocumentoReporte documento, int filaInicial) {
        List<ParDato> datos = documento.datosEncabezado();
        if (datos.isEmpty()) {
            return filaInicial;
        }
        int ancho = anchoFranja(documento);
        int mitad = ancho / 2;

        int fila = filaInicial;
        for (int i = 0; i < datos.size(); i += 2) {
            Row renglon = hoja.createRow(fila);
            escribirPar(hoja, renglon, estilos, datos.get(i), 0, mitad - 1);
            if (i + 1 < datos.size()) {
                escribirPar(hoja, renglon, estilos, datos.get(i + 1), mitad, ancho - 1);
            }
            fila++;
        }
        return fila;
    }

    private static void escribirPar(Sheet hoja, Row renglon, Estilos estilos,
            ParDato par, int columnaEtiqueta, int ultimaColumnaValor) {
        celda(renglon, columnaEtiqueta, estilos.etiqueta).setCellValue(par.etiqueta());
        celda(renglon, columnaEtiqueta + 1, estilos.valor)
                .setCellValue(par.valor() == null ? "" : par.valor());
        rellenar(renglon, columnaEtiqueta + 2, ultimaColumnaValor + 1, estilos.valor);
        combinar(hoja, renglon.getRowNum(), renglon.getRowNum(), columnaEtiqueta + 1, ultimaColumnaValor);
    }

    // Devuelve la última fila de datos, sin contar la de totales, para acotar el autofiltro.
    private static int escribirTabla(Sheet hoja, Estilos estilos,
            DocumentoReporte documento, int filaCabecera) {
        List<ColumnaReporte> columnas = documento.columnas();

        Row cabecera = hoja.createRow(filaCabecera);
        cabecera.setHeightInPoints(16f);
        for (int c = 0; c < columnas.size(); c++) {
            celda(cabecera, c, estilos.cabecera(columnas.get(c))).setCellValue(columnas.get(c).titulo());
        }

        List<FilaReporte> filas = documento.filas();
        if (filas.isEmpty()) {
            Row aviso = hoja.createRow(filaCabecera + 1);
            celda(aviso, 0, estilos.vacio).setCellValue("Sin registros que mostrar.");
            return filaCabecera;
        }

        int fila = filaCabecera;
        for (FilaReporte renglon : filas) {
            fila++;
            Row destino = hoja.createRow(fila);
            List<ValorCelda> celdas = renglon.celdas();
            for (int c = 0; c < columnas.size(); c++) {
                if (c < celdas.size()) {
                    escribirCelda(destino, c, celdas.get(c), columnas.get(c), estilos, false);
                }
            }
        }
        int ultimaFilaDatos = fila;

        if (!documento.totales().isEmpty()) {
            fila++;
            Row destino = hoja.createRow(fila);
            List<ValorCelda> totales = documento.totales();
            for (int c = 0; c < columnas.size(); c++) {
                escribirCelda(destino, c, totales.get(c), columnas.get(c), estilos, true);
            }
        }
        return ultimaFilaDatos;
    }

    private static void escribirCelda(Row renglon, int columna, ValorCelda valor,
            ColumnaReporte definicion, Estilos estilos, boolean total) {
        Cell celda = celda(renglon, columna, estilos.dato(valor, definicion, total));
        escribirValor(celda, valor);
    }

    // Tipo nativo (double, fecha) para que Excel pueda sumar y ordenar; un nulo deja la celda
    // en blanco en vez de un cero que pasaría por dato real.
    private static void escribirValor(Cell celda, ValorCelda valor) {
        switch (valor) {
            case ValorCelda.Texto(String texto) -> {
                if (texto != null && !texto.isEmpty()) {
                    celda.setCellValue(texto);
                }
            }
            case ValorCelda.Entero(int numero) -> celda.setCellValue(numero);
            case ValorCelda.Moneda(BigDecimal importe) -> {
                if (importe != null) {
                    celda.setCellValue(importe.doubleValue());
                }
            }
            case ValorCelda.Porcentaje(BigDecimal tasa) -> {
                if (tasa != null) {
                    celda.setCellValue(tasa.doubleValue());
                }
            }
            case ValorCelda.Fecha(LocalDate fecha) -> {
                if (fecha != null) {
                    celda.setCellValue(fecha);
                }
            }
            case ValorCelda.FechaHora(LocalDateTime fechaHora) -> {
                if (fechaHora != null) {
                    celda.setCellValue(fechaHora);
                }
            }
        }
    }

    private static String formatoDe(ValorCelda valor) {
        return switch (valor) {
            case ValorCelda.Texto ignorado -> FORMATO_GENERAL;
            case ValorCelda.Entero ignorado -> FORMATO_ENTERO;
            case ValorCelda.Moneda ignorado -> FORMATO_MONEDA;
            case ValorCelda.Porcentaje ignorado -> FORMATO_PORCENTAJE;
            case ValorCelda.Fecha ignorado -> FORMATO_FECHA;
            case ValorCelda.FechaHora ignorado -> FORMATO_FECHA_HORA;
        };
    }

    private static void configurarVista(Sheet hoja, DocumentoReporte documento,
            int filaCabecera, int ultimaFilaDatos) {
        hoja.createFreezePane(0, filaCabecera + 1);
        hoja.setAutoFilter(new CellRangeAddress(
                filaCabecera,
                Math.max(ultimaFilaDatos, filaCabecera),
                0,
                documento.columnas().size() - 1));
    }

    private static void configurarImpresion(Sheet hoja, DocumentoReporte documento, int filaCabecera) {
        Header encabezado = hoja.getHeader();
        encabezado.setLeft(PaletaReporte.SIGLAS + " · " + PaletaReporte.ENTIDAD);
        encabezado.setCenter(documento.titulo());
        encabezado.setRight(documento.subtitulo());

        Footer pie = hoja.getFooter();
        pie.setLeft(documento.notaPie());
        pie.setCenter("Generado por " + documento.generadoPor()
                + " el " + FormatoValores.fechaHora(documento.generadoEn()));
        // &P y &N son códigos de campo de Excel (página y total); POI no los expone tipados.
        pie.setRight("Página &P de &N");

        // El rango de filas repetidas es 1-based, como en la interfaz de Excel.
        int filaVisible = filaCabecera + 1;
        hoja.setRepeatingRows(CellRangeAddress.valueOf(filaVisible + ":" + filaVisible));

        hoja.setFitToPage(true);
        hoja.setAutobreaks(true);
        PrintSetup impresion = hoja.getPrintSetup();
        impresion.setPaperSize(PrintSetup.A4_PAPERSIZE);
        impresion.setLandscape(documento.columnas().size() > COLUMNAS_PARA_GIRAR);
        impresion.setFitWidth((short) 1);
        impresion.setFitHeight((short) 0); // 0 = sin límite de páginas a lo alto
        hoja.setMargin(PageMargin.HEADER, 0.4);
        hoja.setMargin(PageMargin.FOOTER, 0.4);
    }

    private static void ajustarAnchos(Sheet hoja, DocumentoReporte documento) {
        List<ColumnaReporte> columnas = documento.columnas();
        for (int i = 0; i < columnas.size(); i++) {
            int ancho = anchoMinimo(columnas.get(i));
            try {
                // autoSizeColumn usa fuentes de java.awt, que el JRE mínimo del contenedor puede
                // no traer; si falla se usa el mínimo estimado en lugar de perder el reporte.
                hoja.autoSizeColumn(i);
                ancho = Math.max(hoja.getColumnWidth(i), ancho);
            } catch (RuntimeException | LinkageError | InternalError e) {
                LOG.debug("No se pudo medir el ancho de la columna {} ({}); se usa el mínimo estimado",
                        i, columnas.get(i).titulo(), e);
            }
            hoja.setColumnWidth(i, Math.min(ancho + HOLGURA_ANCHO, ANCHO_MAXIMO));
        }
    }

    // La alineación delata el tipo de dato: derecha = importes ("Q 1,234,567.89" ~14 caracteres).
    private static int anchoMinimo(ColumnaReporte columna) {
        int caracteres = switch (columna.alineacion()) {
            case CENTRO -> Math.max(columna.titulo().length(), 6);
            case DERECHA -> Math.max(columna.titulo().length(), 14);
            case IZQUIERDA -> Math.max(columna.titulo().length(), 16);
        };
        return caracteres * UNIDAD_ANCHO;
    }

    private static int anchoFranja(DocumentoReporte documento) {
        return Math.max(documento.columnas().size(), COLUMNAS_MINIMAS_FRANJA);
    }

    private static Cell celda(Row renglon, int columna, CellStyle estilo) {
        Cell celda = renglon.createCell(columna);
        celda.setCellStyle(estilo);
        return celda;
    }

    // Sin celdas creadas, Excel no pinta el fondo ni los bordes del rango combinado.
    private static void rellenar(Row renglon, int desde, int hasta, CellStyle estilo) {
        for (int c = desde; c < hasta; c++) {
            celda(renglon, c, estilo);
        }
    }

    // Excel rechaza combinar una sola celda.
    private static void combinar(Sheet hoja, int primeraFila, int ultimaFila,
            int primeraColumna, int ultimaColumna) {
        if (ultimaColumna > primeraColumna || ultimaFila > primeraFila) {
            hoja.addMergedRegion(
                    new CellRangeAddress(primeraFila, ultimaFila, primeraColumna, ultimaColumna));
        }
    }

    private static HorizontalAlignment alineacion(ColumnaReporte columna) {
        return switch (columna.alineacion()) {
            case IZQUIERDA -> HorizontalAlignment.LEFT;
            case CENTRO -> HorizontalAlignment.CENTER;
            case DERECHA -> HorizontalAlignment.RIGHT;
        };
    }

    // Un libro admite un número limitado de estilos: se cachean por formato, alineación y total
    // en vez de crear uno por celda.
    private static final class Estilos {

        private final XSSFWorkbook libro;
        private final DataFormat formatos;
        private final XSSFColor azul;
        private final XSSFFont fuenteNormal;
        private final XSSFFont fuenteNegrita;
        private final Map<String, CellStyle> cacheDatos = new HashMap<>();

        private final CellStyle siglas;
        private final CellStyle entidad;
        private final CellStyle sistema;
        private final CellStyle titulo;
        private final CellStyle etiqueta;
        private final CellStyle valor;
        private final CellStyle vacio;

        private Estilos(XSSFWorkbook libro) {
            this.libro = libro;
            this.formatos = libro.createDataFormat();
            // POI necesita el mapa de colores indexados del libro para resolver un RGB.
            this.azul = new XSSFColor(
                    PaletaReporte.rgb(PaletaReporte.AZUL_INSTITUCIONAL),
                    libro.getStylesSource().getIndexedColors());
            XSSFColor azulOscuro = new XSSFColor(
                    PaletaReporte.rgb(PaletaReporte.AZUL_OSCURO),
                    libro.getStylesSource().getIndexedColors());
            XSSFColor gris = new XSSFColor(
                    PaletaReporte.rgb(PaletaReporte.GRIS_BLOQUE_DATOS),
                    libro.getStylesSource().getIndexedColors());

            this.fuenteNormal = fuente(false, 10, null);
            this.fuenteNegrita = fuente(true, 10, null);

            this.siglas = relleno(azulOscuro, fuente(true, 14, PaletaReporte.BLANCO));
            ((XSSFCellStyle) this.siglas).setAlignment(HorizontalAlignment.CENTER);
            ((XSSFCellStyle) this.siglas).setVerticalAlignment(VerticalAlignment.CENTER);

            this.entidad = relleno(azul, fuente(true, 12, PaletaReporte.BLANCO));
            this.sistema = relleno(azul, fuente(false, 9, PaletaReporte.BLANCO));

            XSSFCellStyle estiloTitulo = libro.createCellStyle();
            estiloTitulo.setFont(fuente(true, 12, PaletaReporte.AZUL_INSTITUCIONAL));
            estiloTitulo.setVerticalAlignment(VerticalAlignment.CENTER);
            estiloTitulo.setBorderBottom(BorderStyle.MEDIUM);
            estiloTitulo.setBottomBorderColor(azul);
            this.titulo = estiloTitulo;

            XSSFCellStyle estiloEtiqueta = libro.createCellStyle();
            estiloEtiqueta.setFont(fuente(true, 9, PaletaReporte.GRIS_TEXTO));
            estiloEtiqueta.setFillForegroundColor(gris);
            estiloEtiqueta.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloEtiqueta.setAlignment(HorizontalAlignment.RIGHT);
            this.etiqueta = estiloEtiqueta;

            XSSFCellStyle estiloValor = libro.createCellStyle();
            estiloValor.setFont(fuente(false, 9, PaletaReporte.NEGRO_TEXTO));
            estiloValor.setFillForegroundColor(gris);
            estiloValor.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloValor.setAlignment(HorizontalAlignment.LEFT);
            this.valor = estiloValor;

            XSSFCellStyle estiloVacio = libro.createCellStyle();
            estiloVacio.setFont(fuente(false, 10, PaletaReporte.GRIS_TEXTO));
            this.vacio = estiloVacio;
        }

        private CellStyle cabecera(ColumnaReporte columna) {
            return cacheDatos.computeIfAbsent("cabecera|" + columna.alineacion(), clave -> {
                XSSFCellStyle estilo = relleno(azul, fuente(true, 10, PaletaReporte.BLANCO));
                estilo.setAlignment(alineacion(columna));
                estilo.setVerticalAlignment(VerticalAlignment.CENTER);
                estilo.setWrapText(true);
                return estilo;
            });
        }

        private CellStyle dato(ValorCelda valor, ColumnaReporte columna, boolean total) {
            String patron = formatoDe(valor);
            String clave = patron + '|' + columna.alineacion() + '|' + total;
            return cacheDatos.computeIfAbsent(clave, ignorada -> {
                XSSFCellStyle estilo = libro.createCellStyle();
                estilo.setFont(total ? fuenteNegrita : fuenteNormal);
                estilo.setDataFormat(formatos.getFormat(patron));
                estilo.setAlignment(alineacion(columna));
                estilo.setVerticalAlignment(VerticalAlignment.CENTER);
                if (total) {
                    estilo.setBorderTop(BorderStyle.THIN);
                    estilo.setTopBorderColor(azul);
                }
                return estilo;
            });
        }

        private XSSFCellStyle relleno(XSSFColor fondo, XSSFFont fuente) {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.setFont(fuente);
            estilo.setFillForegroundColor(fondo);
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            return estilo;
        }

        private XSSFFont fuente(boolean negrita, int puntos, java.awt.Color color) {
            XSSFFont fuente = libro.createFont();
            fuente.setFontName("Calibri");
            fuente.setFontHeightInPoints((short) puntos);
            fuente.setBold(negrita);
            if (color != null) {
                fuente.setColor(new XSSFColor(
                        PaletaReporte.rgb(color), libro.getStylesSource().getIndexedColors()));
            }
            return fuente;
        }
    }
}
