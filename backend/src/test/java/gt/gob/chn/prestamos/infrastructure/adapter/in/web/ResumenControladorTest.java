package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.domain.port.in.ConsultarResumenUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador.ResumenControlador;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ResumenControlador.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(username = "consulta", roles = {"CONSULTA"})
@DisplayName("ResumenControlador - totales y series del tablero")
class ResumenControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarResumenUseCase consultarResumen;

    @Test
    @DisplayName("Devuelve 200 con los 9 totales sin cambios")
    void devuelve_los_totales() throws Exception {
        given(consultarResumen.obtener()).willReturn(resumenDemo());

        mockMvc.perform(get("/api/v1/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClientes").value(8))
                .andExpect(jsonPath("$.solicitudesEnProceso").value(4))
                .andExpect(jsonPath("$.solicitudesAprobadas").value(4))
                .andExpect(jsonPath("$.solicitudesRechazadas").value(2))
                .andExpect(jsonPath("$.prestamosVigentes").value(4))
                .andExpect(jsonPath("$.prestamosLiquidados").value(0))
                .andExpect(jsonPath("$.montoTotalAprobado").value(1495000.00))
                .andExpect(jsonPath("$.saldoPendienteTotal").value(2107286.71))
                .andExpect(jsonPath("$.totalRecuperado").value(42377.26));

        verify(consultarResumen).obtener();
    }

    @Test
    @DisplayName("carteraPorTipo es un arreglo con los 5 tipos en orden y sus indicadores")
    void cartera_por_tipo_tiene_la_forma_del_contrato() throws Exception {
        given(consultarResumen.obtener()).willReturn(resumenDemo());

        mockMvc.perform(get("/api/v1/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carteraPorTipo", hasSize(5)))
                .andExpect(jsonPath("$.carteraPorTipo[*].tipoPrestamo",
                        contains("PERSONAL", "HIPOTECARIO", "VEHICULAR", "EMPRESARIAL", "EDUCATIVO")))
                .andExpect(jsonPath("$.carteraPorTipo[1].tipoPrestamo").value("HIPOTECARIO"))
                .andExpect(jsonPath("$.carteraPorTipo[1].cantidadPrestamos").value(1))
                .andExpect(jsonPath("$.carteraPorTipo[1].montoAprobado").value(850000.00))
                .andExpect(jsonPath("$.carteraPorTipo[1].saldoPendiente").value(1334009.98))
                .andExpect(jsonPath("$.carteraPorTipo[1].totalRecuperado").value(0.00))
                .andExpect(jsonPath("$.carteraPorTipo[3].tipoPrestamo").value("EMPRESARIAL"))
                .andExpect(jsonPath("$.carteraPorTipo[3].cantidadPrestamos").value(0))
                .andExpect(jsonPath("$.carteraPorTipo[3].montoAprobado").value(0.00))
                // jsonPath(...).value(...) ignora la escala (850000 == 850000.00): los dos decimales
                // se verifican en el texto, incluidos los ceros que llegan como BigDecimal.ZERO.
                .andExpect(content().string(containsString("\"montoAprobado\":850000.00")))
                .andExpect(content().string(containsString("\"totalRecuperado\":0.00")))
                .andExpect(content().string(containsString("\"montoAprobado\":0.00")));
    }

    @Test
    @DisplayName("recaudacionMensual es un arreglo de 12 meses con periodo AAAA-MM, anio, mes y monto")
    void recaudacion_mensual_tiene_la_forma_del_contrato() throws Exception {
        given(consultarResumen.obtener()).willReturn(resumenDemo());

        mockMvc.perform(get("/api/v1/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recaudacionMensual", hasSize(12)))
                .andExpect(jsonPath("$.recaudacionMensual[0].periodo").value("2025-10"))
                .andExpect(jsonPath("$.recaudacionMensual[0].anio").value(2025))
                .andExpect(jsonPath("$.recaudacionMensual[0].mes").value(10))
                .andExpect(jsonPath("$.recaudacionMensual[0].cantidadPagos").value(0))
                .andExpect(jsonPath("$.recaudacionMensual[0].monto").value(0.00))
                .andExpect(jsonPath("$.recaudacionMensual[5].periodo").value("2026-03"))
                .andExpect(jsonPath("$.recaudacionMensual[5].anio").value(2026))
                .andExpect(jsonPath("$.recaudacionMensual[5].mes").value(3))
                .andExpect(jsonPath("$.recaudacionMensual[5].cantidadPagos").value(2))
                .andExpect(jsonPath("$.recaudacionMensual[5].monto").value(17769.76))
                .andExpect(jsonPath("$.recaudacionMensual[11].periodo").value("2026-09"))
                // Mismo motivo: los meses sin pagos deben viajar como 0.00 y no como 0.
                .andExpect(content().string(containsString("\"monto\":0.00")));
    }

    @Test
    @DisplayName("Con el resumen vacio las series viajan como arreglos vacios, no como null")
    void con_el_resumen_vacio_las_series_son_arreglos_vacios() throws Exception {
        given(consultarResumen.obtener()).willReturn(ResumenGeneral.vacio());

        mockMvc.perform(get("/api/v1/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClientes").value(0))
                .andExpect(jsonPath("$.carteraPorTipo", hasSize(0)))
                .andExpect(jsonPath("$.recaudacionMensual", hasSize(0)));
    }

    private static ResumenGeneral resumenDemo() {
        ResumenGeneral totales = new ResumenGeneral(8L, 4L, 4L, 2L, 4L, 0L,
                new BigDecimal("1495000.00"), new BigDecimal("2107286.71"), new BigDecimal("42377.26"));
        List<CarteraPorTipo> cartera = CarteraPorTipo.completar(List.of(
                new CarteraPorTipo(TipoPrestamo.HIPOTECARIO, 1L, new BigDecimal("850000.00"),
                        new BigDecimal("1334009.98"), BigDecimal.ZERO)));
        List<RecaudacionMensual> recaudacion = RecaudacionMensual.completar(List.of(
                        new RecaudacionMensual(YearMonth.of(2026, 3), 2L, new BigDecimal("17769.76"))),
                YearMonth.of(2025, 10), YearMonth.of(2026, 9));
        return totales.conSeries(cartera, recaudacion);
    }
}
