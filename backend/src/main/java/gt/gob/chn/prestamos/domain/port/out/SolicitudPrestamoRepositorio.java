package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroSolicitud;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import java.util.List;
import java.util.Optional;

public interface SolicitudPrestamoRepositorio {

    SolicitudPrestamo guardar(SolicitudPrestamo solicitud);

    Optional<SolicitudPrestamo> buscarPorId(Long id);

    Optional<SolicitudDetalle> buscarDetallePorId(Long id);

    PaginaDominio<SolicitudDetalle> listarDetalle(FiltroSolicitud filtro);

    List<SolicitudDetalle> listarDetallePorCliente(Long clienteId);

    /** Borrado en cascada explicito: se invoca al eliminar un cliente. */
    void eliminarPorCliente(Long clienteId);
}
