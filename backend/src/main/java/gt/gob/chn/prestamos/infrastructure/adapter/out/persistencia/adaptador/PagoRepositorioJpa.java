package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPago;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.out.PagoRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PagoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorPago;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.PagoJpaRepositorio;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PagoRepositorioJpa implements PagoRepositorio {

    private final PagoJpaRepositorio repositorio;
    private final MapeadorPago mapeador;

    public PagoRepositorioJpa(PagoJpaRepositorio repositorio, MapeadorPago mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    @Override
    public Pago guardar(Pago pago) {
        PagoEntidad guardado = repositorio.save(mapeador.aEntidad(pago));
        return mapeador.aDominio(guardado);
    }

    @Override
    public Optional<PagoDetalle> buscarDetallePorId(Long id) {
        return repositorio.buscarDetallePorId(id).map(mapeador::aDetalle);
    }

    @Override
    public List<PagoDetalle> listarDetallePorPrestamo(Long prestamoId) {
        return repositorio.listarDetallePorPrestamo(prestamoId).stream()
                .map(mapeador::aDetalle)
                .toList();
    }

    @Override
    public PaginaDominio<PagoDetalle> listarDetalle(FiltroPago filtro) {
        return PaginacionJpa.convertir(
                repositorio.buscarDetalle(
                        CriteriosJpa.textoONulo(filtro.busqueda()),
                        filtro.prestamoId(),
                        filtro.clienteId(),
                        filtro.montoMinimo(),
                        filtro.montoMaximo(),
                        CriteriosJpa.inicioDelDia(filtro.fechaDesde()),
                        CriteriosJpa.inicioDelDiaSiguiente(filtro.fechaHasta()),
                        CriteriosJpa.textoONulo(filtro.usuarioRegistro()),
                        PaginacionJpa.solicitud(filtro.pagina(), filtro.tamano())),
                mapeador::aDetalle);
    }

    // Primer paso de la baja del cliente: los pagos se borran antes que sus prestamos.
    @Override
    public void eliminarPorCliente(Long clienteId) {
        repositorio.eliminarPorCliente(clienteId);
    }
}
