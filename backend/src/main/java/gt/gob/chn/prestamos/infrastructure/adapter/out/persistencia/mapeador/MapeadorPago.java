package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.mapeador;

import gt.gob.chn.prestamos.domain.model.Pago;
import gt.gob.chn.prestamos.domain.model.consulta.PagoDetalle;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PagoEntidad;
import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion.PagoProyeccion;
import org.springframework.stereotype.Component;

@Component
public class MapeadorPago {

    public Pago aDominio(PagoEntidad entidad) {
        if (entidad == null) {
            return null;
        }
        return Pago.reconstituir(
                entidad.getId(),
                entidad.getNumeroRecibo(),
                entidad.getPrestamoId(),
                entidad.getMonto(),
                entidad.getFechaPago(),
                entidad.getFormaPago(),
                entidad.getSaldoAnterior(),
                entidad.getSaldoPosterior(),
                entidad.getUsuarioRegistro(),
                entidad.getObservaciones());
    }

    public PagoEntidad aEntidad(Pago pago) {
        return new PagoEntidad(
                pago.getId(),
                pago.getNumeroRecibo(),
                pago.getPrestamoId(),
                pago.getMonto(),
                pago.getFechaPago(),
                pago.getFormaPago(),
                pago.getSaldoAnterior(),
                pago.getSaldoPosterior(),
                pago.getUsuarioRegistro(),
                pago.getObservaciones());
    }

    public PagoDetalle aDetalle(PagoProyeccion proyeccion) {
        return new PagoDetalle(
                aDominio(proyeccion.pago()),
                proyeccion.numeroPrestamo(),
                proyeccion.clienteId(),
                nombreCompleto(proyeccion.nombreCliente(), proyeccion.apellidoCliente()));
    }

    private String nombreCompleto(String nombre, String apellido) {
        return (nombre + " " + apellido).trim();
    }
}
