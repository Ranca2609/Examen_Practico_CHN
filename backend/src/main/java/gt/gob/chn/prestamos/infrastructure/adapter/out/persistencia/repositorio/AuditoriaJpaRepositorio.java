package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.AuditoriaEntidad;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaJpaRepositorio extends JpaRepository<AuditoriaEntidad, Long> {

    // El id desempata registros del mismo instante para que las paginas no se solapen.
    @Query(value = """
            SELECT a FROM AuditoriaEntidad a
            WHERE (:busqueda IS NULL
                   OR LOWER(a.usuario) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.entidadId) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:usuario IS NULL OR LOWER(a.usuario) LIKE LOWER(CONCAT('%', :usuario, '%')))
              AND (:accion IS NULL OR a.accion = :accion)
              AND (:entidad IS NULL OR a.entidad = :entidad)
              AND (:fechaDesde IS NULL OR a.fecha >= :fechaDesde)
              AND (:fechaHasta IS NULL OR a.fecha < :fechaHasta)
            ORDER BY a.fecha DESC, a.id DESC
            """,
            countQuery = """
            SELECT COUNT(a.id) FROM AuditoriaEntidad a
            WHERE (:busqueda IS NULL
                   OR LOWER(a.usuario) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.detalle) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(a.entidadId) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:usuario IS NULL OR LOWER(a.usuario) LIKE LOWER(CONCAT('%', :usuario, '%')))
              AND (:accion IS NULL OR a.accion = :accion)
              AND (:entidad IS NULL OR a.entidad = :entidad)
              AND (:fechaDesde IS NULL OR a.fecha >= :fechaDesde)
              AND (:fechaHasta IS NULL OR a.fecha < :fechaHasta)
            """)
    Page<AuditoriaEntidad> buscar(@Param("busqueda") String busqueda,
                                  @Param("usuario") String usuario,
                                  @Param("accion") String accion,
                                  @Param("entidad") String entidad,
                                  @Param("fechaDesde") LocalDateTime fechaDesde,
                                  @Param("fechaHasta") LocalDateTime fechaHasta,
                                  Pageable paginacion);
}
