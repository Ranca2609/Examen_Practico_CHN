package gt.gob.chn.prestamos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvaluadorCapacidadPago - politica de endeudamiento del banco")
class EvaluadorCapacidadPagoTest {

    private final EvaluadorCapacidadPago evaluador = new EvaluadorCapacidadPago();

    @Test
    void recomienda_cuando_la_cuota_esta_dentro_del_limite_y_no_hay_exceso_de_prestamos() {
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("10000.00"), new BigDecimal("2000.00"), 0);

        assertThat(resultado.porcentajeComprometido()).isEqualByComparingTo("20.00");
        assertThat(resultado.recomendado()).isTrue();
        assertThat(resultado.prestamosVigentes()).isZero();
        assertThat(resultado.observacion()).isNotBlank();
    }

    @Test
    void recomienda_en_el_limite_exacto_del_cuarenta_por_ciento() {
        // La politica es "hasta 40%" inclusive: el limite exacto no descalifica.
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("10000.00"), new BigDecimal("4000.00"), 1);

        assertThat(resultado.porcentajeComprometido()).isEqualByComparingTo("40.00");
        assertThat(resultado.recomendado()).isTrue();
    }

    @Test
    void no_recomienda_cuando_la_cuota_supera_el_porcentaje_maximo() {
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("5000.00"), new BigDecimal("3000.00"), 0);

        assertThat(resultado.porcentajeComprometido()).isEqualByComparingTo("60.00");
        assertThat(resultado.recomendado()).isFalse();
        assertThat(resultado.observacion()).contains("60.00");
    }

    @Test
    void no_recomienda_cuando_el_cliente_ya_tiene_tres_prestamos_vigentes() {
        // Aun con una cuota comoda (5% del ingreso), tres prestamos vigentes descalifican.
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("20000.00"), new BigDecimal("1000.00"),
                EvaluadorCapacidadPago.MAXIMO_PRESTAMOS_VIGENTES);

        assertThat(resultado.porcentajeComprometido()).isEqualByComparingTo("5.00");
        assertThat(resultado.recomendado()).isFalse();
        assertThat(resultado.prestamosVigentes()).isEqualTo(3L);
        assertThat(resultado.observacion()).isNotBlank();
    }

    @Test
    void recomienda_con_dos_prestamos_vigentes_por_estar_bajo_el_maximo() {
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("20000.00"), new BigDecimal("1000.00"), 2);

        assertThat(resultado.recomendado()).isTrue();
    }

    @Test
    void ingreso_cero_se_rechaza_como_dato_invalido_y_no_provoca_division_por_cero() {
        assertThatThrownBy(() -> evaluador.evaluar(BigDecimal.ZERO, new BigDecimal("1200.00"), 0))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("ingreso mensual");

        assertThatThrownBy(() -> evaluador.evaluar(null, new BigDecimal("1200.00"), 0))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void rechaza_cuota_no_positiva_y_cantidad_negativa_de_prestamos() {
        assertThatThrownBy(() -> evaluador.evaluar(new BigDecimal("10000.00"), BigDecimal.ZERO, 0))
                .isInstanceOf(ValidacionDominioException.class);

        assertThatThrownBy(() -> evaluador.evaluar(new BigDecimal("10000.00"), new BigDecimal("1000.00"), -1))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    void redondea_el_porcentaje_comprometido_a_dos_decimales() {
        // 3,000 / 7,000 = 42.857...% -> 42.86%
        ResultadoEvaluacion resultado = evaluador.evaluar(
                new BigDecimal("7000.00"), new BigDecimal("3000.00"), 0);

        assertThat(resultado.porcentajeComprometido()).isEqualByComparingTo("42.86");
        assertThat(resultado.porcentajeComprometido().scale()).isEqualTo(2);
        assertThat(resultado.recomendado()).isFalse();
    }
}
