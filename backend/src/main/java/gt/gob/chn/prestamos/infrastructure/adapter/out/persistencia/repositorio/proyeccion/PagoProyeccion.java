package gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.repositorio.proyeccion;

import gt.gob.chn.prestamos.infrastructure.adapter.out.persistencia.entidad.PagoEntidad;

public record PagoProyeccion(
        PagoEntidad pago,
        String numeroPrestamo,
        Long clienteId,
        String nombreCliente,
        String apellidoCliente) {
}
