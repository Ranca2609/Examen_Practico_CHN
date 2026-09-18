package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPago;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import java.util.List;
import java.util.Optional;

public interface PagoRepositorio {

    Pago guardar(Pago pago);

    Optional<PagoDetalle> buscarDetallePorId(Long id);

    List<PagoDetalle> listarDetallePorPrestamo(Long prestamoId);

    PaginaDominio<PagoDetalle> listarDetalle(FiltroPago filtro);

    /** Borrado en cascada explicito: se invoca al eliminar un cliente. */
    void eliminarPorCliente(Long clienteId);
}
