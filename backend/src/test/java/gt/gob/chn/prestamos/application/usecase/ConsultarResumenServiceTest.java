package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.ResumenRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarResumenService - totales y series del tablero")
class ConsultarResumenServiceTest {

    /** Fecha del sistema en la demo: la ventana va de octubre de 2025 a septiembre de 2026. */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 18);
    private static final YearMonth DESDE = YearMonth.of(2025, 10);
    private static final YearMonth HASTA = YearMonth.of(2026, 9);

    @Mock
    private ResumenRepositorio resumenRepositorio;

    @Mock
    private RelojPort reloj;

    private ConsultarResumenService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new ConsultarResumenService(resumenRepositorio, reloj);
    }

    @Test
    @DisplayName("Pide la recaudacion de los 12 meses que terminan en el mes en curso")
    void pide_la_recaudacion_de_los_doce_meses_que_terminan_en_el_mes_en_curso() {
        when(reloj.hoy()).thenReturn(HOY);
        when(resumenRepositorio.obtener()).thenReturn(ResumenGeneral.vacio());
        when(resumenRepositorio.carteraPorTipo()).thenReturn(List.of());
        when(resumenRepositorio.recaudacionMensual(DESDE, HASTA)).thenReturn(List.of());

        ResumenGeneral resumen = servicio.obtener();

        verify(resumenRepositorio).recaudacionMensual(DESDE, HASTA);
        assertThat(resumen.recaudacionMensual()).hasSize(ConsultarResumenService.MESES_RECAUDACION);
        assertThat(resumen.recaudacionMensual().get(0).periodo()).isEqualTo(DESDE);
        assertThat(resumen.recaudacionMensual().get(11).periodo()).isEqualTo(HASTA);
    }

    @Test
    @DisplayName("En enero la ventana cruza al anio anterior")
    void en_enero_la_ventana_cruza_al_anio_anterior() {
        when(reloj.hoy()).thenReturn(LocalDate.of(2027, 1, 1));
        when(resumenRepositorio.obtener()).thenReturn(ResumenGeneral.vacio());
        when(resumenRepositorio.carteraPorTipo()).thenReturn(List.of());
        when(resumenRepositorio.recaudacionMensual(YearMonth.of(2026, 2), YearMonth.of(2027, 1)))
                .thenReturn(List.of());

        ResumenGeneral resumen = servicio.obtener();

        assertThat(resumen.recaudacionMensual()).extracting(RecaudacionMensual::periodo)
                .startsWith(YearMonth.of(2026, 2))
                .endsWith(YearMonth.of(2027, 1));
    }

    @Test
    @DisplayName("Compone los totales con las series completadas por el dominio")
    void compone_los_totales_con_las_series_completas() {
        ResumenGeneral totales = new ResumenGeneral(8L, 4L, 4L, 2L, 4L, 0L,
                new BigDecimal("1495000.00"), new BigDecimal("2107286.71"), new BigDecimal("42377.26"));
        when(reloj.hoy()).thenReturn(HOY);
        when(resumenRepositorio.obtener()).thenReturn(totales);
        when(resumenRepositorio.carteraPorTipo()).thenReturn(List.of(
                new CarteraPorTipo(TipoPrestamo.HIPOTECARIO, 1L, new BigDecimal("850000.00"),
                        new BigDecimal("1334009.98"), BigDecimal.ZERO),
                new CarteraPorTipo(TipoPrestamo.PERSONAL, 1L, new BigDecimal("75000.00"),
                        new BigDecimal("70000.00"), new BigDecimal("12000.00"))));
        when(resumenRepositorio.recaudacionMensual(DESDE, HASTA)).thenReturn(List.of(
                new RecaudacionMensual(YearMonth.of(2026, 4), 2L, new BigDecimal("12000.00")),
                new RecaudacionMensual(YearMonth.of(2026, 3), 1L, new BigDecimal("5000.00"))));

        ResumenGeneral resumen = servicio.obtener();

        assertThat(resumen.totalClientes()).isEqualTo(8L);
        assertThat(resumen.saldoPendienteTotal()).isEqualByComparingTo("2107286.71");
        assertThat(resumen.totalRecuperado()).isEqualByComparingTo("42377.26");

        // Cartera: 5 tipos en el orden del enum, con ceros donde no hay prestamos.
        assertThat(resumen.carteraPorTipo()).extracting(CarteraPorTipo::tipoPrestamo)
                .containsExactly(TipoPrestamo.values());
        assertThat(resumen.carteraPorTipo().get(0).montoAprobado()).isEqualByComparingTo("75000.00");
        assertThat(resumen.carteraPorTipo().get(1).saldoPendiente()).isEqualByComparingTo("1334009.98");
        assertThat(resumen.carteraPorTipo().get(2).cantidadPrestamos()).isZero();

        // Recaudacion: marzo y abril de 2026 en su posicion, el resto en cero.
        assertThat(resumen.recaudacionMensual()).hasSize(12);
        assertThat(resumen.recaudacionMensual().get(5))
                .isEqualTo(new RecaudacionMensual(YearMonth.of(2026, 3), 1L, new BigDecimal("5000.00")));
        assertThat(resumen.recaudacionMensual().get(6).monto()).isEqualByComparingTo("12000.00");
        assertThat(resumen.recaudacionMensual())
                .filteredOn(mes -> mes.cantidadPagos() == 0)
                .hasSize(10);
    }
}
