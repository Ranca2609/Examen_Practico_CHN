package gt.gob.chn.prestamos.domain.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface RelojPort {

    // Zona America/Guatemala. El dominio no llama a now() para poder fijar el reloj en pruebas.
    LocalDateTime ahora();

    LocalDate hoy();
}
