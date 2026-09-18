package gt.gob.chn.prestamos.infrastructure.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.exception.ReglaNegocioException;
import gt.gob.chn.prestamos.domain.model.FormaPago;
import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.RegistrarPagosUseCase;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.controlador.PagoControlador;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PagoControlador.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(ExtractorContextoOperacion.class)
@WithMockUser(username = "cajero", roles = {"CAJERO"})
@DisplayName("PagoControlador - registro de abonos y control del saldo")
class PagoControladorTest {

    private static final LocalDateTime FECHA_PAGO = LocalDateTime.of(2026, 4, 5, 10, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrarPagosUseCase registrarPagos;

    @Test
    void registrar_devuelve_201_con_el_recibo_y_los_saldos() throws Exception {
        given(registrarPagos.registrar(any(), any())).willReturn(pagoDemo());

        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2500.00")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/pagos/30"))
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.numeroRecibo").value("RC-001-2026-000001-4"))
                .andExpect(jsonPath("$.numeroPrestamo").value("PR-001-2026-000001-9"))
                .andExpect(jsonPath("$.monto").value(2500.00))
                .andExpect(jsonPath("$.saldoAnterior").value(10000.00))
                .andExpect(jsonPath("$.saldoPosterior").value(7500.00))
                .andExpect(jsonPath("$.formaPago").value("EFECTIVO"))
                .andExpect(jsonPath("$.usuarioRegistro").value("cajero"));

        verify(registrarPagos).registrar(any(), any());
    }

    @Test
    void registrar_un_monto_mayor_al_saldo_devuelve_409() throws Exception {
        given(registrarPagos.registrar(any(), any()))
                .willThrow(new ReglaNegocioException("El monto del pago excede el saldo pendiente "
                        + "del prestamo. Saldo disponible: 7500.00."));

        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("9000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"))
                .andExpect(jsonPath("$.mensaje").value(containsString("7500.00")));
    }

    @Test
    void registrar_un_monto_de_cero_devuelve_400_con_el_campo_monto() throws Exception {
        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("0.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("monto")));

        verifyNoInteractions(registrarPagos);
    }

    @Test
    void registrar_sin_prestamo_devuelve_400() throws Exception {
        String cuerpoSinPrestamo = """
                {
                  "monto": 2500.00,
                  "observaciones": "Abono de cuota"
                }
                """;

        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSinPrestamo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("prestamoId")));

        verifyNoInteractions(registrarPagos);
    }

    @Test
    void registrar_sobre_un_prestamo_inexistente_devuelve_404() throws Exception {
        given(registrarPagos.registrar(any(), any()))
                .willThrow(new RecursoNoEncontradoException("Prestamo", 99L));

        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("2500.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void registrar_con_json_ilegible_devuelve_400() throws Exception {
        mockMvc.perform(post("/api/v1/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prestamoId\": 20, \"monto\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void listar_devuelve_la_pagina_de_pagos() throws Exception {
        given(registrarPagos.listar(any()))
                .willReturn(PaginaDominio.de(List.of(pagoDemo()), 0, 10, 1));

        mockMvc.perform(get("/api/v1/pagos").param("prestamoId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].numeroRecibo").value("RC-001-2026-000001-4"))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    private static String cuerpo(String monto) {
        return """
                {
                  "prestamoId": 20,
                  "monto": %s,
                  "observaciones": "Abono de cuota"
                }
                """.formatted(monto);
    }

    private static PagoDetalle pagoDemo() {
        Pago pago = Pago.reconstituir(30L, "RC-001-2026-000001-4", 20L, new BigDecimal("2500.00"),
                FECHA_PAGO, FormaPago.EFECTIVO, new BigDecimal("10000.00"), new BigDecimal("7500.00"),
                "cajero", "Abono de cuota");
        return new PagoDetalle(pago, "PR-001-2026-000001-9", 5L, "Maria Jose Lopez Garcia");
    }
}
