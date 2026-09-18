package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.adaptador;

import gt.gob.chn.prestamos.domain.model.Usuario;
import gt.gob.chn.prestamos.domain.port.out.UsuarioRepositorio;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.UsuarioEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador.MapeadorUsuario;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.UsuarioJpaRepositorio;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UsuarioRepositorioJpa implements UsuarioRepositorio {

    private final UsuarioJpaRepositorio repositorio;
    private final MapeadorUsuario mapeador;

    public UsuarioRepositorioJpa(UsuarioJpaRepositorio repositorio, MapeadorUsuario mapeador) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
    }

    @Override
    public Optional<Usuario> buscarPorUsername(String username) {
        return repositorio.findByUsername(username).map(mapeador::aDominio);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        UsuarioEntidad guardado = repositorio.save(mapeador.aEntidad(usuario));
        return mapeador.aDominio(guardado);
    }

    @Override
    public boolean existePorUsername(String username) {
        return repositorio.existsByUsername(username);
    }
}
