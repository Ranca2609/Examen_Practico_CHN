package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.Cliente;
import gt.gob.chn.prestamos.domain.model.consulta.FiltroCliente;
import gt.gob.chn.prestamos.domain.model.consulta.PaginaDominio;
import gt.gob.chn.prestamos.domain.port.out.ClienteRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.ClienteEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorCliente;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.ClienteJpaRepositorio;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ClienteRepositorioJpa implements ClienteRepositorio {

    private final ClienteJpaRepositorio repositorio;
    private final MapeadorCliente mapeador;

    public ClienteRepositorioJpa(ClienteJpaRepositorio repositorio, MapeadorCliente mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    @Override
    public Cliente guardar(Cliente cliente) {
        ClienteEntidad guardada = repositorio.save(mapeador.aEntidad(cliente));
        return mapeador.aDominio(guardada);
    }

    @Override
    public Optional<Cliente> buscarPorId(Long id) {
        return repositorio.findById(id).map(mapeador::aDominio);
    }

    @Override
    public Optional<Cliente> buscarPorNumeroIdentificacion(String numeroIdentificacion) {
        return repositorio.findByNumeroIdentificacion(numeroIdentificacion).map(mapeador::aDominio);
    }

    @Override
    public Optional<Cliente> buscarPorCorreoElectronico(String correo) {
        // El dominio guarda el correo en minusculas; se normaliza igual para no fallar por capitalizacion.
        String normalizado = correo == null ? null : correo.trim().toLowerCase();
        return repositorio.findByCorreoElectronico(normalizado).map(mapeador::aDominio);
    }

    @Override
    public PaginaDominio<Cliente> listar(FiltroCliente filtro) {
        return PaginacionJpa.convertir(
                repositorio.buscar(
                        CriteriosJpa.textoONulo(filtro.busqueda()),
                        filtro.nacimientoDesde(),
                        filtro.nacimientoHasta(),
                        CriteriosJpa.inicioDelDia(filtro.creacionDesde()),
                        CriteriosJpa.inicioDelDiaSiguiente(filtro.creacionHasta()),
                        filtro.activo(),
                        PaginacionJpa.solicitud(filtro.pagina(), filtro.tamano())),
                mapeador::aDominio);
    }

    @Override
    public void eliminar(Long id) {
        repositorio.deleteById(id);
    }
}
