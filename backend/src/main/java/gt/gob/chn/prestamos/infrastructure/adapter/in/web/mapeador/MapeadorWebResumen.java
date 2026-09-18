package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.CarteraPorTipo;
import gt.gob.chn.prestamos.domain.model.RecaudacionMensual;
import gt.gob.chn.prestamos.domain.model.ResumenGeneral;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.CarteraTipoResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.RecaudacionMensualResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.ResumenResponse;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public final class MapeadorWebResumen {

    // AAAA-MM: el orden alfabetico coincide con el cronologico.
    private static final DateTimeFormatter FORMATO_PERIODO = DateTimeFormatter.ofPattern("uuuu-MM");

    private MapeadorWebResumen() {
    }

    public static ResumenResponse aRespuesta(ResumenGeneral resumen) {
        return new ResumenResponse(
                resumen.totalClientes(),
                resumen.solicitudesEnProceso(),
                resumen.solicitudesAprobadas(),
                resumen.solicitudesRechazadas(),
                resumen.prestamosVigentes(),
                resumen.prestamosLiquidados(),
                resumen.montoTotalAprobado(),
                resumen.saldoPendienteTotal(),
                resumen.totalRecuperado(),
                resumen.carteraPorTipo().stream().map(MapeadorWebResumen::aCartera).toList(),
                resumen.recaudacionMensual().stream().map(MapeadorWebResumen::aRecaudacion).toList());
    }

    private static CarteraTipoResponse aCartera(CarteraPorTipo cartera) {
        return new CarteraTipoResponse(
                cartera.tipoPrestamo().name(),
                cartera.cantidadPrestamos(),
                cartera.montoAprobado(),
                cartera.saldoPendiente(),
                cartera.totalRecuperado());
    }

    private static RecaudacionMensualResponse aRecaudacion(RecaudacionMensual recaudacion) {
        YearMonth periodo = recaudacion.periodo();
        return new RecaudacionMensualResponse(
                periodo.format(FORMATO_PERIODO),
                periodo.getYear(),
                periodo.getMonthValue(),
                recaudacion.cantidadPagos(),
                recaudacion.monto());
    }
}
