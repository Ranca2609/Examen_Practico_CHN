package gt.gob.chn.prestamos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResumenGeneral - totales, series inmutables y copia con series")
class ResumenGeneralTest {

    @Test
    @DisplayName("El resumen vacio tiene totales en cero y series vacias")
    void el_resumen_vacio_tiene_ceros_y_series_vacias() {
        ResumenGeneral vacio = ResumenGeneral.vacio();

        assertThat(vacio.totalClientes()).isZero();
        assertThat(vacio.prestamosVigentes()).isZero();
        assertThat(vacio.montoTotalAprobado()).isEqualTo(new BigDecimal("0.00"));
        assertThat(vacio.saldoPendienteTotal()).isEqualTo(new BigDecimal("0.00"));
        assertThat(vacio.totalRecuperado()).isEqualTo(new BigDecimal("0.00"));
        assertThat(vacio.carteraPorTipo()).isEmpty();
        assertThat(vacio.recaudacionMensual()).isEmpty();
    }

    @Test
    @DisplayName("El constructor de solo totales deja las series vacias y normaliza los montos")
    void el_constructor_de_totales_deja_series_vacias() {
        ResumenGeneral resumen = totales();

        assertThat(resumen.montoTotalAprobado()).isEqualTo(new BigDecimal("1495000.00"));
        assertThat(resumen.totalRecuperado()).isEqualTo(new BigDecimal("0.00"));
        assertThat(resumen.carteraPorTipo()).isEmpty();
        assertThat(resumen.recaudacionMensual()).isEmpty();
    }

    @Test
    @DisplayName("Las series nulas se convierten en listas vacias")
    void las_series_nulas_se_convierten_en_vacias() {
        ResumenGeneral resumen = totales().conSeries(null, null);

        assertThat(resumen.carteraPorTipo()).isNotNull().isEmpty();
        assertThat(resumen.recaudacionMensual()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("conSeries devuelve una copia con las series y conserva los totales")
    void con_series_devuelve_una_copia_y_conserva_los_totales() {
        ResumenGeneral original = totales();
        List<CarteraPorTipo> cartera = CarteraPorTipo.completar(List.of());
        List<RecaudacionMensual> recaudacion = RecaudacionMensual.completar(
                List.of(), YearMonth.of(2025, 10), YearMonth.of(2026, 9));

        ResumenGeneral conSeries = original.conSeries(cartera, recaudacion);

        assertThat(conSeries).isNotSameAs(original);
        assertThat(conSeries.totalClientes()).isEqualTo(8L);
        assertThat(conSeries.solicitudesEnProceso()).isEqualTo(4L);
        assertThat(conSeries.solicitudesAprobadas()).isEqualTo(4L);
        assertThat(conSeries.solicitudesRechazadas()).isEqualTo(2L);
        assertThat(conSeries.prestamosVigentes()).isEqualTo(4L);
        assertThat(conSeries.prestamosLiquidados()).isZero();
        assertThat(conSeries.montoTotalAprobado()).isEqualTo(original.montoTotalAprobado());
        assertThat(conSeries.saldoPendienteTotal()).isEqualTo(original.saldoPendienteTotal());
        assertThat(conSeries.totalRecuperado()).isEqualTo(original.totalRecuperado());
        assertThat(conSeries.carteraPorTipo()).hasSize(5).isEqualTo(cartera);
        assertThat(conSeries.recaudacionMensual()).hasSize(12).isEqualTo(recaudacion);
        assertThat(original.carteraPorTipo()).isEmpty();
        assertThat(original.recaudacionMensual()).isEmpty();
    }

    @Test
    @DisplayName("Las series se copian: cambiar la lista de origen no altera el resumen")
    void las_series_se_copian_de_forma_defensiva() {
        List<CarteraPorTipo> cartera = new ArrayList<>(List.of(CarteraPorTipo.sinPrestamos(TipoPrestamo.PERSONAL)));
        List<RecaudacionMensual> recaudacion =
                new ArrayList<>(List.of(RecaudacionMensual.sinPagos(YearMonth.of(2026, 9))));

        ResumenGeneral resumen = totales().conSeries(cartera, recaudacion);
        cartera.add(CarteraPorTipo.sinPrestamos(TipoPrestamo.HIPOTECARIO));
        recaudacion.clear();

        assertThat(resumen.carteraPorTipo()).hasSize(1);
        assertThat(resumen.recaudacionMensual()).hasSize(1);
        assertThatThrownBy(() -> resumen.carteraPorTipo().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> resumen.recaudacionMensual().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static ResumenGeneral totales() {
        return new ResumenGeneral(8L, 4L, 4L, 2L, 4L, 0L,
                new BigDecimal("1495000"), new BigDecimal("1800000.5"), null);
    }
}
