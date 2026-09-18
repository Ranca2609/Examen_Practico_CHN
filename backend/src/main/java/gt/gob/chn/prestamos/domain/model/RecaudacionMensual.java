package gt.gob.chn.prestamos.domain.model;

import gt.gob.chn.prestamos.domain.exception.ValidacionDominioException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record RecaudacionMensual(
        YearMonth periodo,
        long cantidadPagos,
        BigDecimal monto) {

    public RecaudacionMensual {
        Validaciones.exigirNoNulo(periodo, "periodo");
        if (cantidadPagos < 0) {
            throw new ValidacionDominioException("La cantidad de pagos no puede ser negativa.");
        }
        monto = Validaciones.exigirNoNegativo(Montos.ceroSiNulo(monto), "monto recaudado");
    }

    public static RecaudacionMensual sinPagos(YearMonth periodo) {
        return new RecaudacionMensual(periodo, 0L, Montos.CERO);
    }

    /** Serie contigua desde..hasta con ceros en los meses sin pagos: la grafica necesita un punto por mes. */
    public static List<RecaudacionMensual> completar(
            List<RecaudacionMensual> recaudaciones, YearMonth desde, YearMonth hasta) {
        Validaciones.exigirNoNulo(desde, "mes inicial");
        Validaciones.exigirNoNulo(hasta, "mes final");
        Validaciones.exigirOrdenCronologico(desde, hasta, "recaudacion mensual");

        Map<YearMonth, RecaudacionMensual> porMes = new HashMap<>();
        if (recaudaciones != null) {
            for (RecaudacionMensual recaudacion : recaudaciones) {
                Validaciones.exigirNoNulo(recaudacion, "recaudacion mensual");
                YearMonth mes = recaudacion.periodo();
                // Un mes fuera de la ventana no es error de quien consulta el tablero: se ignora.
                if (!mes.isBefore(desde) && !mes.isAfter(hasta)) {
                    porMes.merge(mes, recaudacion, RecaudacionMensual::sumar);
                }
            }
        }

        List<RecaudacionMensual> serie = new ArrayList<>();
        for (YearMonth mes = desde; !mes.isAfter(hasta); mes = mes.plusMonths(1)) {
            RecaudacionMensual recaudacion = porMes.get(mes);
            serie.add(recaudacion != null ? recaudacion : sinPagos(mes));
        }
        return List.copyOf(serie);
    }

    private RecaudacionMensual sumar(RecaudacionMensual otra) {
        return new RecaudacionMensual(periodo, cantidadPagos + otra.cantidadPagos, monto.add(otra.monto));
    }
}
