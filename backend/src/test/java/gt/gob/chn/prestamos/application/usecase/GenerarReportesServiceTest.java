package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.FormaPago;
import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.DocumentoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.model.reporte.ParDato;
import gt.gob.chn.prestamos.domain.model.reporte.ValorCelda;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.GeneradorReportePort;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
// LENIENT: el escenario comun prepara generadores y reloj; las rutas de error cortan antes de usarlos.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GenerarReportesService - documento, totales y auditoria de la descarga")
class GenerarReportesServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 4, 5, 10, 15);
    private static final LocalDate DESEMBOLSO = LocalDate.of(2026, 3, 10);
    private static final ContextoOperacion CONTEXTO = new ContextoOperacion("cajero", "10.0.0.9");

    private static final long PRESTAMO_ID = 20L;
    private static final String NUMERO_PRESTAMO = "PR-001-2026-000001-9";
    private static final BigDecimal MONTO_APROBADO = new BigDecimal("100000.00");
    private static final int PLAZO_MESES = 12;
    private static final BigDecimal TASA_ANUAL = new BigDecimal("12.00");

    /** Etiquetas del bloque de encabezado, en el orden acordado para los dos reportes. */
    private static final List<String> ETIQUETAS_ENCABEZADO = List.of(
            "Prestamo", "Solicitud de origen", "Cliente", "DPI", "Estado", "Monto aprobado",
            "Plazo", "Tasa de interes anual", "Cuota mensual", "Total a pagar", "Total pagado",
            "Saldo pendiente", "Fecha de desembolso", "Fecha de vencimiento");

    // Real a proposito: solo con el plan verdadero se puede afirmar que los totales cuadran al centavo.
    private final CalculadoraAmortizacion calculadora = new CalculadoraAmortizacion();

    @Mock
    private PrestamoRepositorio prestamoRepositorio;

    @Mock
    private PagoRepositorio pagoRepositorio;

    @Mock
    private RelojPort relojPort;

    @Mock
    private AuditoriaPort auditoriaPort;

    @Mock
    private GeneradorReportePort generadorPdf;

    @Mock
    private GeneradorReportePort generadorExcel;

    private GenerarReportesService servicio;

    @BeforeEach
    void prepararEscenario() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(generadorPdf.formato()).thenReturn(FormatoReporte.PDF);
        when(generadorExcel.formato()).thenReturn(FormatoReporte.EXCEL);
        // Los dobles imitan al adaptador real: nombran el archivo con la base que trae el
        // documento, para poder comprobar que el caso de uso propone el nombre correcto.
        when(generadorPdf.generar(any())).thenAnswer(
                llamada -> archivo(llamada.getArgument(0), FormatoReporte.PDF));
        when(generadorExcel.generar(any())).thenAnswer(
                llamada -> archivo(llamada.getArgument(0), FormatoReporte.EXCEL));
        when(prestamoRepositorio.buscarDetallePorId(PRESTAMO_ID))
                .thenReturn(Optional.of(detalleDelPrestamo()));
        when(pagoRepositorio.listarDetallePorPrestamo(PRESTAMO_ID)).thenReturn(pagosDesordenados());

        servicio = new GenerarReportesService(prestamoRepositorio, pagoRepositorio, calculadora,
                relojPort, auditoriaPort, List.of(generadorPdf, generadorExcel));
    }

    @Test
    @DisplayName("El plan lleva una fila por mes del plazo y la fila de totales cuadra al centavo")
    void el_plan_de_amortizacion_tiene_una_fila_por_cuota_y_totales_que_cuadran() {
        ArchivoGenerado resultado = servicio.planAmortizacion(PRESTAMO_ID, FormatoReporte.PDF, CONTEXTO);

        assertThat(resultado.nombre()).isEqualTo("plan-amortizacion-" + NUMERO_PRESTAMO + ".pdf");

        DocumentoReporte documento = documentoEnviadoA(generadorPdf);
        // Los rotulos impresos llevan tildes; las comparaciones las normalizan para que la
        // prueba afirme sobre el contenido del reporte y no sobre su ortografia.
        assertThat(sinTildes(documento.titulo())).isEqualTo("Plan de amortizacion");
        assertThat(documento.subtitulo()).contains(NUMERO_PRESTAMO);
        assertThat(documento.nombreArchivoBase()).isEqualTo("plan-amortizacion-" + NUMERO_PRESTAMO);
        assertThat(documento.columnas()).hasSize(6);
        assertThat(documento.filas()).hasSize(PLAZO_MESES);

        // Los totales del documento deben reproducir las condiciones pactadas: el capital
        // amortizado es el monto aprobado y la suma de cuotas, el total a pagar.
        PlanAmortizacion plan = calculadora.calcular(MONTO_APROBADO, PLAZO_MESES, TASA_ANUAL);
        assertThat(documento.totales()).hasSameSizeAs(documento.columnas());
        assertThat(texto(documento.totales().get(0))).contains("Totales");
        assertThat(moneda(documento.totales().get(3))).isEqualByComparingTo(MONTO_APROBADO);
        assertThat(moneda(documento.totales().get(2)))
                .isCloseTo(plan.montoTotal(), within(new BigDecimal("0.02")));
        assertThat(moneda(documento.totales().get(4)))
                .isCloseTo(plan.totalIntereses(), within(new BigDecimal("0.02")));
        // Saldo inicial y saldo final no se suman: van vacios para no inventar un total.
        assertThat(texto(documento.totales().get(1))).isEmpty();
        assertThat(texto(documento.totales().get(5))).isEmpty();

        // Y la fila de totales tiene que ser la suma de lo impreso, no un calculo aparte.
        assertThat(sumarColumna(documento, 3)).isEqualByComparingTo(moneda(documento.totales().get(3)));
        assertThat(sumarColumna(documento, 2)).isEqualByComparingTo(moneda(documento.totales().get(2)));

        assertThat(sinTildes(documento.notaPie())).containsIgnoringCase("frances");
    }

    @Test
    @DisplayName("Las filas del plan van numeradas y con el detalle tipado de cada cuota")
    void las_filas_del_plan_repiten_el_plan_calculado() {
        servicio.planAmortizacion(PRESTAMO_ID, FormatoReporte.PDF, CONTEXTO);

        DocumentoReporte documento = documentoEnviadoA(generadorPdf);
        PlanAmortizacion plan = calculadora.calcular(MONTO_APROBADO, PLAZO_MESES, TASA_ANUAL);

        for (int indice = 0; indice < PLAZO_MESES; indice++) {
            List<ValorCelda> celdas = documento.filas().get(indice).celdas();
            assertThat(celdas).hasSameSizeAs(documento.columnas());
            assertThat(entero(celdas.get(0))).isEqualTo(indice + 1);
            assertThat(moneda(celdas.get(2)))
                    .isEqualByComparingTo(plan.cuotas().get(indice).cuota());
            assertThat(moneda(celdas.get(3)))
                    .isEqualByComparingTo(plan.cuotas().get(indice).abonoCapital());
        }
        assertThat(moneda(documento.filas().get(PLAZO_MESES - 1).celdas().get(5)))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("El encabezado del documento lleva los catorce datos del prestamo y su cliente")
    void el_documento_lleva_el_bloque_de_encabezado_completo() {
        servicio.planAmortizacion(PRESTAMO_ID, FormatoReporte.PDF, CONTEXTO);

        DocumentoReporte documento = documentoEnviadoA(generadorPdf);

        // Requisito del usuario: el encabezado identifica prestamo y cliente en cada pagina.
        assertThat(documento.datosEncabezado()).hasSize(ETIQUETAS_ENCABEZADO.size());
        assertThat(documento.datosEncabezado().stream()
                .map(ParDato::etiqueta)
                .map(GenerarReportesServiceTest::sinTildes)
                .toList())
                .containsExactlyElementsOf(ETIQUETAS_ENCABEZADO.stream()
                        .map(GenerarReportesServiceTest::sinTildes)
                        .toList());
        assertThat(documento.datosEncabezado()).extracting(ParDato::valor)
                .contains(NUMERO_PRESTAMO, "SC-001-2026-000001-3", "Maria Jose Lopez Garcia",
                        "2547896301234");
        assertThat(valorDe(documento, "Monto aprobado")).contains("100,000.00");

        assertThat(documento.generadoPor()).isEqualTo("cajero");
        assertThat(documento.generadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("El historial ordena del pago mas antiguo al mas reciente y suma los montos")
    void el_historial_de_pagos_va_del_mas_antiguo_al_mas_reciente() {
        servicio.historialPagos(PRESTAMO_ID, FormatoReporte.EXCEL, CONTEXTO);

        DocumentoReporte documento = documentoEnviadoA(generadorExcel);
        assertThat(documento.titulo()).isEqualTo("Historial de pagos");
        assertThat(documento.nombreArchivoBase()).isEqualTo("historial-pagos-" + NUMERO_PRESTAMO);
        assertThat(documento.columnas()).hasSize(8);
        assertThat(documento.filas()).hasSize(3);

        // Desordenados a proposito: el estado de cuenta se lee en orden cronologico.
        assertThat(documento.filas().stream()
                .map(fila -> texto(fila.celdas().get(0)))
                .toList())
                .containsExactly("RC-001-2026-000001-4", "RC-001-2026-000002-2", "RC-001-2026-000003-0");
        assertThat(documento.filas().stream()
                .map(fila -> fechaHora(fila.celdas().get(1)))
                .toList())
                .isSorted();

        assertThat(documento.totales()).hasSameSizeAs(documento.columnas());
        assertThat(texto(documento.totales().get(0))).contains("Total pagado");
        assertThat(moneda(documento.totales().get(2))).isEqualByComparingTo("4500.00");
        assertThat(sumarColumna(documento, 2)).isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("Un prestamo sin pagos genera el documento igual, vacio y con la nota que lo dice")
    void un_prestamo_sin_pagos_genera_el_documento_con_la_nota_correspondiente() {
        when(pagoRepositorio.listarDetallePorPrestamo(PRESTAMO_ID)).thenReturn(List.of());

        ArchivoGenerado resultado = servicio.historialPagos(PRESTAMO_ID, FormatoReporte.PDF, CONTEXTO);

        assertThat(resultado).isNotNull();
        DocumentoReporte documento = documentoEnviadoA(generadorPdf);
        // Un reporte vacio es una respuesta valida: sirve de constancia de que no hay abonos.
        assertThat(documento.filas()).isEmpty();
        assertThat(documento.notaPie()).containsIgnoringCase("no registra pagos");
        assertThat(documento.datosEncabezado()).hasSize(ETIQUETAS_ENCABEZADO.size());
    }

    @Test
    @DisplayName("Un prestamo inexistente no genera archivo alguno")
    void un_prestamo_inexistente_lanza_no_encontrado() {
        when(prestamoRepositorio.buscarDetallePorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.planAmortizacion(99L, FormatoReporte.PDF, CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.historialPagos(99L, FormatoReporte.EXCEL, CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(generadorPdf, never()).generar(any());
        verify(generadorExcel, never()).generar(any());
        verify(auditoriaPort, never()).registrar(anyString(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    @DisplayName("La descarga queda en la bitacora con la accion y el formato exportado")
    void la_descarga_se_audita_con_la_accion_y_el_formato() {
        servicio.historialPagos(PRESTAMO_ID, FormatoReporte.EXCEL, CONTEXTO);

        // Exportar datos de clientes es una operacion sensible: debe quedar registrada
        // con quien la hizo, desde donde y en que formato se llevo la informacion.
        ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
        verify(auditoriaPort).registrar(eq("cajero"), eq(AccionesAuditoria.REPORTE_DESCARGADO),
                eq(AccionesAuditoria.ENTIDAD_PRESTAMO), eq(String.valueOf(PRESTAMO_ID)),
                detalle.capture(), eq("10.0.0.9"));

        String texto = detalle.getValue().toLowerCase(Locale.ROOT);
        assertThat(texto).satisfiesAnyOf(
                valor -> assertThat(valor).contains("excel"),
                valor -> assertThat(valor).contains("xlsx"));
    }

    @Test
    @DisplayName("Cada formato lo materializa su generador y nunca el otro")
    void se_delega_unicamente_en_el_generador_del_formato_pedido() {
        servicio.planAmortizacion(PRESTAMO_ID, FormatoReporte.PDF, CONTEXTO);

        verify(generadorPdf).generar(any());
        verify(generadorExcel, never()).generar(any());

        servicio.planAmortizacion(PRESTAMO_ID, FormatoReporte.EXCEL, CONTEXTO);

        verify(generadorExcel).generar(any());
        verify(generadorPdf).generar(any());
    }

    @Test
    @DisplayName("Si falta el generador de un formato el servicio no arranca")
    void sin_generador_para_un_formato_el_servicio_falla_al_construirse() {
        // Fallo temprano: es preferible que el contexto de Spring no levante que descubrir
        // en produccion que el boton de Excel responde con un error.
        assertThatThrownBy(() -> new GenerarReportesService(prestamoRepositorio, pagoRepositorio,
                calculadora, relojPort, auditoriaPort, List.of(generadorPdf)))
                .isInstanceOf(IllegalStateException.class);
    }

    private DocumentoReporte documentoEnviadoA(GeneradorReportePort generador) {
        ArgumentCaptor<DocumentoReporte> captor = ArgumentCaptor.forClass(DocumentoReporte.class);
        verify(generador).generar(captor.capture());
        return captor.getValue();
    }

    private static BigDecimal sumarColumna(DocumentoReporte documento, int columna) {
        return documento.filas().stream()
                .map(fila -> moneda(fila.celdas().get(columna)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String valorDe(DocumentoReporte documento, String etiqueta) {
        return documento.datosEncabezado().stream()
                .filter(par -> sinTildes(par.etiqueta()).equalsIgnoreCase(sinTildes(etiqueta)))
                .map(ParDato::valor)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Falta el dato de encabezado: " + etiqueta));
    }

    private static BigDecimal moneda(ValorCelda celda) {
        if (celda instanceof ValorCelda.Moneda moneda) {
            return moneda.valor();
        }
        throw new AssertionError("Se esperaba una celda de moneda y llego: " + celda);
    }

    private static int entero(ValorCelda celda) {
        if (celda instanceof ValorCelda.Entero entero) {
            return entero.valor();
        }
        throw new AssertionError("Se esperaba una celda entera y llego: " + celda);
    }

    private static String texto(ValorCelda celda) {
        if (celda instanceof ValorCelda.Texto texto) {
            return texto.valor() == null ? "" : texto.valor();
        }
        throw new AssertionError("Se esperaba una celda de texto y llego: " + celda);
    }

    private static LocalDateTime fechaHora(ValorCelda celda) {
        if (celda instanceof ValorCelda.FechaHora fechaHora) {
            return fechaHora.valor();
        }
        throw new AssertionError("Se esperaba una celda de fecha y hora y llego: " + celda);
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private PrestamoDetalle detalleDelPrestamo() {
        PlanAmortizacion plan = calculadora.calcular(MONTO_APROBADO, PLAZO_MESES, TASA_ANUAL);
        Prestamo prestamo = Prestamo.reconstituir(PRESTAMO_ID, NUMERO_PRESTAMO, 10L, 5L,
                MONTO_APROBADO, PLAZO_MESES, TASA_ANUAL, plan.cuotaMensual(), plan.montoTotal(),
                new BigDecimal("4500.00"), EstadoPrestamo.VIGENTE, DESEMBOLSO,
                DESEMBOLSO.plusMonths(PLAZO_MESES), AHORA);
        return new PrestamoDetalle(prestamo, "SC-001-2026-000001-3", "Maria Jose Lopez Garcia",
                "2547896301234");
    }

    private static List<PagoDetalle> pagosDesordenados() {
        return List.of(
                pago(32L, "RC-001-2026-000003-0", "1500.00", AHORA.plusDays(20),
                        "103500.00", "102000.00"),
                pago(30L, "RC-001-2026-000001-4", "1500.00", AHORA,
                        "106500.00", "105000.00"),
                pago(31L, "RC-001-2026-000002-2", "1500.00", AHORA.plusDays(10),
                        "105000.00", "103500.00"));
    }

    private static PagoDetalle pago(Long id, String recibo, String monto, LocalDateTime fecha,
                                    String saldoAnterior, String saldoPosterior) {
        Pago registrado = Pago.reconstituir(id, recibo, PRESTAMO_ID, new BigDecimal(monto), fecha,
                FormaPago.EFECTIVO, new BigDecimal(saldoAnterior), new BigDecimal(saldoPosterior),
                "cajero", "Abono de cuota");
        return new PagoDetalle(registrado, NUMERO_PRESTAMO, 5L, "Maria Jose Lopez Garcia");
    }

    private static ArchivoGenerado archivo(DocumentoReporte documento, FormatoReporte formato) {
        return new ArchivoGenerado(documento.nombreArchivoBase() + "." + formato.extension(),
                formato.tipoContenido(), new byte[] {1, 2, 3, 4});
    }
}
