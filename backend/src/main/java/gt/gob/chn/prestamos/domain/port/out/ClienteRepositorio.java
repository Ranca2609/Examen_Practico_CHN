package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import java.util.Optional;

public interface ClienteRepositorio {

    Cliente guardar(Cliente cliente);

    Optional<Cliente> buscarPorId(Long id);

    Optional<Cliente> buscarPorNumeroIdentificacion(String numeroIdentificacion);

    Optional<Cliente> buscarPorCorreoElectronico(String correo);

    PaginaDominio<Cliente> listar(FiltroCliente filtro);

    /** El historial del cliente debe eliminarse antes. */
    void eliminar(Long id);
}
