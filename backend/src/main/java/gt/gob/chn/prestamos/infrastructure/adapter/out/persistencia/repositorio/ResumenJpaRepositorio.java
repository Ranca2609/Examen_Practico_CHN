package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.ClienteEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.CarteraTipoProyeccion;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.RecaudacionMensualProyeccion;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.ResumenProyeccion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// ClienteEntidad solo cumple el requisito de Spring Data; Repository (no JpaRepository) evita exponer escrituras.
public interface ResumenJpaRepositorio extends Repository<ClienteEntidad, Long> {

    @Query(nativeQuery = true, value = """
            SELECT total_clientes          AS totalClientes,
                   solicitudes_en_proceso  AS solicitudesEnProceso,
                   solicitudes_aprobadas   AS solicitudesAprobadas,
                   solicitudes_rechazadas  AS solicitudesRechazadas,
                   prestamos_vigentes      AS prestamosVigentes,
                   prestamos_liquidados    AS prestamosLiquidados,
                   monto_total_aprobado    AS montoTotalAprobado,
                   saldo_pendiente_total   AS saldoPendienteTotal,
                   total_recuperado        AS totalRecuperado
            FROM dbo.vw_resumen_general
            """)
    Optional<ResumenProyeccion> obtenerResumen();

    // Sin ORDER BY: el orden lo fija el dominio.
    @Query(nativeQuery = true, value = """
            SELECT tipo_prestamo       AS tipoPrestamo,
                   cantidad_prestamos  AS cantidadPrestamos,
                   monto_aprobado      AS montoAprobado,
                   saldo_pendiente     AS saldoPendiente,
                   total_recuperado    AS totalRecuperado
            FROM dbo.vw_cartera_por_tipo
            """)
    List<CarteraTipoProyeccion> carteraPorTipo();

    // Extremos AAAAMM: anio * 100 + mes acota con un solo BETWEEN rangos que cruzan de diciembre a enero.
    @Query(nativeQuery = true, value = """
            SELECT anio             AS anio,
                   mes              AS mes,
                   cantidad_pagos   AS cantidadPagos,
                   monto_recaudado  AS montoRecaudado
            FROM dbo.vw_recaudacion_mensual
            WHERE (anio * 100 + mes) BETWEEN :desde AND :hasta
            ORDER BY anio, mes
            """)
    List<RecaudacionMensualProyeccion> recaudacionMensual(@Param("desde") int desde,
                                                          @Param("hasta") int hasta);
}
