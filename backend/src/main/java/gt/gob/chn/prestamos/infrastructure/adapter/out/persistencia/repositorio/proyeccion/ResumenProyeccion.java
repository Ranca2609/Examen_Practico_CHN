package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import java.math.BigDecimal;

// Interfaz y no record: Spring Data materializa las consultas nativas por alias de columna.
public interface ResumenProyeccion {

    long getTotalClientes();

    long getSolicitudesEnProceso();

    long getSolicitudesAprobadas();

    long getSolicitudesRechazadas();

    long getPrestamosVigentes();

    long getPrestamosLiquidados();

    BigDecimal getMontoTotalAprobado();

    BigDecimal getSaldoPendienteTotal();

    BigDecimal getTotalRecuperado();
}
