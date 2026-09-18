package gt.gob.chn.prestamos.domain.port.out;

import gt.gob.chn.prestamos.domain.model.Usuario;
import java.util.Optional;

public interface UsuarioRepositorio {

    Optional<Usuario> buscarPorUsername(String username);

    Usuario guardar(Usuario usuario);

    boolean existePorUsername(String username);
}
