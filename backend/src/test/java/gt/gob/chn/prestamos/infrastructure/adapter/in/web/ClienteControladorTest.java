package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.gob.chn.prestamos.domain.exception.ConflictoRecursoException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.ConsultarPrestamosUseCase;
import gt.gob.chn.prestamos.domain.port.in.GestionarClientesUseCase;
import gt.gob.chn.prestamos.domain.port.in.GestionarSolicitudesUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador.ClienteControlador;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ClienteControlador.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
// El extractor del contexto es un colaborador sin estado del adaptador web: se usa el real.
// ManejadorExcepcionesGlobal lo aporta el propio corte @WebMvcTest al ser @RestControllerAdvice.
@Import(ExtractorContextoOperacion.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
@DisplayName("ClienteControlador - contrato HTTP y traduccion de errores")
class ClienteControladorTest {

    private static final String CUERPO_VALIDO = """
            {
              "nombre": "Maria Jose",
              "apellido": "Lopez Garcia",
              "numeroIdentificacion": "2547896301234",
              "fechaNacimiento": "1990-05-20",
              "direccion": "Zona 10, Ciudad de Guatemala",
              "correoElectronico": "maria.lopez@correo.gt",
              "telefono": "55512345"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GestionarClientesUseCase gestionarClientes;

    @MockitoBean
    private GestionarSolicitudesUseCase gestionarSolicitudes;

    @MockitoBean
    private ConsultarPrestamosUseCase consultarPrestamos;

    @Test
    void registrar_devuelve_201_con_el_cliente_creado_y_la_cabecera_location() throws Exception {
        given(gestionarClientes.registrar(any(), any())).willReturn(clienteDemo());

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/clientes/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.nombreCompleto").value("Maria Jose Lopez Garcia"))
                .andExpect(jsonPath("$.numeroIdentificacion").value("2547896301234"))
                .andExpect(jsonPath("$.activo").value(true));

        verify(gestionarClientes).registrar(any(), any());
    }

    @Test
    void registrar_con_dpi_invalido_devuelve_400_con_la_lista_de_errores_de_campo() throws Exception {
        String cuerpoConDpiCorto = CUERPO_VALIDO.replace("2547896301234", "254789630");

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoConDpiCorto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.ruta").value("/api/v1/clientes"))
                .andExpect(jsonPath("$.errores").isArray())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("numeroIdentificacion")));

        // La peticion invalida se rechaza en el borde: el caso de uso nunca se invoca.
        verifyNoInteractions(gestionarClientes);
    }

    @Test
    void registrar_con_varios_campos_invalidos_reporta_todos_los_errores() throws Exception {
        String cuerpo = """
                {
                  "nombre": "M",
                  "apellido": "Lopez Garcia",
                  "numeroIdentificacion": "2547896301234",
                  "fechaNacimiento": "1990-05-20",
                  "direccion": "Zona",
                  "correoElectronico": "correo-invalido",
                  "telefono": "5551234"
                }
                """;

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("direccion")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("correoElectronico")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("telefono")));
    }

    @Test
    void obtener_cliente_inexistente_devuelve_404() throws Exception {
        given(gestionarClientes.obtener(99L))
                .willThrow(new RecursoNoEncontradoException("Cliente", 99L));

        mockMvc.perform(get("/api/v1/clientes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No se encontro Cliente con identificador 99."));
    }

    @Test
    void registrar_con_dpi_ya_existente_devuelve_409() throws Exception {
        given(gestionarClientes.registrar(any(), any()))
                .willThrow(new ConflictoRecursoException("El DPI 2547896301234 ya esta registrado."));

        mockMvc.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"));
    }

    @Test
    void eliminar_devuelve_204_sin_cuerpo() throws Exception {
        mockMvc.perform(delete("/api/v1/clientes/7"))
                .andExpect(status().isNoContent());

        verify(gestionarClientes).eliminar(eq(7L), any());
    }

    @Test
    void eliminar_cliente_inexistente_devuelve_404() throws Exception {
        willThrow(new RecursoNoEncontradoException("Cliente", 99L))
                .given(gestionarClientes).eliminar(eq(99L), any());

        mockMvc.perform(delete("/api/v1/clientes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void actualizar_devuelve_200_con_los_datos_modificados() throws Exception {
        given(gestionarClientes.actualizar(eq(7L), any(), any())).willReturn(clienteDemo());
        String cuerpo = """
                {
                  "nombre": "Maria Jose",
                  "apellido": "Lopez Garcia",
                  "direccion": "Zona 15, Ciudad de Guatemala",
                  "correoElectronico": "maria.lopez@correo.gt",
                  "telefono": "55512345"
                }
                """;

        mockMvc.perform(put("/api/v1/clientes/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void listar_devuelve_la_pagina_con_los_metadatos_de_paginacion() throws Exception {
        given(gestionarClientes.listar(any()))
                .willReturn(PaginaDominio.de(List.of(clienteDemo()), 0, 10, 1));

        mockMvc.perform(get("/api/v1/clientes").param("pagina", "0").param("tamano", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(7))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(10))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void listar_con_pagina_negativa_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/clientes").param("pagina", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    @DisplayName("Listar traslada al caso de uso cada criterio de busqueda recibido en la URL")
    void listar_traslada_todos_los_criterios_al_caso_de_uso() throws Exception {
        given(gestionarClientes.listar(any()))
                .willReturn(PaginaDominio.de(List.of(clienteDemo()), 0, 10, 1));

        mockMvc.perform(get("/api/v1/clientes")
                        .param("busqueda", "Lopez")
                        .param("nacimientoDesde", "1990-01-01")
                        .param("nacimientoHasta", "1995-12-31")
                        .param("creacionDesde", "2026-01-01")
                        .param("creacionHasta", "2026-03-31")
                        .param("activo", "true")
                        .param("pagina", "0")
                        .param("tamano", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].id").value(7));

        // El filtro se arma en el controlador: se captura para comprobar que ningun
        // criterio se pierde en el camino y que la consulta se resuelve en la base.
        ArgumentCaptor<FiltroCliente> captor = ArgumentCaptor.forClass(FiltroCliente.class);
        verify(gestionarClientes).listar(captor.capture());
        FiltroCliente filtro = captor.getValue();

        assertThat(filtro.busqueda()).isEqualTo("Lopez");
        assertThat(filtro.nacimientoDesde()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(filtro.nacimientoHasta()).isEqualTo(LocalDate.of(1995, 12, 31));
        assertThat(filtro.creacionDesde()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filtro.creacionHasta()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(filtro.activo()).isTrue();
        assertThat(filtro.pagina()).isZero();
        assertThat(filtro.tamano()).isEqualTo(10);
    }

    @Test
    @DisplayName("Listar sin criterios entrega un filtro vacio, no uno con cadenas en blanco")
    void listar_sin_criterios_entrega_un_filtro_sin_restricciones() throws Exception {
        given(gestionarClientes.listar(any()))
                .willReturn(PaginaDominio.de(List.of(clienteDemo()), 0, 10, 1));

        mockMvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroCliente> captor = ArgumentCaptor.forClass(FiltroCliente.class);
        verify(gestionarClientes).listar(captor.capture());
        FiltroCliente filtro = captor.getValue();

        assertThat(filtro.busqueda()).isNull();
        assertThat(filtro.nacimientoDesde()).isNull();
        assertThat(filtro.nacimientoHasta()).isNull();
        assertThat(filtro.creacionDesde()).isNull();
        assertThat(filtro.creacionHasta()).isNull();
        assertThat(filtro.activo()).isNull();
        assertThat(filtro.tieneFiltrosActivos()).isFalse();
    }

    @Test
    @DisplayName("Una fecha fuera del formato ISO devuelve 400 nombrando el parametro")
    void listar_con_una_fecha_mal_formada_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/clientes").param("nacimientoDesde", "31-01-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje", containsString("nacimientoDesde")));

        // La conversion falla en el borde: la consulta nunca llega al caso de uso.
        verifyNoInteractions(gestionarClientes);
    }

    @Test
    @DisplayName("Un rango de fechas invertido devuelve 400 por la validacion del filtro")
    void listar_con_un_rango_invertido_devuelve_400() throws Exception {
        mockMvc.perform(get("/api/v1/clientes")
                        .param("nacimientoDesde", "1995-12-31")
                        .param("nacimientoHasta", "1990-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje", containsString("fecha")));

        verifyNoInteractions(gestionarClientes);
    }

    private static Cliente clienteDemo() {
        return Cliente.reconstituir(7L, "Maria Jose", "Lopez Garcia", "2547896301234",
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala",
                "maria.lopez@correo.gt", "55512345", true,
                LocalDateTime.of(2026, 3, 10, 9, 30), null);
    }
}
