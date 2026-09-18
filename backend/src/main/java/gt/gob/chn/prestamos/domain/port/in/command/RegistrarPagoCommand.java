package gt.gob.chn.prestamos.domain.port.in.command;

import java.math.BigDecimal;

public record RegistrarPagoCommand(
        Long prestamoId,
        BigDecimal monto,
        String observaciones) {
}
