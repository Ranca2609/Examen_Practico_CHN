package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import java.util.List;
import java.util.Optional;

public interface PrestamoRepositorio {

    Prestamo guardar(Prestamo prestamo);

    Optional<Prestamo> buscarPorId(Long id);

    Optional<PrestamoDetalle> buscarDetallePorId(Long id);

    /** Un prestamo por solicitud: evita desembolsar dos veces la misma aprobacion. */
    Optional<Prestamo> buscarPorSolicitudId(Long solicitudId);

    PaginaDominio<PrestamoDetalle> listarDetalle(FiltroPrestamo filtro);

    List<PrestamoDetalle> listarDetallePorCliente(Long clienteId);

    long contarVigentesPorCliente(Long clienteId);

    /** Borrado en cascada explicito: se invoca al eliminar un cliente. */
    void eliminarPorCliente(Long clienteId);
}
