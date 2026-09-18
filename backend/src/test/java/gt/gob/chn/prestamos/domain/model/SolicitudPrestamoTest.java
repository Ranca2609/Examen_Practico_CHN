package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SolicitudPrestamo - rangos permitidos y maquina de estados")
class SolicitudPrestamoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final String MOTIVO_VALIDO = "Capacidad de pago suficiente y sin mora reportada";

    @Test
    void crea_solicitud_valida_en_proceso() {
        SolicitudPrestamo solicitud = solicitudNueva();

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.EN_PROCESO);
        assertThat(solicitud.estaEnProceso()).isTrue();
        assertThat(solicitud.getResolucion()).isNull();
        assertThat(solicitud.getFechaSolicitud()).isEqualTo(AHORA);
        assertThat(solicitud.getMontoSolicitado()).isEqualByComparingTo("100000.00");
        // Todo importe del dominio se normaliza a dos decimales (GTQ).
        assertThat(solicitud.getMontoSolicitado().scale()).isEqualTo(2);
        assertThat(solicitud.getTasaInteresAnual().scale()).isEqualTo(2);
    }

    @Test
    void rechaza_monto_menor_al_minimo_permitido() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("999.99"),
                12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("monto solicitado");
    }

    @Test
    void rechaza_monto_mayor_al_maximo_permitido() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("5000000.01"),
                12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void rechaza_plazo_fuera_de_rango() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                5, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("plazo");

        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                361, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void rechaza_tasa_fuera_de_rango() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                12, BigDecimal.ZERO, TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("tasa");

        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                12, new BigDecimal("100.01"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void rechaza_ingreso_mensual_no_positivo() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                BigDecimal.ZERO, null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("ingreso");
    }

    @Test
    void rechaza_cliente_nulo_y_tipo_de_prestamo_nulo() {
        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", null, new BigDecimal("100000.00"),
                12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class);

        assertThatThrownBy(() -> SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"),
                12, new BigDecimal("12.00"), null, "Capital de trabajo",
                new BigDecimal("15000.00"), null, AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void aprobar_cambia_el_estado_y_registra_la_resolucion() {
        SolicitudPrestamo solicitud = solicitudNueva();
        LocalDateTime fechaResolucion = AHORA.plusDays(2);

        solicitud.aprobar(new BigDecimal("80000.00"), 24, new BigDecimal("14.00"),
                "analista", MOTIVO_VALIDO, fechaResolucion);

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(solicitud.estaEnProceso()).isFalse();
        assertThat(solicitud.getResolucion()).isNotNull();
        assertThat(solicitud.getResolucion().fechaResolucion()).isEqualTo(fechaResolucion);
        assertThat(solicitud.getResolucion().usuarioResolucion()).isEqualTo("analista");
        assertThat(solicitud.getResolucion().montoAprobado()).isEqualByComparingTo("80000.00");
        assertThat(solicitud.getResolucion().plazoAprobadoMeses()).isEqualTo(24);
        assertThat(solicitud.getResolucion().tasaAprobada()).isEqualByComparingTo("14.00");
        assertThat(solicitud.getResolucion().motivo()).isEqualTo(MOTIVO_VALIDO);
    }

    @Test
    void aprobar_rechaza_monto_mayor_al_solicitado() {
        SolicitudPrestamo solicitud = solicitudNueva();

        assertThatThrownBy(() -> solicitud.aprobar(new BigDecimal("100000.01"), 12,
                new BigDecimal("12.00"), "analista", null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("no puede exceder");
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.EN_PROCESO);
    }

    @Test
    void aprobar_dos_veces_lanza_regla_de_negocio() {
        SolicitudPrestamo solicitud = solicitudNueva();
        solicitud.aprobar(new BigDecimal("80000.00"), 24, new BigDecimal("14.00"),
                "analista", MOTIVO_VALIDO, AHORA);

        assertThatThrownBy(() -> solicitud.aprobar(new BigDecimal("50000.00"), 12,
                new BigDecimal("14.00"), "analista", MOTIVO_VALIDO, AHORA))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya fue resuelta");
    }

    @Test
    void rechazar_una_solicitud_ya_aprobada_lanza_regla_de_negocio() {
        SolicitudPrestamo solicitud = solicitudNueva();
        solicitud.aprobar(new BigDecimal("80000.00"), 24, new BigDecimal("14.00"),
                "analista", null, AHORA);

        assertThatThrownBy(() -> solicitud.rechazar("analista", MOTIVO_VALIDO, AHORA))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void rechazar_registra_el_motivo_y_deja_el_detalle_del_credito_en_nulo() {
        SolicitudPrestamo solicitud = solicitudNueva();
        String motivo = "Ingresos insuficientes para la cuota solicitada";

        solicitud.rechazar("analista", motivo, AHORA);

        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(solicitud.getResolucion().motivo()).isEqualTo(motivo);
        assertThat(solicitud.getResolucion().montoAprobado()).isNull();
        assertThat(solicitud.getResolucion().plazoAprobadoMeses()).isNull();
        assertThat(solicitud.getResolucion().tasaAprobada()).isNull();
    }

    @Test
    void rechazar_sin_motivo_lanza_validacion() {
        SolicitudPrestamo solicitud = solicitudNueva();

        assertThatThrownBy(() -> solicitud.rechazar("analista", null, AHORA))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("motivo");
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.EN_PROCESO);
    }

    @Test
    void rechazar_con_motivo_demasiado_corto_lanza_validacion() {
        SolicitudPrestamo solicitud = solicitudNueva();

        // El motivo es constancia para el cliente y respaldo ante auditoria: minimo 10 caracteres.
        assertThatThrownBy(() -> solicitud.rechazar("analista", "No aplica", AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void reconstituir_conserva_el_estado_y_la_resolucion_persistidos() {
        ResolucionSolicitud resolucion = new ResolucionSolicitud(AHORA, "analista",
                new BigDecimal("80000.00"), 24, new BigDecimal("14.00"), MOTIVO_VALIDO);

        SolicitudPrestamo solicitud = SolicitudPrestamo.reconstituir(10L, "SC-001-2026-000001-3", 5L,
                new BigDecimal("100000.00"), 12, new BigDecimal("12.00"), TipoPrestamo.PERSONAL,
                "Capital de trabajo", new BigDecimal("15000.00"), EstadoSolicitud.APROBADA,
                AHORA, null, resolucion);

        assertThat(solicitud.getId()).isEqualTo(10L);
        assertThat(solicitud.estaEnProceso()).isFalse();
        assertThat(solicitud.getResolucion()).isEqualTo(resolucion);
    }

    private static SolicitudPrestamo solicitudNueva() {
        return SolicitudPrestamo.nueva("SC-001-2026-000001-3", 5L, new BigDecimal("100000.00"), 12,
                new BigDecimal("12.00"), TipoPrestamo.PERSONAL, "Capital de trabajo para negocio propio",
                new BigDecimal("15000.00"), "Cliente recurrente", AHORA);
    }
}
