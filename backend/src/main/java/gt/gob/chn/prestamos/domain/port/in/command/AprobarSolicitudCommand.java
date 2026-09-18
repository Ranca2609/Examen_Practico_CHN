package gt.gob.chn.prestamos.domain.port.in.command;

import java.math.BigDecimal;

public record AprobarSolicitudCommand(
        BigDecimal montoAprobado,
        Integer plazoAprobadoMeses,
        BigDecimal tasaAprobada,
        String motivo) {
}
