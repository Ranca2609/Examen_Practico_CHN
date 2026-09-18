package gt.gob.chn.prestamos.infrastructure.adapter.in.web.mapeador;

import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.domain.port.in.command.RegistrarPagoCommand;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.peticion.PagoRequest;
import gt.gob.chn.prestamos.infrastructure.adapter.in.web.dto.respuesta.PagoResponse;

public final class MapeadorWebPago {

    private MapeadorWebPago() {
    }

    public static RegistrarPagoCommand aComando(PagoRequest peticion) {
        return new RegistrarPagoCommand(
                peticion.prestamoId(),
                peticion.monto(),
                peticion.observaciones());
    }

    public static PagoResponse aRespuesta(PagoDetalle detalle) {
        Pago pago = detalle.pago();
        return new PagoResponse(
                pago.getId(),
                pago.getNumeroRecibo(),
                pago.getPrestamoId(),
                detalle.numeroPrestamo(),
                detalle.clienteId(),
                detalle.nombreCliente(),
                pago.getMonto(),
                pago.getFechaPago(),
                pago.getFormaPago().name(),
                pago.getSaldoAnterior(),
                pago.getSaldoPosterior(),
                pago.getUsuarioRegistro(),
                pago.getObservaciones());
    }
}
