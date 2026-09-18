package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.gob.chn.prestamos.domain.model.RegistroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroAuditoria;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.ConsultarAuditoriaUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador.AuditoriaControlador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ManejadorExcepcionesGlobal no se importa: @WebMvcTest registra los @RestControllerAdvice por si solo.
@WebMvcTest(controllers = AuditoriaControlador.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = {"ADMIN"})
@DisplayName("AuditoriaControlador - filtros de la bitacora y traduccion de errores")
class AuditoriaControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarAuditoriaUseCase consultarAuditoria;

    @Test
    @DisplayName("Listar devuelve 200 con la pagina y traslada cada criterio al caso de uso")
    void listar_devuelve_200_y_traslada_los_criterios_al_caso_de_uso() throws Exception {
        given(consultarAuditoria.listar(any()))
                .willReturn(PaginaDominio.de(List.of(registroDemo()), 0, 20, 1));

        mockMvc.perform(get("/api/v1/auditoria")
                        .param("busqueda", "RC-001-2026")
                        .param("usuario", "cajero")
                        .param("accion", "PAGO_REGISTRADO")
                        .param("entidad", "PAGO")
                        .param("fechaDesde", "2026-04-01")
                        .param("fechaHasta", "2026-04-30")
                        .param("pagina", "0")
                        .param("tamano", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(12))
                .andExpect(jsonPath("$.contenido[0].usuario").value("cajero"))
                .andExpect(jsonPath("$.contenido[0].accion").value("PAGO_REGISTRADO"))
                .andExpect(jsonPath("$.contenido[0].entidad").value("PAGO"))
                .andExpect(jsonPath("$.contenido[0].direccionIp").value("192.168.1.25"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));

        // El filtro se arma en el controlador: se captura para comprobar que el
        // filtrado se delega completo y no se recorta ningun criterio.
        ArgumentCaptor<FiltroAuditoria> captor = ArgumentCaptor.forClass(FiltroAuditoria.class);
        verify(consultarAuditoria).listar(captor.capture());
        FiltroAuditoria filtro = captor.getValue();

        assertThat(filtro.busqueda()).isEqualTo("RC-001-2026");
        assertThat(filtro.usuario()).isEqualTo("cajero");
        assertThat(filtro.accion()).isEqualTo("PAGO_REGISTRADO");
        assertThat(filtro.entidad()).isEqualTo("PAGO");
        assertThat(filtro.fechaDesde()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(filtro.fechaHasta()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(filtro.pagina()).isZero();
        assertThat(filtro.tamano()).isEqualTo(20);
        assertThat(filtro.tieneFiltrosActivos()).isTrue();
    }

    @Test
    @DisplayName("Listar sin criterios consulta la bitacora completa con la paginacion por defecto")
    void listar_sin_criterios_entrega_un_filtro_sin_restricciones() throws Exception {
        given(consultarAuditoria.listar(any()))
                .willReturn(PaginaDominio.vacia(0, 10));

        mockMvc.perform(get("/api/v1/auditoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.totalElementos").value(0));

        ArgumentCaptor<FiltroAuditoria> captor = ArgumentCaptor.forClass(FiltroAuditoria.class);
        verify(consultarAuditoria).listar(captor.capture());
        FiltroAuditoria filtro = captor.getValue();

        assertThat(filtro.busqueda()).isNull();
        assertThat(filtro.usuario()).isNull();
        assertThat(filtro.accion()).isNull();
        assertThat(filtro.entidad()).isNull();
        assertThat(filtro.fechaDesde()).isNull();
        assertThat(filtro.fechaHasta()).isNull();
        assertThat(filtro.tieneFiltrosActivos()).isFalse();
    }

    @Test
    @DisplayName("Una fecha fuera del formato ISO devuelve 400 nombrando el parametro")
    void listar_con_una_fecha_mal_formada_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria").param("fechaDesde", "01/04/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.ruta").value("/api/v1/auditoria"))
                .andExpect(jsonPath("$.mensaje", containsString("fechaDesde")));

        // La conversion falla en el borde: la bitacora nunca se consulta.
        verifyNoInteractions(consultarAuditoria);
    }

    @Test
    @DisplayName("Un tamano de pagina mayor a 100 devuelve 400 y no consulta la bitacora")
    void listar_con_tamano_mayor_al_maximo_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria").param("tamano", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));

        verifyNoInteractions(consultarAuditoria);
    }

    @Test
    @DisplayName("Un rango de fechas invertido devuelve 400 por la validacion del filtro")
    void listar_con_un_rango_invertido_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/auditoria")
                        .param("fechaDesde", "2026-04-30")
                        .param("fechaHasta", "2026-04-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje", containsString("fecha")));

        verifyNoInteractions(consultarAuditoria);
    }

    private static RegistroAuditoria registroDemo() {
        return new RegistroAuditoria(12L, "cajero", "PAGO_REGISTRADO", "PAGO", "30",
                "Pago RC-001-2026-000001-4 por 2500.00 sobre el prestamo PR-001-2026-000001-9",
                "192.168.1.25", LocalDateTime.of(2026, 4, 5, 10, 15));
    }
}
