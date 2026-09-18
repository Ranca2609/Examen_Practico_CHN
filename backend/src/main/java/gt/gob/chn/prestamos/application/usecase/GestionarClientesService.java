package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.ConflictoRecursoException;
import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.GestionarClientesUseCase;
import gt.gob.chn.prestamos.domain.port.in.command.ActualizarClienteCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarClienteCommand;
import gt.gob.chn.prestamos.domain.port.out.AuditoriaPort;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.port.out.RelojPort;
import gt.gob.chn.prestamos.domain.port.out.SolicitudPrestamoRepositorio;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GestionarClientesService implements GestionarClientesUseCase {

    private final ClienteRepositorio clienteRepositorio;
    private final SolicitudPrestamoRepositorio solicitudRepositorio;
    private final PrestamoRepositorio prestamoRepositorio;
    private final PagoRepositorio pagoRepositorio;
    private final AuditoriaPort auditoria;
    private final RelojPort reloj;

    public GestionarClientesService(ClienteRepositorio clienteRepositorio,
                                    SolicitudPrestamoRepositorio solicitudRepositorio,
                                    PrestamoRepositorio prestamoRepositorio,
                                    PagoRepositorio pagoRepositorio,
                                    AuditoriaPort auditoria,
                                    RelojPort reloj) {
        this.clienteRepositorio = clienteRepositorio;
        this.solicitudRepositorio = solicitudRepositorio;
        this.prestamoRepositorio = prestamoRepositorio;
        this.pagoRepositorio = pagoRepositorio;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public Cliente registrar(RegistrarClienteCommand cmd, ContextoOperacion ctx) {
        // Se valida antes que el UNIQUE de la base para responder un 409 con mensaje claro.
        exigirIdentificacionLibre(cmd.numeroIdentificacion());
        exigirCorreoLibre(cmd.correoElectronico(), null);

        Cliente cliente = Cliente.nuevo(cmd.nombre(), cmd.apellido(), cmd.numeroIdentificacion(),
                cmd.fechaNacimiento(), cmd.direccion(), cmd.correoElectronico(), cmd.telefono(),
                reloj.ahora());
        Cliente guardado = clienteRepositorio.guardar(cliente);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.CLIENTE_CREADO,
                AccionesAuditoria.ENTIDAD_CLIENTE, String.valueOf(guardado.getId()),
                "Alta de cliente " + guardado.nombreCompleto() + " (DPI "
                        + guardado.getNumeroIdentificacion() + ")",
                ctx.direccionIp());
        return guardado;
    }

    @Override
    public Cliente actualizar(Long id, ActualizarClienteCommand cmd, ContextoOperacion ctx) {
        Cliente cliente = exigirCliente(id);
        exigirCorreoLibre(cmd.correoElectronico(), id);

        cliente.actualizarDatos(cmd.nombre(), cmd.apellido(), cmd.direccion(),
                cmd.correoElectronico(), cmd.telefono(), reloj.ahora());
        Cliente guardado = clienteRepositorio.guardar(cliente);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.CLIENTE_ACTUALIZADO,
                AccionesAuditoria.ENTIDAD_CLIENTE, String.valueOf(id),
                "Actualización de datos de contacto de " + guardado.nombreCompleto(),
                ctx.direccionIp());
        return guardado;
    }

    @Override
    public void eliminar(Long id, ContextoOperacion ctx) {
        Cliente cliente = exigirCliente(id);

        // Cascada manual: SQL Server rechaza múltiples rutas de ON DELETE CASCADE hacia pagos.
        // El orden importa: primero las filas que referencian a las siguientes.
        pagoRepositorio.eliminarPorCliente(id);
        prestamoRepositorio.eliminarPorCliente(id);
        solicitudRepositorio.eliminarPorCliente(id);
        clienteRepositorio.eliminar(id);

        auditoria.registrar(ctx.usuario(), AccionesAuditoria.CLIENTE_ELIMINADO,
                AccionesAuditoria.ENTIDAD_CLIENTE, String.valueOf(id),
                "Baja de " + cliente.nombreCompleto() + " (DPI " + cliente.getNumeroIdentificacion()
                        + ") junto con sus pagos, préstamos y solicitudes",
                ctx.direccionIp());
    }

    @Override
    @Transactional(readOnly = true)
    public Cliente obtener(Long id) {
        return exigirCliente(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDominio<Cliente> listar(FiltroCliente filtro) {
        return clienteRepositorio.listar(filtro);
    }

    private Cliente exigirCliente(Long id) {
        return clienteRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    private void exigirIdentificacionLibre(String numeroIdentificacion) {
        if (numeroIdentificacion == null) {
            return; // El nulo lo rechaza el dominio con su propio mensaje.
        }
        clienteRepositorio.buscarPorNumeroIdentificacion(numeroIdentificacion.trim())
                .ifPresent(existente -> {
                    throw new ConflictoRecursoException(
                            "Ya existe un cliente con el número de identificación "
                                    + numeroIdentificacion.trim());
                });
    }

    // idPropio es null en un alta; en una edición permite conservar el correo propio.
    private void exigirCorreoLibre(String correo, Long idPropio) {
        if (correo == null) {
            return;
        }
        String normalizado = correo.trim().toLowerCase();
        Optional<Cliente> duenio = clienteRepositorio.buscarPorCorreoElectronico(normalizado);
        if (duenio.isPresent() && !duenio.get().getId().equals(idPropio)) {
            throw new ConflictoRecursoException(
                    "Ya existe un cliente con el correo electrónico " + normalizado);
        }
    }
}
