package gt.gob.chn.prestamos.domain.port.in.command;

import java.math.BigDecimal;

public record SimularSolicitudCommand(
        // Opcional: si viene, la evaluacion considera sus prestamos vigentes.
        Long clienteId,
        BigDecimal monto,
        Integer plazoMeses,
        BigDecimal tasaInteresAnual,
        BigDecimal ingresoMensual) {
}
