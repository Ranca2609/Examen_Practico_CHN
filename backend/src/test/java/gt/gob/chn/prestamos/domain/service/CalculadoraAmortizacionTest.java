package gt.gob.chn.prestamos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CalculadoraAmortizacion - cuota fija del sistema frances")
class CalculadoraAmortizacionTest {

    private final CalculadoraAmortizacion calculadora = new CalculadoraAmortizacion();

    @Test
    void calcula_la_cuota_conocida_de_referencia() {
        // Caso de referencia del negocio: Q100,000 a 12 meses al 12% anual -> cuota Q8,884.88.
        BigDecimal cuota = calculadora.calcularCuotaMensual(
                new BigDecimal("100000.00"), 12, new BigDecimal("12.00"));

        assertThat(cuota).isCloseTo(new BigDecimal("8884.88"), within(new BigDecimal("0.05")));
        assertThat(cuota.scale()).isEqualTo(2);
    }

    @Test
    void con_tasa_cero_la_cuota_es_el_monto_entre_el_plazo() {
        BigDecimal cuota = calculadora.calcularCuotaMensual(
                new BigDecimal("12000.00"), 12, BigDecimal.ZERO);

        assertThat(cuota).isEqualByComparingTo("1000.00");
    }

    @Test
    void con_tasa_cero_el_plan_no_genera_intereses() {
        PlanAmortizacion plan = calculadora.calcular(new BigDecimal("12000.00"), 12, BigDecimal.ZERO);

        assertThat(plan.totalIntereses()).isEqualByComparingTo("0.00");
        assertThat(plan.montoTotal()).isEqualByComparingTo("12000.00");
        assertThat(plan.cuotas()).allSatisfy(cuota ->
                assertThat(cuota.abonoInteres()).isEqualByComparingTo("0.00"));
    }

    @Test
    void el_plan_contiene_una_cuota_por_cada_mes_del_plazo_y_numeradas_en_orden() {
        PlanAmortizacion plan = calculadora.calcular(new BigDecimal("100000.00"), 12, new BigDecimal("12.00"));

        assertThat(plan.cuotas()).hasSize(12);
        assertThat(plan.cuotas().get(0).numero()).isEqualTo(1);
        assertThat(plan.cuotas().get(11).numero()).isEqualTo(12);
        for (int indice = 0; indice < plan.cuotas().size(); indice++) {
            assertThat(plan.cuotas().get(indice).numero()).isEqualTo(indice + 1);
        }
    }

    @Test
    void la_suma_de_abonos_a_capital_equivale_al_monto_y_el_saldo_final_es_cero() {
        BigDecimal monto = new BigDecimal("100000.00");
        PlanAmortizacion plan = calculadora.calcular(monto, 12, new BigDecimal("12.00"));

        BigDecimal capitalAmortizado = plan.cuotas().stream()
                .map(CuotaAmortizacion::abonoCapital)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(capitalAmortizado).isEqualByComparingTo(monto);
        assertThat(ultima(plan.cuotas()).saldoFinal()).isEqualByComparingTo("0.00");
    }

    @Test
    void el_monto_total_del_plan_es_el_capital_mas_los_intereses() {
        BigDecimal monto = new BigDecimal("250000.00");
        PlanAmortizacion plan = calculadora.calcular(monto, 60, new BigDecimal("15.50"));

        BigDecimal interesesSumados = plan.cuotas().stream()
                .map(CuotaAmortizacion::abonoInteres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(plan.totalIntereses()).isEqualByComparingTo(interesesSumados);
        assertThat(plan.montoTotal()).isEqualByComparingTo(monto.add(plan.totalIntereses()));
        assertThat(plan.cuotaMensual()).isPositive();
    }

    @Test
    void el_saldo_decrece_de_forma_encadenada_entre_cuotas() {
        PlanAmortizacion plan = calculadora.calcular(new BigDecimal("50000.00"), 24, new BigDecimal("18.00"));

        for (int indice = 1; indice < plan.cuotas().size(); indice++) {
            CuotaAmortizacion anterior = plan.cuotas().get(indice - 1);
            CuotaAmortizacion actual = plan.cuotas().get(indice);
            // El saldo final de una cuota es el saldo inicial de la siguiente.
            assertThat(actual.saldoInicial()).isEqualByComparingTo(anterior.saldoFinal());
            assertThat(actual.saldoFinal()).isLessThan(actual.saldoInicial());
        }
    }

    @Test
    void en_el_sistema_frances_el_interes_decrece_y_el_capital_crece() {
        PlanAmortizacion plan = calculadora.calcular(new BigDecimal("100000.00"), 12, new BigDecimal("12.00"));

        CuotaAmortizacion primera = plan.cuotas().get(0);
        CuotaAmortizacion penultima = plan.cuotas().get(plan.cuotas().size() - 2);

        assertThat(penultima.abonoInteres()).isLessThan(primera.abonoInteres());
        assertThat(penultima.abonoCapital()).isGreaterThan(primera.abonoCapital());
        // Primer interes: 100,000 * 12% / 12 = 1,000.00
        assertThat(primera.abonoInteres()).isEqualByComparingTo("1000.00");
    }

    @Test
    void rechaza_parametros_invalidos() {
        assertThatThrownBy(() -> calculadora.calcularCuotaMensual(BigDecimal.ZERO, 12, new BigDecimal("12.00")))
                .isInstanceOf(ValidacionDominioException.class);

        assertThatThrownBy(() -> calculadora.calcularCuotaMensual(new BigDecimal("1000.00"), 0,
                new BigDecimal("12.00")))
                .isInstanceOf(ValidacionDominioException.class);

        assertThatThrownBy(() -> calculadora.calcularCuotaMensual(new BigDecimal("1000.00"), 12,
                new BigDecimal("-1.00")))
                .isInstanceOf(ValidacionDominioException.class);
    }

    private static CuotaAmortizacion ultima(List<CuotaAmortizacion> cuotas) {
        return cuotas.get(cuotas.size() - 1);
    }
}
