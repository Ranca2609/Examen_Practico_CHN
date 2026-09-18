package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.ResolucionSolicitud;
import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.AprobarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.CrearSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.RechazarSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.in.command.SimularSolicitudCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.domain.port.out.CorrelativoPort;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.SolicitudPrestamoRepositorio;
import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.EvaluadorCapacidadPago;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import gt.gob.chn.prestamos.domain.service.ResultadoSimulacion;
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
// LENIENT: el escenario comun prepara todos los dobles y no todas las rutas de negocio los consumen.
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GestionarSolicitudesService - resolucion de solicitudes y alta del prestamo")
class GestionarSolicitudesServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final LocalDate HOY = LocalDate.of(2026, 3, 10);
    private static final ContextoOperacion CONTEXTO = new ContextoOperacion("analista", "10.0.0.5");
    private static final String MOTIVO = "Capacidad de pago suficiente y sin mora reportada";

    // Servicios de dominio reales: se verifica la aritmetica del credito, no solo la orquestacion.
    private final CalculadoraAmortizacion calculadora = new CalculadoraAmortizacion();
    private final EvaluadorCapacidadPago evaluador = new EvaluadorCapacidadPago();

    @Mock
    private ClienteRepositorio clienteRepositorio;

    @Mock
    private SolicitudPrestamoRepositorio solicitudRepositorio;

    @Mock
    private PrestamoRepositorio prestamoRepositorio;

    @Mock
    private CorrelativoPort correlativoPort;

    @Mock
    private AuditoriaPort auditoriaPort;

    @Mock
    private RelojPort relojPort;

    private GestionarSolicitudesService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new GestionarSolicitudesService(clienteRepositorio, solicitudRepositorio,
                prestamoRepositorio, correlativoPort, auditoriaPort, relojPort, calculadora,
                evaluador);
    }

    @Test
    void crear_asigna_el_correlativo_deja_la_solicitud_en_proceso_y_audita() {
        prepararEscenario();

        SolicitudDetalle detalle = servicio.crear(comandoCreacion(), CONTEXTO);

        assertThat(detalle).isNotNull();

        ArgumentCaptor<SolicitudPrestamo> capturada = ArgumentCaptor.forClass(SolicitudPrestamo.class);
        verify(solicitudRepositorio).guardar(capturada.capture());
        assertThat(capturada.getValue().getNumeroSolicitud()).isEqualTo("SC-001-2026-000001-3");
        assertThat(capturada.getValue().getEstado()).isEqualTo(EstadoSolicitud.EN_PROCESO);
        assertThat(capturada.getValue().getClienteId()).isEqualTo(5L);
        assertThat(capturada.getValue().getMontoSolicitado()).isEqualByComparingTo("100000.00");
        assertThat(capturada.getValue().getFechaSolicitud()).isEqualTo(AHORA);
        assertThat(capturada.getValue().getResolucion()).isNull();

        verify(auditoriaPort).registrar(eq("analista"), eq(AccionesAuditoria.SOLICITUD_CREADA),
                eq(AccionesAuditoria.ENTIDAD_SOLICITUD), eq("10"), anyString(), eq("10.0.0.5"));
        // Crear una solicitud no desembolsa nada.
        verify(prestamoRepositorio, never()).guardar(any());
    }

    @Test
    void crear_para_un_cliente_inexistente_lanza_no_encontrado() {
        when(clienteRepositorio.buscarPorId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(comandoCreacion(), CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(solicitudRepositorio, never()).guardar(any());
    }

    @Test
    void aprobar_resuelve_la_solicitud_y_crea_el_prestamo_con_la_cuota_calculada() {
        prepararEscenario();
        when(solicitudRepositorio.buscarPorId(10L)).thenReturn(Optional.of(solicitudEnProceso()));

        servicio.aprobar(10L, new AprobarSolicitudCommand(new BigDecimal("80000.00"), 24,
                new BigDecimal("14.00"), MOTIVO), CONTEXTO);

        PlanAmortizacion planEsperado = new CalculadoraAmortizacion()
                .calcular(new BigDecimal("80000.00"), 24, new BigDecimal("14.00"));

        ArgumentCaptor<Prestamo> prestamoCapturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepositorio).guardar(prestamoCapturado.capture());
        Prestamo prestamo = prestamoCapturado.getValue();
        assertThat(prestamo.getNumeroPrestamo()).isEqualTo("PR-001-2026-000001-9");
        assertThat(prestamo.getSolicitudId()).isEqualTo(10L);
        assertThat(prestamo.getClienteId()).isEqualTo(5L);
        assertThat(prestamo.getMontoAprobado()).isEqualByComparingTo("80000.00");
        assertThat(prestamo.getPlazoMeses()).isEqualTo(24);
        assertThat(prestamo.getTasaInteresAnual()).isEqualByComparingTo("14.00");
        assertThat(prestamo.getCuotaMensual()).isEqualByComparingTo(planEsperado.cuotaMensual());
        assertThat(prestamo.getMontoTotalAPagar()).isEqualByComparingTo(planEsperado.montoTotal());
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(prestamo.getTotalPagado()).isEqualByComparingTo("0.00");
        assertThat(prestamo.getFechaDesembolso()).isEqualTo(HOY);
        assertThat(prestamo.getFechaVencimiento()).isEqualTo(HOY.plusMonths(24));

        ArgumentCaptor<SolicitudPrestamo> solicitudCapturada =
                ArgumentCaptor.forClass(SolicitudPrestamo.class);
        verify(solicitudRepositorio).guardar(solicitudCapturada.capture());
        assertThat(solicitudCapturada.getValue().getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(solicitudCapturada.getValue().getResolucion().usuarioResolucion())
                .isEqualTo("analista");
        assertThat(solicitudCapturada.getValue().getResolucion().montoAprobado())
                .isEqualByComparingTo("80000.00");
        assertThat(solicitudCapturada.getValue().getResolucion().motivo()).isEqualTo(MOTIVO);

        // La aprobacion deja dos huellas: la resolucion y el prestamo generado.
        verify(auditoriaPort).registrar(eq("analista"), eq(AccionesAuditoria.SOLICITUD_APROBADA),
                eq(AccionesAuditoria.ENTIDAD_SOLICITUD), eq("10"), anyString(), eq("10.0.0.5"));
        verify(auditoriaPort).registrar(eq("analista"), eq(AccionesAuditoria.PRESTAMO_CREADO),
                eq(AccionesAuditoria.ENTIDAD_PRESTAMO), any(), anyString(), eq("10.0.0.5"));
    }

    @Test
    void aprobar_con_campos_nulos_toma_las_condiciones_solicitadas() {
        prepararEscenario();
        when(solicitudRepositorio.buscarPorId(10L)).thenReturn(Optional.of(solicitudEnProceso()));

        // Aprobacion "tal como se solicito": el analista no ajusta monto, plazo ni tasa.
        servicio.aprobar(10L, new AprobarSolicitudCommand(null, null, null, null), CONTEXTO);

        ArgumentCaptor<Prestamo> prestamoCapturado = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepositorio).guardar(prestamoCapturado.capture());
        assertThat(prestamoCapturado.getValue().getMontoAprobado()).isEqualByComparingTo("100000.00");
        assertThat(prestamoCapturado.getValue().getPlazoMeses()).isEqualTo(12);
        assertThat(prestamoCapturado.getValue().getTasaInteresAnual()).isEqualByComparingTo("12.00");
        // Q100,000 a 12 meses al 12% anual -> cuota de Q8,884.88
        assertThat(prestamoCapturado.getValue().getCuotaMensual()).isEqualByComparingTo("8884.88");
    }

    @Test
    void aprobar_una_solicitud_ya_resuelta_propaga_la_regla_de_negocio() {
        prepararEscenario();
        when(solicitudRepositorio.buscarPorId(10L)).thenReturn(Optional.of(solicitudResuelta()));

        assertThatThrownBy(() -> servicio.aprobar(10L, new AprobarSolicitudCommand(
                new BigDecimal("80000.00"), 24, new BigDecimal("14.00"), MOTIVO), CONTEXTO))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya fue resuelta");

        verify(prestamoRepositorio, never()).guardar(any());
        verify(solicitudRepositorio, never()).guardar(any());
    }

    @Test
    void aprobar_una_solicitud_que_ya_genero_prestamo_propaga_la_regla_de_negocio() {
        prepararEscenario();
        when(solicitudRepositorio.buscarPorId(10L)).thenReturn(Optional.of(solicitudEnProceso()));
        when(prestamoRepositorio.buscarPorSolicitudId(10L)).thenReturn(Optional.of(prestamoExistente()));

        assertThatThrownBy(() -> servicio.aprobar(10L, new AprobarSolicitudCommand(
                new BigDecimal("80000.00"), 24, new BigDecimal("14.00"), MOTIVO), CONTEXTO))
                .isInstanceOf(ReglaNegocioException.class);

        verify(prestamoRepositorio, never()).guardar(any());
    }

    @Test
    void aprobar_una_solicitud_inexistente_lanza_no_encontrado() {
        when(solicitudRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(99L, new AprobarSolicitudCommand(
                new BigDecimal("80000.00"), 24, new BigDecimal("14.00"), MOTIVO), CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazar_registra_el_motivo_y_no_crea_prestamo() {
        prepararEscenario();
        when(solicitudRepositorio.buscarPorId(10L)).thenReturn(Optional.of(solicitudEnProceso()));
        String motivo = "Ingresos insuficientes para la cuota solicitada";

        servicio.rechazar(10L, new RechazarSolicitudCommand(motivo), CONTEXTO);

        ArgumentCaptor<SolicitudPrestamo> capturada = ArgumentCaptor.forClass(SolicitudPrestamo.class);
        verify(solicitudRepositorio).guardar(capturada.capture());
        assertThat(capturada.getValue().getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(capturada.getValue().getResolucion().motivo()).isEqualTo(motivo);
        assertThat(capturada.getValue().getResolucion().usuarioResolucion()).isEqualTo("analista");
        assertThat(capturada.getValue().getResolucion().montoAprobado()).isNull();

        verify(prestamoRepositorio, never()).guardar(any());
        verify(auditoriaPort).registrar(eq("analista"), eq(AccionesAuditoria.SOLICITUD_RECHAZADA),
                eq(AccionesAuditoria.ENTIDAD_SOLICITUD), eq("10"), anyString(), eq("10.0.0.5"));
    }

    @Test
    void simular_no_persiste_nada_y_devuelve_plan_y_evaluacion() {
        when(prestamoRepositorio.contarVigentesPorCliente(5L)).thenReturn(1L);

        ResultadoSimulacion resultado = servicio.simular(new SimularSolicitudCommand(5L,
                new BigDecimal("100000.00"), 12, new BigDecimal("12.00"), new BigDecimal("30000.00")));

        assertThat(resultado.plan().cuotas()).hasSize(12);
        assertThat(resultado.plan().cuotaMensual()).isEqualByComparingTo("8884.88");
        assertThat(resultado.evaluacion().prestamosVigentes()).isEqualTo(1L);
        // 8,884.88 / 30,000 = 29.62% del ingreso, por debajo del limite del 40%.
        assertThat(resultado.evaluacion().porcentajeComprometido()).isEqualByComparingTo("29.62");
        assertThat(resultado.evaluacion().recomendado()).isTrue();

        // Una simulacion es solo un calculo: no escribe en la base ni deja auditoria.
        verify(solicitudRepositorio, never()).guardar(any());
        verify(prestamoRepositorio, never()).guardar(any());
        verifyNoInteractions(correlativoPort);
        verifyNoInteractions(auditoriaPort);
    }

    @Test
    void obtener_solicitud_inexistente_lanza_no_encontrado() {
        when(solicitudRepositorio.buscarDetallePorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private void prepararEscenario() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(relojPort.hoy()).thenReturn(HOY);
        when(clienteRepositorio.buscarPorId(5L)).thenReturn(Optional.of(cliente()));
        when(correlativoPort.siguienteNumeroSolicitud()).thenReturn("SC-001-2026-000001-3");
        when(correlativoPort.siguienteNumeroPrestamo()).thenReturn("PR-001-2026-000001-9");
        when(solicitudRepositorio.guardar(any(SolicitudPrestamo.class)))
                .thenAnswer(llamada -> conId(llamada.getArgument(0)));
        when(prestamoRepositorio.guardar(any(Prestamo.class)))
                .thenAnswer(llamada -> llamada.getArgument(0));
        when(solicitudRepositorio.buscarDetallePorId(10L))
                .thenReturn(Optional.of(new SolicitudDetalle(solicitudEnProceso(),
                        "Maria Jose Lopez Garcia", "2547896301234")));
    }

    private static SolicitudPrestamo conId(SolicitudPrestamo solicitud) {
        return SolicitudPrestamo.reconstituir(10L, solicitud.getNumeroSolicitud(),
                solicitud.getClienteId(), solicitud.getMontoSolicitado(), solicitud.getPlazoMeses(),
                solicitud.getTasaInteresAnual(), solicitud.getTipoPrestamo(), solicitud.getDestino(),
                solicitud.getIngresoMensualDeclarado(), solicitud.getEstado(),
                solicitud.getFechaSolicitud(), solicitud.getObservaciones(), solicitud.getResolucion());
    }

    private static CrearSolicitudCommand comandoCreacion() {
        return new CrearSolicitudCommand(5L, new BigDecimal("100000.00"), 12,
                new BigDecimal("12.00"), TipoPrestamo.PERSONAL,
                "Capital de trabajo para negocio propio", new BigDecimal("30000.00"),
                "Cliente recurrente");
    }

    private static SolicitudPrestamo solicitudEnProceso() {
        return SolicitudPrestamo.reconstituir(10L, "SC-001-2026-000001-3", 5L,
                new BigDecimal("100000.00"), 12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL,
                "Capital de trabajo para negocio propio", new BigDecimal("30000.00"),
                EstadoSolicitud.EN_PROCESO, AHORA, "Cliente recurrente", null);
    }

    private static SolicitudPrestamo solicitudResuelta() {
        return SolicitudPrestamo.reconstituir(10L, "SC-001-2026-000001-3", 5L,
                new BigDecimal("100000.00"), 12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL,
                "Capital de trabajo para negocio propio", new BigDecimal("30000.00"),
                EstadoSolicitud.APROBADA, AHORA, null,
                new ResolucionSolicitud(AHORA, "analista", new BigDecimal("100000.00"), 12,
                        new BigDecimal("12.00"), MOTIVO));
    }

    private static Prestamo prestamoExistente() {
        return Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L, new BigDecimal("100000.00"),
                12, new BigDecimal("12.00"), new BigDecimal("8884.88"), new BigDecimal("106618.56"),
                new BigDecimal("0.00"), EstadoPrestamo.VIGENTE, HOY, HOY.plusMonths(12), AHORA);
    }

    private static Cliente cliente() {
        return Cliente.reconstituir(5L, "Maria Jose", "Lopez Garcia", "2547896301234",
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala",
                "maria.lopez@correo.gt", "55512345", true, AHORA, null);
    }
}
