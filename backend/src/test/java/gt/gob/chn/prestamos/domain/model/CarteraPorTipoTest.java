package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CarteraPorTipo - relleno de tipos, orden y normalizacion de montos")
class CarteraPorTipoTest {

    @Test
    @DisplayName("Normaliza los montos a escala 2 y convierte los nulos en cero")
    void normaliza_los_montos_y_convierte_nulos_en_cero() {
        CarteraPorTipo cartera = new CarteraPorTipo(TipoPrestamo.PERSONAL, 2L,
                new BigDecimal("75000"), new BigDecimal("12345.678"), null);

        assertThat(cartera.montoAprobado()).isEqualByComparingTo("75000.00");
        assertThat(cartera.montoAprobado().scale()).isEqualTo(2);
        // 12345.678 -> 12345.68 (HALF_UP), igual que el resto de importes del sistema.
        assertThat(cartera.saldoPendiente()).isEqualTo(new BigDecimal("12345.68"));
        assertThat(cartera.totalRecuperado()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Sin datos devuelve los cinco tipos en cero y en el orden del enum")
    void sin_datos_devuelve_los_cinco_tipos_en_cero() {
        List<CarteraPorTipo> serie = CarteraPorTipo.completar(List.of());

        assertThat(serie).extracting(CarteraPorTipo::tipoPrestamo)
                .containsExactly(TipoPrestamo.values());
        assertThat(serie).allSatisfy(cartera -> {
            assertThat(cartera.cantidadPrestamos()).isZero();
            assertThat(cartera.montoAprobado()).isEqualTo(new BigDecimal("0.00"));
            assertThat(cartera.saldoPendiente()).isEqualTo(new BigDecimal("0.00"));
            assertThat(cartera.totalRecuperado()).isEqualTo(new BigDecimal("0.00"));
        });
    }

    @Test
    @DisplayName("Una lista nula se trata como vacia")
    void una_lista_nula_se_trata_como_vacia() {
        assertThat(CarteraPorTipo.completar(null))
                .hasSize(TipoPrestamo.values().length)
                .allSatisfy(cartera -> assertThat(cartera.cantidadPrestamos()).isZero());
    }

    @Test
    @DisplayName("Conserva los tipos con datos, rellena los faltantes y reordena segun el enum")
    void conserva_los_datos_rellena_faltantes_y_ordena_segun_el_enum() {
        // Llegan desordenados, como podria devolverlos la vista (GROUP BY no garantiza orden).
        List<CarteraPorTipo> datos = List.of(
                cartera(TipoPrestamo.HIPOTECARIO, 1, "850000.00", "1334009.98", "0.00"),
                cartera(TipoPrestamo.PERSONAL, 1, "75000.00", "80000.00", "5000.00"));

        List<CarteraPorTipo> serie = CarteraPorTipo.completar(datos);

        assertThat(serie).extracting(CarteraPorTipo::tipoPrestamo).containsExactly(
                TipoPrestamo.PERSONAL, TipoPrestamo.HIPOTECARIO, TipoPrestamo.VEHICULAR,
                TipoPrestamo.EMPRESARIAL, TipoPrestamo.EDUCATIVO);
        assertThat(serie.get(0).montoAprobado()).isEqualByComparingTo("75000.00");
        assertThat(serie.get(0).totalRecuperado()).isEqualByComparingTo("5000.00");
        assertThat(serie.get(1).cantidadPrestamos()).isEqualTo(1L);
        assertThat(serie.get(1).saldoPendiente()).isEqualByComparingTo("1334009.98");
        assertThat(serie.get(2)).isEqualTo(CarteraPorTipo.sinPrestamos(TipoPrestamo.VEHICULAR));
        assertThat(serie.get(3)).isEqualTo(CarteraPorTipo.sinPrestamos(TipoPrestamo.EMPRESARIAL));
        assertThat(serie.get(4)).isEqualTo(CarteraPorTipo.sinPrestamos(TipoPrestamo.EDUCATIVO));
    }

    @Test
    @DisplayName("Un tipo repetido se acumula: conteos e importes son aditivos")
    void un_tipo_repetido_se_suma() {
        List<CarteraPorTipo> serie = CarteraPorTipo.completar(List.of(
                cartera(TipoPrestamo.VEHICULAR, 1, "450000.00", "400000.00", "50000.00"),
                cartera(TipoPrestamo.VEHICULAR, 2, "100000.50", "90000.25", "10000.25")));

        CarteraPorTipo vehicular = serie.get(TipoPrestamo.VEHICULAR.ordinal());
        assertThat(serie).hasSize(5);
        assertThat(vehicular.cantidadPrestamos()).isEqualTo(3L);
        assertThat(vehicular.montoAprobado()).isEqualTo(new BigDecimal("550000.50"));
        assertThat(vehicular.saldoPendiente()).isEqualTo(new BigDecimal("490000.25"));
        assertThat(vehicular.totalRecuperado()).isEqualTo(new BigDecimal("60000.25"));
    }

    @Test
    @DisplayName("La serie devuelta es inmutable")
    void la_serie_devuelta_es_inmutable() {
        List<CarteraPorTipo> serie = CarteraPorTipo.completar(new ArrayList<>());

        assertThatThrownBy(() -> serie.add(CarteraPorTipo.sinPrestamos(TipoPrestamo.PERSONAL)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Rechaza un elemento nulo dentro de la lista")
    void rechaza_un_elemento_nulo() {
        List<CarteraPorTipo> conNulo = Arrays.asList(
                CarteraPorTipo.sinPrestamos(TipoPrestamo.PERSONAL), null);

        assertThatThrownBy(() -> CarteraPorTipo.completar(conNulo))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("cartera por tipo");
    }

    @Test
    @DisplayName("Exige el tipo de prestamo")
    void exige_el_tipo_de_prestamo() {
        assertThatThrownBy(() -> new CarteraPorTipo(null, 0L, null, null, null))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("tipo de prestamo");
    }

    @Test
    @DisplayName("Rechaza una cantidad de prestamos negativa")
    void rechaza_cantidad_negativa() {
        assertThatThrownBy(() -> new CarteraPorTipo(TipoPrestamo.PERSONAL, -1L, null, null, null))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("cantidad de prestamos");
    }

    @Test
    @DisplayName("Rechaza montos negativos")
    void rechaza_montos_negativos() {
        assertThatThrownBy(() -> cartera(TipoPrestamo.PERSONAL, 1, "-0.01", "0", "0"))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("monto aprobado");
        assertThatThrownBy(() -> cartera(TipoPrestamo.PERSONAL, 1, "0", "-5", "0"))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("saldo pendiente");
        assertThatThrownBy(() -> cartera(TipoPrestamo.PERSONAL, 1, "0", "0", "-5"))
                .isInstanceOf(ValidacionDominioException.class)
                .hasMessageContaining("total recuperado");
    }

    private static CarteraPorTipo cartera(TipoPrestamo tipo, long cantidad, String aprobado,
                                          String saldo, String recuperado) {
        return new CarteraPorTipo(tipo, cantidad, new BigDecimal(aprobado), new BigDecimal(saldo),
                new BigDecimal(recuperado));
    }
}
