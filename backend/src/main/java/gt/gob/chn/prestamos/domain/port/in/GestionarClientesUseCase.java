package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.in.command.ActualizarClienteCommand;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarClienteCommand;

public interface GestionarClientesUseCase {

    /** Falla con ConflictoRecursoException si el DPI o el correo ya existen. */
    Cliente registrar(RegistrarClienteCommand cmd, ContextoOperacion ctx);

    Cliente actualizar(Long id, ActualizarClienteCommand cmd, ContextoOperacion ctx);

    /** Borra tambien pagos, prestamos y solicitudes del cliente, en una sola transaccion. */
    void eliminar(Long id, ContextoOperacion ctx);

    Cliente obtener(Long id);

    PaginaDominio<Cliente> listar(FiltroCliente filtro);
}
