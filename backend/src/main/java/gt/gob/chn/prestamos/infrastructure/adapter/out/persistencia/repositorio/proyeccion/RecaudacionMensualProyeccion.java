package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import java.math.BigDecimal;

public interface RecaudacionMensualProyeccion {

    int getAnio();

    int getMes();

    long getCantidadPagos();

    BigDecimal getMontoRecaudado();
}
