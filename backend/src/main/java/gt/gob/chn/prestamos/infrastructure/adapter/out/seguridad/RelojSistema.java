package gt.gob.chn.prestamos.infrastructure.adapter.out.seguridad;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import gt.gob.chn.prestamos.domain.port.out.RelojPort;

@Component
public class RelojSistema implements RelojPort {

    // Zona fija del negocio: el contenedor corre en UTC y correría las fechas.
    private static final ZoneId ZONA_GUATEMALA = ZoneId.of("America/Guatemala");

    private final Clock reloj;

    public RelojSistema() {
        this.reloj = Clock.system(ZONA_GUATEMALA);
    }

    @Override
    public LocalDateTime ahora() {
        // DATETIME2(0) no guarda fracciones: truncar evita diferencias entre memoria y BD.
        return LocalDateTime.now(reloj).withNano(0);
    }

    @Override
    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }
}
