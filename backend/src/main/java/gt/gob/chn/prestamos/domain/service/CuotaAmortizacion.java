package gt.gob.chn.prestamos.domain.service;

import java.math.BigDecimal;

public record CuotaAmortizacion(
        int numero,
        BigDecimal saldoInicial,
        BigDecimal cuota,
        BigDecimal abonoCapital,
        BigDecimal abonoInteres,
        BigDecimal saldoFinal) {
}
