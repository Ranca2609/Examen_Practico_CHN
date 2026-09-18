package gt.gob.chn.prestamos.application.usecase;

import gt.gob.chn.prestamos.domain.exception.RecursoNoEncontradoException;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.port.in.ConsultarPrestamosUseCase;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.domain.service.CalculadoraAmortizacion;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsultarPrestamosService implements ConsultarPrestamosUseCase {

    private final PrestamoRepositorio prestamoRepositorio;
    private final ClienteRepositorio clienteRepositorio;
    private final CalculadoraAmortizacion calculadora;

    public ConsultarPrestamosService(PrestamoRepositorio prestamoRepositorio,
                                     ClienteRepositorio clienteRepositorio,
                                     CalculadoraAmortizacion calculadora) {
        this.prestamoRepositorio = prestamoRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.calculadora = calculadora;
    }

    @Override
    public PrestamoDetalle obtener(Long id) {
        return prestamoRepositorio.buscarDetallePorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo", id));
    }

    @Override
    public PaginaDominio<PrestamoDetalle> listar(FiltroPrestamo filtro) {
        return prestamoRepositorio.listarDetalle(filtro);
    }

    @Override
    public List<PrestamoDetalle> listarPorCliente(Long clienteId) {
        if (clienteRepositorio.buscarPorId(clienteId).isEmpty()) {
            throw new RecursoNoEncontradoException("Cliente", clienteId);
        }
        return prestamoRepositorio.listarDetallePorCliente(clienteId);
    }

    @Override
    public PlanAmortizacion planAmortizacion(Long prestamoId) {
        Prestamo prestamo = prestamoRepositorio.buscarPorId(prestamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo", prestamoId));
        // El plan no se persiste: se recalcula con las condiciones aprobadas, que son inmutables.
        return calculadora.calcular(prestamo.getMontoAprobado(), prestamo.getPlazoMeses(),
                prestamo.getTasaInteresAnual());
    }
}
