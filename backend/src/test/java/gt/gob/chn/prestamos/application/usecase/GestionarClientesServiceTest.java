package gt.gob.chn.prestamos.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gt.gob.chn.prestamos.domain.exception.ConflictoRecursoException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.command.ActualizarClienteCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarClienteCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.SolicitudPrestamoRepositorio;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GestionarClientesService - unicidad, auditoria y borrado en cascada")
class GestionarClientesServiceTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 10, 9, 30);
    private static final ContextoOperacion CONTEXTO = new ContextoOperacion("admin", "127.0.0.1");
    private static final String DPI = "2547896301234";
    private static final String CORREO = "maria.lopez@correo.gt";

    @Mock
    private ClienteRepositorio clienteRepositorio;

    @Mock
    private SolicitudPrestamoRepositorio solicitudRepositorio;

    @Mock
    private PrestamoRepositorio prestamoRepositorio;

    @Mock
    private PagoRepositorio pagoRepositorio;

    @Mock
    private AuditoriaPort auditoriaPort;

    @Mock
    private RelojPort relojPort;

    private GestionarClientesService servicio;

    @BeforeEach
    void prepararServicio() {
        servicio = new GestionarClientesService(clienteRepositorio, solicitudRepositorio,
                prestamoRepositorio, pagoRepositorio, auditoriaPort, relojPort);
    }

    @Test
    void registrar_guarda_el_cliente_nuevo_y_deja_constancia_en_auditoria() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(clienteRepositorio.buscarPorNumeroIdentificacion(DPI)).thenReturn(Optional.empty());
        when(clienteRepositorio.buscarPorCorreoElectronico(CORREO)).thenReturn(Optional.empty());
        when(clienteRepositorio.guardar(any(Cliente.class))).thenReturn(clienteExistente(7L));

        Cliente registrado = servicio.registrar(comandoRegistro(), CONTEXTO);

        assertThat(registrado.getId()).isEqualTo(7L);

        ArgumentCaptor<Cliente> capturado = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepositorio).guardar(capturado.capture());
        // El agregado se construye sin id (lo asigna la base) y nace activo.
        assertThat(capturado.getValue().getId()).isNull();
        assertThat(capturado.getValue().getNumeroIdentificacion()).isEqualTo(DPI);
        assertThat(capturado.getValue().isActivo()).isTrue();
        assertThat(capturado.getValue().getFechaCreacion()).isEqualTo(AHORA);

        verify(auditoriaPort).registrar(eq("admin"), eq(AccionesAuditoria.CLIENTE_CREADO),
                eq(AccionesAuditoria.ENTIDAD_CLIENTE), eq("7"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void registrar_con_dpi_duplicado_lanza_conflicto_y_no_guarda() {
        when(clienteRepositorio.buscarPorNumeroIdentificacion(DPI))
                .thenReturn(Optional.of(clienteExistente(1L)));

        assertThatThrownBy(() -> servicio.registrar(comandoRegistro(), CONTEXTO))
                .isInstanceOf(ConflictoRecursoException.class);

        verify(clienteRepositorio, never()).guardar(any());
        verify(auditoriaPort, never()).registrar(anyString(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void registrar_con_correo_duplicado_lanza_conflicto_y_no_guarda() {
        when(clienteRepositorio.buscarPorNumeroIdentificacion(DPI)).thenReturn(Optional.empty());
        when(clienteRepositorio.buscarPorCorreoElectronico(CORREO))
                .thenReturn(Optional.of(clienteExistente(1L)));

        assertThatThrownBy(() -> servicio.registrar(comandoRegistro(), CONTEXTO))
                .isInstanceOf(ConflictoRecursoException.class);

        verify(clienteRepositorio, never()).guardar(any());
    }

    @Test
    void actualizar_modifica_los_datos_de_contacto_y_audita() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(clienteRepositorio.buscarPorId(7L)).thenReturn(Optional.of(clienteExistente(7L)));
        when(clienteRepositorio.buscarPorCorreoElectronico("maria.mejia@correo.gt"))
                .thenReturn(Optional.empty());
        when(clienteRepositorio.guardar(any(Cliente.class))).thenAnswer(llamada -> llamada.getArgument(0));

        Cliente actualizado = servicio.actualizar(7L, new ActualizarClienteCommand(
                "Maria Jose", "Lopez de Mejia", "Zona 15, Ciudad de Guatemala",
                "maria.mejia@correo.gt", "55598765"), CONTEXTO);

        assertThat(actualizado.getApellido()).isEqualTo("Lopez de Mejia");
        assertThat(actualizado.getCorreoElectronico()).isEqualTo("maria.mejia@correo.gt");
        assertThat(actualizado.getTelefono()).isEqualTo("55598765");
        assertThat(actualizado.getFechaModificacion()).isEqualTo(AHORA);
        // El DPI es identidad legal: la actualizacion no lo toca.
        assertThat(actualizado.getNumeroIdentificacion()).isEqualTo(DPI);

        verify(auditoriaPort).registrar(eq("admin"), eq(AccionesAuditoria.CLIENTE_ACTUALIZADO),
                eq(AccionesAuditoria.ENTIDAD_CLIENTE), eq("7"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void actualizar_con_correo_de_otro_cliente_lanza_conflicto() {
        when(clienteRepositorio.buscarPorId(7L)).thenReturn(Optional.of(clienteExistente(7L)));
        // El correo ya pertenece al cliente 8: la unicidad se evalua contra los demas registros.
        when(clienteRepositorio.buscarPorCorreoElectronico("otro.cliente@correo.gt"))
                .thenReturn(Optional.of(clienteExistente(8L)));

        assertThatThrownBy(() -> servicio.actualizar(7L, new ActualizarClienteCommand(
                "Maria Jose", "Lopez Garcia", "Zona 10, Ciudad de Guatemala",
                "otro.cliente@correo.gt", "55512345"), CONTEXTO))
                .isInstanceOf(ConflictoRecursoException.class);

        verify(clienteRepositorio, never()).guardar(any());
    }

    @Test
    void actualizar_conservando_el_correo_propio_no_es_conflicto() {
        when(relojPort.ahora()).thenReturn(AHORA);
        when(clienteRepositorio.buscarPorId(7L)).thenReturn(Optional.of(clienteExistente(7L)));
        when(clienteRepositorio.buscarPorCorreoElectronico(CORREO))
                .thenReturn(Optional.of(clienteExistente(7L)));
        when(clienteRepositorio.guardar(any(Cliente.class))).thenAnswer(llamada -> llamada.getArgument(0));

        Cliente actualizado = servicio.actualizar(7L, new ActualizarClienteCommand(
                "Maria Jose", "Lopez Garcia", "Zona 11, Ciudad de Guatemala",
                CORREO, "55512345"), CONTEXTO);

        assertThat(actualizado.getCorreoElectronico()).isEqualTo(CORREO);
        assertThat(actualizado.getDireccion()).isEqualTo("Zona 11, Ciudad de Guatemala");
    }

    @Test
    void actualizar_cliente_inexistente_lanza_no_encontrado() {
        when(clienteRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(99L, new ActualizarClienteCommand(
                "Maria Jose", "Lopez Garcia", "Zona 10, Ciudad de Guatemala",
                CORREO, "55512345"), CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(clienteRepositorio, never()).guardar(any());
    }

    @Test
    void obtener_cliente_inexistente_lanza_no_encontrado() {
        when(clienteRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtener_devuelve_el_cliente_existente() {
        when(clienteRepositorio.buscarPorId(7L)).thenReturn(Optional.of(clienteExistente(7L)));

        assertThat(servicio.obtener(7L).getNumeroIdentificacion()).isEqualTo(DPI);
    }

    @Test
    void eliminar_borra_el_historial_en_orden_pagos_prestamos_solicitudes_y_cliente() {
        when(clienteRepositorio.buscarPorId(7L)).thenReturn(Optional.of(clienteExistente(7L)));

        servicio.eliminar(7L, CONTEXTO);

        // SQL Server rechaza multiples rutas de cascada, por eso el orden lo impone
        // el caso de uso: primero las filas que referencian a las siguientes.
        InOrder orden = inOrder(pagoRepositorio, prestamoRepositorio, solicitudRepositorio,
                clienteRepositorio);
        orden.verify(pagoRepositorio).eliminarPorCliente(7L);
        orden.verify(prestamoRepositorio).eliminarPorCliente(7L);
        orden.verify(solicitudRepositorio).eliminarPorCliente(7L);
        orden.verify(clienteRepositorio).eliminar(7L);

        verify(auditoriaPort).registrar(eq("admin"), eq(AccionesAuditoria.CLIENTE_ELIMINADO),
                eq(AccionesAuditoria.ENTIDAD_CLIENTE), eq("7"), anyString(), eq("127.0.0.1"));
    }

    @Test
    void eliminar_cliente_inexistente_lanza_no_encontrado_y_no_borra_nada() {
        when(clienteRepositorio.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(99L, CONTEXTO))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(pagoRepositorio, never()).eliminarPorCliente(any());
        verify(prestamoRepositorio, never()).eliminarPorCliente(any());
        verify(solicitudRepositorio, never()).eliminarPorCliente(any());
        verify(clienteRepositorio, never()).eliminar(any());
    }

    @Test
    void listar_delega_el_filtro_al_repositorio() {
        FiltroCliente filtro = FiltroCliente.de("Lopez", 0, 10);
        when(clienteRepositorio.listar(filtro))
                .thenReturn(PaginaDominio.de(List.of(clienteExistente(7L)), 0, 10, 1));

        PaginaDominio<Cliente> resultado = servicio.listar(filtro);

        assertThat(resultado.contenido()).hasSize(1);
        assertThat(resultado.totalElementos()).isEqualTo(1);
        assertThat(resultado.totalPaginas()).isEqualTo(1);
    }

    private static RegistrarClienteCommand comandoRegistro() {
        return new RegistrarClienteCommand("Maria Jose", "Lopez Garcia", DPI,
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala", CORREO, "55512345");
    }

    private static Cliente clienteExistente(Long id) {
        return Cliente.reconstituir(id, "Maria Jose", "Lopez Garcia", DPI,
                LocalDate.of(1990, 5, 20), "Zona 10, Ciudad de Guatemala", CORREO,
                "55512345", true, AHORA, null);
    }
}
