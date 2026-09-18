package gt.gob.chn.prestamos.domain.port.in;

import gt.gob.chn.prestamos.domain.model.consulta.FiltroPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import java.util.List;

public interface ConsultarPrestamosUseCase {

    PrestamoDetalle obtener(Long id);

    PaginaDominio<PrestamoDetalle> listar(FiltroPrestamo filtro);

    List<PrestamoDetalle> listarPorCliente(Long clienteId);

    PlanAmortizacion planAmortizacion(Long prestamoId);
}
