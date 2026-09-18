package gt.gob.chn.prestamos.domain.service;

import java.math.BigDecimal;
import java.util.List;

public record PlanAmortizacion(
        BigDecimal cuotaMensual,
        BigDecimal totalIntereses,
        BigDecimal montoTotal,
        List<CuotaAmortizacion> cuotas) {

    public PlanAmortizacion {
        cuotas = cuotas == null ? List.of() : List.copyOf(cuotas);
    }
}
