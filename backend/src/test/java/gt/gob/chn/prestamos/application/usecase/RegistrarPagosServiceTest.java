package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.FormaPago;
import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarPagoCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.CorrelativoPort;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
// LENIENT: las rutas de error cortan antes de consumir los dobles de correlativo y persistencia.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrarPagosService - abonos, saldos y liquidacion")
class RegistrarPagosServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 4, 5, 10, 15);
    private static final LocalDate DESEMBOLSO = LocalDate.of(2026, 3, 10);
    private static final ContextoOperacion CONTEXTO = new ContextoOperacion("cajero", "10.0.0.9");

    @Mock
    private PrestamoRepositorio prestamoRepositorio;

    @Mock
    private PagoRepositorio pagoRepositorio;

    @Mock
    private CorrelativoPort correlativoPort;

    @Mock
    private AuditoriaPort auditoriaPort;

    @Mock
    private RelojPort relojPort;

    private RegistrarPagosService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new RegistrarPagosService(prestamoRepositorio, pagoRepositorio, correlativoPort,
                auditoriaPort, relojPort);
    }

    @Test
    void registrar_guarda_el_recibo_con_los_saldos_y_actualiza_el_prestamo() {
        prepararEscenario(prestamoConSaldo("0.00"));

        PagoDetalle detalle = servicio.registrar(
                new RegistrarPagoCommand(20L, new BigDecimal("2500.00"), "Abono de cuota"), CONTEXTO);

        assertThat(detalle).isNotNull();
        assertThat(detalle.numeroPrestamo()).isEqualTo("PR-001-2026-000001-9");

        ArgumentCaptor<Pago> pagoCapturado = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepositorio).guardar(pagoCapturado.capture());
        Pago pago = pagoCapturado.getValue();
        assertThat(pago.getNumeroRecibo()).isEqualTo("RC-001-2026-000001-4");
        assertThat(pago.getPrestamoId()).isEqualTo(20L);
        assertThat(pago.getMonto()).isEqualByComparingTo("2500.00");
        assertThat(pago.getFormaPago()).isEqualTo(FormaPago.EFECTIVO);
        // El recibo es el comprobante del cliente: guarda el saldo antes y despues.
        assertThat(pago.getSaldoAnterior()).isEqualByComparingTo("10000.00");
        assertThat(pago.getSaldoPosterior()).isEqualByComparingTo("7500.00");
        assertThat(pago.getUsuarioRegistro()).isEqualTo("cajero");
        assertThat(pago.getFechaPago()).isEqualTo(AHORA);

        ArgumentCaptor<Prestamo> prestamoCapturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepositorio).guardar(prestamoCapturado.capture());
        assertThat(prestamoCapturado.getValue().getTotalPagado()).isEqualByComparingTo("2500.00");
        assertThat(prestamoCapturado.getValue().getSaldoPendiente()).isEqualByComparingTo("7500.00");
        assertThat(prestamoCapturado.getValue().getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);

        verify(auditoriaPort).registrar(eq("cajero"), eq(AccionesAuditoria.PAGO_REGISTRADO),
                eq(AccionesAuditoria.ENTIDAD_PAGO), eq("30"), anyString(), eq("10.0.0.9"));
        // Un abono parcial no liquida el prestamo, asi que no hay segunda huella.
        verify(auditoriaPort, never()).registrar(anyString(),
                eq(AccionesAuditoria.PRESTAMO_LIQUIDADO), anyString(), any(), any(), any());
    }

    @Test
    void el_pago_que_cubre_el_saldo_liquida_el_prestamo_y_lo_audita() {
        prepararEscenario(prestamoConSaldo("7500.00"));

        servicio.registrar(new RegistrarPagoCommand(20L, new BigDecimal("2500.00"),
                "Pago final"), CONTEXTO);

        ArgumentCaptor<Prestamo> prestamoCapturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepositorio).guardar(prestamoCapturado.capture());
        assertThat(prestamoCapturado.getValue().getEstado()).isEqualTo(EstadoPrestamo.LIQUIDADO);
        assertThat(prestamoCapturado.getValue().getSaldoPendiente()).isEqualByComparingTo("0.00");
        assertThat(prestamoCapturado.getValue().getPorcentajePagado()).isEqualByComparingTo("100.00");

        // La liquidacion es un hecho relevante del expediente: queda en la bitacora.
        verify(auditoriaPort).registrar(eq("cajero"), eq(AccionesAuditoria.PRESTAMO_LIQUIDADO),
                eq(AccionesAuditoria.ENTIDAD_PRESTAMO), eq("20"), anyString(), eq("10.0.0.9"));
    }

    @Test
    void registrar_un_monto_mayor_al_saldo_propaga_la_regla_de_negocio() {
        prepararEscenario(prestamoConSaldo("7500.00"));

        assertThatThrownBy(() -> servicio.registrar(
                new RegistrarPagoCommand(20L, new BigDecimal("9000.00"), null), CONTEXTO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2500.00");

        verify(pagoRepositorio, never()).guardar(any());
        verify(prestamoRepositorio, never()).guardar(any());
        verify(auditoriaPort, never()).registrar(anyString(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void registrar_sobre_un_prestamo_liquidado_propaga_la_regla_de_negocio() {
        Prestamo liquidado = Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L,
                new BigDecimal("9000.00"), 12, new BigDecimal("20.00"), new BigDecimal("833.33"),
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), EstadoPrestamo.LIQUIDADO,
                DESEMBOLSO, DESEMBOLSO.plusMonths(12), AHORA);
        prepararEscenario(liquidado);

        assertThatThrownBy(() -> servicio.registrar(
                new RegistrarPagoCommand(20L, new BigDecimal("100.00"), null), CONTEXTO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya esta liquidado");

        verify(pagoRepositorio, never()).guardar(any());
    }

    @Test
    void registrar_un_monto_no_positivo_propaga_la_validacion_del_dominio() {
        prepararEscenario(prestamoConSaldo("0.00"));

        assertThatThrownBy(() -> servicio.registrar(
                new RegistrarPagoCommand(20L, BigDecimal.ZERO, null), CONTEXTO))
                .isInstanceOf(ValidacionDominioException.class);

        verify(pagoRepositorio, never()).guardar(any());
    }

    @Test
    void registrar_sobre_un_prestamo_inexistente_lanza_no_encontrado() {
        when(prestamoRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(
                new RegistrarPagoCommand(99L, new BigDecimal("500.00"), null), CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pagoRepositorio, never()).guardar(any());
        verify(correlativoPort, never()).siguienteNumeroRecibo();
    }

    private void prepararEscenario(Prestamo prestamo) {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(prestamoRepositorio.buscarPorId(20L)).thenReturn(Optional.of(prestamo));
        when(correlativoPort.siguienteNumeroRecibo()).thenReturn("RC-001-2026-000001-4");
        when(prestamoRepositorio.guardar(any(Prestamo.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));
        when(pagoRepositorio.guardar(any(Pago.class)))
                .thenAnswer(llamada -> conId(llamada.getArgument(0)));
        when(pagoRepositorio.buscarDetallePorId(30L)).thenAnswer(llamada ->
                Optional.of(new PagoDetalle(conId(pagoDemo()), "PR-001-2026-000001-9", 5L,
                        "Maria Jose Lopez Garcia")));
    }

    /** Prestamo con Q10,000.00 de total a pagar y el abono acumulado que reciba la prueba. */
    private static Prestamo prestamoConSaldo(String totalPagado) {
        return Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L, new BigDecimal("9000.00"),
                12, new BigDecimal("20.00"), new BigDecimal("833.33"), new BigDecimal("10000.00"),
                new BigDecimal(totalPagado), EstadoPrestamo.VIGENTE, DESEMBOLSO,
                DESEMBOLSO.plusMonths(12), AHORA);
    }

    private static Pago conId(Pago pago) {
        return Pago.reconstituir(30L, pago.getNumeroRecibo(), pago.getPrestamoId(), pago.getMonto(),
                pago.getFechaPago(), pago.getFormaPago(), pago.getSaldoAnterior(),
                pago.getSaldoPosterior(), pago.getUsuarioRegistro(), pago.getObservaciones());
    }

    private static Pago pagoDemo() {
        return Pago.nuevo("RC-001-2026-000001-4", 20L, new BigDecimal("2500.00"), FormaPago.EFECTIVO,
                new BigDecimal("10000.00"), new BigDecimal("7500.00"), "cajero", "Abono de cuota",
                AHORA);
    }
}
