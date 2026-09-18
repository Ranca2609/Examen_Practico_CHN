package gt.gob.chn.prestamos.domain.port.in.command;

import gt.gob.chn.prestamos.domain.model.TipoPrestamo;
import java.math.BigDecimal;

public record CrearSolicitudCommand(
        Long clienteId,
        BigDecimal montoSolicitado,
        Integer plazoMeses,
        BigDecimal tasaInteresAnual,
        TipoPrestamo tipoPrestamo,
        String destino,
        BigDecimal ingresoMensualDeclarado,
        String observaciones) {
}
