package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.SolicitudPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroSolicitud;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.SolicitudDetalle;
import gt.gob.chn.prestamos.domain.port.out.SolicitudPrestamoRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.SolicitudPrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorSolicitud;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.SolicitudPrestamoJpaRepositorio;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SolicitudPrestamoRepositorioJpa implements SolicitudPrestamoRepositorio {

    private final SolicitudPrestamoJpaRepositorio repositorio;
    private final MapeadorSolicitud mapeador;

    public SolicitudPrestamoRepositorioJpa(SolicitudPrestamoJpaRepositorio repositorio,
                                           MapeadorSolicitud mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    @Override
    public SolicitudPrestamo guardar(SolicitudPrestamo solicitud) {
        SolicitudPrestamoEntidad guardada = repositorio.save(mapeador.aEntidad(solicitud));
        return mapeador.aDominio(guardada);
    }

    @Override
    public Optional<SolicitudPrestamo> buscarPorId(Long id) {
        return repositorio.findById(id).map(mapeador::aDominio);
    }

    @Override
    public Optional<SolicitudDetalle> buscarDetallePorId(Long id) {
        return repositorio.buscarDetallePorId(id).map(mapeador::aDetalle);
    }

    @Override
    public PaginaDominio<SolicitudDetalle> listarDetalle(FiltroSolicitud filtro) {
        return PaginacionJpa.convertir(
                repositorio.buscarDetalle(
                        CriteriosJpa.textoONulo(filtro.busqueda()),
                        filtro.clienteId(),
                        filtro.estado(),
                        filtro.tipoPrestamo(),
                        filtro.montoMinimo(),
                        filtro.montoMaximo(),
                        filtro.plazoMinimo(),
                        filtro.plazoMaximo(),
                        CriteriosJpa.inicioDelDia(filtro.fechaDesde()),
                        CriteriosJpa.inicioDelDiaSiguiente(filtro.fechaHasta()),
                        PaginacionJpa.solicitud(filtro.pagina(), filtro.tamano())),
                mapeador::aDetalle);
    }

    @Override
    public List<SolicitudDetalle> listarDetallePorCliente(Long clienteId) {
        return repositorio.listarDetallePorCliente(clienteId).stream()
                .map(mapeador::aDetalle)
                .toList();
    }

    // El caso de uso borra pagos -> prestamos -> solicitudes: SQL Server no admite
    // multiples rutas de ON DELETE CASCADE entre estas tablas.
    @Override
    public void eliminarPorCliente(Long clienteId) {
        repositorio.deleteByClienteId(clienteId);
    }
}
