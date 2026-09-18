package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroPrestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.port.out.PrestamoRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorPrestamo;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.PrestamoJpaRepositorio;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PrestamoRepositorioJpa implements PrestamoRepositorio {

    private final PrestamoJpaRepositorio repositorio;
    private final MapeadorPrestamo mapeador;

    public PrestamoRepositorioJpa(PrestamoJpaRepositorio repositorio, MapeadorPrestamo mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    @Override
    public Prestamo guardar(Prestamo prestamo) {
        PrestamoEntidad guardado = repositorio.save(mapeador.aEntidad(prestamo));
        return mapeador.aDominio(guardado);
    }

    @Override
    public Optional<Prestamo> buscarPorId(Long id) {
        return repositorio.findById(id).map(mapeador::aDominio);
    }

    @Override
    public Optional<PrestamoDetalle> buscarDetallePorId(Long id) {
        return repositorio.buscarDetallePorId(id).map(mapeador::aDetalle);
    }

    @Override
    public Optional<Prestamo> buscarPorSolicitudId(Long solicitudId) {
        return repositorio.findBySolicitudId(solicitudId).map(mapeador::aDominio);
    }

    @Override
    public PaginaDominio<PrestamoDetalle> listarDetalle(FiltroPrestamo filtro) {
        return PaginacionJpa.convertir(
                repositorio.buscarDetalle(
                        CriteriosJpa.textoONulo(filtro.busqueda()),
                        filtro.clienteId(),
                        filtro.estado(),
                        filtro.montoMinimo(),
                        filtro.montoMaximo(),
                        filtro.saldoMinimo(),
                        filtro.saldoMaximo(),
                        filtro.desembolsoDesde(),
                        filtro.desembolsoHasta(),
                        filtro.vencimientoDesde(),
                        filtro.vencimientoHasta(),
                        PaginacionJpa.solicitud(filtro.pagina(), filtro.tamano())),
                mapeador::aDetalle);
    }

    @Override
    public List<PrestamoDetalle> listarDetallePorCliente(Long clienteId) {
        return repositorio.listarDetallePorCliente(clienteId).stream()
                .map(mapeador::aDetalle)
                .toList();
    }

    @Override
    public long contarVigentesPorCliente(Long clienteId) {
        return repositorio.countByClienteIdAndEstado(clienteId, EstadoPrestamo.VIGENTE);
    }

    // Requiere que los pagos del cliente ya se hayan borrado (FK pagos -> prestamos).
    @Override
    public void eliminarPorCliente(Long clienteId) {
        repositorio.deleteByClienteId(clienteId);
    }
}
