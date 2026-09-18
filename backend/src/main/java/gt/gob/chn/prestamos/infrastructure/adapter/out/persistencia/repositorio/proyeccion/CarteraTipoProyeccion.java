package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import java.math.BigDecimal;

public interface CarteraTipoProyeccion {

    String getTipoPrestamo();

    long getCantidadPrestamos();

    BigDecimal getMontoAprobado();

    BigDecimal getSaldoPendiente();

    BigDecimal getTotalRecuperado();
}
