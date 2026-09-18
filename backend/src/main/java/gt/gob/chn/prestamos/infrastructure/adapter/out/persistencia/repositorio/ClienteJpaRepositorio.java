package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.ClienteEntidad;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteJpaRepositorio extends JpaRepository<ClienteEntidad, Long> {

    Optional<ClienteEntidad> findByNumeroIdentificacion(String numeroIdentificacion);

    Optional<ClienteEntidad> findByCorreoElectronico(String correoElectronico);

    @Query(value = """
            SELECT c FROM ClienteEntidad c
            WHERE (:busqueda IS NULL
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.correoElectronico) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.telefono) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:nacimientoDesde IS NULL OR c.fechaNacimiento >= :nacimientoDesde)
              AND (:nacimientoHasta IS NULL OR c.fechaNacimiento <= :nacimientoHasta)
              AND (:creacionDesde IS NULL OR c.fechaCreacion >= :creacionDesde)
              AND (:creacionHasta IS NULL OR c.fechaCreacion < :creacionHasta)
              AND (:activo IS NULL OR c.activo = :activo)
            ORDER BY c.apellido ASC, c.nombre ASC, c.id ASC
            """,
            countQuery = """
            SELECT COUNT(c.id) FROM ClienteEntidad c
            WHERE (:busqueda IS NULL
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.correoElectronico) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.telefono) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:nacimientoDesde IS NULL OR c.fechaNacimiento >= :nacimientoDesde)
              AND (:nacimientoHasta IS NULL OR c.fechaNacimiento <= :nacimientoHasta)
              AND (:creacionDesde IS NULL OR c.fechaCreacion >= :creacionDesde)
              AND (:creacionHasta IS NULL OR c.fechaCreacion < :creacionHasta)
              AND (:activo IS NULL OR c.activo = :activo)
            """)
    Page<ClienteEntidad> buscar(@Param("busqueda") String busqueda,
                                @Param("nacimientoDesde") LocalDate nacimientoDesde,
                                @Param("nacimientoHasta") LocalDate nacimientoHasta,
                                @Param("creacionDesde") LocalDateTime creacionDesde,
                                @Param("creacionHasta") LocalDateTime creacionHasta,
                                @Param("activo") Boolean activo,
                                Pageable paginacion);
}
