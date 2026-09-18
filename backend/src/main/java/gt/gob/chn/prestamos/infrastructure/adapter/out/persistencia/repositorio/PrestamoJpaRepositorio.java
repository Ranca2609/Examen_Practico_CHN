package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.domain.model.EstadoPrestamo;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PrestamoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PrestamoProyeccion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PrestamoJpaRepositorio extends JpaRepository<PrestamoEntidad, Long> {

    Optional<PrestamoEntidad> findBySolicitudId(Long solicitudId);

    long countByClienteIdAndEstado(Long clienteId, EstadoPrestamo estado);

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PrestamoProyeccion(
                       p, s.numeroSolicitud, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM PrestamoEntidad p
            JOIN SolicitudPrestamoEntidad s ON s.id = p.solicitudId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE p.id = :id
            """)
    Optional<PrestamoProyeccion> buscarDetallePorId(@Param("id") Long id);

    // saldo_pendiente no esta mapeado (columna calculada): se filtra con su expresion equivalente.
    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PrestamoProyeccion(
                       p, s.numeroSolicitud, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM PrestamoEntidad p
            JOIN SolicitudPrestamoEntidad s ON s.id = p.solicitudId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(p.numeroPrestamo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(s.numeroSolicitud) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:clienteId IS NULL OR p.clienteId = :clienteId)
              AND (:estado IS NULL OR p.estado = :estado)
              AND (:montoMinimo IS NULL OR p.montoAprobado >= :montoMinimo)
              AND (:montoMaximo IS NULL OR p.montoAprobado <= :montoMaximo)
              AND (:saldoMinimo IS NULL OR (p.montoTotalAPagar - p.totalPagado) >= :saldoMinimo)
              AND (:saldoMaximo IS NULL OR (p.montoTotalAPagar - p.totalPagado) <= :saldoMaximo)
              AND (:desembolsoDesde IS NULL OR p.fechaDesembolso >= :desembolsoDesde)
              AND (:desembolsoHasta IS NULL OR p.fechaDesembolso <= :desembolsoHasta)
              AND (:vencimientoDesde IS NULL OR p.fechaVencimiento >= :vencimientoDesde)
              AND (:vencimientoHasta IS NULL OR p.fechaVencimiento <= :vencimientoHasta)
            ORDER BY p.fechaCreacion DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(p.id)
            FROM PrestamoEntidad p
            JOIN SolicitudPrestamoEntidad s ON s.id = p.solicitudId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE (:busqueda IS NULL
                   OR LOWER(p.numeroPrestamo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(s.numeroSolicitud) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.apellido) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(CONCAT(c.nombre, ' ', c.apellido)) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                   OR LOWER(c.numeroIdentificacion) LIKE LOWER(CONCAT('%', :busqueda, '%')))
              AND (:clienteId IS NULL OR p.clienteId = :clienteId)
              AND (:estado IS NULL OR p.estado = :estado)
              AND (:montoMinimo IS NULL OR p.montoAprobado >= :montoMinimo)
              AND (:montoMaximo IS NULL OR p.montoAprobado <= :montoMaximo)
              AND (:saldoMinimo IS NULL OR (p.montoTotalAPagar - p.totalPagado) >= :saldoMinimo)
              AND (:saldoMaximo IS NULL OR (p.montoTotalAPagar - p.totalPagado) <= :saldoMaximo)
              AND (:desembolsoDesde IS NULL OR p.fechaDesembolso >= :desembolsoDesde)
              AND (:desembolsoHasta IS NULL OR p.fechaDesembolso <= :desembolsoHasta)
              AND (:vencimientoDesde IS NULL OR p.fechaVencimiento >= :vencimientoDesde)
              AND (:vencimientoHasta IS NULL OR p.fechaVencimiento <= :vencimientoHasta)
            """)
    Page<PrestamoProyeccion> buscarDetalle(@Param("busqueda") String busqueda,
                                           @Param("clienteId") Long clienteId,
                                           @Param("estado") EstadoPrestamo estado,
                                           @Param("montoMinimo") BigDecimal montoMinimo,
                                           @Param("montoMaximo") BigDecimal montoMaximo,
                                           @Param("saldoMinimo") BigDecimal saldoMinimo,
                                           @Param("saldoMaximo") BigDecimal saldoMaximo,
                                           @Param("desembolsoDesde") LocalDate desembolsoDesde,
                                           @Param("desembolsoHasta") LocalDate desembolsoHasta,
                                           @Param("vencimientoDesde") LocalDate vencimientoDesde,
                                           @Param("vencimientoHasta") LocalDate vencimientoHasta,
                                           Pageable paginacion);

    @Query(value = """
            SELECT new gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PrestamoProyeccion(
                       p, s.numeroSolicitud, c.nombre, c.apellido, c.numeroIdentificacion)
            FROM PrestamoEntidad p
            JOIN SolicitudPrestamoEntidad s ON s.id = p.solicitudId
            JOIN ClienteEntidad c ON c.id = p.clienteId
            WHERE p.clienteId = :clienteId
            ORDER BY p.fechaCreacion DESC, p.id DESC
            """)
    List<PrestamoProyeccion> listarDetallePorCliente(@Param("clienteId") Long clienteId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM PrestamoEntidad p WHERE p.clienteId = :clienteId")
    int deleteByClienteId(@Param("clienteId") Long clienteId);
}
