package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroPago;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.ContextoOperacion;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarPagoCommand;
import java.util.List;

public interface RegistrarPagosUseCase {

    /** Actualiza el saldo del prestamo y lo liquida cuando llega a cero. */
    PagoDetalle registrar(RegistrarPagoCommand cmd, ContextoOperacion ctx);

    List<PagoDetalle> listarPorPrestamo(Long prestamoId);

    PaginaDominio<PagoDetalle> listar(FiltroPago filtro);
}
