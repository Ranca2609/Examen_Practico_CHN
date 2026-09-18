package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.Prestamo;
import gt.gob.chn.prestamos.domain.model.consulta.PrestamoDetalle;
import gt.gob.chn.prestamos.domain.service.CuotaAmortizacion;
import gt.gob.chn.prestamos.domain.service.PlanAmortizacion;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.CuotaResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PlanAmortizacionResponse;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PrestamoResponse;
import java.util.List;

public final class MapeadorWebPrestamo {

    private MapeadorWebPrestamo() {
    }

    public static PrestamoResponse aRespuesta(PrestamoDetalle detalle) {
        Prestamo prestamo = detalle.prestamo();
        return new PrestamoResponse(
                prestamo.getId(),
                prestamo.getNumeroPrestamo(),
                prestamo.getSolicitudId(),
                detalle.numeroSolicitud(),
                prestamo.getClienteId(),
                detalle.nombreCliente(),
                detalle.identificacionCliente(),
                prestamo.getMontoAprobado(),
                prestamo.getPlazoMeses(),
                prestamo.getTasaInteresAnual(),
                prestamo.getCuotaMensual(),
                prestamo.getMontoTotalAPagar(),
                prestamo.getTotalPagado(),
                // Derivados del dominio: se exponen resueltos para no duplicar la formula en el frontend.
                prestamo.getSaldoPendiente(),
                prestamo.getPorcentajePagado(),
                prestamo.getEstado().name(),
                prestamo.getFechaDesembolso(),
                prestamo.getFechaVencimiento());
    }

    public static PlanAmortizacionResponse aRespuesta(PlanAmortizacion plan) {
        List<CuotaResponse> cuotas = plan.cuotas().stream()
                .map(MapeadorWebPrestamo::aRespuesta)
                .toList();
        return new PlanAmortizacionResponse(
                plan.cuotaMensual(),
                plan.totalIntereses(),
                plan.montoTotal(),
                cuotas);
    }

    private static CuotaResponse aRespuesta(CuotaAmortizacion cuota) {
        return new CuotaResponse(
                cuota.numero(),
                cuota.saldoInicial(),
                cuota.cuota(),
                cuota.abonoCapital(),
                cuota.abonoInteres(),
                cuota.saldoFinal());
    }
}
