package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PagoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PagoProyeccion;
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

public interface PagoJpaRepositorio extends JpaRepository<PagoEntidad, Long> {

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PagoProyeccion(
                       g, p.numeroPrestamo, c.id, c.nombre, c.apellido)
            FROM PagoEntidad g
            JOIN PrestamoEntidad p ON p.id = g.prestamoId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE g.id = :id
            """)
    Optional<PagoProyeccion> buscarDetallePorId(@Param("id") Long id);

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PagoProyeccion(
                       g, p.numeroPrestamo, c.id, c.nombre, c.apellido)
            FROM PagoEntidad g
            JOIN PrestamoEntidad p ON p.id = g.prestamoId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE g.prestamoId = :prestamoId
            ORDER BY g.fechaPago DESC, g.id DESC
            """)
    List<PagoProyeccion> listarDetallePorPrestamo(@Param("prestamoId") Long prestamoId);

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PagoProyeccion(
                       g, p.numeroPrestamo, c.id, c.nombre, c.apellido)
            FROM PagoEntidad g
            JOIN PrestamoEntidad p ON p.id = g.prestamoId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(g.numeroRecibo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(p.numeroPrestamo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:prestamoId IS NULL OR g.prestamoId = :prestamoId)
              AND (:clienteId IS NULL OR p.clienteId = :clienteId)
              AND (:montoMinimo IS NULL OR g.monto >= :montoMinimo)
              AND (:montoMaximo IS NULL OR g.monto <= :montoMaximo)
              AND (:fechaDesde IS NULL OR g.fechaPago >= :fechaDesde)
              AND (:fechaHasta IS NULL OR g.fechaPago < :fechaHasta)
              AND (:usuarioRegistro IS NULL
                   OR LOWER(g.usuarioRegistro) LIKE LOWER(CONCAT('%', :usuarioRegistro, '%')))
            ORDER BY g.fechaPago DESC, g.id DESC
            """,
            countQuery = """
            SELECT COUNT(g.id)
            FROM PagoEntidad g
            JOIN PrestamoEntidad p ON p.id = g.prestamoId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(g.numeroRecibo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(p.numeroPrestamo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:prestamoId IS NULL OR g.prestamoId = :prestamoId)
              AND (:clienteId IS NULL OR p.clienteId = :clienteId)
              AND (:montoMinimo IS NULL OR g.monto >= :montoMinimo)
              AND (:montoMaximo IS NULL OR g.monto <= :montoMaximo)
              AND (:fechaDesde IS NULL OR g.fechaPago >= :fechaDesde)
              AND (:fechaHasta IS NULL OR g.fechaPago < :fechaHasta)
              AND (:usuarioRegistro IS NULL
                   OR LOWER(g.usuarioRegistro) LIKE LOWER(CONCAT('%', :usuarioRegistro, '%')))
            """)
    Page<PagoProyeccion> buscarDetalle(@Param("busqueda") String busqueda,
                                        @Param("prestamoId") Long prestamoId,
                                        @Param("clienteId") Long clienteId,
                                        @Param("montoMinimo") BigDecimal montoMinimo,
                                        @Param("montoMaximo") BigDecimal montoMaximo,
                                        @Param("fechaDesde") LocalDateTime fechaDesde,
                                        @Param("fechaHasta") LocalDateTime fechaHasta,
                                        @Param("usuarioRegistro") String usuarioRegistro,
                                        Pageable paginacion);

    // El pago no guarda el cliente: se localiza por subconsulta sobre sus prestamos.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            DELETE FROM PagoEntidad g
            WHERE g.prestamoId IN (SELECT p.id FROM PrestamoEntidad p WHERE p.clienteId = :clienteId)
            """)
    int eliminarPorCliente(@Param("clienteId") Long clienteId);
}
