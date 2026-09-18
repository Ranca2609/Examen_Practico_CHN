package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.domain.model.EstadoSolicitud;
import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.SolicitudPrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.SolicitudProyeccion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SolicitudPrestamoJpaRepositorio extends JpaRepository<SolicitudPrestamoEntidad, Long> {

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.SolicitudProyeccion(
                       s, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM SolicitudPrestamoEntidad s
            JOIN ClienteEntidad c ON c.id = s.clienteId
            WHERE s.id = :id
            """)
    Optional<SolicitudProyeccion> buscarDetallePorId(@Param("id") Long id);

    // Cada parametro aparece tambien en una comparacion para que Hibernate infiera su tipo cuando llega nulo.
    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.SolicitudProyeccion(
                       s, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM SolicitudPrestamoEntidad s
            JOIN ClienteEntidad c ON c.id = s.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(s.numeroSolicitud) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(s.destino) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:clienteId IS NULL OR s.clienteId = :clienteId)
              AND (:estado IS NULL OR s.estado = :estado)
              AND (:tipoPrestamo IS NULL OR s.tipoPrestamo = :tipoPrestamo)
              AND (:montoMinimo IS NULL OR s.montoSolicitado >= :montoMinimo)
              AND (:montoMaximo IS NULL OR s.montoSolicitado <= :montoMaximo)
              AND (:plazoMinimo IS NULL OR s.plazoMeses >= :plazoMinimo)
              AND (:plazoMaximo IS NULL OR s.plazoMeses <= :plazoMaximo)
              AND (:fechaDesde IS NULL OR s.fechaSolicitud >= :fechaDesde)
              AND (:fechaHasta IS NULL OR s.fechaSolicitud < :fechaHasta)
            ORDER BY s.fechaSolicitud DESC, s.id DESC
            """,
            countQuery = """
            SELECT COUNT(s.id)
            FROM SolicitudPrestamoEntidad s
            JOIN ClienteEntidad c ON c.id = s.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(s.numeroSolicitud) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(s.destino) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:clienteId IS NULL OR s.clienteId = :clienteId)
              AND (:estado IS NULL OR s.estado = :estado)
              AND (:tipoPrestamo IS NULL OR s.tipoPrestamo = :tipoPrestamo)
              AND (:montoMinimo IS NULL OR s.montoSolicitado >= :montoMinimo)
              AND (:montoMaximo IS NULL OR s.montoSolicitado <= :montoMaximo)
              AND (:plazoMinimo IS NULL OR s.plazoMeses >= :plazoMinimo)
              AND (:plazoMaximo IS NULL OR s.plazoMeses <= :plazoMaximo)
              AND (:fechaDesde IS NULL OR s.fechaSolicitud >= :fechaDesde)
              AND (:fechaHasta IS NULL OR s.fechaSolicitud < :fechaHasta)
            """)
    Page<SolicitudProyeccion> buscarDetalle(@Param("busqueda") String busqueda,
                                            @Param("clienteId") Long clienteId,
                                            @Param("estado") EstadoSolicitud estado,
                                            @Param("tipoPrestamo") TipoPrestamo tipoPrestamo,
                                            @Param("montoMinimo") BigDecimal montoMinimo,
                                            @Param("montoMaximo") BigDecimal montoMaximo,
                                            @Param("plazoMinimo") Integer plazoMinimo,
                                            @Param("plazoMaximo") Integer plazoMaximo,
                                            @Param("fechaDesde") LocalDateTime fechaDesde,
                                            @Param("fechaHasta") LocalDateTime fechaHasta,
                                            Pageable paginacion);

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.SolicitudProyeccion(
                       s, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM SolicitudPrestamoEntidad s
            JOIN ClienteEntidad c ON c.id = s.clienteId
            WHERE s.clienteId = :clienteId
            ORDER BY s.fechaSolicitud DESC, s.id DESC
            """)
    List<SolicitudProyeccion> listarDetallePorCliente(@Param("clienteId") Long clienteId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM SolicitudPrestamoEntidad s WHERE s.clienteId = :clienteId")
    int deleteByClienteId(@Param("clienteId") Long clienteId);
}
