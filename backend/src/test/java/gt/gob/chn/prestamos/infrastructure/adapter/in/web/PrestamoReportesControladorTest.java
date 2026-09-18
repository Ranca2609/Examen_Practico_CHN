package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.reporte.ArchivoGenerado;
import gt.gob.chn.prestamos.domain.model.reporte.FormatoReporte;
import gt.gob.chn.prestamos.domain.port.in.ConsultarPrestamosUseCase;
import gt.gob.chn.prestamos.domain.port.in.GenerarReportesUseCase;
import gt.gob.chn.prestamos.domain.port.in.RegistrarPagosUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador.PrestamoControlador;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PrestamoControlador.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// El extractor del contexto es un colaborador sin estado del adaptador web: se usa el real.
// ManejadorExcepcionesGlobal lo aporta el propio corte @WebMvcTest al ser @RestControllerAdvice.
@Import(ExtractorContextoOperacion.class)
@WithMockUser(username = "cajero", roles = {"CAJERO"})
@DisplayName("PrestamoControlador - descarga de reportes en PDF y Excel")
class PrestamoReportesControladorTest {

    private static final String TIPO_PDF = "application/pdf";
    private static final String TIPO_EXCEL =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Firma de un PDF real ("%PDF"): sirve para comprobar que los bytes no se alteran. */
    private static final byte[] BYTES = new byte[] {37, 80, 68, 70, 45, 49, 46, 52};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarPrestamosUseCase consultarPrestamos;

    @MockitoBean
    private RegistrarPagosUseCase registrarPagos;

    @MockitoBean
    private GenerarReportesUseCase generarReportes;

    @Test
    @DisplayName("El plan de amortizacion en PDF se descarga como adjunto con su nombre")
    void amortizacion_en_pdf_devuelve_el_archivo_como_adjunto() throws Exception {
        String nombre = "plan-amortizacion-PR-001-2026-000001-9.pdf";
        given(generarReportes.planAmortizacion(eq(1L), eq(FormatoReporte.PDF), any()))
                .willReturn(new ArchivoGenerado(nombre, TIPO_PDF, BYTES));

        MockHttpServletResponse respuesta = mockMvc.perform(
                        get("/api/v1/prestamos/1/amortizacion.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(TIPO_PDF)))
                // Sin "attachment" el navegador abriria el PDF en una pestana en lugar
                // de guardarlo, y el nombre del archivo se perderia.
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(nombre)))
                // Son datos de clientes: no deben quedar en la cache del navegador.
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse();

        assertThat(respuesta.getContentAsByteArray()).isEqualTo(BYTES);
        verify(generarReportes).planAmortizacion(eq(1L), eq(FormatoReporte.PDF), any());
    }

    @Test
    @DisplayName("El plan de amortizacion en Excel se descarga con el tipo de contenido de xlsx")
    void amortizacion_en_excel_devuelve_el_libro_de_trabajo() throws Exception {
        String nombre = "plan-amortizacion-PR-001-2026-000001-9.xlsx";
        given(generarReportes.planAmortizacion(eq(1L), eq(FormatoReporte.EXCEL), any()))
                .willReturn(new ArchivoGenerado(nombre, TIPO_EXCEL, BYTES));

        MockHttpServletResponse respuesta = mockMvc.perform(
                        get("/api/v1/prestamos/1/amortizacion.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(TIPO_EXCEL)))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(nombre)))
                .andReturn().getResponse();

        assertThat(respuesta.getContentAsByteArray()).isEqualTo(BYTES);
        verify(generarReportes).planAmortizacion(eq(1L), eq(FormatoReporte.EXCEL), any());
    }

    @Test
    @DisplayName("El historial de pagos en PDF se descarga como adjunto")
    void pagos_en_pdf_devuelve_el_archivo_como_adjunto() throws Exception {
        String nombre = "historial-pagos-PR-001-2026-000001-9.pdf";
        given(generarReportes.historialPagos(eq(1L), eq(FormatoReporte.PDF), any()))
                .willReturn(new ArchivoGenerado(nombre, TIPO_PDF, BYTES));

        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/prestamos/1/pagos.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(TIPO_PDF)))
                .andExpect(header().string("Content-Disposition", containsString(nombre)))
                .andReturn().getResponse();

        assertThat(respuesta.getContentAsByteArray()).isEqualTo(BYTES);
        verify(generarReportes).historialPagos(eq(1L), eq(FormatoReporte.PDF), any());
    }

    @Test
    @DisplayName("El historial de pagos en Excel se descarga con el tipo de contenido de xlsx")
    void pagos_en_excel_devuelve_el_libro_de_trabajo() throws Exception {
        String nombre = "historial-pagos-PR-001-2026-000001-9.xlsx";
        given(generarReportes.historialPagos(eq(1L), eq(FormatoReporte.EXCEL), any()))
                .willReturn(new ArchivoGenerado(nombre, TIPO_EXCEL, BYTES));

        MockHttpServletResponse respuesta = mockMvc.perform(get("/api/v1/prestamos/1/pagos.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString(TIPO_EXCEL)))
                .andExpect(header().string("Content-Disposition", containsString(nombre)))
                .andReturn().getResponse();

        assertThat(respuesta.getContentAsByteArray()).isEqualTo(BYTES);
        verify(generarReportes).historialPagos(eq(1L), eq(FormatoReporte.EXCEL), any());
    }

    @Test
    @DisplayName("Una extension que el sistema no genera responde 400 con el error uniforme")
    void una_extension_desconocida_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/prestamos/1/amortizacion.csv"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        // La extension se rechaza en el borde: el caso de uso nunca se invoca.
        verifyNoInteractions(generarReportes);
    }

    @Test
    @DisplayName("Un prestamo inexistente responde 404 y no descarga nada")
    void un_prestamo_inexistente_devuelve_404() throws Exception {
        given(generarReportes.planAmortizacion(eq(99L), eq(FormatoReporte.PDF), any()))
                .willThrow(new RecursoNoEncontradoException("Prestamo", 99L));

        mockMvc.perform(get("/api/v1/prestamos/99/amortizacion.pdf"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
