package gt.gob.chn.prestamos.domain.model.consulta;

import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.Validaciones;

public record PagoDetalle(
        Pago pago,
        String numeroPrestamo,
        Long clienteId,
        String nombreCliente) {

    public PagoDetalle {
        Validaciones.exigirNoNulo(pago, "pago");
    }
}
