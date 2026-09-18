package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.UsuarioEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioJpaRepositorio extends JpaRepository<UsuarioEntidad, Long> {

    Optional<UsuarioEntidad> findByUsername(String username);

    boolean existsByUsername(String username);
}
