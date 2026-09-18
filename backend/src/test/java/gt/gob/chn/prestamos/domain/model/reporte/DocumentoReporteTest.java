package gt.gob.chn.prestamos.domain.model.reporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Modelo de reporte - documento, formatos y archivo generado")
class DocumentoReporteTest {

    private static final LocalDateTime GENERADO_EN = LocalDateTime.of(2026, 4, 5, 10, 15);

    @Nested
    @DisplayName("FormatoReporte")
    class Formatos {

        @Test
        @DisplayName("Cada formato conoce su extension y su tipo de contenido HTTP")
        void cada_formato_expone_su_extension_y_su_tipo_de_contenido() {
            assertThat(FormatoReporte.PDF.extension()).isEqualTo("pdf");
            assertThat(FormatoReporte.PDF.tipoContenido()).isEqualTo("application/pdf");
            assertThat(FormatoReporte.EXCEL.extension()).isEqualTo("xlsx");
            assertThat(FormatoReporte.EXCEL.tipoContenido()).isEqualTo(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Test
        @DisplayName("La extension se resuelve sin distinguir mayusculas ni espacios")
        void la_extension_se_resuelve_sin_distinguir_mayusculas() {
            // La extension llega desde la URL: ".PDF" y ".pdf" son el mismo recurso.
            assertThat(FormatoReporte.desdeExtension("pdf")).isEqualTo(FormatoReporte.PDF);
            assertThat(FormatoReporte.desdeExtension("PDF")).isEqualTo(FormatoReporte.PDF);
            assertThat(FormatoReporte.desdeExtension("xlsx")).isEqualTo(FormatoReporte.EXCEL);
            assertThat(FormatoReporte.desdeExtension("XLSX")).isEqualTo(FormatoReporte.EXCEL);
        }

        @Test
        @DisplayName("Una extension desconocida se rechaza enumerando los valores permitidos")
        void una_extension_desconocida_se_rechaza_con_los_valores_permitidos() {
            // El mensaje es lo unico que ve quien llama a la API: debe decir que si acepta.
            assertThatThrownBy(() -> FormatoReporte.desdeExtension("csv"))
                    .isInstanceOf(ValidacionDominioException.class)
                    .hasMessageContaining("pdf")
                    .hasMessageContaining("xlsx");
        }

        @Test
        @DisplayName("Una extension ausente se rechaza igual que una desconocida")
        void una_extension_ausente_se_rechaza() {
            assertThatThrownBy(() -> FormatoReporte.desdeExtension(null))
                    .isInstanceOf(ValidacionDominioException.class);
        }
    }

    @Nested
    @DisplayName("DocumentoReporte")
    class Documento {

        @Test
        @DisplayName("Un documento con totales alineados con las columnas se construye sin error")
        void un_documento_coherente_se_construye() {
            assertThatCode(DocumentoReporteTest::documentoValido).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("La fila de totales puede venir vacia cuando el reporte no suma nada")
        void la_fila_de_totales_puede_venir_vacia() {
            assertThatCode(() -> documento(columnas(), filas(), List.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una fila de totales con mas o menos celdas que columnas se rechaza")
        void los_totales_deben_tener_tantas_celdas_como_columnas() {
            // Si no cuadran, el adaptador escribiria el total debajo de otra columna:
            // un error de cuadre que nadie detectaria leyendo el archivo.
            List<ValorCelda> totalesCortos = List.of(
                    new ValorCelda.Texto("Totales"),
                    new ValorCelda.Moneda(new BigDecimal("100.00")));

            assertThatThrownBy(() -> documento(columnas(), filas(), totalesCortos))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Un titulo ausente se rechaza: el documento no puede imprimirse sin encabezado")
        void el_titulo_es_obligatorio() {
            assertThatThrownBy(() -> new DocumentoReporte(null, "Prestamo PR-001-2026-000001-9",
                    List.of(new ParDato("Prestamo", "PR-001-2026-000001-9")), columnas(), filas(),
                    List.of(), "Nota al pie", "admin", GENERADO_EN, "reporte-demo"))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Las columnas son obligatorias: sin cabecera no hay tabla que dibujar")
        void las_columnas_son_obligatorias() {
            assertThatThrownBy(() -> new DocumentoReporte("Plan de amortizacion",
                    "Prestamo PR-001-2026-000001-9",
                    List.of(new ParDato("Prestamo", "PR-001-2026-000001-9")), null, filas(),
                    List.of(), "Nota al pie", "admin", GENERADO_EN, "reporte-demo"))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Un documento sin filas es valido: un reporte vacio sigue siendo una constancia")
        void un_documento_sin_filas_es_valido() {
            DocumentoReporte documento = documento(columnas(), List.of(), List.of());

            assertThat(documento.filas()).isEmpty();
            assertThat(documento.titulo()).isEqualTo("Plan de amortizacion");
        }

        @Test
        @DisplayName("Las listas del documento quedan inmutables una vez construido")
        void las_listas_del_documento_son_inmutables() {
            DocumentoReporte documento = documentoValido();

            assertThatThrownBy(() -> documento.datosEncabezado()
                    .add(new ParDato("Intruso", "valor")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> documento.columnas()
                    .add(new ColumnaReporte("Intrusa", ColumnaReporte.Alineacion.IZQUIERDA)))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> documento.filas()
                    .add(new FilaReporte(List.of(new ValorCelda.Texto("intrusa")))))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> documento.totales()
                    .add(new ValorCelda.Texto("intruso")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Modificar la lista original despues de construir no altera el documento")
        void el_documento_copia_las_listas_que_recibe() {
            List<FilaReporte> mutables = new ArrayList<>(filas());
            DocumentoReporte documento = documento(columnas(), mutables, List.of());

            mutables.clear();

            assertThat(documento.filas()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("FilaReporte y ValorCelda")
    class Filas {

        @Test
        @DisplayName("Una fila sin celdas se rechaza: no hay nada que escribir")
        void las_celdas_son_obligatorias() {
            assertThatThrownBy(() -> new FilaReporte(null))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Las celdas de la fila quedan inmutables")
        void las_celdas_de_la_fila_son_inmutables() {
            FilaReporte fila = new FilaReporte(List.of(new ValorCelda.Entero(1)));

            assertThatThrownBy(() -> fila.celdas().add(new ValorCelda.Texto("intrusa")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Cada celda conserva su valor tipado para que Excel escriba numeros de verdad")
        void cada_celda_conserva_su_valor_tipado() {
            // El tipo es lo que permite sumar en Excel y alinear a la derecha en PDF:
            // si el dominio entregara texto ya formateado, esa informacion se perderia.
            assertThat(new ValorCelda.Texto("EFECTIVO").valor()).isEqualTo("EFECTIVO");
            assertThat(new ValorCelda.Entero(7).valor()).isEqualTo(7);
            assertThat(new ValorCelda.Moneda(new BigDecimal("1234.56")).valor())
                    .isEqualByComparingTo("1234.56");
            assertThat(new ValorCelda.Porcentaje(new BigDecimal("12.50")).valor())
                    .isEqualByComparingTo("12.50");
            assertThat(new ValorCelda.Fecha(LocalDate.of(2026, 3, 10)).valor())
                    .isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(new ValorCelda.FechaHora(GENERADO_EN).valor()).isEqualTo(GENERADO_EN);
        }

        @Test
        @DisplayName("ValorCelda es sellada: los adaptadores pueden resolverla por patrones")
        void valor_celda_es_sellada() {
            // La interfaz sellada garantiza que un switch por patrones cubra todos los casos
            // sin rama por defecto: si manana se agrega un tipo, el compilador avisa.
            assertThat(ValorCelda.class.isSealed()).isTrue();
            assertThat(ValorCelda.class.getPermittedSubclasses()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("ArchivoGenerado")
    class Archivos {

        @Test
        @DisplayName("El archivo lleva nombre con extension, tipo de contenido y bytes")
        void el_archivo_lleva_nombre_tipo_y_contenido() {
            ArchivoGenerado archivo = new ArchivoGenerado("plan-amortizacion-PR-001-2026-000001-9.pdf",
                    FormatoReporte.PDF.tipoContenido(), new byte[] {37, 80, 68, 70});

            assertThat(archivo.nombre()).endsWith(".pdf");
            assertThat(archivo.tipoContenido()).isEqualTo("application/pdf");
            assertThat(archivo.contenido()).hasSize(4);
        }

        @Test
        @DisplayName("Un archivo sin bytes se rechaza: descargar un documento vacio es un fallo")
        void un_archivo_con_contenido_vacio_se_rechaza() {
            assertThatThrownBy(() -> new ArchivoGenerado("vacio.pdf", "application/pdf", new byte[0]))
                    .isInstanceOf(ValidacionDominioException.class);
        }

        @Test
        @DisplayName("Un archivo sin contenido se rechaza igual que uno vacio")
        void un_archivo_sin_contenido_se_rechaza() {
            assertThatThrownBy(() -> new ArchivoGenerado("vacio.pdf", "application/pdf", null))
                    .isInstanceOf(ValidacionDominioException.class);
        }
    }

    /** Documento minimo pero coherente: tres columnas y una fila de totales alineada. */
    private static DocumentoReporte documentoValido() {
        return documento(columnas(), filas(), List.of(
                new ValorCelda.Texto("Totales"),
                new ValorCelda.Moneda(new BigDecimal("2000.00")),
                new ValorCelda.Texto("")));
    }

    private static DocumentoReporte documento(List<ColumnaReporte> columnas, List<FilaReporte> filas,
                                              List<ValorCelda> totales) {
        return new DocumentoReporte("Plan de amortizacion", "Prestamo PR-001-2026-000001-9",
                List.of(new ParDato("Prestamo", "PR-001-2026-000001-9"),
                        new ParDato("Cliente", "Maria Jose Lopez Garcia")),
                columnas, filas, totales,
                "Cuotas niveladas sobre saldos (sistema frances).", "admin", GENERADO_EN,
                "plan-amortizacion-PR-001-2026-000001-9");
    }

    private static List<ColumnaReporte> columnas() {
        return List.of(
                new ColumnaReporte("No.", ColumnaReporte.Alineacion.CENTRO),
                new ColumnaReporte("Cuota", ColumnaReporte.Alineacion.DERECHA),
                new ColumnaReporte("Observaciones", ColumnaReporte.Alineacion.IZQUIERDA));
    }

    private static List<FilaReporte> filas() {
        return List.of(
                new FilaReporte(List.of(new ValorCelda.Entero(1),
                        new ValorCelda.Moneda(new BigDecimal("1000.00")),
                        new ValorCelda.Texto("Primera cuota"))),
                new FilaReporte(List.of(new ValorCelda.Entero(2),
                        new ValorCelda.Moneda(new BigDecimal("1000.00")),
                        new ValorCelda.Texto("Segunda cuota"))));
    }
}
