package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Prestamo - saldo, abonos y liquidacion")
class PrestamoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final LocalDate DESEMBOLSO = LocalDate.of(2026, 3, 10);

    @Test
    void nace_vigente_sin_pagos_y_con_vencimiento_al_final_del_plazo() {
        Prestamo prestamo = prestamoNuevo();

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
        assertThat(prestamo.estaLiquidado()).isFalse();
        assertThat(prestamo.getTotalPagado()).isEqualByComparingTo("0.00");
        assertThat(prestamo.getFechaVencimiento()).isEqualTo(DESEMBOLSO.plusMonths(12));
        assertThat(prestamo.getSaldoPendiente()).isEqualByComparingTo("106618.56");
        assertThat(prestamo.getPorcentajePagado()).isEqualByComparingTo("0.00");
    }

    @Test
    void calcula_saldo_pendiente_y_porcentaje_pagado() {
        Prestamo prestamo = prestamoGrandeConPagos("10000.00");

        assertThat(prestamo.getSaldoPendiente()).isEqualByComparingTo("96618.56");
        // 10,000 / 106,618.56 = 9.3792...% -> se redondea a dos decimales (HALF_UP).
        assertThat(prestamo.getPorcentajePagado()).isEqualByComparingTo("9.38");
        assertThat(prestamo.getSaldoPendiente().scale()).isEqualTo(2);
    }

    @Test
    void aplicar_pago_parcial_acumula_el_total_pagado_y_mantiene_vigente() {
        Prestamo prestamo = prestamoNuevo();

        prestamo.aplicarPago(new BigDecimal("8884.88"));
        prestamo.aplicarPago(new BigDecimal("8884.88"));

        assertThat(prestamo.getTotalPagado()).isEqualByComparingTo("17769.76");
        assertThat(prestamo.getSaldoPendiente()).isEqualByComparingTo("88848.80");
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
    }

    @Test
    void aplicar_pago_que_cubre_el_saldo_liquida_el_prestamo() {
        Prestamo prestamo = prestamoPequenoConPagos("7500.00");

        prestamo.aplicarPago(new BigDecimal("2500.00"));

        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.LIQUIDADO);
        assertThat(prestamo.estaLiquidado()).isTrue();
        assertThat(prestamo.getSaldoPendiente()).isEqualByComparingTo("0.00");
        assertThat(prestamo.getPorcentajePagado()).isEqualByComparingTo("100.00");
    }

    @Test
    void aplicar_pago_mayor_al_saldo_lanza_regla_de_negocio_e_informa_el_saldo() {
        Prestamo prestamo = prestamoPequenoConPagos("7500.00");

        assertThatThrownBy(() -> prestamo.aplicarPago(new BigDecimal("2500.01")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("2500.00");
        assertThat(prestamo.getTotalPagado()).isEqualByComparingTo("7500.00");
        assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VIGENTE);
    }

    @Test
    void aplicar_pago_sobre_prestamo_liquidado_lanza_regla_de_negocio() {
        Prestamo prestamo = Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L,
                new BigDecimal("10000.00"), 12, new BigDecimal("12.00"), new BigDecimal("888.49"),
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), EstadoPrestamo.LIQUIDADO,
                DESEMBOLSO, DESEMBOLSO.plusMonths(12), AHORA);

        assertThatThrownBy(() -> prestamo.aplicarPago(new BigDecimal("100.00")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya esta liquidado");
    }

    @Test
    void aplicar_pago_de_monto_cero_o_negativo_lanza_validacion() {
        Prestamo prestamo = prestamoNuevo();

        assertThatThrownBy(() -> prestamo.aplicarPago(BigDecimal.ZERO))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("mayor que cero");

        assertThatThrownBy(() -> prestamo.aplicarPago(new BigDecimal("-500.00")))
                .isInstanceOf(ValidacionDominioException.class);

        assertThatThrownBy(() -> prestamo.aplicarPago(null))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void reconstituir_rechaza_un_total_pagado_mayor_al_monto_total() {
        assertThatThrownBy(() -> Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L,
                new BigDecimal("10000.00"), 12, new BigDecimal("12.00"), new BigDecimal("888.49"),
                new BigDecimal("10000.00"), new BigDecimal("10000.01"), EstadoPrestamo.VIGENTE,
                DESEMBOLSO, DESEMBOLSO.plusMonths(12), AHORA))
                .isInstanceOf(ValidacionDominioException.class);
    }

    /** Prestamo recien desembolsado: Q100,000 a 12 meses al 12% anual (cuota 8,884.88). */
    private static Prestamo prestamoNuevo() {
        return Prestamo.nuevo("PR-001-2026-000001-9", 10L, 5L, new BigDecimal("100000.00"), 12,
                new BigDecimal("12.00"), new BigDecimal("8884.88"), new BigDecimal("106618.56"),
                DESEMBOLSO, AHORA);
    }

    private static Prestamo prestamoGrandeConPagos(String totalPagado) {
        return Prestamo.reconstituir(20L, "PR-001-2026-000001-9", 10L, 5L, new BigDecimal("100000.00"), 12,
                new BigDecimal("12.00"), new BigDecimal("8884.88"), new BigDecimal("106618.56"),
                new BigDecimal(totalPagado), EstadoPrestamo.VIGENTE, DESEMBOLSO,
                DESEMBOLSO.plusMonths(12), AHORA);
    }

    /** Prestamo pequeno (total a pagar Q10,000.00) para probar la liquidacion con cifras redondas. */
    private static Prestamo prestamoPequenoConPagos(String totalPagado) {
        return Prestamo.reconstituir(21L, "PR-001-2026-000002-7", 11L, 5L, new BigDecimal("9000.00"), 12,
                new BigDecimal("20.00"), new BigDecimal("833.33"), new BigDecimal("10000.00"),
                new BigDecimal(totalPagado), EstadoPrestamo.VIGENTE, DESEMBOLSO,
                DESEMBOLSO.plusMonths(12), AHORA);
    }
}
