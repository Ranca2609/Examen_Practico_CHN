package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecaudacionMensual - meses contiguos, ceros, rango y normalizacion")
class RecaudacionMensualTest {

    private static final YearMonth OCTUBRE_2025 = YearMonth.of(2025, 10);
    private static final YearMonth SEPTIEMBRE_2026 = YearMonth.of(2026, 9);

    @Test
    @DisplayName("Normaliza el monto a escala 2 y convierte el nulo en cero")
    void normaliza_el_monto_y_convierte_el_nulo_en_cero() {
        assertThat(new RecaudacionMensual(OCTUBRE_2025, 1L, new BigDecimal("2500.555")).monto())
                .isEqualTo(new BigDecimal("2500.56"));
        assertThat(new RecaudacionMensual(OCTUBRE_2025, 0L, null).monto())
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Sin datos devuelve 12 meses en cero, contiguos y ascendentes, cruzando de anio")
    void sin_datos_devuelve_doce_meses_en_cero_cruzando_de_anio() {
        List<RecaudacionMensual> serie = RecaudacionMensual.completar(List.of(), OCTUBRE_2025, SEPTIEMBRE_2026);

        assertThat(serie).hasSize(12);
        assertThat(serie).extracting(RecaudacionMensual::periodo).containsExactly(
                YearMonth.of(2025, 10), YearMonth.of(2025, 11), YearMonth.of(2025, 12),
                YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3),
                YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6),
                YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        assertThat(serie).allSatisfy(mes -> {
            assertThat(mes.cantidadPagos()).isZero();
            assertThat(mes.monto()).isEqualTo(new BigDecimal("0.00"));
        });
    }

    @Test
    @DisplayName("Coloca cada mes con pagos en su posicion y rellena el resto con ceros")
    void coloca_los_meses_con_pagos_y_rellena_el_resto() {
        // Desordenados a proposito: el orden de la serie lo fija el dominio.
        List<RecaudacionMensual> datos = List.of(
                recaudacion(2026, 6, 2, "12000.00"),
                recaudacion(2026, 3, 1, "8884.88"),
                recaudacion(2025, 12, 3, "300.10"));

        List<RecaudacionMensual> serie = RecaudacionMensual.completar(datos, OCTUBRE_2025, SEPTIEMBRE_2026);

        assertThat(serie).hasSize(12);
        assertThat(serie).extracting(RecaudacionMensual::periodo).isSorted();
        assertThat(serie.get(2)).isEqualTo(recaudacion(2025, 12, 3, "300.10"));
        assertThat(serie.get(5)).isEqualTo(recaudacion(2026, 3, 1, "8884.88"));
        assertThat(serie.get(8)).isEqualTo(recaudacion(2026, 6, 2, "12000.00"));
        assertThat(serie.get(11)).isEqualTo(RecaudacionMensual.sinPagos(SEPTIEMBRE_2026));
        assertThat(serie.stream().map(RecaudacionMensual::monto).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("21184.98");
    }

    @Test
    @DisplayName("Ignora los meses fuera del rango en lugar de rechazarlos")
    void ignora_los_meses_fuera_del_rango() {
        List<RecaudacionMensual> datos = List.of(
                recaudacion(2025, 9, 4, "1000.00"),   // un mes antes de la ventana
                recaudacion(2026, 10, 1, "500.00"),   // un mes despues (fecha futura)
                recaudacion(2026, 1, 1, "250.00"));

        List<RecaudacionMensual> serie = RecaudacionMensual.completar(datos, OCTUBRE_2025, SEPTIEMBRE_2026);

        assertThat(serie).hasSize(12);
        assertThat(serie.get(0).periodo()).isEqualTo(OCTUBRE_2025);
        assertThat(serie.get(11).periodo()).isEqualTo(SEPTIEMBRE_2026);
        assertThat(serie).filteredOn(mes -> mes.cantidadPagos() > 0)
                .containsExactly(recaudacion(2026, 1, 1, "250.00"));
    }

    @Test
    @DisplayName("Un mes repetido se acumula")
    void un_mes_repetido_se_suma() {
        List<RecaudacionMensual> serie = RecaudacionMensual.completar(List.of(
                        recaudacion(2026, 4, 1, "100.25"),
                        recaudacion(2026, 4, 2, "200.50")),
                YearMonth.of(2026, 4), YearMonth.of(2026, 4));

        assertThat(serie).containsExactly(recaudacion(2026, 4, 3, "300.75"));
    }

    @Test
    @DisplayName("Un rango de un solo mes devuelve un solo elemento")
    void un_rango_de_un_mes_devuelve_un_elemento() {
        assertThat(RecaudacionMensual.completar(null, SEPTIEMBRE_2026, SEPTIEMBRE_2026))
                .containsExactly(RecaudacionMensual.sinPagos(SEPTIEMBRE_2026));
    }

    @Test
    @DisplayName("La serie devuelta es inmutable")
    void la_serie_devuelta_es_inmutable() {
        List<RecaudacionMensual> serie = RecaudacionMensual.completar(List.of(), OCTUBRE_2025, SEPTIEMBRE_2026);

        assertThatThrownBy(() -> serie.remove(0)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Rechaza un rango invertido")
    void rechaza_un_rango_invertido() {
        assertThatThrownBy(() -> RecaudacionMensual.completar(List.of(), SEPTIEMBRE_2026, OCTUBRE_2025))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("recaudacion mensual");
    }

    @Test
    @DisplayName("Exige ambos extremos del rango")
    void exige_ambos_extremos_del_rango() {
        assertThatThrownBy(() -> RecaudacionMensual.completar(List.of(), null, SEPTIEMBRE_2026))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("mes inicial");
        assertThatThrownBy(() -> RecaudacionMensual.completar(List.of(), OCTUBRE_2025, null))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("mes final");
    }

    @Test
    @DisplayName("Rechaza un elemento nulo dentro de la lista")
    void rechaza_un_elemento_nulo() {
        List<RecaudacionMensual> conNulo = Arrays.asList(RecaudacionMensual.sinPagos(OCTUBRE_2025), null);

        assertThatThrownBy(() -> RecaudacionMensual.completar(conNulo, OCTUBRE_2025, SEPTIEMBRE_2026))
                .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    @DisplayName("Valida periodo, cantidad y monto de cada mes")
    void valida_periodo_cantidad_y_monto() {
        assertThatThrownBy(() -> new RecaudacionMensual(null, 0L, null))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("periodo");
        assertThatThrownBy(() -> new RecaudacionMensual(OCTUBRE_2025, -1L, null))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("cantidad de pagos");
        assertThatThrownBy(() -> new RecaudacionMensual(OCTUBRE_2025, 1L, new BigDecimal("-1.00")))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("monto recaudado");
    }

    private static RecaudacionMensual recaudacion(int anio, int mes, long pagos, String monto) {
        return new RecaudacionMensual(YearMonth.of(anio, mes), pagos, new BigDecimal(monto));
    }
}
