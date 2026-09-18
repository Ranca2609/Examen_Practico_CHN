package gt.gob.chn.prestamos.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record ResumenGeneral(
        long totalClientes,
        long solicitudesEnProceso,
        long solicitudesAprobadas,
        long solicitudesRechazadas,
        long prestamosVigentes,
        long prestamosLiquidados,
        BigDecimal montoTotalAprobado,
        BigDecimal saldoPendienteTotal,
        BigDecimal totalRecuperado,
        List<CarteraPorTipo> carteraPorTipo,
        List<RecaudacionMensual> recaudacionMensual) {

    public ResumenGeneral {
        montoTotalAprobado = Montos.ceroSiNulo(montoTotalAprobado);
        saldoPendienteTotal = Montos.ceroSiNulo(saldoPendienteTotal);
        totalRecuperado = Montos.ceroSiNulo(totalRecuperado);
        carteraPorTipo = carteraPorTipo == null ? List.of() : List.copyOf(carteraPorTipo);
        recaudacionMensual = recaudacionMensual == null ? List.of() : List.copyOf(recaudacionMensual);
    }

    // Solo totales, como los entrega vw_resumen_general: las series dependen del mes en curso,
    // que conoce el caso de uso y no la persistencia, y se agregan con conSeries().
    public ResumenGeneral(long totalClientes, long solicitudesEnProceso, long solicitudesAprobadas,
                          long solicitudesRechazadas, long prestamosVigentes, long prestamosLiquidados,
                          BigDecimal montoTotalAprobado, BigDecimal saldoPendienteTotal,
                          BigDecimal totalRecuperado) {
        this(totalClientes, solicitudesEnProceso, solicitudesAprobadas, solicitudesRechazadas,
                prestamosVigentes, prestamosLiquidados, montoTotalAprobado, saldoPendienteTotal,
                totalRecuperado, List.of(), List.of());
    }

    public static ResumenGeneral vacio() {
        return new ResumenGeneral(0L, 0L, 0L, 0L, 0L, 0L, Montos.CERO, Montos.CERO, Montos.CERO,
                List.of(), List.of());
    }

    public ResumenGeneral conSeries(List<CarteraPorTipo> cartera, List<RecaudacionMensual> recaudacion) {
        return new ResumenGeneral(totalClientes, solicitudesEnProceso, solicitudesAprobadas,
                solicitudesRechazadas, prestamosVigentes, prestamosLiquidados, montoTotalAprobado,
                saldoPendienteTotal, totalRecuperado, cartera, recaudacion);
    }
}
